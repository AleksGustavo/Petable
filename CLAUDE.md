# PetConnect Web — instruções para o Claude Code

Gestão de petshop migrada de PHP para Spring Boot 4.1 (`backend/`) + Next.js 16 (`frontend/`). Leia `docs/arquitetura.md` e `docs/banco-de-dados.md` antes de mudanças estruturais.

## Fluxo de trabalho
- As etapas chegam como especificações `.md` escritas pelo ChatGPT em `docs/next-stage/NN-*.md`. Execute a etapa, atualize o histórico em `docs/next-stage/README.md` e **termine entregando o prompt de handoff da próxima etapa** (modelo: fim de `00-fundacao.md`).
- Uma etapa = uma branch `feat/<modulo>-...` = um PR. Commits no padrão Conventional Commits, em português.
- `legacy/php/` é só referência: nunca edite.

## Convenções
- Backend: `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`. DTOs são `record` com Bean Validation, entidades sem setters públicos, exclusão lógica, auditoria via `AuditoriaService`.
- Banco: nunca editar migration aplicada. Crie `V<n+1>__descricao.sql`. `ddl-auto=validate`.
- Frontend: leia `frontend/AGENTS.md` (Next 16 tem APIs novas; docs em `frontend/node_modules/next/dist/docs/`). Leitura em Server Components via `apiFetch`, escrita em Server Actions + Zod. Mobile-first.

## Verificação antes de concluir
```bash
cd backend && ./mvnw verify                 # precisa do Docker (Testcontainers)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
docker compose up -d --build                # teste manual; portas no .env
```
- Nesta máquina a API não sobe direto no Windows (bug de loopback NIO do Tomcat). Rode a API no container.
- As portas 3000 e 8080 costumam estar ocupadas. Use `WEB_PORT`/`API_PORT` no `.env`.
