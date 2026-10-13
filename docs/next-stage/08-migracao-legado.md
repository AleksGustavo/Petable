# Etapa 08 — Migração dos dados legados (PHP/MySQL → PetConnect Web)

## 1. Objetivo

Implementar uma migração única, controlada, idempotente, auditável e retomável dos dados do banco legado `petshop_db` para o PetConnect Web, sem alterar o legado, sem reutilizar credenciais inseguras e sem violar as invariantes introduzidas nas etapas 00–07.

A migração será executada exclusivamente pelo backend através do módulo `migracao`, usando uma segunda `DataSource` somente leitura. O modo padrão será DRY-RUN. A gravação real exigirá habilitação explícita. Não haverá tela nem endpoint HTTP.

Entregáveis obrigatórios:

- migration `V10__migracao_legado.sql`;
- módulo `br.com.tecnodreamer.petconnect.migracao`;
- `MigracaoLegadoRunner` baseado em `ApplicationRunner`;
- `LegacyDataSourceConfig` independente da DataSource principal;
- mapa persistente legado → novo;
- histórico de execuções sem PII;
- normalizadores/conversores explícitos;
- migração em ordem fixa e lotes de 500;
- DRY-RUN com rollback;
- execução real retomável;
- relatório Markdown sem dados pessoais;
- conferências automáticas;
- fixture sintética anonimizada;
- testes unitários e de integração;
- perfil Docker de migração e runbook.

## 2. Análise obrigatória

Antes de codificar, o executor deve:

1. Ler V1–V9 e confirmar o schema físico atual. Nunca modificar migrations aplicadas.
2. Ler o script legado em `docs/legacy/documentacao/Banco de Dados/Scripts do Banco/pet_e_pet_DB - Script do Banco com Procedures e Functions.sql` apenas como referência.
3. NÃO executar nem copiar para testes o script legado que contém dados pessoais reais.
4. Inspecionar entidades, repositories e services atuais de usuário, cliente, funcionário, fornecedor, pet, vacina, agendamento, produto, estoque, compra, venda, serviço e financeiro.
5. Inspecionar `EstoqueService`, `FinanceiroService`, `FinanceiroBackfillRunner`, serviço de foto, `PoliticaSenha`, `Documentos`, `Cnpj`, `AuditoriaService` e bootstrap ADMIN.
6. Inspecionar constraints UNIQUE, CHECK, FKs, campos `ativo`, índices e enums reais do banco novo.
7. Consultar no banco legado, em modo somente leitura, os valores distintos de todos os campos categóricos antes da execução real. No mínimo:
   - `usuario.ativo`;
   - papéis/cargos;
   - `funcionario.sexo`;
   - `pet.porte`, `castrado`, `vacinado`, `ativo`;
   - `agendamento.status`;
   - `compra.status`;
   - `venda.forma_pagamento`;
   - `movimentacao_financeira.tipo` e `categoria`;
   - nomes distintos de espécies, raças, vacinas, serviços e categorias.
8. Registrar valores não reconhecidos no relatório como pendência, nunca adivinhar silenciosamente.
9. Rodar toda a suíte atual antes da alteração e registrar baseline.
10. Fazer backup validado do banco novo antes da execução real.

## 3. Decisões arquiteturais

