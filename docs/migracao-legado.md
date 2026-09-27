# Migração do sistema PHP legado — runbook

Ferramenta **offline** que leva os dados do banco antigo `petshop_db` (PHP/MySQL) para o PetConnect Web. Ela roda uma vez, na virada, e fica desligada no uso normal. Não existe tela nem endpoint para ela.

Especificação: [next-stage/08-migracao-legado.md](next-stage/08-migracao-legado.md).

## Como funciona (resumo)

**Leitura e simulação**
- O banco legado é só **lido**: por uma conexão `readOnly` e por um usuário SQL que só tem `SELECT`.
- **DRY-RUN é o padrão.** A simulação executa todas as regras dentro de uma transação que termina em rollback. O relatório sai completo e nada fica no banco novo.
- A execução **real** só acontece com `MIGRACAO_DRY_RUN=false` (ou `--petconnect.migracao.dry-run=false`).

**Ordem e retomada**
- Os dados entram em ordem fixa, respeitando as chaves estrangeiras:
  papel → usuário → cliente → funcionário → fornecedor → espécie → raça → vacina → serviço → preço por porte → categoria → pet → foto → carteira → consulta → produto → saldo inicial → compra → agendamento → serviço realizado → venda → financeiro → backfill financeiro → conferências.
- A leitura é em lotes de 500, paginados por id.
- **Idempotente e retomável.** A tabela `migracao_legado_mapa` (UNIQUE entidade + id legado) registra cada registro legado já processado.
  - Se uma execução real falhar no meio, o que já foi confirmado fica gravado.
  - A próxima execução continua de onde parou.
  - Rodar de novo depois de concluída não cria nada.

**Relatório**
- Cada execução gera `migracao-legado-<id>-<dry-run|real>.md`.
- Ele traz contagens por entidade, **pendências por id legado** e o resultado das conferências.
- Não traz nome, CPF, e-mail, telefone ou endereço.

## Pré-requisitos

1. Stack normal funcionando (`docker compose up -d`). O banco novo precisa estar com as migrations V1–V10 aplicadas; a API aplica ao subir.
2. `.env` com as variáveis da seção "migração do legado" do `.env.example`. **Nunca** use nelas a senha real de outro sistema.
3. O dump do banco antigo, gerado com:
   ```bash
   mysqldump --single-transaction --routines --default-character-set=utf8mb4 -u <usuario> -p petshop_db > docker/migracao/dados-reais/petshop_db.sql
   ```
   A pasta `docker/migracao/dados-reais/` está no `.gitignore`: **dump real nunca vai para o git**.
4. Opcional: a pasta de uploads do PHP com as fotos dos pets. Copie para `docker/migracao/dados-reais/fotos` e aponte `MIGRACAO_FOTOS_DIR` para ela.
5. **Em host Linux:** a imagem da API roda com um usuário sem privilégio de root, que precisa conseguir gravar os relatórios. Antes da primeira execução, rode `mkdir -p .migracao/relatorios && chmod 777 .migracao/relatorios`. No Docker Desktop (Windows/macOS) isso não é necessário.
6. **Acentos:** o dump precisa carregar como UTF-8. O `mysqldump` do passo 3 já grava `SET NAMES`; se o arquivo vier de outra ferramenta, garanta que a primeira linha seja `SET NAMES utf8mb4;`. Sem isso, valores como `'Não compareceu'` chegam corrompidos e viram pendência.

## 1. Backup do banco novo (obrigatório antes da execução real)

```bash
docker compose exec mysql sh -c 'mysqldump --single-transaction -u root -p"$MYSQL_ROOT_PASSWORD" petconnect_web' > .migracao/backup-antes.sql
docker compose exec mongo mongodump --archive --db petconnect_web > .migracao/backup-mongo-antes.archive
```

**Teste o restore** antes de seguir. Suba um MySQL descartável, importe `backup-antes.sql` e confira a contagem das tabelas. A migração não tem "desfazer": o único rollback é restaurar este backup.

## 2. Subir o banco legado

**Primeiro teste:** use a fixture sintética. É o padrão, sem nenhuma configuração.

```bash
docker compose --profile migracao up -d mysql-legado
```

**Com o dump real:** no `.env`, defina as duas linhas abaixo e recrie o volume.

```
LEGADO_SCHEMA_SQL=./docker/migracao/vazio.sql
LEGADO_DADOS_SQL=./docker/migracao/dados-reais/petshop_db.sql
```

