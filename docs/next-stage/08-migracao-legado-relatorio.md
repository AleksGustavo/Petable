# Etapa 08 — Relatório final

Especificação: [08-migracao-legado.md](08-migracao-legado.md). Foi gerada pelo ChatGPT a partir do [prompt](08-prompt-chatgpt.md).

- **Branch:** `feat/migracao-legado`, criada a partir de `feat/relatorios`.
- **Commits:** nenhum. As etapas 01–08 seguem sem commit.
- **Runbook:** [docs/migracao-legado.md](../migracao-legado.md).

**Resultado: PASS.**

## Implementação

- **V10** (`V10__migracao_legado.sql`): cria `migracao_legado_execucao` e `migracao_legado_mapa` com o SQL exato da §6. Nenhuma coluna guarda dado pessoal. V1–V9 não foram tocadas.

- **Módulo `migracao`:** 22 `Migrador`es em ordem fixa por `@Order`:
  papel → usuario → cliente → funcionario → fornecedor → especie → raca → vacina → servico → preco_servico_porte → categoria_produto → pet → pet_foto → carteira_vacina → historico_consulta → produto → produto_estoque → compra → agendamento → servico_realizado → venda → movimentacao_financeira.
  - Em seguida rodam o backfill e as conferências.
  - Apoio: `TextoNormalizado`, `ConversorLegado` (tabelas da §8), `EnderecoLegado`, `DestinoMigracao` (gravação no banco novo), `MapaMigracao` e `ExecucaoMigracaoRepository`.
  - A consulta de usuários **não lê** `senha_hash`, `token_senha` nem `token_expira`.

- **DataSource do legado:**
  - `LegacyDataSourceConfig` só é criado com `petconnect.migracao.legado=true`.
  - O bean `legacyJdbcTemplate` (`LegadoConexao`) usa Hikari com `readOnly=true`, pool de 2 conexões e nunca é `@Primary`.
  - Sem URL, usuário e senha configurados, a aplicação não sobe, e a mensagem não mostra valores.

- **Runner:** `MigracaoLegadoRunner` implementa `ApplicationRunner`, é condicional e encerra a aplicação ao final: código 0 = concluída, 1 = falhou.

- **Dry-run:**
  - É o padrão.
  - Roda tudo, inclusive backfill e conferências, dentro de uma transação que termina em rollback; o mapa também é desfeito.
  - Não audita.
  - O histórico da execução é gravado em transação própria.

- **Idempotência:** vem do UNIQUE `(entidade, id_legado)` do mapa. A segunda execução real deu 66/66 "já migrados" e 0 registros novos.

- **Retomada:**
  - Execução real com uma transação por lote e leitura paginada por `id > :ultimo` (lote padrão de 500).
  - Uma falha interrompe as entidades seguintes, marca a execução como FALHOU e gera o relatório. A próxima execução continua de onde parou.
  - A mensagem de falha traz só a entidade, o id legado e o tipo da exceção, porque a mensagem do banco pode conter documento.

- **Backfill:**
  - Reaproveita o `FinanceiroBackfill` pelo overload novo `executar(FormaPagamento)`, que recebe a forma configurada na migração para os serviços antigos.
  - Compras que estão no mapa não viram conta a pagar.

- **Conferências:** o `MigracaoConferenciaService` cobre os itens 1–11 da §15; todos falham como críticos. Os itens 12–15 dependem da API e estão nos ITs.

- **Relatório:**
  - Arquivo `migracao-legado-<id>-<dry-run|real>.md`.
  - Contém parâmetros, contagens por entidade, resultado do backfill, pendências no formato `ENTIDADE legado #id — CÓDIGO` e conferências.

- **Auditoria:**
  - `AuditoriaService.semAuditoriaPorRegistro` suprime os eventos por item gerados pelos serviços de domínio, porque o Mongo não participa do rollback do dry-run.
  - É gravado um único `MIGRACAO_LEGADO_EXECUTADA` por execução real, só com contagens.

- **Ajustes em código existente** (mínimos, cada um com teste):
  - `FotoPetService.importar(petId, bytes)`: mesma validação do upload; apaga o arquivo se a transação for desfeita.
  - `FinanceiroService.registrarLegado(...)`: chave `LEGADO:<id>`, sem caixa e sem usuário.
  - `FinanceiroBackfill.executar(FormaPagamento)`.
  - `ContaPagarRepository.comprasRecebidasSemConta` passa a ignorar as compras migradas.
  - `AuditoriaService`: escopo sem auditoria por registro e `registrarSempre`.
  - `application.yml`: propriedades da §11.

- **DevOps:**
  - `compose.yaml` ganhou o perfil `migracao`:
    - `mysql-legado` na porta 3308, com o usuário `legado_read` só com SELECT, criado por `docker/migracao/00-usuario-leitura.sh`;
    - `api-migracao` como ferramenta offline, com `restart: "no"`.
  - `.env.example` recebeu placeholders.
  - `.gitignore` agora cobre relatórios, `.migracao/`, `docker/migracao/dados-reais/` e `*.legacy.sql`.