| ID | Decisão | Justificativa |
|---|---|---|
| DA-01 | Criar módulo `migracao` isolado. | Evita contaminar regras normais com lógica temporária. |
| DA-02 | Executar via `MigracaoLegadoRunner implements ApplicationRunner`. | Migração não é funcionalidade HTTP. |
| DA-03 | Runner só ativa com `petconnect.migracao.legado=true`. | Impede execução acidental. |
| DA-04 | DRY-RUN é `true` por padrão. | A primeira execução nunca grava por acidente. |
| DA-05 | Execução real exige `--petconnect.migracao.dry-run=false`. | Consentimento operacional explícito. |
| DA-06 | Segunda DataSource exclusiva para legado, com usuário SQL somente leitura. | Backend nunca deve escrever no sistema antigo. |
| DA-07 | V10 cria `migracao_legado_mapa` e `migracao_legado_execucao`. | Idempotência e rastreabilidade persistentes. |
| DA-08 | Ordem de entidades é fixa e respeita FKs. | Evita referências órfãs. |
| DA-09 | Processamento em lotes de 500. | Limita memória, lock e tamanho transacional. |
| DA-10 | Execução real confirma por entidade/lote; falha interrompe entidades posteriores. | Permite retomada segura. |
| DA-11 | DRY-RUN executa lógica real, mas toda gravação no banco novo é revertida. | Valida conversões sem persistência. |
| DA-12 | Senhas SHA-256 não são migradas. | Hash legado é inseguro e incompatível. |
| DA-13 | Usuário recebe BCrypt de segredo aleatório descartado. | Senha antiga nunca autentica; acesso ocorre por recuperação. |
| DA-14 | Usuário sem e-mail é migrado inativo e vira pendência. | Não existe canal seguro de recuperação. |
| DA-15 | Username que colide com bootstrap vira `<username>_legado`, com sufixo numérico se necessário. | Nunca sobrescrever ADMIN atual. |
| DA-16 | `super_usuario` mapeia para papel ADMIN. | Preserva privilégio administrativo documentado. |
| DA-17 | Endereço legado vira registro em `endereco`. | Adapta modelo embutido ao modelo normalizado. |
| DA-18 | ViaCEP é enriquecimento desabilitado por padrão. | Migração não pode depender de serviço externo. |
| DA-19 | CPF/CNPJ inválido ou duplicado vira NULL e pendência. | Não bloquear migração por dado ruim nem fabricar documento. |
| DA-20 | Normalização textual usa trim, lowercase e remoção de acentos somente para comparação. | Preserva nomes exibidos e melhora matching. |
| DA-21 | Catálogo existente é casado por nome normalizado; ausente é criado e marcado AJUSTADO. | Evita perder relações. |
| DA-22 | Produto é criado com estoque 0 e saldo final legado entra via `EstoqueService.ajustar`. | Mantém histórico e regra de ponto único de saldo. |
| DA-23 | Saldo legado negativo vira 0 + pendência. | Banco novo proíbe saldo negativo. |
| DA-24 | Código de barras duplicado/inválido vira NULL + pendência. | Preserva UNIQUE sem inventar código. |
| DA-25 | Venda histórica entra `CONCLUIDA`, chave `LEGADO:<id>`, sem caixa e sem `SAIDA_VENDA`. | Estoque migrado já representa saldo final. |
| DA-26 | Venda com total <= 0 ou item sem produto válido é IGNORADA. | Não criar fato financeiro/venda inconsistente. |
| DA-27 | Compra histórica não gera `ENTRADA_COMPRA`. | Saldo final já foi ajustado. |
| DA-28 | Conta a pagar só é criada quando os dados legados comprovarem que a compra está pendente. | Não presumir dívida. |
| DA-29 | Financeiro de venda/serviço é regenerado pelo backfill atual. | Evita duplicidade. |
| DA-30 | Financeiro legado não relacionado a venda/serviço pode ser migrado com `LEGADO:<id>`. | Preserva despesas/entradas manuais históricas. |
| DA-31 | Fotos usam o serviço de foto atual e diretório configurado. | Mantém validação de tipo/tamanho. |
| DA-32 | Ausência/erro de foto não interrompe a entidade. | Foto é pendência, não bloqueio. |
| DA-33 | Uma auditoria `MIGRACAO_LEGADO_EXECUTADA` por execução real. | Evita auditoria massiva com PII. |
| DA-34 | Relatórios contêm somente IDs legados, situações, motivos e contagens. | Minimização LGPD. |
| DA-35 | Testes usam fixture sintética criada nesta etapa. | Dados reais nunca entram em teste/CI. |
| DA-36 | Não reescrever histórico Git automaticamente. | Operação destrutiva deve ser decisão do proprietário; recomendar `git filter-repo`. |

## 4. Escopo

Inclui configuração, leitura do legado, normalização, migração dos dados necessários às tabelas funcionais atuais, estoque inicial, vendas/compras históricas, financeiro não regenerável, fotos, backfill financeiro, relatórios, conferências, testes e DevOps específico.

## 5. Fora de escopo

- alterar código PHP legado;
- corrigir dados no banco legado;
- migrar hash SHA-256;
- criar tela de migração;
- criar endpoint de migração;
- migrar tokens de recuperação;
- preservar IDs antigos como PKs novas;
- executar migração automaticamente no deploy normal;
- reescrever histórico Git;
- usar dados pessoais reais em testes;
- garantir enriquecimento de cidade por ViaCEP;
- gerar movimentos históricos de estoque por venda/compra.

## 6. Banco — V10 com SQL completo

Criar `V10__migracao_legado.sql`:

```sql
CREATE TABLE migracao_legado_execucao (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    iniciada_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finalizada_em DATETIME(6) NULL,
    modo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_lidos BIGINT NOT NULL DEFAULT 0,
    total_migrados BIGINT NOT NULL DEFAULT 0,
    total_ajustados BIGINT NOT NULL DEFAULT 0,
    total_ignorados BIGINT NOT NULL DEFAULT 0,
    total_ja_migrados BIGINT NOT NULL DEFAULT 0,
    total_pendencias BIGINT NOT NULL DEFAULT 0,
    mensagem VARCHAR(500) NULL,
    CONSTRAINT chk_migracao_exec_modo CHECK (modo IN ('DRY_RUN','REAL')),
    CONSTRAINT chk_migracao_exec_status CHECK (status IN ('EM_ANDAMENTO','CONCLUIDA','FALHOU'))
);

CREATE INDEX idx_migracao_exec_inicio
    ON migracao_legado_execucao(iniciada_em);

CREATE TABLE migracao_legado_mapa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entidade VARCHAR(50) NOT NULL,
    id_legado BIGINT NOT NULL,
    id_novo BIGINT NULL,
    situacao VARCHAR(20) NOT NULL,
    motivo VARCHAR(500) NULL,
    execucao_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_migracao_mapa_execucao
        FOREIGN KEY (execucao_id) REFERENCES migracao_legado_execucao(id),
    CONSTRAINT chk_migracao_mapa_situacao
        CHECK (situacao IN ('MIGRADO','IGNORADO','AJUSTADO')),
    CONSTRAINT uk_migracao_mapa_entidade_legado
        UNIQUE (entidade, id_legado)
);

CREATE INDEX idx_migracao_mapa_novo
    ON migracao_legado_mapa(entidade, id_novo);

CREATE INDEX idx_migracao_mapa_execucao
    ON migracao_legado_mapa(execucao_id, entidade, situacao);
```

Não armazenar PII nessas tabelas.

