TAREFA: gere um ARQUIVO MARKDOWN NOVO chamado `09-producao-operacao.md`.
NÃO repita, NÃO resuma e NÃO devolva este prompt. Sua resposta deve começar exatamente com a linha
`# Etapa 09 — Produção e operação (endurecimento para go-live)` e conter SOMENTE o conteúdo do arquivo,
de preferência como anexo .md para download (se não puder anexar, entregue em um único bloco ```markdown).

======================================================================
CONTEXTO DO PROJETO (você não vê o repositório; tudo o que precisa está aqui)
======================================================================

PetConnect Web: sistema de gestão de petshop (monorepo GitHub `AleksGustavo/PetConnect-Web`), migrado de um PI em PHP.
Etapas 00–08 concluídas: fundação/clientes, pets/vacinas, agendamentos/serviços, usuários/funcionários/senha,
produtos/estoque/compras, PDV/vendas, financeiro/caixa/contas a pagar, relatórios gerenciais (CSV/impressão) e
migração offline do banco legado (dry-run, idempotente, retomável).

Stack atual:
- backend `backend/`: Spring Boot 4.1, Java 21, monólito modular (`br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`);
  MySQL 8.4 com Flyway V1–V10 (NUNCA editar migrations aplicadas), `ddl-auto=validate`; MongoDB 7 só para auditoria;
  JWT HS256 (`JWT_SECRET` ≥ 32 bytes, TTL 8h, claim `tv` = token_version conferida no banco a cada requisição, revogação imediata);
  BCrypt 12; bloqueio de login por tentativas; recuperação de senha por e-mail (token SHA-256, 30 min, uso único) com limitador
  EM MEMÓRIA (3/e-mail e 10/IP por 30 min, IP via X-Forwarded-For repassado pelo Next);
  Actuator só `health` e `info` expostos (probes liveness/readiness habilitadas, health de mail desligado);
  perfil `prod` (`application-prod.yml`) sem defaults de segredo e com Swagger/api-docs desligados;
  erros RFC 9457 (ProblemDetail); `Clock` America/Sao_Paulo; dinheiro BigDecimal.
- frontend `frontend/`: Next.js 16 (App Router, `output: standalone`) como BFF: o JWT fica em cookie httpOnly
  (`COOKIE_SECURE` padrão true; false só no compose local), Server Actions, route handlers; React 19, Tailwind 4, Zod, Vitest.
  Cabeçalhos atuais: X-Frame-Options DENY, X-Content-Type-Options nosniff, Referrer-Policy strict-origin-when-cross-origin,
  Permissions-Policy (camera/microphone/geolocation vazios), `poweredByHeader: false`. NÃO há CSP nem HSTS ainda.
- dados persistentes: volume MySQL, volume MongoDB, volume `petconnect_uploads` (fotos dos pets em disco local, interface
  `FotoPetStorage` permite trocar por storage externo).
- Docker: `compose.yaml` de DESENVOLVIMENTO (mysql, mongo, mailpit, api, web; perfil `migracao` com mysql-legado + api-migracao).
  Imagens: API multi-stage (JRE 21, usuário não-root, HEALTHCHECK readiness), Web standalone.
- CI/CD (GitHub Actions): `backend.yml` (`mvn verify` com Testcontainers + JaCoCo), `frontend.yml` (lint, typecheck, vitest, build),
  `codeql.yml`, `delivery.yml` (publica imagens da API e do Web no GHCR a cada push na main e em tags v*),
  `dependabot.yml` (maven, npm, actions, docker), `CODEOWNERS`, template de PR. NÃO há deploy, NÃO há E2E (Playwright),
  NÃO há smoke test pós-deploy, NÃO há backup automatizado, NÃO há métricas/alertas/logs estruturados.
- Testes atuais: ~325 unitários + ~259 integração (backend), ~240 frontend. Nunca desabilitar testes/lint.
- Papéis: ADMIN, VENDAS, SERVICO. Auditoria no Mongo (append-only) de todas as ações de negócio.
- A migração do legado é ferramenta OFFLINE (`petconnect.migracao.legado=false` por padrão) e deve continuar desligada em produção.
- Existe script com dados pessoais reais versionado em `docs/legacy/` (pendência LGPD registrada; limpeza do histórico é decisão do dono).

Restrições permanentes: NÃO editar migrations V1–V10; NÃO alterar `legacy/` nem `docs/legacy/`; NÃO hard-delete;
NÃO double/float para dinheiro; NÃO desabilitar testes/lint; NÃO usar `any` no front; NÃO commit/push automático;
NUNCA versionar ou logar segredos/dados pessoais; NÃO adicionar novo domínio de negócio nesta etapa.

======================================================================
O QUE A ETAPA 09 DEVE DECIDIR E COBRIR (detalhe como DA-xx, com escolhas concretas)
======================================================================
Escolha UMA estratégia de deploy simples e barata, adequada a um petshop (ex.: 1 VPS Linux com Docker Compose de produção
+ Caddy/Traefik para HTTPS automático via Let's Encrypt), e deixe alternativas como "fora de escopo". Cubra:

1. `compose.prod.yaml` separado do de desenvolvimento: sem Mailpit, sem portas de banco expostas, imagens do GHCR por tag,
   `restart: unless-stopped`, limites de memória, rede interna, healthchecks, perfil `prod`, `COOKIE_SECURE=true`.
2. Proxy reverso com HTTPS obrigatório, redirecionamento HTTP→HTTPS, HSTS, compressão, limite de tamanho de corpo coerente com a foto (6 MB).
3. CSP no Next (com nonce ou política compatível com o App Router), HSTS, e revisão de CORS/cookies (SameSite, Secure, domínio) para o domínio real.
4. Segredos fora do repositório: `.env.prod` só no servidor (ou Docker secrets), rotação de `JWT_SECRET` (efeito: derruba sessões), checklist de segredos.
5. Estratégia de migrations no deploy (Flyway na subida da API, backup ANTES, proibição de migration destrutiva sem plano) e rollback de aplicação (voltar tag de imagem; banco só por restore).
6. Backup automatizado: MySQL (mysqldump diário com retenção), MongoDB (mongodump), volume de fotos; cópia fora do servidor; criptografia; TESTE PERIÓDICO DE RESTORE automatizado (script que restaura em container descartável e confere contagens).
7. Observabilidade: logs estruturados JSON (sem dados pessoais — definir mascaramento), correlação por request id (Next → API), métricas via Actuator/Micrometer (Prometheus) SÓ em porta/rota interna, readiness/liveness, alertas mínimos (API fora, disco, backup falhou, erro 5xx).
8. Rate limiting distribuído ou na borda (login, recuperação de senha, API geral) — decidir entre proxy e Bucket4j/Redis e justificar; o limitador em memória atual não escala para mais de uma instância.
9. E2E Playwright dos fluxos críticos no CI (login, cadastro cliente+pet, agendamento→conclusão, venda no PDV com caixa aberto, fechamento de caixa, relatório de vendas), rodando contra o compose de CI.
10. Smoke test pós-deploy (health, login de um usuário técnico de smoke, página inicial) e workflow de deploy (manual com aprovação/`environment` do GitHub, por tag).
11. Segurança da cadeia: SAST (CodeQL já existe), scan de dependências e de imagens (ex.: Trivy/Grype) com falha em CVE crítica, SBOM, fixar versões de actions por SHA, proteção de branch main (PR obrigatório, checks obrigatórios, revisão).
12. Política de retenção: logs, auditoria no Mongo (índice TTL? decidir e justificar considerando obrigação legal), backups.
13. LGPD: inventário de dados pessoais por tabela, base legal, direitos do titular (exportar/anonimizar cliente sem hard-delete — definir a regra), procedimento de incidente, aviso de privacidade; e a recomendação sobre o script legado com dados reais.
14. Runbooks: operação diária, deploy, rollback, restore, incidente, rotação de segredo, go-live (incluindo a migração do legado já documentada em `docs/migracao-legado.md`).
15. Teste de recuperação de desastre (servidor perdido → reconstruir a partir dos backups) com tempo-alvo (RTO/RPO definidos).
16. Checklist de go-live e documentação final de arquitetura/operação.

======================================================================
FORMATO OBRIGATÓRIO DO ARQUIVO (seções numeradas, nesta ordem)
======================================================================
1. Objetivo
2. Análise obrigatória (o que o executor deve ler/verificar antes de codificar)
3. Decisões arquiteturais (tabela DA-01, DA-02, ... com justificativa)
4. Escopo
5. Fora de escopo
6. Arquitetura de produção (diagrama em texto: internet → proxy → web/api → mysql/mongo/volumes; redes; portas)
7. `compose.prod.yaml` (conteúdo COMPLETO sugerido)
8. Proxy reverso e HTTPS (configuração COMPLETA sugerida)
9. Cabeçalhos de segurança, CSP, cookies e CORS (valores exatos)
10. Segredos e configuração por ambiente (tabela de variáveis: nome, obrigatório, exemplo sem valor real, onde vive)
11. Banco: migrations no deploy, backup, restore, retenção (scripts COMPLETOS sugeridos)
12. Observabilidade (logs JSON com mascaramento, request id, métricas, health, alertas)
13. Rate limiting
14. CI/CD: workflows novos/alterados (YAML COMPLETO sugerido): e2e, scan, deploy com aprovação, smoke test
15. Testes E2E Playwright (arquivos, cenários, dados de teste, como rodar local e no CI)
16. LGPD e retenção (inventário, regras de anonimização/exportação sem hard-delete, incidente)
17. Regras numeradas (R-01, R-02, ...)
18. Testes unitários novos (nome da classe e cenários) — ex.: mascaramento de log, filtro de request id, rate limit
19. Testes de integração novos (nome da classe e cenários)
20. Testes frontend novos (arquivo e cenários) — ex.: CSP/nonce, cabeçalhos
21. Documentação a criar/atualizar (docs/producao.md, docs/runbooks/*.md, README, arquitetura, next-stage/README)
22. Critérios de aceite (checklist verificável)
23. Riscos
24. Ordem de implementação
25. Restrições (copie as permanentes acima)
26. Relatório final `09-producao-operacao-relatorio.md` no formato: "unitários: X/Y, integração: X/Y, frontend: X/Y, e2e: X/Y",
    PASS/FAIL de backend build, frontend build, lint, typecheck, Docker (dev e prod local), E2E, scan; divergências; pendências;
    e handoff da etapa 10 (sugestão: melhorias pós-go-live / app mobile Flutter consumindo a API)
27. Definição de pronto

Seja específico e verificável (nomes de arquivos, chaves de configuração, comandos, YAML/config completos). Quando algo depender
do provedor escolhido pelo dono (domínio, VPS), use placeholders claros como `<DOMINIO>` e `<IP_DO_SERVIDOR>` e diga como validar localmente
(ex.: compose de produção rodando em localhost com certificado local).

LEMBRETE FINAL: devolva APENAS o arquivo `09-producao-operacao.md` pronto, começando pelo título da etapa. Não repita este prompt.