- **Documentação:**
  - Novo `docs/migracao-legado.md` (runbook: backup, restore testado, dry-run, leitura do relatório com os códigos, execução real, reexecução, rollback, LGPD).
  - Atualizados: `banco-de-dados.md` (V10, seção da migração, evento de auditoria), `arquitetura.md` (módulo e fluxo), `README.md` e `next-stage/README.md`.

## Testes

- **unitários: 325/325** (39 novos)
  - `TextoNormalizadoTest` (4), `ConversorLegadoTest` (5), `MigradorUsuarioTest` (5), `MigradorClienteTest` (6), `MigradorProdutoTest` (4), `MigradorVendaTest` (6), `MigradorFinanceiroTest` (5), `MigracaoRelatorioServiceTest` (4).
- **integração: 259/259** (27 novos)
  - `MigracaoLegadoDryRunIT` 4, `MigracaoLegadoIT` 6, `MigracaoLegadoIdempotenciaIT` 4, `MigracaoLegadoRetomadaIT` 3, `MigracaoLegadoEstoqueIT` 4, `MigracaoLegadoFinanceiroIT` 4, `MigracaoLegadoLgpdIT` 2.
  - O cenário roda uma vez por JVM: o banco `petshop_db` é criado no mesmo container MySQL, com o usuário `legado_read` só com SELECT (por tabela).
  - A sequência é dry-run → real com falha (SELECT revogado em `venda`) → real que retoma → real repetida.
  - A fixture tem lote 2, para forçar vários lotes.
- **frontend: 240/240.** Nada mudou no front.

## Validações

| Item | Resultado |
|---|---|
| backend build (`./mvnw -B clean verify`) | PASS |
| frontend build (`npm run build`) | PASS |
| lint (`eslint --max-warnings=0`) | PASS |
| typecheck (`tsc --noEmit`) | PASS |
| Docker | PASS |

Detalhes do Docker:
- **Stack de dev reconstruída:**
  - Flyway V9 → V10 aplicada no banco local.
  - `api` saudável.
  - Login, clientes, relatório de vendas, health e tela de login responderam 200.
- **Perfil `migracao`,** num projeto Compose isolado (`petconnect-migteste`), removido ao final:
  - O dry-run saiu com código 0 e o banco novo ficou com 0 registros.
  - O usuário de leitura recebeu `CREATE command denied` ao tentar escrever.
  - A execução real saiu com código 0.
  - A repetição saiu com código 0 e 66/66 "já migrados".

## Migração/fixture validada

Execução real sobre a fixture sintética, no Docker:

- lidos: 66
- migrados: 31
- ajustados: 21
- ignorados: 14
- já migrados: 0 (na repetição: 66)
- pendências: 40, todas previstas pela fixture

Resultado no banco novo:
- 3 clientes, 3 pets e 1 foto importada.
- 2 vendas somando R$ 276,00 (legado = novo), com 2 lançamentos `VENDA:` gerados pelo backfill.
- Saldos 10/0/0/7, com 2 ajustes de estoque.
- 3 lançamentos `LEGADO:`.
- 0 contas a pagar de compra histórica.
- Relatório de vendas da etapa 07 em mar/2019: 2 vendas, R$ 276,00.

## Divergências

1. **Conexão legada não é um bean `DataSource`.**
   - A spec pedia `legacyDataSource`/`legacyJdbcTemplate`.
   - Um segundo bean `DataSource` desligaria a autoconfiguração do banco principal.
   - O DataSource vive dentro de `LegadoConexao` (bean `legacyJdbcTemplate`), continua `readOnly` e nunca é primário.
2. **NOT NULL do schema novo que a spec não previa:**
   - `endereco.cidade/uf`: a spec dizia cidade NULL, mas a coluna é NOT NULL. Fica "Não informada" e UF "NI" (a UF usa `estado` quando existe), sempre com pendência. Com o ViaCEP ligado, os dois são preenchidos.
   - `usuario.email` é NOT NULL/UNIQUE: usuário sem e-mail fica inativo com `legado-<id>@migracao.invalid` (domínio reservado, não recebe e-mail).
   - `cliente.telefone` é NOT NULL/UNIQUE: inválido ou repetido faz o cliente ser IGNORADO, pela mesma regra que a spec dá para documento obrigatório. Os pets dele ficam `CLIENTE_NAO_MIGRADO`.
3. **Porta do `mysql-legado`:** 3308, não 3307. A 3307 já é a porta padrão do banco novo neste projeto.
4. **Local da fixture:** fica em `backend/src/test/resources/legacy/`, a fonte única para testes e Docker, em vez de `docker/migracao/fixture`.
   - O esquema de teste reproduz as colunas do legado sem procedures e sem FKs.
   - Usa VARCHAR no lugar de ENUM, para simular dado sujo como `'desmarcado'`, `'Enorme'` e itens órfãos.