## 7. Mapeamento legado → novo

| Legado | Novo | Conversão principal |
|---|---|---|
| `papel` | `papel` | casar por nome normalizado; não duplicar seeds |
| `usuario` | `usuario` | `usuario→username`; e-mail normalizado; papel mapeado; senha aleatória BCrypt; token legado ignorado |
| `super_usuario` | `usuario.papel_id` | usuário relacionado passa a ADMIN |
| `cliente` | `endereco` + `cliente` | endereço extraído; CPF limpo/validado; inválido/duplicado NULL |
| `funcionario` | `endereco` + `funcionario` | usuário mapeado; sexo convertido; CPF tratado |
| `fornecedor` | `endereco` + `fornecedor` | CNPJ/contatos conforme schema atual; endereço extraído |
| `especie` | `especie` | casar por nome normalizado; criar se ausente |
| `raca` | `raca` | espécie primeiro; casar nome normalizado dentro da espécie |
| `pet` | `pet` | mapear cliente/espécie/raça; porte; booleanos; foto depois |
| `vacina` | `vacina` | casar nome normalizado + espécie; criar se ausente |
| `carteira_vacina` | `carteira_vacina` | resolver vacina por texto + espécie do pet; veterinário/obs preservados |
| `historico_consulta` | `historico_consulta` | mapear pet; preservar datas/observações válidas |
| `servico` | `servico` | casar por nome normalizado; criar se ausente |
| `preco_servico_porte` | `preco_servico_porte` | mapear serviço/porte; BigDecimal escala 2 |
| `agendamento` | `agendamento` | mapear pet/serviço/funcionário; converter status; preservar total válido |
| `servico_realizado` | `servico_realizado` | mapear relações; valor BigDecimal; backfill financeiro posterior |
| `categoria_produto` | `categoria_produto` | casar/criar por nome normalizado |
| `produto` | `produto` | criar saldo 0; ajustar depois via EstoqueService |
| `compra` | `compra` | mapear fornecedor/funcionário/status; sem entrada de estoque |
| `item_compra` | `item_compra` | mapear compra/produto; preço/quantidade válidos |
| `venda` | `venda` | CONCLUIDA; `LEGADO:id`; sem caixa; mapear cliente/funcionário |
| `item_venda` | `item_venda` | mapear produto; congelar preço legado válido |
| `movimentacao_financeira` | `movimentacao_financeira` | venda/serviço ignorados para backfill; demais migrados via FinanceiroService |

### Usuário

- `id` legado somente no mapa.
- `usuario` → `username`, trim; colisão → `_legado`, depois `_legado_2`, etc.
- `senha_hash`, `token_senha`, `token_expira` → nunca copiar.
- gerar segredo com `SecureRandom` >= 32 bytes, convertê-lo em BCrypt custo 12 e descartar texto puro imediatamente.
- e-mail válido → normalizar lowercase.
- sem e-mail válido → `ativo=false` e pendência `USUARIO_SEM_EMAIL_RECUPERACAO`.
- `super_usuario` força ADMIN.

### Cliente/funcionário/fornecedor e endereço

Criar endereço somente quando existir ao menos CEP, rua, bairro, número, complemento ou estado. Campos inexistentes no legado, como cidade, ficam NULL. ViaCEP só preenche cidade/UF quando flag ativa e resposta válida; falha não interrompe.

Documento: remover não dígitos. Se validação falhar ou UNIQUE colidir, gravar NULL quando o schema permitir e marcar AJUSTADO. Se o schema atual exigir NOT NULL, não fabricar documento: marcar registro IGNORADO e pendência `DOCUMENTO_INVALIDO_SCHEMA_OBRIGATORIO`.

### Pet/foto

Migrar dados relacionais primeiro. Foto é etapa posterior. Resolver `foto` contra `petconnect.migracao.fotos-dir`, impedir path traversal e aceitar somente arquivo regular dentro da raiz configurada. Importar pelo serviço atual.

### Estoque

Produto novo sempre nasce `quantidade_estoque=0`. Depois:

```java
estoqueService.ajustar(
    produtoNovoId,
    max(saldoLegado, 0),
    "Saldo inicial migrado do sistema antigo",
    usuarioTecnicoId
);
```

Saldo negativo: ajustar 0 e registrar pendência. Não criar `SAIDA_VENDA` nem `ENTRADA_COMPRA` históricas.

### Venda

- status novo: `CONCLUIDA`;
- `chave_idempotencia`: `LEGADO:<id>`;
- `caixa_id`: NULL;
- data/cliente/funcionário preservados quando mapeáveis;
- não chamar baixa de estoque;
- total <= 0 → IGNORADA;
- item sem produto mapeado → venda inteira IGNORADA;
- soma dos itens deve ser conferida contra total/desconto; divergência é AJUSTADO/pendência, sem alterar silenciosamente o valor histórico.

## 8. Tabelas de conversão

Normalização para comparação: `trim → lowercase → remover acentos → substituir espaços repetidos`.

### Sexo

| Legado normalizado | Novo |
|---|---|
| masculino, m | MASCULINO |
| feminino, f | FEMININO |
| outro, outros, o | OUTRO |
| desconhecido | OUTRO + pendência |

### Porte

| Legado | Novo |
|---|---|
| pequeno | PEQUENO |
| medio, médio | MEDIO |
| grande | GRANDE |
| desconhecido | MEDIO + pendência `PORTE_DESCONHECIDO` |

