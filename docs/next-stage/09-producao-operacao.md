# Etapa 09 — Produção e operação (endurecimento para go-live)

> Spec escrita pelo Claude Code (o ChatGPT devolveu o prompt em vez da spec), no formato das etapas anteriores.
> Base: `feat/migracao-legado` → branch `feat/producao-operacao`. Sem commit/push automático.

## 1. Objetivo

Deixar o PetConnect Web pronto para rodar em produção num petshop, com operação segura e recuperável:
- deploy reproduzível;
- HTTPS;
- cabeçalhos de segurança;
- segredos fora do git;
- backup automatizado com teste de restore;
- logs sem dados pessoais e correlacionáveis;
- métricas internas e alertas mínimos;
- limite de tentativas no login;
- E2E dos fluxos críticos;
- scan de vulnerabilidades e SBOM;
- deploy com aprovação e smoke test;
- LGPD (inventário, exportação e anonimização do titular);
- runbooks.

Nenhum domínio de negócio novo.

## 2. Análise obrigatória

1. Ler `compose.yaml`, os dois `Dockerfile`, `application.yml`, `application-prod.yml`, `SecurityConfig`, `proxy.ts`, `next.config.ts`, `lib/api.ts` e `app/actions/auth.ts`, além de todos os workflows em `.github/`.
2. Confirmar os nomes das imagens publicadas por `delivery.yml` (GHCR) e o que cada container precisa em variáveis.
3. Confirmar que a API não precisa ser pública: o navegador fala só com o Next (BFF). O app mobile da etapa 10 decidirá sobre expor `/api`.
4. Verificar a API de logging estruturado do Spring Boot 4.1 (`logging.structured.*`, `StructuredLoggingJsonMembersCustomizer`, `JsonWriter.ValueProcessor`).
5. Verificar como o Next 16 aplica nonce de CSP (cabeçalho `Content-Security-Policy` na requisição, definido no `proxy.ts`).
6. Rodar a baseline: backend `verify`, frontend (vitest, lint, typecheck, build).

## 3. Decisões arquiteturais

