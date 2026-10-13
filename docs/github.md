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

## Proteção da `main`

**Hoje NÃO há proteção ativa na `main`.** O GitHub só oferece *branch protection* e *rulesets* em repositório **privado** nos planos pagos (Pro/Team): a API responde `403 Upgrade to GitHub Pro or make this repository public`. O repositório é privado de propósito (o histórico tem dados pessoais do sistema antigo, ver a issue #2), então a proteção fica pendente da issue #3.

Enquanto isso, vale a **regra de disciplina**: sempre abrir PR, esperar o CI ficar verde e nunca fazer push direto na `main`. O CI roda em todo PR e na `main`, mas não impede o merge.

Quando houver plano pago (ou o repositório puder ficar público), aplique a configuração abaixo. Com um único mantenedor, **não exija aprovação**: ele não consegue aprovar o próprio PR.

- PR obrigatório, sem force-push e sem exclusão da branch.
- Checks obrigatórios: `Build + testes (unit + integração)`, `Lint + tipos + testes + build`, `Playwright (Chromium)`, `Análise (java-kotlin)` e `Análise (javascript-typescript)`.
- Branch atualizada antes do merge e conversas resolvidas.
- Para o time, passar para 1 aprovação com *Code Owners* (`.github/CODEOWNERS`).

Recomendação completa (para o time):

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

## Planejamento (Kanban)

- Quadro **PetConnect Web — Roadmap** (aba *Projects* do repositório): colunas *Todo → In Progress → Done* (padrão do GitHub Projects).
- Todo trabalho novo nasce como **issue** (modelos em `.github/ISSUE_TEMPLATE`), com etiquetas de prioridade (`prioridade:alta|media|baixa`) e de área (`arquitetura`, `modularizacao`, `lgpd`, `infra`, `mobile`, `docs`, `testes`, `estoque`).
- Etapas concluídas ficam registradas em `docs/next-stage/` e no [CHANGELOG](../CHANGELOG.md).

## CodeQL em repositório privado

Sem o plano pago GitHub Code Security, o GitHub não aceita o envio dos resultados do CodeQL para a aba *Security* em repositório privado. O workflow `codeql.yml` roda a análise com `upload: never`, guarda o SARIF como artefato do job e reprova o CI se houver achado de nível `error`. Com Code Security ativo, volte para `upload: always` (instruções no cabeçalho do workflow).

## Visibilidade

O repositório é **privado**: o histórico do git guarda um script do sistema antigo com dados pessoais reais (`Documentação/Banco de Dados/Scripts do Banco/`). A limpeza do histórico (`git filter-repo` + force-push) está registrada como issue e depende de avisar os colaboradores, porque muda os hashes dos commits.

## Segurança do repositório

- Ative em **Settings → Code security**: Dependabot alerts, Dependabot security updates, Secret scanning e Push protection.
- O Dependabot (`.github/dependabot.yml`) abre PRs semanais para Maven e npm, e mensais para Actions e imagens Docker.