### Status de agendamento

| Legado normalizado | Novo |
|---|---|
| agendado | AGENDADO |
| confirmado | CONFIRMADO |
| em andamento, em_andamento | EM_ANDAMENTO |
| concluido, concluído | CONCLUIDO |
| cancelado | CANCELADO |
| nao compareceu, não compareceu, nao_compareceu | NAO_COMPARECEU |
| desconhecido | CANCELADO + pendência |

### Forma de pagamento

| Legado normalizado | Novo |
|---|---|
| dinheiro | DINHEIRO |
| cartao_credito, cartão crédito, credito | CARTAO_CREDITO |
| cartao_debito, cartão débito, debito | CARTAO_DEBITO |
| pix | PIX |
| transferencia, transferência | TRANSFERENCIA |
| desconhecido | TRANSFERENCIA + pendência |

### Tipo financeiro

`entrada` → `ENTRADA`; `saida/saída` → `SAIDA`; desconhecido → IGNORADO + pendência.

### Categoria financeira

Mapear normalizado para `VENDA_PRODUTO`, `SERVICO`, `DESPESA_FUNCIONARIO`, `DESPESA_OPERACIONAL`, `COMPRA_MERCADORIA`, `OUTROS`. Desconhecido → `OUTROS` + AJUSTADO.

### Booleanos

Aceitar `1,true,sim,s` como true; `0,false,nao,não,n` como false. Desconhecido → false + pendência.

## 9. Ordem de execução e falha/retomada

Ordem fixa:

1. papéis/referências;
2. usuários e super usuários;
3. endereços/clientes;
4. endereços/funcionários;
5. endereços/fornecedores;
6. espécies;
7. raças;
8. vacinas;
9. serviços;
10. preços de serviço;
11. categorias de produto;
12. pets;
13. fotos de pets;
14. carteira de vacina;
15. histórico de consulta;
16. produtos;
17. ajuste inicial de estoque;
18. compras;
19. itens de compra;
20. agendamentos;
21. serviços realizados;
22. vendas;
23. itens de venda;
24. financeiro legado não regenerável;
25. `FinanceiroBackfillRunner`;
26. conferências.

Usar paginação por `id > ultimoId ORDER BY id LIMIT 500`, nunca OFFSET para lotes grandes.

Execução real: transação por lote. Depois de completar uma entidade, seguir à próxima. Falha marca execução `FALHOU`, gera relatório e encerra. Reexecução consulta `migracao_legado_mapa` e pula registros já processados.

Não criar mapa persistente em DRY-RUN. O mapa do dry-run existe apenas em memória/transação revertida.

## 10. Dry-run, idempotência e relatório

Propriedade padrão:

```properties
petconnect.migracao.legado=false
petconnect.migracao.dry-run=true
```

DRY-RUN executa leitura, validação, conversão, services e conferências possíveis, mas termina com rollback. Não audita.

Execução real usa UNIQUE `(entidade,id_legado)` como barreira de idempotência. Segunda execução deve reportar registros como `ja_migrados` e não gerar novos fatos.

Relatório: `build/migracao-relatorios/migracao-legado-<execucaoId>.md` ou diretório configurado e ignorado pelo Git.

Exemplo:

```markdown
# Relatório — Migração legado

- modo: REAL
- status: CONCLUIDA
- início: 2026-09-27T08:00:00
- fim: 2026-09-27T08:04:31

| Entidade | Lidos | Migrados | Ajustados | Ignorados | Já migrados |
|---|---:|---:|---:|---:|---:|
| cliente | 120 | 112 | 6 | 2 | 0 |
| produto | 340 | 332 | 8 | 0 | 0 |

## Pendências
- CLIENTE legado #18 — CPF_INVALIDO
- PRODUTO legado #77 — CODIGO_BARRAS_DUPLICADO
- PET legado #91 — FOTO_AUSENTE

## Conferências
- contagens: PASS
- vendas: PASS
- estoque: PASS
- financeiro/backfill: PASS
- idempotência: PASS
```

Nunca incluir nome, CPF, CNPJ, e-mail, telefone, endereço, senha, hash ou token.

## 11. Configuração

```properties
petconnect.migracao.legado=${PETCONNECT_MIGRACAO_LEGADO:false}
petconnect.migracao.dry-run=${PETCONNECT_MIGRACAO_DRY_RUN:true}
petconnect.migracao.lote=${PETCONNECT_MIGRACAO_LOTE:500}
petconnect.migracao.relatorio-dir=${PETCONNECT_MIGRACAO_RELATORIO_DIR:build/migracao-relatorios}
petconnect.migracao.fotos-dir=${PETCONNECT_MIGRACAO_FOTOS_DIR:}
petconnect.migracao.viacep.enabled=${PETCONNECT_MIGRACAO_VIACEP:false}
petconnect.migracao.viacep.timeout-ms=${PETCONNECT_MIGRACAO_VIACEP_TIMEOUT_MS:1500}
petconnect.migracao.backfill-servico-forma=${PETCONNECT_MIGRACAO_BACKFILL_SERVICO_FORMA:}

spring.datasource.legacy.url=${LEGADO_DB_URL:}
spring.datasource.legacy.username=${LEGADO_DB_USERNAME:}
spring.datasource.legacy.password=${LEGADO_DB_PASSWORD:}
spring.datasource.legacy.driver-class-name=com.mysql.cj.jdbc.Driver
```

