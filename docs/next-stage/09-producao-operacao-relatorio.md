# Etapa 09 — Produção e operação — Relatório final

Spec: [09-producao-operacao.md](09-producao-operacao.md). O ChatGPT devolveu o prompt em vez da spec; com autorização do dono, a spec foi escrita pelo Claude Code no formato de 27 seções.

- **Branch:** `feat/producao-operacao`, criada a partir de `feat/migracao-legado`.
- **Commits:** nenhum. As etapas 01–09 seguem sem commit.
- **Documentação:** [docs/producao.md](../producao.md) e [docs/runbooks/](../runbooks).

**Resultado: PASS.**

## Testes

- **unitários: 346/346** (21 novos)
  - `MascaramentoLogsTest` (6), `RequestIdFilterTest` (4), `LimitadorLoginTest` (4), `ClientePrivacidadeServiceTest` (7).
- **integração: 267/267** (8 novos)
  - `RequestIdIT` (2), `LoginRateLimitIT` (2), `PrometheusIT` (1), `ClientePrivacidadeIT` (3).
- **frontend: 249/249** (9 novos)
  - `lib/seguranca.test.ts` (6: CSP/nonce, request id) e `cliente-privacidade.test.tsx` (3).
- **e2e: 6/6** (Playwright/Chromium), executado 3 vezes seguidas contra a stack de dev, inclusive com as imagens finais:
  1. login;
  2. cliente + pet;
  3. agendamento até a conclusão, com caixa aberto e Pix;
  4. venda no PDV;
  5. fechamento de caixa conferido;
  6. relatório de vendas.

## Validações

| Item | Resultado |
|---|---|
| backend build (`./mvnw -B clean verify`) | PASS |
| frontend build (`npm run build`) | PASS (todas as páginas dinâmicas, requisito do nonce) |
| lint (`eslint --max-warnings=0`) | PASS |
| typecheck (`next typegen && tsc`) | PASS |
| Docker dev (`compose.yaml`) | PASS |
| Docker prod local (`compose.prod.yaml`, `tls internal`, portas 8880/8443) | PASS |
| E2E | PASS (6/6) |
| Scan Trivy (CRITICAL corrigível) | PASS depois da correção do Tomcat (ver divergência 1) |
| `actionlint` (todos os workflows) | PASS |
| `shellcheck` (todos os scripts) | PASS |

**Produção local (projeto isolado `petconnect-prodlocal`, removido ao final)**
- Portas: só o Caddy publica portas; a API não é alcançável pelo host.
- `smoke.sh` 7/7: login 200, CSP com nonce, HSTS, X-Request-Id, HTTP→HTTPS 308, readiness na 9090, login do usuário de smoke.
- Métricas: `/actuator/prometheus` responde 200 na 9090 e 404 na 8080.
- Limite de login: 20 falhas do mesmo IP → 429 com `Retry-After`.
- Logs: JSON ECS, com `requestId` e sem dados pessoais.
- Backup/restore real: 2 clientes → backup → 3º cliente → restore → volta a 2.
- **Rollback automático:** deploy de uma tag inexistente → pull falha → volta sozinho para a tag anterior → smoke OK → saída 1. Tag com injeção (`x;rm -rf /`) é recusada.
- `monitorar.sh`: alerta `WEB_FORA` com o Web parado.

**Backup na stack de dev**
- `backup.sh` + `restore-test.sh`: 9/9 conferências (8 tabelas + Flyway 10) num MySQL descartável.
- Casos negativos: 1 byte adulterado → hash não confere; passphrase errada → falha.

## Implementação

- **Infra**
  - `compose.prod.yaml`:
    - Caddy + Web + API + MySQL + MongoDB (com autenticação);
    - redes `borda`, `interna` (`internal: true`) e `saida`;
    - `mem_limit`, healthchecks e rotação de logs;
    - imagens do GHCR por `TAG`; migração do legado desligada.
  - `deploy/Caddyfile`:
    - HTTPS automático, HSTS, zstd/gzip, corpo ≤ 7 MB;
    - `X-Request-Id` gerado na borda;
    - log JSON sem a busca e com IP truncado;
    - `admin off`.
  - `.env.prod.example`, sem valores.
- **Scripts:** `backup.sh`, `restore-test.sh`, `restore.sh`, `deploy.sh`, `smoke.sh`, `monitorar.sh` e `_comum.sh`.
  - Leitura segura do `.env`.
  - Rodam em Linux e no Git Bash do Windows, usado na validação.
- **Backend**
  - `RequestIdFilter` (MDC `requestId`).
  - `MascaraDadosPessoais` + `MascaramentoLogs` (customizer do log estruturado).
  - Perfil prod: logs ECS e management na 9090.
  - `LimitadorLogin` + `LimiteExcedidoException` (429 com `Retry-After`).
  - `micrometer-registry-prometheus`.
  - LGPD: `ClientePrivacidadeService` e `ClientePrivacidadeController` (`GET /clientes/{id}/dados-pessoais`, `POST /clientes/{id}/anonimizar`), só ADMIN por URL e por `@PreAuthorize`, com auditoria.
- **Frontend**
  - `lib/seguranca.ts`: CSP com nonce e request id.
  - `proxy.ts` aplica CSP e X-Request-Id em toda resposta; o layout lê os cabeçalhos para forçar a renderização dinâmica.
  - `apiFetch` e a action de login repassam `X-Request-Id`; o login repassa também o `X-Forwarded-For`.
  - Cartão "Privacidade (LGPD)" no cliente, só para ADMIN: exportar JSON e anonimizar com motivo + confirmação `ANONIMIZAR`.