```bash
docker compose --profile migracao down -v mysql-legado
docker compose --profile migracao up -d mysql-legado
```

> `down -v` apaga **só** o volume `mysql-legado-data` quando você passa o nome do serviço.
> Confira duas vezes. **Nunca** rode `down -v` sem o nome do serviço: isso apaga o banco novo.

Confira que o usuário de leitura não consegue escrever (deve dar erro de permissão):

```bash
docker compose exec mysql-legado sh -c 'mysql -u "$LEGADO_DB_USERNAME" -p"$LEGADO_DB_PASSWORD" petshop_db -e "CREATE TABLE teste (id INT)"'
```

## 3. Dry-run (simulação)

```bash
docker compose --profile migracao run --rm api-migracao
```

- Código de saída 0 quer dizer que a simulação terminou e as conferências passaram.
- O relatório fica em `.migracao/relatorios/migracao-legado-<id>-dry-run.md`.

### Como ler o relatório

- **Tabela por entidade:**

  | Coluna | Significado |
  |---|---|
  | Lidos | registros encontrados no legado |
  | Migrados | gravados sem ajuste |
  | Ajustados | gravados com alguma correção, registrada como pendência |
  | Ignorados | não gravados |
  | Já migrados | vieram de uma execução anterior |

- **Pendências:** cada linha tem o formato `ENTIDADE legado #id — CÓDIGO`. Consulte o registro pelo id no banco antigo. Os códigos mais comuns:

  | Código | O que significa / o que fazer |
  |---|---|
  | `USUARIO_SEM_EMAIL_RECUPERACAO` | usuário migrado **inativo**; sem e-mail, não consegue redefinir a senha. Cadastre o e-mail e ative pela tela de Usuários |
  | `USERNAME_RENOMEADO` | o login antigo colidiu (ex.: com o ADMIN do bootstrap) e virou `<nome>_legado` |
  | `CPF_INVALIDO`, `CPF_DUPLICADO` | CPF ficou em branco; o cadastro recebeu uma observação (sem o número) |
  | `DOCUMENTO_INVALIDO_SCHEMA_OBRIGATORIO` | funcionário ou fornecedor **não migrado**: CPF/CNPJ é obrigatório no sistema novo. Corrija no legado e rode de novo |
  | `TELEFONE_INVALIDO_SCHEMA_OBRIGATORIO`, `TELEFONE_DUPLICADO_SCHEMA_OBRIGATORIO` | cliente **não migrado**: telefone é obrigatório e único. Os pets dele aparecem como `CLIENTE_NAO_MIGRADO` |
  | `ENDERECO_CIDADE_NAO_INFORMADA`, `ENDERECO_UF_NAO_INFORMADA` | o legado não tem cidade. Fica "Não informada"/"NI", a menos que o ViaCEP esteja ligado |
  | `ENDERECO_INCOMPLETO` | sem CEP válido ou sem rua: o endereço não foi criado |
  | `CATALOGO_CRIADO`, `VACINA_CRIADA` | espécie, raça, vacina, serviço ou categoria criada porque não casou com o catálogo |
  | `SALDO_NEGATIVO` | o estoque legado era negativo e entrou como 0 |
  | `CODIGO_BARRAS_DUPLICADO` | o segundo produto com o mesmo código ficou sem código |
  | `VENDA_TOTAL_INVALIDO`, `ITEM_SEM_PRODUTO` | venda inteira não migrada |
  | `VALOR_DIVERGENTE_ITENS` | a soma dos itens menos o desconto difere do total. O total histórico foi mantido |
  | `REGENERADO_PELO_BACKFILL` | lançamento de venda/serviço do legado. O sistema novo gera o próprio a partir da venda/serviço migrado |
  | `SERVICOS_SEM_FORMA_PAGAMENTO (n)` | serviços antigos sem lançamento financeiro, porque o legado não guarda a forma. Defina `MIGRACAO_BACKFILL_SERVICO_FORMA` para gerá-los |
  | `FOTO_AUSENTE`, `FOTO_CAMINHO_INVALIDO`, `FOTO_INVALIDA` | pet migrado sem foto |

- **Conferências:** todas precisam estar `PASS`.

Corrija no legado o que quiser e repita o dry-run quantas vezes precisar.

## 4. Execução real