Quando migração estiver habilitada, URL/username/password do legado são obrigatórios. Senha nunca é logada.

`LegacyDataSourceConfig` deve criar DataSource/JdbcTemplate nomeados (`legacyDataSource`, `legacyJdbcTemplate`) e nunca marcá-los `@Primary`.

## 12. Auditoria

Somente execução REAL concluída ou falha após ter gravado lotes gera:

`MIGRACAO_LEGADO_EXECUTADA`.

Detalhes permitidos:

```json
{
  "execucaoId": 12,
  "status": "CONCLUIDA",
  "lidos": 4300,
  "migrados": 4090,
  "ajustados": 170,
  "ignorados": 40,
  "pendencias": 92
}
```

Não auditar PII. DRY-RUN não audita.

## 13. Segurança e LGPD

- legado acessado por credencial SQL somente leitura;
- nunca logar senha/hash/token/CPF/CNPJ/e-mail/telefone/endereço;
- relatórios usam ID legado e código de pendência;
- fixture de teste é 100% sintética;
- script com dados reais não entra em CI;
- dumps reais ficam em pasta ignorada pelo Git;
- `.env.example` contém somente placeholders;
- apagar arquivos temporários de importação após uso operacional conforme runbook;
- não reescrever Git automaticamente;
- documentar recomendação ao proprietário para remover dados pessoais históricos com `git filter-repo`, revogar credenciais expostas e rotacionar segredos.

## 14. DevOps

Adicionar profile Compose `migracao`:

```yaml
mysql-legado:
  profiles: ["migracao"]
  image: mysql:8.4
  ports:
    - "3307:3306"
  environment:
    MYSQL_DATABASE: petshop_db
    MYSQL_USER: legado_read
    MYSQL_PASSWORD: ${LEGADO_DB_PASSWORD}
    MYSQL_ROOT_PASSWORD: ${LEGADO_ROOT_PASSWORD}
  volumes:
    - ./docker/migracao/fixture:/docker-entrypoint-initdb.d:ro

api-migracao:
  profiles: ["migracao"]
  build: ./backend
  depends_on:
    - mysql
    - mongo
    - mysql-legado
  environment:
    PETCONNECT_MIGRACAO_LEGADO: "true"
    PETCONNECT_MIGRACAO_DRY_RUN: ${PETCONNECT_MIGRACAO_DRY_RUN:-true}
    LEGADO_DB_URL: jdbc:mysql://mysql-legado:3306/petshop_db
    LEGADO_DB_USERNAME: legado_read
    LEGADO_DB_PASSWORD: ${LEGADO_DB_PASSWORD}
```

A fixture versionada deve ser sintética. Dump real do usuário deve ficar em diretório separado ignorado.

Adicionar ao `.gitignore`:

```gitignore
build/migracao-relatorios/
.migracao/
docker/migracao/dados-reais/
*.legacy.sql
```

Criar `docs/migracao-legado.md` com: pré-requisitos, backup, restore testado, dry-run, leitura do relatório, execução real, conferências, reexecução, rollback por restauração de backup e aviso LGPD.

## 15. Conferências automáticas

Criar `MigracaoConferenciaService`.

Obrigatórias:

1. `lidos = migrados + ajustados + ignorados + jaMigrados` por entidade.
2. Nenhum mapa duplicado `(entidade,id_legado)`.
3. Soma de vendas legadas aceitas = soma de vendas novas com `chave_idempotencia LIKE 'LEGADO:%'`, respeitando desconto/valor histórico definido.
4. Cada venda `LEGADO:%` possui todos os itens esperados.
5. Saldo de cada produto migrado = `max(saldo_legado,0)` após ajuste inicial.
6. Nenhum saldo novo negativo.
7. Nenhuma venda histórica gerou `SAIDA_VENDA`.
8. Nenhuma compra histórica gerou `ENTRADA_COMPRA`.
9. Após backfill, cada venda histórica válida possui lançamento `VENDA:<idNovo>`.
10. Serviços migrados com forma configurada possuem lançamento após backfill.
11. Financeiro legado de venda/serviço não foi duplicado.
12. Relatório de vendas da etapa 07 para o período histórico fecha com vendas migradas.
13. Autenticação com senha antiga conhecida da fixture retorna 401.
14. Segunda execução real não cria novos registros.
15. Relatório não contém padrões de CPF/e-mail/telefone da fixture.

Falha de conferência crítica marca execução `FALHOU` e impede declaração de sucesso.

## 16. Regras numeradas