- **E2E**
  - `playwright.config.ts`, `e2e/global-setup.ts` (usuário ADMIN efêmero + produto com estoque, via API), `e2e/global-teardown.ts` (desativa o usuário) e `e2e/fluxos.spec.ts`.
- **CI**
  - Novos: `e2e.yml` (compose com segredos efêmeros mascarados), `seguranca.yml` (Trivy fs + imagens, SBOM CycloneDX como artefato) e `deploy.yml` (`workflow_dispatch` com a tag, `environment: producao`, SSH, input validado e passado por env).
  - **Todas as actions fixadas por SHA**, na mesma versão principal de antes.
- **Documentação**
  - `docs/producao.md` e 10 runbooks: README, operação diária, deploy, rollback, backup/restore, incidente, rotação de segredos, recuperação de desastre, go-live e LGPD.
  - Atualizados: README, `arquitetura.md`, `banco-de-dados.md` e `next-stage/README.md`.

## Divergências

1. **Tomcat 11.0.26 (achado do Trivy).** A imagem da API tinha 3 CVEs **críticas** no `tomcat-embed-core` 11.0.24, trazido pelo Spring Boot 4.1.1: CVE-2026-65182, -65905 e -68525. `tomcat.version` foi fixado em 11.0.26 no `pom.xml`. Suítes completas e E2E repetidos com a versão nova. Remover o override quando o Boot trouxer ≥ 11.0.25.
2. **Limite de login conta só falhas.** Contar todas as tentativas bloquearia logins legítimos e os ITs. O bloqueio por conta (5 falhas) continua na frente.
3. **Scan de dependências em modo offline.** Localmente, o `trivy fs` rodou com `--offline-scan`, porque o Maven Central devolveu 429 às consultas de POM do Trivy. No CI ele roda online, com `timeout: 15m0s`.
4. **Base de teste do E2E.** O E2E roda contra o compose de dev/CI, que usa as mesmas imagens, e não contra a produção local: o setup precisa da API, que em produção não é exposta. A produção local foi validada pelo `smoke.sh`.
5. **`@AutoConfigureMetrics` na base dos ITs.** Os testes do Spring Boot desligam a exportação de métricas. A anotação foi posta na base `IntegrationTest` para manter um único contexto.
6. **Configuração do GitHub não aplicada.** Proteção da `main` e environment `producao` não foram aplicados, por decisão da spec (DA-23). Os comandos estão no [go-live](../runbooks/go-live.md).
7. **Adaptações para o Windows.** Os scripts têm `pwd -W` e `NULO=NUL`, só para rodar a validação no Git Bash. Não afetam o Linux.

## Problemas encontrados e corrigidos na validação

- A conclusão de atendimento, a finalização da venda e a sangria usam `window.confirm`. O E2E aceita e confere esses diálogos.
- Os `isVisible()` e `.all()` do Playwright não esperam a página; os testes passaram a usar asserções que esperam.
- No `backup.sh`, uma falha no meio deixava a pasta parcial. Agora ela é removida (trap ERR) e o `ultimo-ok` só é gravado no fim.
- O `sha256sum` do Git Bash grava `*arquivo` (modo binário); o filtro do manifesto passou a aceitar os dois formatos.
- `smoke.sh` usava `eval`: reescrito sem `eval`, sem `errexit` e com `shellcheck` limpo.
- `monitorar.sh`: um container parado gerava uma mensagem quebrada; corrigido.
- Em testes rodados em paralelo com o backend, 5 testes do frontend estouraram o tempo. Sozinhos e na suíte completa sem concorrência, passam.

## Pendências

- **Do dono:**
  - contratar a VPS e o domínio;
  - criar o environment `producao` (revisores + secrets `DEPLOY_*`) e a proteção da `main`;
  - `docker login ghcr.io` no servidor, se os pacotes forem privados;
  - configurar `BACKUP_REMOTO` e `ALERTA_WEBHOOK_URL`;
  - guardar o `.env.prod` e a `BACKUP_PASSPHRASE` no cofre.
- **Não exercitado de verdade:**
  - emissão Let's Encrypt real (testado com `tls internal`);
  - envio ao webhook de alerta;
  - execução dos workflows novos no GitHub (sem push);
  - deploy via SSH (o `deploy.sh` foi validado localmente, inclusive o rollback).
- **Máquina local:** o `monitorar.sh` disparou um `DISCO_85` real — o disco `C:` deste PC está com **93% de uso**.
- **LGPD:** decisão sobre o script com dados reais no histórico do git (`docs/legacy`).
- **Commits:** as etapas 01–09 seguem sem commit.

## Handoff da etapa 10

O prompt completo para o ChatGPT gerar `10-app-mobile.md` está em **[10-prompt-chatgpt.md](10-prompt-chatgpt.md)** (app mobile Flutter consumindo a API).

Estado entregue:

- **Exposição da API:** hoje só o Next é público. A etapa 10 decide como expor a API (`api.<DOMINIO>` no Caddy) sem mexer na web.
- **Auth:** JWT de 8 h sem refresh; revogação por `token_version`; limite por IP e bloqueio por conta.
- **Contrato:** `/api/v1`, ProblemDetail, `PageResponse` e OpenAPI via springdoc (desligado no perfil prod).
- **Operação pronta para receber o app:** logs com `requestId` (o app deve enviar `X-Request-Id`), métricas internas, backup/restore e deploy com rollback.
- **Projetos irmãos:** `PetConnect` (Flutter, org TecnoDreamer) e `PetConnect-API` (antigo). A etapa 10 decide se reaproveita ou descontinua.
