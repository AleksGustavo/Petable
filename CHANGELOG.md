# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). O projeto segue
[versionamento semântico](https://semver.org/lang/pt-BR/): tags `vX.Y.Z` geram as imagens do GHCR.

## [Não lançado]

Veja o quadro **PetConnect Web — Roadmap** (aba Projects do repositório) e as issues abertas.

## [0.1.0] — 2026-10

Primeira versão do **PetConnect Web**, reescrita do Pet & Pet (PHP) em Spring Boot + Next.js.
Cada etapa tem spec e relatório em [`docs/next-stage/`](docs/next-stage).

### Adicionado

- **Fundação (00):** monorepo `backend/` + `frontend/`, MySQL com Flyway, MongoDB de auditoria, JWT, Docker, CI/CD, clientes e endereços.
- **Pets (01):** espécies e raças, foto validada, carteira de vacinação e histórico de consultas.
- **Agendamentos (02):** serviços com preço por porte, agenda dia/semana, conflito de horário e máquina de estados.
- **Usuários (03):** papéis ADMIN/VENDAS/SERVICO, funcionários, perfil, troca e recuperação de senha por e-mail.
- **Estoque (04):** produtos, estoque com histórico imutável e custo médio, fornecedores e compras.
- **PDV (05):** vendas com leitor de código de barras, idempotência, cancelamento com estorno e comprovante.
- **Financeiro (06):** caixa por operador, lançamentos automáticos e manuais, contas a pagar e fluxo.
- **Relatórios (07):** nove relatórios gerenciais com exportação CSV e impressão.
- **Migração do legado (08):** ferramenta offline com simulação (dry-run), idempotente e retomável.
- **Produção (09):** Docker Compose de produção com Caddy (HTTPS), CSP com nonce, logs JSON mascarados,
  backup cifrado com teste de restore, deploy com rollback, E2E (Playwright), Trivy/SBOM e recursos de LGPD
  (exportar e anonimizar cliente).
- **Governança:** licença, política de segurança, guia de contribuição, dicionário de dados e diagrama ER completo.

### Segurança

- Tomcat fixado em 11.0.26 (CVE-2026-65182, -65905 e -68525, críticas, presentes no 11.0.24).

[Não lançado]: https://github.com/AleksGustavo/PetConnect-Web/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/AleksGustavo/PetConnect-Web/releases/tag/v0.1.0