- **R-01:** migração só executa quando explicitamente habilitada.
- **R-02:** DRY-RUN é padrão.
- **R-03:** banco legado é somente leitura.
- **R-04:** migrations V1–V9 são imutáveis.
- **R-05:** lote padrão é 500.
- **R-06:** ordem de entidades é fixa.
- **R-07:** falha interrompe entidades seguintes.
- **R-08:** reexecução pula mapa existente.
- **R-09:** senha/hash/token legado nunca é copiado.
- **R-10:** senha antiga nunca autentica no sistema novo.
- **R-11:** usuário sem e-mail fica inativo e pendente.
- **R-12:** bootstrap ADMIN nunca é sobrescrito.
- **R-13:** super usuário legado recebe ADMIN.
- **R-14:** documento inválido nunca é inventado.
- **R-15:** documento duplicado posterior vira NULL quando schema permitir.
- **R-16:** ViaCEP é enriquecimento, não dependência.
- **R-17:** valor de enum desconhecido sempre gera pendência.
- **R-18:** catálogo é casado por nome normalizado.
- **R-19:** catálogo ausente pode ser criado e marcado AJUSTADO.
- **R-20:** produto nasce com estoque zero.
- **R-21:** saldo inicial passa por EstoqueService.
- **R-22:** saldo legado negativo vira zero + pendência.
- **R-23:** código de barras duplicado vira NULL + pendência.
- **R-24:** venda histórica não altera estoque.
- **R-25:** venda histórica válida é CONCLUIDA e usa `LEGADO:id`.
- **R-26:** venda inválida é ignorada integralmente.
- **R-27:** compra histórica não gera entrada de estoque.
- **R-28:** conta a pagar só nasce com evidência de dívida.
- **R-29:** financeiro de venda/serviço é regenerado pelo backfill.
- **R-30:** financeiro manual legado usa FinanceiroService.
- **R-31:** foto ausente não aborta pet.
- **R-32:** foto nunca pode escapar da raiz configurada.
- **R-33:** dry-run não audita.
- **R-34:** relatório não contém PII.
- **R-35:** teste nunca usa dado real.
- **R-36:** dinheiro usa BigDecimal escala 2 HALF_UP.
- **R-37:** datas seguem America/Sao_Paulo.
- **R-38:** migração real exige backup prévio documentado.
- **R-39:** nenhuma tela/endpoint de migração será criado.
- **R-40:** nenhuma execução faz commit/push Git.

## 17. Testes unitários

### `TextoNormalizadoTest`
- `deveRemoverAcentosParaComparacao`
- `deveIgnorarMaiusculasEMinusculas`
- `deveNormalizarEspacos`

### `ConversorLegadoTest`
- `deveConverterFormasPagamentoConhecidas`
- `deveConverterStatusAgendamentoComAcento`
- `deveConverterPorteMedio`
- `deveGerarPendenciaParaEnumDesconhecido`
- `deveConverterBooleanosLegados`

### `MigradorUsuarioTest`
- `naoDeveMigrarHashSha256`
- `deveGerarBcryptDeSegredoAleatorio`
- `deveRenomearUsernameQueColideComBootstrap`
- `superUsuarioDeveVirarAdmin`
- `usuarioSemEmailDeveFicarInativo`

### `MigradorClienteTest`
- `deveExtrairEndereco`
- `deveLimparMascaraCpf`
- `cpfInvalidoDeveVirarNullEPendencia`
- `cpfDuplicadoDeveVirarNullNoSegundo`
- `falhaViaCepNaoDeveAbortar`

### `MigradorProdutoTest`
- `produtoDeveNascerComSaldoZero`
- `deveAplicarSaldoInicialViaEstoqueService`
- `saldoNegativoDeveVirarZero`
- `codigoBarrasDuplicadoDeveVirarNull`

### `MigradorVendaTest`
- `deveCriarVendaConcluidaSemCaixa`
- `deveUsarChaveLegado`
- `naoDeveBaixarEstoque`
- `totalNaoPositivoDeveIgnorarVenda`
- `itemSemProdutoDeveIgnorarVendaInteira`

### `MigradorFinanceiroTest`
- `deveIgnorarFinanceiroDeVendaParaBackfill`
- `deveIgnorarFinanceiroDeServicoParaBackfill`
- `deveMigrarDespesaManualLegada`
- `deveUsarChaveOrigemLegado`

### `MigracaoRelatorioServiceTest`
- `relatorioNaoDeveConterCpf`
- `relatorioNaoDeveConterEmail`
- `relatorioNaoDeveConterTelefone`
- `deveExibirSomenteIdLegadoNasPendencias`

## 18. Testes de integração e fixture sintética

Criar fixture em `backend/src/test/resources/legacy/fixture-legado.sql`, exclusivamente sintética.

Conteúdo mínimo:

- 3 papéis;
- 4 usuários, incluindo super usuário, usuário sem e-mail e username em colisão com bootstrap;
- CPFs sintéticos válidos e um inválido/duplicado;
- 3 clientes;
- 2 funcionários;
- 2 fornecedores;
- espécies/raças com diferença de acento/case;
- vacina textual que casa e outra que exige criação;
- 4 pets, um com foto ausente;
- 4 produtos: saldo positivo, zero, negativo e barcode duplicado;
- 2 compras;
- 3 vendas: válida, total zero, item sem produto;
- agendamentos com statuses variados;
- serviços realizados;
- financeiro de venda, serviço e despesa manual.

Classes:

### `MigracaoLegadoDryRunIT`
- `dryRunNaoDevePersistirDados`
- `dryRunNaoDevePersistirMapa`
- `dryRunNaoDeveAuditar`
- `dryRunDeveGerarRelatorio`

### `MigracaoLegadoIT`
- `deveMigrarFixtureCompleta`
- `deveRespeitarOrdemDeForeignKeys`
- `deveMigrarEmLotes`
- `senhaAntigaDeveRetornar401`
- `deveCriarMapaParaRegistrosProcessados`
- `deveGerarApenasContagensNaExecucao`

### `MigracaoLegadoIdempotenciaIT`
- `segundaExecucaoNaoDeveCriarNovosRegistros`
- `segundaExecucaoDeveMarcarJaMigrados`
- `naoDeveDuplicarEstoque`
- `naoDeveDuplicarFinanceiro`