| ID | Decisão | Justificativa |
|---|---|---|
| DA-01 | **1 VPS Linux + Docker Compose de produção** (`compose.prod.yaml`) com imagens do GHCR por tag | Barato, simples, suficiente para um petshop. Kubernetes e PaaS ficam fora. |
| DA-02 | **Caddy 2** como proxy reverso, com HTTPS automático (Let's Encrypt), HTTP→HTTPS, HSTS, compressão e limite de corpo de 7 MB | TLS sem manutenção; config curta. `tls internal` permite validar localmente. |
| DA-03 | Só o Caddy publica portas (80/443). Web, API, MySQL e Mongo ficam em redes Docker internas. **A API não é exposta à internet** | O BFF já faz todas as chamadas; isso reduz a superfície. |
| DA-04 | Redes: `borda` (caddy, web), `interna` (`internal: true`: web, api, mysql, mongo) e `saida` (api, para SMTP) | Bancos sem rota para a internet; a API alcança só o SMTP. |
| DA-05 | Segredos em `.env.prod` **só no servidor** (modo 600, dono root); o repo tem apenas `.env.prod.example` com placeholders | Simples e auditável; Docker secrets ficam como evolução. |
| DA-06 | Flyway roda na subida da API. `deploy.sh` faz **backup antes**. Migration destrutiva exige plano e aprovação | O rollback de banco é só por restore. |
| DA-07 | Rollback de aplicação = voltar para a tag anterior (`.ultimo-deploy`) e `up -d` | Imagens imutáveis por tag. |
| DA-08 | Backup diário por cron: `mysqldump`, `mongodump`, tar das fotos → criptografia AES-256 (`openssl enc -pbkdf2`) → retenção 7 diários, 4 semanais e 6 mensais → cópia off-site opcional via `rclone` | Ferramentas padrão, sem serviço pago. |
| DA-09 | **Teste de restore automatizado** (`restore-test.sh`): restaura o último backup num MySQL descartável e compara com o manifesto de contagens gravado no backup | Backup sem restore testado não conta. |
| DA-10 | Logs JSON (ECS) no perfil prod via `logging.structured.format.console=ecs` + `MascaramentoLogs` (customizer), que mascara CPF, e-mail, telefone, JWT e `Bearer` em qualquer valor texto | Recurso nativo do Boot; o mascaramento é a rede de segurança. |
| DA-11 | Request id: o `proxy.ts` gera/propaga `X-Request-Id` → `apiFetch` repassa → `RequestIdFilter` na API coloca em MDC (`requestId`) e ecoa na resposta | Correlação Next ↔ API. Valor externo validado (máx. 64 caracteres `[A-Za-z0-9-]`). |
| DA-12 | Métricas: Micrometer + Prometheus em `/actuator/prometheus`. No perfil prod, o **management fica na porta 9090** (não publicada nem roteada pelo Caddy) | "Rota interna" de verdade. O healthcheck do compose.prod usa a 9090. |
| DA-13 | Alertas mínimos: `monitorar.sh` (cron a cada 5 min) verifica health da API/Web, disco > 85%, idade do último backup > 26 h e erros 5xx no log do Caddy. Envia para `ALERTA_WEBHOOK_URL` (Discord/Slack/Telegram) | Sem stack de observabilidade paga. |
| DA-14 | **Rate limit na aplicação, em memória**: login por IP (20 tentativas em 5 min → 429 + `Retry-After`), somado ao bloqueio por conta que já existe e ao limitador de recuperação | Implantação é de **uma instância** (DA-01), então memória é suficiente. Escalar horizontalmente exigiria Bucket4j + Redis (fora de escopo, registrado). O rate limit na borda do Caddy exige plugin e não enxerga a Server Action de login como rota distinta. |
| DA-15 | O IP do cliente chega por `X-Forwarded-For` (Caddy → Next → API). A API só confia porque não é alcançável de fora (DA-03). O login do Next passa a repassar o XFF | Mesmo padrão da recuperação de senha. |
| DA-16 | CSP com **nonce por requisição** gerado no `proxy.ts`: `script-src 'self' 'nonce-…' 'strict-dynamic'`; `style-src 'self' 'unsafe-inline'`; `img-src 'self' blob: data:`; `font-src 'self'`; `connect-src 'self'`; `frame-ancestors 'none'`; `form-action 'self'`; `base-uri 'self'`; `object-src 'none'`; `upgrade-insecure-requests` só com cookie seguro | Padrão recomendado pelo Next; estilos inline vêm do próprio framework. |
| DA-17 | HSTS (`max-age=31536000; includeSubDomains`) aplicado pelo Caddy, só em HTTPS | Cabeçalho de transporte pertence à borda. |
| DA-18 | Cookie de sessão continua `httpOnly`, `SameSite=Lax`, `Secure` em produção, sem `Domain` (host-only). CORS da API restrito a `CORS_ALLOWED_ORIGINS` = origem pública | A API não é chamada pelo navegador; CORS mínimo. |
| DA-19 | E2E com **Playwright** (Chromium) em `frontend/e2e`. Setup via API cria um usuário ADMIN efêmero e um produto com estoque; o teardown desativa o usuário | Fluxos isolados dos dados existentes; sem hard-delete. |
| DA-20 | CI: `e2e.yml` (compose de dev com segredos gerados no job), `seguranca.yml` (Trivy fs + imagens com falha em CRITICAL corrigível, SBOM CycloneDX como artefato) | Ferramentas gratuitas. |
| DA-21 | `deploy.yml`: `workflow_dispatch` com a tag, `environment: producao` (aprovação obrigatória configurada pelo dono), SSH → `scripts/deploy.sh <tag>` (backup → pull → up → smoke → rollback automático se falhar) | Deploy manual, auditável e reversível. |
| DA-22 | Todas as actions fixadas por **SHA** (com comentário da versão); Dependabot atualiza | Cadeia de suprimentos. |
| DA-23 | Proteção da `main` (PR obrigatório, checks obrigatórios, 1 revisão, sem force-push): **documentada com comandos `gh`**; aplicar é decisão do dono | Configuração de conta não é feita pela automação. |
| DA-24 | Retenção: logs Docker com rotação (`json-file` 10 MB × 5); backups conforme DA-08; **auditoria no Mongo sem TTL** (prova de operações financeiras e de acesso, guardada ao menos pelo prazo fiscal de 5 anos; expurgo anual manual documentado) | TTL apagaria evidência de forma silenciosa. |
| DA-25 | LGPD — direitos do titular sem hard-delete: `GET /api/v1/clientes/{id}/dados-pessoais` (exportação JSON, ADMIN) e `POST /api/v1/clientes/{id}/anonimizar` (ADMIN, motivo obrigatório, irreversível) | Atende acesso/portabilidade e eliminação preservando o histórico financeiro. |
| DA-26 | Anonimização: nome → `Cliente anonimizado #<id>`; CPF, e-mail, nascimento e observações → NULL; telefone → marcador único `0` + id com zeros (11 dígitos, DDD 0 inexistente); endereço desvinculado e limpo; cliente inativo; observações dos pets limpas; vendas e agendamentos mantidos. Auditoria `CLIENTE_ANONIMIZADO` (só motivo) e `CLIENTE_DADOS_EXPORTADOS` | Telefone é NOT NULL/UNIQUE; o marcador é inequívoco. |
| DA-27 | RTO 4 h, RPO 24 h. Teste de desastre semestral: servidor novo → `.env.prod` do cofre → restore → smoke | Adequado ao porte. |

## 4. Escopo

`compose.prod.yaml`, `deploy/Caddyfile`, `.env.prod.example`, `scripts/{backup,restore,restore-test,deploy,smoke,monitorar}.sh`. Backend: `RequestIdFilter`, `MascaramentoLogs`, `LimitadorLogin` (429), Prometheus e porta de management no prod, LGPD (exportar/anonimizar). Frontend: CSP com nonce, request id, XFF no login e cartão "Privacidade (LGPD)" no cliente (ADMIN). Por fim: E2E Playwright, workflows (`e2e`, `seguranca`, `deploy`), actions por SHA, documentação e runbooks.

## 5. Fora de escopo

- Kubernetes, PaaS e várias instâncias; Redis/Bucket4j distribuído.
- Stack Prometheus/Grafana/Loki hospedada (o endpoint fica pronto).
- WAF.
- Storage de fotos em nuvem (a interface já permite).
- Aplicar a proteção de branch e os *environments* no GitHub (fica documentado).
- Reescrever o histórico do git.
- Expor a API publicamente (etapa 10).

## 6. Arquitetura de produção

```
Internet ──80/443──▶ [caddy]  (rede borda)
                        │ reverse_proxy web:3000  (+ X-Request-Id, HSTS, gzip/zstd, body ≤ 7 MB)
                        ▼
                     [web: Next BFF]  (borda + interna)
                        │ http://api:8080  (X-Request-Id, X-Forwarded-For, Bearer do cookie)
                        ▼
                     [api: Spring]  (interna + saida→SMTP)   management :9090 (só interna)
                     │                 │
              [mysql:3306]       [mongo:27017]      (interna, internal: true, sem portas publicadas)
volumes: mysql-data, mongo-data, uploads (fotos), caddy-data (certificados), caddy-config
host: /opt/petconnect (compose.prod.yaml, .env.prod, deploy/, scripts/, backups/)
```

## 7. `compose.prod.yaml`

Arquivo completo na raiz. Pontos:
- Imagens `ghcr.io/aleksgustavo/petconnect-web-{api,web}:${TAG}`.
- `restart: unless-stopped`, `mem_limit`, healthchecks e logs com rotação.
- `APP_ENV=prod`, `COOKIE_SECURE=true`.
- Sem Mailpit; sem portas de banco.
- Caddy com `HTTP_PORT`/`HTTPS_PORT` configuráveis (para validação local sem conflitar com o XAMPP).

## 8. Proxy reverso e HTTPS

`deploy/Caddyfile` completo:
- Site `{$DOMINIO}` com `tls {$CADDY_TLS}`: o e-mail ACME em produção, `internal` em teste local.
- `encode zstd gzip`, `request_body max_size 7MB`.
- `header` com HSTS e `-Server`.
- `reverse_proxy web:3000` com `header_up X-Request-Id {http.request.uuid}`.
- Log de acesso em JSON (usado pelo `monitorar.sh`).

## 9. Cabeçalhos de segurança, CSP, cookies e CORS

**Cabeçalhos**

| Cabeçalho | Valor | Onde |
|---|---|---|
| Content-Security-Policy | ver DA-16 (nonce por requisição; em dev adiciona `'unsafe-eval'`) | `proxy.ts` |
| Strict-Transport-Security | `max-age=31536000; includeSubDomains` | Caddy |
| X-Frame-Options | `DENY` | next.config (mantido) |
| X-Content-Type-Options | `nosniff` | next.config (mantido) |
| Referrer-Policy | `strict-origin-when-cross-origin` | next.config (mantido) |
| Permissions-Policy | `camera=(), microphone=(), geolocation=()` | next.config (mantido) |
| X-Request-Id | uuid/propagado | proxy.ts + API |

**Cookie e CORS**
- Cookie `petconnect_session`: `httpOnly; SameSite=Lax; Secure` (prod); sem `Domain`.
- CORS: `CORS_ALLOWED_ORIGINS=https://<DOMINIO>`.

## 10. Segredos e configuração por ambiente

`.env.prod.example` traz a lista completa. Onde cada grupo vive:

| Variável | Obrig. | Exemplo | Onde vive |
|---|---|---|---|
| `TAG` | sim | `v1.0.0` | `.env.prod` (o deploy.sh atualiza) |
| `DOMINIO` / `CADDY_TLS` | sim | `<DOMINIO>` / `admin@<DOMINIO>` | `.env.prod` |
| `MYSQL_*`, `MONGO_*`, `JWT_SECRET`, `ADMIN_*`, `SMTP_*` | sim | placeholders | `.env.prod` (servidor, 600) + cofre de senhas do dono |
| `BACKUP_PASSPHRASE` | sim | — | `.env.prod` + cofre (**sem ela não há restore**) |
| `SMOKE_USERNAME` / `SMOKE_PASSWORD` | sim | usuário técnico VENDAS | `.env.prod` |
| `ALERTA_WEBHOOK_URL`, `BACKUP_REMOTO` | não | — | `.env.prod` |
| `DEPLOY_HOST/USER/SSH_KEY` | sim p/ CI | — | GitHub → Environment `producao` → secrets |

**Rotação do `JWT_SECRET`:** trocar no `.env.prod` e rodar `up -d api`. Todas as sessões caem; os usuários logam de novo.

## 11. Banco: migrations, backup, restore, retenção

- **`scripts/backup.sh`**: dump MySQL (`--single-transaction --routines`), dump Mongo (`--archive --gzip`) e tar do volume de fotos, cada um cifrado com `openssl enc -aes-256-cbc -pbkdf2 -iter 200000`.
  - Grava `manifesto.txt` com contagens das tabelas principais e a versão do Flyway.
  - Aplica a retenção e faz `rclone copy` se `BACKUP_REMOTO` existir. Sai ≠ 0 em qualquer falha.
- **`scripts/restore.sh <pasta>`**: restore real (para produção), com confirmação digitada.
- **`scripts/restore-test.sh [pasta]`**: MySQL 8.4 descartável → import → compara contagens com o manifesto → remove o container.
- **Retenção**: diário 7, semanal (domingo) 4, mensal (dia 1) 6.

## 12. Observabilidade

**Logs**
- Perfil prod: `logging.structured.format.console: ecs`, `logging.structured.json.customizer: …MascaramentoLogs`, MDC `requestId`.
- Máscaras: CPF (`***.***.***-**`), e-mail (`***@dominio`), telefone, JWT, `Bearer <token>`.

**Métricas e health**
- `management.endpoints.web.exposure.include: health,info,prometheus`.
- Prod: `management.server.port: 9090`.
- Health: liveness e readiness (já existentes).

**Alertas** — `monitorar.sh` gera estes alertas (texto sem PII):

| Alerta | Condição |
|---|---|
| `API_FORA` | API fora do ar |
| `WEB_FORA` | Web fora do ar |
| `DISCO_85` | disco acima de 85% |
| `BACKUP_ATRASADO` | último backup com mais de 26 h |
| `ERROS_5XX` | mais de 20 respostas 5xx em 5 min |

## 13. Rate limiting

`LimitadorLogin` em memória por IP: janela deslizante de 5 min e limite de 20. Excedido: 429 ProblemDetail + `Retry-After` (s) + log `LOGIN_LIMITADO` sem o login digitado. Continuam valendo o bloqueio por conta (já existe) e o limitador de recuperação (já existe).

## 14. CI/CD

**Novos workflows**
- `e2e.yml`: push/PR para a main e `workflow_dispatch`. Gera um `.env` com segredos aleatórios, `docker compose up -d --build --wait`, `npx playwright install --with-deps chromium`, `npm run e2e`; relatório HTML como artefato.
- `seguranca.yml`: push/PR e semanal. Trivy `fs` (deps) e Trivy `image` (API e Web construídas localmente), `severity: CRITICAL`, `ignore-unfixed`, `exit-code 1`; SBOM CycloneDX (`trivy sbom`) como artefato.
- `deploy.yml`: `workflow_dispatch(tag)`, `environment: producao`, SSH com chave do secret → `scripts/deploy.sh <tag>`.

**Workflows existentes:** todas as actions por SHA.

## 15. Testes E2E Playwright

**Arquivos**
- `frontend/playwright.config.ts`: `baseURL` = `E2E_BASE_URL` (padrão `http://localhost:3000`), `ignoreHTTPSErrors`, Chromium, `retries` 1 no CI, `trace: retain-on-failure`.
- `e2e/global-setup.ts` / `global-teardown.ts` e `e2e/fluxos.spec.ts`.

**Cenários**
1. login inválido e válido;
2. cadastro de cliente + pet;
3. agendamento → confirmar → iniciar → concluir (com caixa aberto e forma PIX);
4. venda no PDV (caixa aberto);
5. fechamento de caixa;
6. relatório de vendas do dia mostrando a venda.

**Variáveis**
- `E2E_ADMIN_USERNAME` / `E2E_ADMIN_PASSWORD`: admin do bootstrap; lidas do ambiente, nunca impressas.
- `E2E_API_URL`: setup e teardown.

**Execução local:** `npm run e2e` (com a stack de dev no ar).

## 16. LGPD e retenção

**Inventário (tabela → dados pessoais → base legal)**

| Tabela | Dados pessoais | Base legal |
|---|---|---|
| `cliente` | nome, CPF, nascimento, telefone, e-mail, observações | execução de contrato |
| `endereco` | endereço | execução de contrato |
| `usuario` | username, e-mail | execução de contrato / legítimo interesse (acesso) |
| `funcionario` | nome, CPF, nascimento, sexo, telefone | obrigação legal / contrato de trabalho |
| `fornecedor` | contato, e-mail, telefone (pessoa jurídica, contato pessoal) | contrato |
| `historico_consulta` e `carteira_vacina` | nome do veterinário | contrato |
| auditoria (Mongo) | username, ids | legítimo interesse / segurança |
| backups | todos os acima | mesma base, cifrados |

**Direitos do titular:** exportação e anonimização conforme DA-25 e DA-26.

**Incidente:** runbook `incidente.md` — conter, avaliar, registrar e comunicar à ANPD e ao titular em até 3 dias úteis quando houver risco relevante.

**Script legado com dados reais:** recomendação de `git filter-repo` + rotação de credenciais; a decisão é do dono.

## 17. Regras

- **R-01** API, MySQL e Mongo nunca publicam porta em produção.
- **R-02** HTTPS obrigatório; HTTP só redireciona.
- **R-03** Toda resposta HTML tem CSP com nonce único.
- **R-04** Nenhum segredo no git; `.env.prod` só no servidor.
- **R-05** Deploy sempre faz backup antes.
- **R-06** Deploy com smoke falho volta para a tag anterior.
- **R-07** Backup é cifrado; sem passphrase não há restore (guardar no cofre).
- **R-08** O teste de restore compara com o manifesto.
- **R-09** Logs de produção em JSON e mascarados.
- **R-10** Toda requisição tem `X-Request-Id`.
- **R-11** Prometheus só na porta interna.
- **R-12** 21ª tentativa de login do mesmo IP em 5 min → 429.
- **R-13** Actions por SHA.
- **R-14** CVE crítica corrigível quebra o CI.
- **R-15** Anonimização é irreversível, exige motivo e só o ADMIN faz.
- **R-16** Anonimização não apaga linha nenhuma.
- **R-17** Exportação e anonimização são auditadas sem dados pessoais.
- **R-18** Auditoria sem TTL.
- **R-19** A migração do legado continua desligada em produção.
- **R-20** Nada de hard-delete, double para dinheiro ou `any`.

## 18. Testes unitários novos

- `MascaramentoLogsTest`: CPF com e sem máscara, e-mail, telefone, JWT, Bearer, texto neutro intacto, nulo.
- `RequestIdFilterTest`: gera quando ausente, aceita válido, rejeita inválido e longo, limpa o MDC, ecoa o cabeçalho.
- `LimitadorLoginTest`: permite até 20, bloqueia a 21ª, libera depois da janela, IPs independentes, `retryAfter`.
- `ClientePrivacidadeServiceTest`: anonimiza todos os campos, telefone marcador, endereço limpo, pets sem observação, já anonimizado → 409, motivo curto → 400, auditoria sem PII.

## 19. Testes de integração novos

- `RequestIdIT`: resposta ecoa o id recebido; gera quando ausente.
- `LoginRateLimitIT`: 21ª tentativa → 429 com `Retry-After`; outro IP segue.
- `PrometheusIT`: `/actuator/prometheus` responde com métricas `jvm_` (no teste, mesma porta).
- `ClientePrivacidadeIT`:
  - exportação traz cliente, endereço, pets e vendas; VENDAS → 403;
  - anonimização grava marcadores, venda continua ligada, auditorias gravadas; segunda vez → 409;
  - busca por nome antigo não encontra.

## 20. Testes frontend novos

- `csp.test.ts`: política contém o nonce, `strict-dynamic`, `frame-ancestors 'none'`; dev adiciona `unsafe-eval`; `upgrade-insecure-requests` só com cookie seguro; nonce muda a cada chamada.
- `request-id.test.ts`: reaproveita válido, gera quando ausente ou inválido.
- `cliente-privacidade.test.tsx`: ADMIN vê exportar/anonimizar; confirmação exige motivo; não-ADMIN não vê.

## 21. Documentação

- `docs/producao.md`: visão, arquitetura, instalação do servidor, variáveis, go-live.
- `docs/runbooks/`: `operacao-diaria.md`, `deploy.md`, `rollback.md`, `backup-restore.md`, `incidente.md`, `rotacao-segredos.md`, `recuperacao-desastre.md`, `go-live.md`, `lgpd.md`.
- Atualizar README (status, links), `arquitetura.md` (produção, observabilidade), `banco-de-dados.md` (auditoria nova, retenção) e `next-stage/README.md`.

## 22. Critérios de aceite

- [ ] `docker compose -f compose.prod.yaml` sobe localmente com `tls internal`.
  - [ ] HTTPS responde; HTTP → 308 para HTTPS; HSTS e CSP presentes.
  - [ ] Só o Caddy publica portas.
  - [ ] Cookie com `Secure`.
- [ ] `backup.sh` gera arquivos cifrados + manifesto; `restore-test.sh` restaura e confere.
- [ ] Logs do perfil prod em JSON com `requestId` e sem dados pessoais.
- [ ] `/actuator/prometheus` responde só na 9090 no perfil prod.
- [ ] 429 no login por IP.
- [ ] E2E: 6 fluxos passando localmente.
- [ ] Workflows novos válidos (`actionlint` ou revisão) e actions por SHA.
- [ ] Exportação e anonimização LGPD funcionando e auditadas.
- [ ] Todas as suítes verdes; lint e typecheck sem avisos.
- [ ] Docs e runbooks completos.

## 23. Riscos

- CSP quebrar algum script inline: E2E e navegação manual validam.
- `strict-dynamic` em navegador antigo: fallback `'self'`.
- Backup sem passphrase guardada = backup inútil: runbook e go-live exigem o cofre.
- VPS única = ponto único de falha: RTO/RPO assumidos; backup off-site.
- Rate limit por IP atrás de NAT (loja inteira num IP): limite folgado (20/5 min).
- E2E instável: `retries` 1 no CI e trace em falha.

## 24. Ordem de implementação

1. Baseline.
2. Backend (request id, máscara, limitador, prometheus, LGPD) + testes.
3. Frontend (CSP, request id, XFF, cartão LGPD) + testes.
4. `compose.prod` + Caddy + `.env.prod.example`.
5. Scripts.
6. Validação local de prod.
7. Playwright.
8. Workflows + SHAs.
9. Documentação.
10. Validação completa.
11. Relatório.

## 25. Restrições

- NÃO editar migrations V1–V10.
- NÃO alterar `legacy/` nem `docs/legacy/`.
- NÃO hard-delete.
- NÃO double/float para dinheiro.
- NÃO desabilitar testes/lint.
- NÃO usar `any` no front.
- NÃO fazer commit/push automático.
- NUNCA versionar ou logar segredos/dados pessoais.
- NÃO adicionar novo domínio de negócio.
- NÃO aplicar configurações de conta no GitHub (branch protection/environments): só documentar.

## 26. Relatório final

`09-producao-operacao-relatorio.md` deve trazer:
- "unitários: X/Y, integração: X/Y, frontend: X/Y, e2e: X/Y";
- PASS/FAIL de: backend build, frontend build, lint, typecheck, Docker dev, Docker prod local, E2E e scan (se executável localmente);
- divergências e pendências;
- handoff da etapa 10 (app mobile Flutter consumindo a API: exposição da API, CORS/tokens para mobile, versionamento de API).

## 27. Definição de pronto

**Stack**
- Produção local sobe atrás do Caddy com HTTPS, CSP e HSTS.
- Nenhuma porta de banco nem de API exposta.

**Operação**
- Backup cifrado com restore testado.
- Logs JSON mascarados e correlacionados.
- Métricas internas.
- Alertas por script.

**Segurança e LGPD**
- Login limitado por IP.
- Titular exportável e anonimizável.
- Actions por SHA.
- Scan com CVE crítica quebrando o CI.

**Qualidade**
- E2E dos 6 fluxos verde.
- Todas as suítes verdes.

**Documentação**
- Runbooks de deploy, rollback, restore, incidente, rotação, desastre e go-live.