5. **Itens de venda e compra** migram junto com o registro pai, na mesma transação: a venda inteira entra ou é ignorada, como pede a DA-26. O mapa registra o pai, não cada item.
6. **Conferências 12–15** (relatório da etapa 07, login 401, segunda execução, relatório sem PII) estão nos ITs, não no serviço, porque dependem da API ou do cenário.
7. **Extensões pequenas, documentadas:**
   - sinônimos de espécie ("Cachorro" = "Cão");
   - status de compra desconhecido vira CANCELADO + pendência (a spec não definia padrão);
   - o `created_at` legado não é preservado.

## Problemas encontrados e corrigidos na validação

- O init `00-usuario-leitura.sh` dependia de `docker_process_sql`, que não existe quando o arquivo é executado (o Windows marca `+x`). Passou a usar o cliente `mysql` pelo socket.
- A fixture carregada pelo init do MySQL perdia os acentos: `'Não compareceu'` virava status desconhecido. Entrou `SET NAMES utf8mb4;` no esquema e na fixture, e o runbook passou a exigir isso também no dump real.
- O `:?` nas senhas do perfil `migracao` quebrava o `docker compose up` normal de quem não tem essas variáveis. Trocado por `:-`: o MySQL e a aplicação recusam subir sem elas, mas só quando o perfil é usado.

## Pendências

- Nenhuma execução com **dados reais** foi feita, de propósito. O próximo passo é o dono rodar o dry-run com o dump real, conforme o runbook, e analisar o relatório.
- O ViaCEP real não foi exercitado em teste; só a falha simulada. Ele fica desligado por padrão.
- Os serviços antigos ficam sem lançamento financeiro enquanto `MIGRACAO_BACKFILL_SERVICO_FORMA` não for definida. Isso aparece como `SERVICOS_SEM_FORMA_PAGAMENTO (n)`.
- LGPD: o script de inserção com dados reais continua no histórico do git em `docs/legacy/...`. A recomendação (`git filter-repo` + rotação de credenciais) está no runbook; a decisão é do dono.
- Commits: as etapas 01–08 seguem sem commit.

## Segurança/LGPD

- dados reais usados em testes: NÃO (só a fixture sintética; os testes procuram os CPFs, e-mails e telefones dela nos relatórios, no mapa, no histórico e nos logs)
- PII em relatório: NÃO
- escrita no legado: NÃO (conexão `readOnly` e usuário só com SELECT, provado no teste e no Docker)

## Handoff da etapa 09

O prompt completo para o ChatGPT está em **[09-prompt-chatgpt.md](09-prompt-chatgpt.md)**. Ele gera `09-producao-operacao.md`.

Estado entregue à etapa 09:

- **migrations:** V1–V10.
- **módulos:** auth, usuario, funcionario, cliente, endereco, pet, servico, agendamento, produto, fornecedor, compra, venda, financeiro, relatorio, migracao (offline) e shared (audit, config, error, security, validation).
- **configuração de produção necessária:**
  - `APP_ENV=prod`, `MYSQL_*`, `MONGODB_URI`, `JWT_SECRET` (≥ 32 bytes), `CORS_ALLOWED_ORIGINS`, `ADMIN_*` (primeiro acesso), `SMTP_*`, `EMAIL_REMETENTE`, `APP_FRONTEND_URL`, `PETS_STORAGE_DIR`;
  - no web: `API_URL` e `COOKIE_SECURE=true`;
  - `PETCONNECT_MIGRACAO_LEGADO` deve ficar `false`.
- **volumes e dados persistentes:** MySQL, MongoDB (auditoria) e `petconnect_uploads` (fotos).
- **backup/restore atual:** manual, documentado no runbook da migração. Não há automação nem teste periódico.
- **endpoints públicos:** `/api/v1/auth/login`, recuperação e redefinição de senha, `/actuator/health` (+ probes) e `/actuator/info`. O resto exige JWT.
- **jobs/runners:** `AdminBootstrap` (sempre), `FinanceiroBackfillRunner` (condicional) e `MigracaoLegadoRunner` (condicional, offline).
- **observabilidade atual:** logs de texto padrão do Spring e health/readiness. Não há métricas, alertas nem request id.
- **CI atual:**
  - `backend.yml` (verify + JaCoCo), `frontend.yml`, `codeql.yml`, `delivery.yml` (imagens no GHCR);
  - `dependabot.yml`, `CODEOWNERS` e template de PR.
- **lacunas de segurança/produção:**
  - sem deploy, HTTPS, CSP nem HSTS;
  - limitador de recuperação de senha em memória;
  - sem E2E nem smoke test;
  - sem scan de imagem;
  - sem backup automatizado nem retenção definida;
  - PII no histórico do git.