### `MigracaoLegadoRetomadaIT`
- `falhaNoMeioDeveInterromperEntidadesSeguintes`
- `reexecucaoDeveContinuarDosNaoProcessados`
- `lotesConfirmadosAntesDaFalhaDevemPermanecerNaExecucaoReal`

### `MigracaoLegadoEstoqueIT`
- `saldoFinalDeveConferirComLegado`
- `saldoNegativoDeveVirarZero`
- `vendaHistoricaNaoDeveGerarSaidaVenda`
- `compraHistoricaNaoDeveGerarEntradaCompra`

### `MigracaoLegadoFinanceiroIT`
- `backfillDeveGerarLancamentoDasVendasMigradas`
- `financeiroLegadoDeVendaDeveSerIgnorado`
- `despesaManualLegadaDeveSerMigradaUmaVez`
- `somaFinanceiraDeVendasDeveConferir`

### `MigracaoLegadoLgpdIT`
- `relatorioNaoDeveConterDadosPessoaisDaFixture`
- `logsNaoDevemConterSegredosOuDocumentos`

## 19. Frontend

Não criar nem alterar tela para migração. Não adicionar menu, rota ou Server Action.

A suíte frontend existente deve continuar verde. Executar testes, lint, typecheck e build para provar ausência de regressão. Se alguma alteração compartilhada do backend afetar contrato público, isso é divergência e deve ser corrigido sem expor migração ao frontend.

## 20. Documentação a atualizar

- `README.md`: mencionar ferramenta de migração offline e apontar para runbook.
- `docs/arquitetura.md`: segunda DataSource, módulo temporário e fluxo de backfill.
- `docs/banco-de-dados.md`: V10 e tabelas de controle.
- `docs/migracao-legado.md`: runbook completo.
- `docs/next-stage/README.md`: registrar etapa 08 concluída e handoff da 09.

Não inserir credenciais nem exemplos com PII real.

## 21. Critérios de aceite

- [ ] V10 aplica sobre V9 sem editar V1–V9.
- [ ] Runner permanece desligado no boot normal.
- [ ] DRY-RUN é padrão.
- [ ] Execução real exige flag explícita.
- [ ] DataSource legado é separada e somente leitura.
- [ ] Lotes usam tamanho 500 por padrão.
- [ ] Migração respeita ordem de FK.
- [ ] Reexecução não duplica dados.
- [ ] Falha permite retomada.
- [ ] Hash SHA-256 não é copiado.
- [ ] Senha antiga da fixture retorna 401.
- [ ] Usuário sem e-mail é pendência/inativo.
- [ ] Bootstrap ADMIN não é sobrescrito.
- [ ] CPF/CNPJ inválido não é inventado.
- [ ] ViaCEP desligado não impede migração.
- [ ] Catálogos são casados por nome normalizado.
- [ ] Produto nasce saldo 0 e saldo inicial passa por EstoqueService.
- [ ] Saldo negativo vira 0 + pendência.
- [ ] Venda histórica não gera SAIDA_VENDA.
- [ ] Compra histórica não gera ENTRADA_COMPRA.
- [ ] Backfill gera financeiro das vendas/serviços elegíveis.
- [ ] Financeiro legado equivalente não duplica backfill.
- [ ] Fotos usam serviço atual e path seguro.
- [ ] Relatório não contém PII.
- [ ] Auditoria real contém somente contagens.
- [ ] Fixture é sintética.
- [ ] Conferências automáticas passam.
- [ ] Segunda execução não migra nada novo.
- [ ] Backend build PASS.
- [ ] Testes unitários PASS.
- [ ] Testes de integração PASS.
- [ ] Frontend tests PASS.
- [ ] Frontend lint PASS.
- [ ] Frontend typecheck PASS.
- [ ] Frontend build PASS.
- [ ] Docker profile de migração funciona.

## 22. Riscos

- dados reais inconsistentes: comportamento padrão + pendência, nunca adivinhação silenciosa;
- PII versionada: não ampliar exposição; recomendar limpeza histórica fora da automação;
- credencial legado com escrita: runbook exige usuário read-only e teste de permissão;
- IDs divergentes: sempre usar mapa;
- falha de rede ViaCEP: cache/timeout/fallback sem abortar;
- fotos ausentes/path traversal: validar raiz e registrar pendência;
- estoque duplicado: nunca reproduzir movimentos históricos; um ajuste inicial;
- financeiro duplicado: backfill para venda/serviço, legado somente para fatos não regeneráveis;
- reexecução após falha: mapa UNIQUE e commits por lote;
- transação gigantesca: lotes de 500;
- senha insegura: descartar SHA-256 e exigir recuperação;
- relatório vazar PII: testes específicos de LGPD.

## 23. Ordem de implementação

1. Rodar baseline.
2. Inspecionar V1–V9 e valores distintos do legado.
3. Criar V10.
4. Criar propriedades/configuração da DataSource legado.
5. Criar domínio de execução/mapa/pendências em memória.
6. Criar normalizadores/conversores.
7. Criar `MigracaoRelatorioService`.
8. Criar migradores de catálogos.
9. Usuários/super usuários.
10. Clientes/endereços.
11. Funcionários/endereços.
12. Fornecedores/endereços.
13. Pets/fotos/vacinas/histórico.
14. Produtos/estoque.
15. Compras/itens.
16. Agendamentos/serviços realizados.
17. Vendas/itens.
18. Financeiro legado filtrado.
19. Integrar FinanceiroBackfillRunner de forma programática reutilizável, sem duplicar lógica.
20. Conferências.
21. Runner e DRY-RUN.
22. Auditoria.
23. Fixture sintética.
24. Unitários.
25. Integração/idempotência/retomada/LGPD.
26. Docker profile/.env.example/.gitignore.
27. Runbook/documentação.
28. Executar validações completas.
29. Gerar relatório final.
30. Revisar `git diff`; não commit/push.

