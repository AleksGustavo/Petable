# GitHub: organização, proteção e entrega

## Organização

A organização **TecnoDreamer** ("sistema de gestão web, aplicativo mobile e site institucional sobre pets") reúne os projetos do ecossistema:

| Repositório | Conteúdo | Onde está hoje |
|---|---|---|
| `PetConnect` | App mobile Flutter | TecnoDreamer |
| `PetConnect-API` | API do app (Spring + MongoDB + Firebase) | AleksGustavo (pessoal) |
| `PetConnect-Web` | **Este repositório**: gestão do petshop | AleksGustavo (pessoal) |
| `tecnodreamer-web` | Site institucional (Next.js) | TecnoDreamer |

### Transferir este repositório para a organização

Faça pela interface (ação do dono da conta):

1. GitHub → `AleksGustavo/PetConnect-Web` → **Settings → General → Danger Zone → Transfer ownership** → `TecnoDreamer`.
2. O GitHub redireciona a URL antiga, mas atualize o remoto local:
   ```bash
   git remote set-url origin https://github.com/TecnoDreamer/PetConnect-Web.git
   ```
3. As imagens do GHCR passam a ser publicadas em `ghcr.io/tecnodreamer/petconnect-web-*`, porque o workflow usa `github.repository_owner`.

Recomendado fazer o mesmo com o `PetConnect-API`, para o ecossistema ficar todo na organização.

### Times sugeridos

| Time | Permissão | Uso |
|---|---|---|
| `mantenedores` | Admin | configurações, releases |
| `devs` | Write | PRs e revisões |

## Proteção da `main` (Settings → Rules → Rulesets)

Crie um ruleset para a branch padrão com:

- **Require a pull request before merging**: 1 aprovação e *Require review from Code Owners* (usa `.github/CODEOWNERS`).
- **Require status checks to pass**:
  - `Build + testes (unit + integração)` (Backend)
  - `Lint + tipos + testes + build` (Frontend)
  - `Análise (java-kotlin)` e `Análise (javascript-typescript)` (CodeQL)
- **Block force pushes** e **Restrict deletions**.
- **Require linear history** (opcional, combina com *Squash and merge*).

> Os workflows de backend e frontend só rodam quando a pasta correspondente muda. Se marcar os dois como obrigatórios, um PR que só mexe no front fica esperando o check do backend. Nesse caso, marque como obrigatório só o CodeQL, ou crie um job agregador numa próxima etapa.

## Fluxo de branches

- `main`: sempre implantável. Cada push publica imagens no GHCR.
- `feat/<modulo>-<resumo>`, `fix/...`, `chore/...`, `docs/...`: uma etapa de `docs/next-stage/` por branch/PR.
- Commits no padrão Conventional Commits (`feat(clientes): ...`), como no PetConnect-API.
- Release: tag `vX.Y.Z` na `main`, que gera imagens com a versão semântica.

## Segredos e ambientes (Settings → Environments)

Os workflows atuais **não precisam de segredo nenhum** (usam `GITHUB_TOKEN` para o GHCR). Quando entrar o deploy:

| Ambiente | Segredos |
|---|---|
| `staging` | `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MONGODB_URI`, `JWT_SECRET`, credenciais do destino de deploy |
| `production` | os mesmos, com *required reviewers* no ambiente |

## Segurança do repositório

- Ative em **Settings → Code security**: Dependabot alerts, Dependabot security updates, Secret scanning e Push protection.
- O Dependabot (`.github/dependabot.yml`) abre PRs semanais para Maven e npm, e mensais para Actions e imagens Docker.
