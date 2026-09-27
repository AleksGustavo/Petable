# 🐾 PetConnect Web

Sistema web de **gestão de petshop** (clientes, pets, agendamentos de banho e tosa, carteira de vacinas, PDV, estoque e relatórios), responsivo, para desktop e celular.

É a evolução do **Pet & Pet** (Projeto Integrador Fatec Araras, 2º sem/2025), que foi escrito em PHP + MySQL. O código antigo continua em [`legacy/php`](legacy/php) como referência até a migração terminar.

Faz parte do ecossistema **PetConnect** da TecnoDreamer, junto com o app mobile Flutter (`PetConnect`) e a API do app (`PetConnect-API`).

## Stack

| Camada | Tecnologia |
|---|---|
| Frontend | Next.js 16 (App Router, Server Actions), React 19, TypeScript, Tailwind CSS 4, Zod |
| Backend | Spring Boot 4.1 (Java 21): Web MVC, Security (JWT), Data JPA, Data MongoDB, Validation, Actuator, springdoc |
| Banco relacional | MySQL 8.4, com schema versionado pelo Flyway |
| Banco de documentos | MongoDB 7, para a trilha de auditoria |
| Testes | JUnit 5, Mockito, Testcontainers (MySQL e Mongo reais), JaCoCo · Vitest, Testing Library |
| DevOps | Docker, Docker Compose, GitHub Actions (CI, CodeQL, imagens no GHCR), Dependabot |

## Estrutura

```
PetConnect-Web/
├── backend/            API Spring Boot (monólito modular)
├── frontend/           App Next.js (BFF: o navegador nunca vê o JWT)
├── legacy/php/         Sistema antigo em PHP (somente leitura, referência)
├── docs/
│   ├── arquitetura.md
│   ├── banco-de-dados.md
│   ├── github.md                  organização, proteção de branch, segredos
│   ├── producao.md                arquitetura de produção, instalação, segurança, observabilidade
│   ├── runbooks/                  deploy, rollback, backup/restore, incidente, rotação, desastre, go-live, LGPD
│   ├── migracao-legado.md         runbook da migração do sistema PHP
│   ├── next-stage/                especificações .md de cada etapa (ChatGPT → Claude)
│   └── legacy/                    documentação original do PI (diagramas, dicionário, slides)
├── compose.yaml        desenvolvimento: MySQL + MongoDB + Mailpit + API + Web (+ perfil migracao)
├── compose.prod.yaml   produção: Caddy (HTTPS) + Web + API + MySQL + MongoDB, só o Caddy exposto
├── deploy/Caddyfile    proxy reverso de produção
├── scripts/            backup, restore(-test), deploy, smoke, monitorar
└── .github/            workflows, Dependabot, template de PR, CODEOWNERS
```

## Rodando localmente

Pré-requisitos: Docker Desktop. Para desenvolver fora do container, também JDK 21 e Node 24.

```bash
cp .env.example .env
docker compose up -d --build
```

| Serviço | URL padrão |
|---|---|
| Web | http://localhost:3000 (`WEB_PORT`) |
| API | http://localhost:8081 (`API_PORT`) |
| Swagger | http://localhost:8081/swagger-ui.html |
| Health | http://localhost:8081/actuator/health |
| MySQL | `localhost:3307`, banco `petconnect_web` |
| MongoDB | `mongodb://localhost:27019/petconnect_web` |
| Mailpit (e-mails de dev) | http://localhost:8025 (`MAILPIT_PORT`) |

O primeiro usuário ADMIN é criado a partir de `ADMIN_USERNAME`/`ADMIN_EMAIL`/`ADMIN_PASSWORD` do `.env`. Não existe senha fixa em script, e a senha precisa atender à política (10–72 caracteres, com maiúscula, minúscula, número e caractere especial). Os demais usuários são criados pelo ADMIN em **Usuários**.

> Se alguma porta já estiver ocupada na sua máquina, mude `WEB_PORT`, `API_PORT`, `MYSQL_PORT` ou `MONGO_PORT` no `.env`.

### Desenvolvimento com hot reload

```bash
docker compose up -d mysql mongo api          # bancos + API
cd frontend && cp .env.example .env.local     # API_URL=http://localhost:8081
npm install && npm run dev
```

A API pode rodar pela IDE (`./mvnw spring-boot:run`) ou com bancos descartáveis (`./mvnw spring-boot:test-run`). Nesta máquina, porém, o Windows bloqueia o loopback NIO do Tomcat, então prefira a API no container (mesmo problema documentado no PetConnect-API).

## Testes

```bash
cd backend  && ./mvnw verify                                  # unit + integração (precisa do Docker)
cd frontend && npm run lint && npm run typecheck && npm test
```

## CI/CD

| Workflow | Quando | O que faz |
|---|---|---|
| `backend.yml` | PR/push que toca `backend/` | `mvn verify` com Testcontainers e publica relatórios e cobertura |
| `frontend.yml` | PR/push que toca `frontend/` | lint, typecheck, testes e build |
| `codeql.yml` | PR, push na `main`, toda segunda | análise de segurança Java e TypeScript |
| `delivery.yml` | push na `main` e tags `v*` | publica `petconnect-web-api` e `petconnect-web-web` no GHCR |

Veja [docs/github.md](docs/github.md) para configurar a organização, a proteção da `main` e os ambientes.

## Status da migração

| Módulo | Status |
|---|---|
| Fundação (estrutura, schema, segurança, CI/CD, Docker) | ✅ |
| Autenticação (login JWT, papéis, bloqueio por tentativas, sessão revogável por `token_version`) | ✅ |
| Usuários, funcionários, perfil, troca e recuperação de senha (e-mail via Mailpit/SMTP) | ✅ |
| Clientes + endereço | ✅ |
| Auditoria (MongoDB) | ✅ (login, clientes, pets, serviços, agendamentos, usuários, produtos, estoque, fornecedores, compras, vendas, caixa, financeiro e exportação de relatórios) |
| Pets, espécies/raças, carteira de vacinas, consultas, foto | ✅ |
| Serviços (catálogo + preço por porte) e agendamentos (agenda, conflitos, status, conclusão, vacinação) | ✅ |
| Produtos, fornecedores, compras e estoque (histórico imutável, custo médio, lock no saldo) | ✅ |
| PDV / vendas (leitor de código, idempotência, cancelamento com estorno, comprovante) | ✅ |
| Financeiro (caixa por operador, lançamentos automáticos e manuais com estorno, contas a pagar, fluxo) | ✅ |
| Relatórios gerenciais (vendas, ranking, curva ABC, estoque/giro, margem estimada, serviços, despesas, caixas, contas; CSV e impressão) | ✅ |
| Migração dos dados do banco legado (ferramenta offline: dry-run, idempotente, retomável, relatório sem dados pessoais — [runbook](docs/migracao-legado.md)) | ✅ |

| Produção e operação (HTTPS/Caddy, CSP com nonce, logs JSON mascarados, métricas internas, backup cifrado com teste de restore, deploy com rollback, E2E Playwright, Trivy/SBOM, LGPD: exportar/anonimizar cliente — [produção](docs/producao.md), [runbooks](docs/runbooks)) | ✅ |

As próximas etapas são especificadas em [`docs/next-stage/`](docs/next-stage).