## 24. Restrições

NÃO editar migrations V1–V9.

NÃO alterar `legacy/` nem `docs/legacy/`.

NÃO hard-delete.

NÃO usar double/float para dinheiro.

NÃO desabilitar testes/lint.

NÃO usar `any` no front.

NÃO fazer commit/push automático.

NÃO migrar hash de senha.

NÃO logar/auditar senha, hash, token, CPF, e-mail ou telefone.

NÃO usar dados pessoais reais em testes.

NÃO escrever no banco legado.

NÃO criar endpoint/tela de migração.

NÃO inventar documento, enum ou dado ausente sem registrar pendência.

NÃO alterar saldo de produto fora do EstoqueService.

NÃO gravar movimentação financeira fora do FinanceiroService.

NÃO gerar movimentos históricos de venda/compra que dupliquem o saldo final.

NÃO executar migração real sem backup validado.

## 25. Relatório final obrigatório

Criar `08-migracao-legado-relatorio.md`:

```text
# Etapa 08 — Relatório final

## Implementação
- V10:
- módulo migracao:
- DataSource legado:
- runner:
- dry-run:
- idempotência:
- retomada:
- backfill:
- conferências:
- DevOps:
- documentação:

## Testes
- unitários: X/Y
- integração: X/Y
- frontend: X/Y

## Validações
- backend build: PASS/FAIL
- frontend build: PASS/FAIL
- lint: PASS/FAIL
- typecheck: PASS/FAIL
- Docker: PASS/FAIL

## Migração/fixture validada
- lidos:
- migrados:
- ajustados:
- ignorados:
- já migrados:
- pendências:

## Divergências
- listar apenas divergências reais

## Pendências
- listar apenas pendências reais e IDs legados; sem PII

## Segurança/LGPD
- dados reais usados em testes: NÃO
- PII em relatório: NÃO
- escrita no legado: NÃO

## Handoff da etapa 09
- migrations V1–V10
- módulos e serviços finais
- configuração de produção necessária
- volumes/dados persistentes
- estratégia de backup/restore atual
- endpoints públicos existentes
- jobs/runners existentes
- observabilidade atual
- CI atual
- lacunas de segurança/produção
```

Nunca declarar PASS sem executar o comando correspondente.

## 26. Definição de pronto

```text
BOOT NORMAL
→ migração desligada
→ nenhuma conexão obrigatória com legado

DRY-RUN
→ habilitar migração
→ conectar legado read-only
→ processar regras reais
→ gerar pendências/conferências
→ rollback
→ nenhuma auditoria

EXECUÇÃO REAL
→ backup validado
→ dry-run analisado
→ flag dry-run=false
→ lotes de 500
→ mapa legado→novo
→ commits controlados
→ falha interrompe
→ reexecução retoma

SEGURANÇA
→ SHA-256 não migra
→ senha antiga = 401
→ PII não aparece em logs/relatórios/auditoria

ESTOQUE
→ produto inicia 0
→ ajuste inicial via EstoqueService
→ venda/compra histórica não altera saldo novamente

FINANCEIRO
→ venda/serviço regenerados por backfill
→ demais fatos legados migrados uma vez
→ nenhuma duplicidade

VALIDAÇÃO
→ contagens fecham
→ vendas fecham
→ estoque fecha
→ financeiro fecha
→ relatórios da etapa 07 fecham
→ segunda execução cria zero novos fatos
```

A etapa somente está pronta quando dry-run, execução real sobre fixture sintética, retomada, idempotência, conferências, LGPD, builds, testes e Docker estiverem comprovados.

## 27. Sugestão da etapa 09 — Endurecimento para produção

A próxima etapa deve ser `09-producao-operacao.md`, sem adicionar novo domínio de negócio. Foco:

1. deploy de produção separado de desenvolvimento;
2. HTTPS obrigatório e headers de segurança;
3. secrets fora do repositório;
4. política de CORS/cookies/JWT para domínio real;
5. backup automatizado de MySQL/Mongo e teste periódico de restore;
6. persistência e backup das fotos;
7. observabilidade: logs estruturados, métricas, health/readiness e alertas;
8. limites/rate limiting distribuído onde necessário;
9. estratégia de migrations no deploy;
10. rollback de aplicação;
11. E2E Playwright dos fluxos críticos no CI;
12. smoke test pós-deploy;
13. SAST/dependency scanning e atualização de dependências;
14. proteção de branches e revisão de PR;
15. política de retenção de logs/auditoria;
16. revisão LGPD: inventário, bases legais, direitos do titular, retenção, privacidade e incidentes;
17. runbook operacional e de incidentes;
18. teste de recuperação de desastre;
19. checklist de go-live;
20. documentação final de arquitetura e operação.

A etapa 09 deve assumir que V1–V10 estão aplicadas e que a migração de legado é uma ferramenta offline, desabilitada no runtime normal de produção.