1. Confirme que o backup do passo 1 existe e que o restore foi testado.
2. Rode:
   ```bash
   MIGRACAO_DRY_RUN=false docker compose --profile migracao run --rm api-migracao
   ```
   (No PowerShell: `$env:MIGRACAO_DRY_RUN="false"; docker compose --profile migracao run --rm api-migracao`.)
3. Leia `.migracao/relatorios/migracao-legado-<id>-real.md`.
   - **Status `CONCLUIDA`:** confira no sistema clientes, pets, produtos, vendas e o relatório de vendas do período antigo.
   - **Status `FALHOU`:** a mensagem diz a entidade e o id legado onde parou (sem dados pessoais). Corrija a causa e rode o mesmo comando de novo; ele retoma de onde parou.
4. Avise os usuários migrados: eles entram pelo **"Esqueci minha senha"**. A senha antiga (SHA-256 sem sal) **não** foi migrada e não funciona.

## 5. Reexecução

Rodar de novo é seguro: registros no mapa contam como "Já migrados". Um registro **ignorado** também fica no mapa; se você o corrigir no legado depois, ele não é reprocessado.

Para reprocessar só esse registro:
1. Faça backup.
2. Remova a linha dele em `migracao_legado_mapa`: `DELETE ... WHERE entidade = 'x' AND id_legado = n`, na situação `IGNORADO`.
3. Rode de novo.

Isso é manutenção manual e excepcional.

## 6. Rollback

Não há rollback automático. Para desfazer uma execução real:
1. Pare a API.
2. Restaure `backup-antes.sql` (MySQL) e o `mongodump`.
3. Suba de novo.

## Configuração (referência)

| Propriedade | Variável | Padrão |
|---|---|---|
| `petconnect.migracao.legado` | `PETCONNECT_MIGRACAO_LEGADO` | `false` (runner desligado) |
| `petconnect.migracao.dry-run` | `PETCONNECT_MIGRACAO_DRY_RUN` | `true` |
| `petconnect.migracao.lote` | `PETCONNECT_MIGRACAO_LOTE` | `500` |
| `petconnect.migracao.relatorio-dir` | `PETCONNECT_MIGRACAO_RELATORIO_DIR` | `build/migracao-relatorios` |
| `petconnect.migracao.fotos-dir` | `PETCONNECT_MIGRACAO_FOTOS_DIR` | vazio (fotos não importadas) |
| `petconnect.migracao.viacep.enabled` | `PETCONNECT_MIGRACAO_VIACEP` | `false` |
| `petconnect.migracao.viacep.timeout-ms` | `PETCONNECT_MIGRACAO_VIACEP_TIMEOUT_MS` | `1500` |
| `petconnect.migracao.backfill-servico-forma` | `PETCONNECT_MIGRACAO_BACKFILL_SERVICO_FORMA` | vazio (serviços pendentes) |
| `petconnect.migracao.encerrar-ao-final` | `PETCONNECT_MIGRACAO_ENCERRAR` | `true` (a aplicação sai ao terminar) |
| `spring.datasource.legacy.url/username/password` | `LEGADO_DB_URL` / `LEGADO_DB_USERNAME` / `LEGADO_DB_PASSWORD` | obrigatórios com a migração ligada |

Fora do Docker (API local), a partir de `backend/`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--petconnect.migracao.legado=true --spring.main.web-application-type=none"
```

## LGPD

- **Minimização**
  - Relatórios, mapa, histórico de execuções, auditoria (`MIGRACAO_LEGADO_EXECUTADA`, só contagens) e logs levam apenas ids legados, códigos e números.
  - A migração nunca lê `senha_hash`, `token_senha` nem `token_expira` do legado.
- **Testes**
  - Usam só a fixture **sintética** (`backend/src/test/resources/legacy/`).
  - O script de inserção com dados reais em `docs/legacy/` nunca é executado em teste nem em CI.
- **Depois da migração**
  - Apague `docker/migracao/dados-reais/` e os backups temporários que não forem mais necessários.
  - Guarde o backup oficial em local protegido.
- **Recomendação ao dono do repositório** (não é feita automaticamente)
  - O script de inserção versionado em `docs/legacy/documentacao/Banco de Dados/Scripts do Banco/` contém dados pessoais reais.
  - Avalie removê-lo do histórico com `git filter-repo`, o que exige force-push e aviso aos colaboradores.
  - Revogue e rotacione qualquer credencial que tenha aparecido no histórico, como a senha `admin123` do legado e as senhas de banco antigas.
