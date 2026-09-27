TAREFA: gere um ARQUIVO MARKDOWN NOVO chamado `10-app-mobile.md`.
NÃO repita, NÃO resuma e NÃO devolva este prompt. Sua resposta deve começar exatamente com a linha
`# Etapa 10 — App mobile (Flutter) consumindo a API do PetConnect Web` e conter SOMENTE o conteúdo do arquivo,
de preferência como anexo .md para download (se não puder anexar, entregue em um único bloco ```markdown).

======================================================================
CONTEXTO (você não vê o repositório; tudo o que precisa está aqui)
======================================================================

PetConnect Web (monorepo `AleksGustavo/PetConnect-Web`): gestão de petshop. Etapas 00–09 concluídas (cadastros, pets/vacinas,
agenda/serviços, usuários, estoque/compras, PDV, financeiro/caixa, relatórios, migração do legado, produção/operação).

Backend `backend/`: Spring Boot 4.1 / Java 21, monólito modular, MySQL 8.4 (Flyway V1–V10, nunca editar), MongoDB (auditoria).
- Autenticação: `POST /api/v1/auth/login` {login, senha} → {accessToken (JWT HS256, 8 h), tokenType, expiresIn, usuario{id,username,email,papel}};
  claim `tv` (token_version) conferida no banco a cada requisição → revogação imediata (troca de senha/papel/desativação). NÃO há refresh token.
  `GET /api/v1/auth/me`. Recuperação de senha por e-mail. Limite: 20 falhas de login por IP em 5 min (429 + Retry-After) + bloqueio por conta.
- Papéis: ADMIN, VENDAS, SERVICO (SERVICO consulta agenda e registra serviços; não vê vendas/financeiro).
- Erros RFC 9457 (ProblemDetail: title, detail, status, errors{campo: msg}); paginação `PageResponse` {content, page, size, totalElements, totalPages}.
- Recursos existentes (todos sob `/api/v1`): clientes, pets (+ foto multipart, + carteira de vacinas, + consultas), especies/racas/vacinas (catálogo),
  servicos, agendamentos (agenda por dia/semana, conflitos, transições AGENDADO→CONFIRMADO→EM_ANDAMENTO→CONCLUIDO/CANCELADO/NAO_COMPARECEU;
  concluir exige caixa aberto do usuário e forma de pagamento), caixas (abrir, atual, fechar), produtos (busca rápida por código de barras),
  vendas (PDV com Idempotency-Key obrigatória), financeiro, relatórios, usuários, funcionários, perfil/troca de senha.
- Cabeçalho `X-Request-Id` aceito/ecoado (correlação de logs).

Frontend `frontend/`: Next.js 16 como BFF (o navegador NUNCA vê o JWT; cookie httpOnly). Por isso, HOJE A API NÃO É PÚBLICA:
em produção (`compose.prod.yaml`) só o Caddy publica 80/443 e roteia apenas para o Next; API, MySQL e Mongo ficam em redes internas.
CORS da API = origem do próprio site.

Ecossistema: existe um projeto Flutter separado chamado `PetConnect` (org GitHub TecnoDreamer) e um `PetConnect-API` antigo
(Spring + Mongo + Firebase). A etapa 10 deve decidir explicitamente se reaproveita o app Flutter existente (apontando para a API nova)
ou cria um app novo, e o que fazer com o PetConnect-API antigo (descontinuar/mesclar).

Restrições permanentes: NÃO editar migrations V1–V10; NÃO alterar `legacy/`/`docs/legacy/`; NÃO hard-delete; NÃO double/float para
dinheiro (BigDecimal na API; no app, centavos inteiros ou decimal); NÃO desabilitar testes/lint; NÃO commit/push automático;
NUNCA versionar/logar segredos ou dados pessoais; a web e seus testes (E2E Playwright) devem continuar verdes.

======================================================================
O QUE A ETAPA 10 DEVE DECIDIR E COBRIR (DA-xx concretas)
======================================================================
1. Público-alvo do app no MVP: recomendar **app da equipe** (funcionários: agenda do dia, check-in/andamento/conclusão de atendimento,
   ficha do pet com carteira de vacinas, foto do pet pela câmera, busca de cliente/pet, leitor de código de barras para consulta de produto)
   e deixar app do tutor/cliente final como fora de escopo (não existem contas de tutor). Justificar.
2. Exposição da API: rota pública `https://api.<DOMINIO>` (ou `https://<DOMINIO>/api`) no Caddy, com os mesmos cabeçalhos de segurança;
   o que muda em CORS (nada para app nativo), rate limit (por IP e por usuário), tamanho de corpo, e como manter a web sem mudança.
3. Autenticação mobile: armazenamento seguro do token (flutter_secure_storage/Keychain/Keystore), expiração de 8 h sem refresh —
   decidir se adiciona refresh token rotativo (nova migration V11: tabela de refresh tokens com hash, dispositivo, revogação) ou
   mantém re-login; logout remoto; biometria opcional para desbloquear o app; bloqueio de captura de tela em telas com dados pessoais.
4. Versionamento/contrato da API: congelar `/api/v1`, publicar OpenAPI gerado (springdoc) como artefato do CI, gerar cliente Dart
   (openapi-generator) ou escrever cliente manual — decidir e justificar; testes de contrato.
5. Offline: definir o mínimo (cache de leitura da agenda do dia; fila de ações de status com idempotência) ou declarar online-only.
6. Arquitetura Flutter: gerenciamento de estado (Riverpod/Bloc — escolher), navegação (go_router), camadas, tratamento de ProblemDetail,
   internacionalização pt-BR, acessibilidade, tema.
7. Qualidade: testes unitários, de widget e de integração (integration_test) contra a stack do compose de dev; lint (flutter_lints/very_good_analysis).
8. CI/CD mobile: workflow `mobile.yml` (analyze, test, build APK/AAB), assinatura (keystore em secrets), distribuição interna
   (Firebase App Distribution ou Google Play Internal Testing), versão por tag.
9. Segurança e LGPD no dispositivo: nada de dado pessoal em logs/analytics, certificado pinning (decidir), jailbreak/root (decidir),
   limpeza de cache ao sair, política de privacidade do app.
10. Mudanças necessárias no backend (listar endpoints novos/alterados, se houver, com testes) e no `compose.prod.yaml`/Caddyfile.

======================================================================
FORMATO OBRIGATÓRIO (seções numeradas, nesta ordem)
======================================================================
1. Objetivo · 2. Análise obrigatória · 3. Decisões arquiteturais (tabela DA-xx) · 4. Escopo · 5. Fora de escopo ·
6. Arquitetura (diagrama em texto: app → Caddy → API) · 7. Mudanças no backend (endpoints, V11 se houver, com SQL completo) ·
8. Mudanças no Caddy/compose.prod (config completa) · 9. Autenticação e sessão no app · 10. Contrato da API/OpenAPI ·
11. Telas do app (lista, campos, estados de carregamento/erro/vazio, permissões por papel) · 12. Estrutura do projeto Flutter ·
13. Offline e idempotência · 14. Segurança e LGPD no dispositivo · 15. Regras numeradas (R-xx) · 16. Testes backend novos ·
17. Testes do app (unitários, widget, integração — nomes e cenários) · 18. CI/CD mobile (YAML completo sugerido) ·
19. Documentação a criar/atualizar · 20. Critérios de aceite · 21. Riscos · 22. Ordem de implementação · 23. Restrições ·
24. Relatório final `10-app-mobile-relatorio.md` ("unitários: X/Y, integração: X/Y, frontend: X/Y, e2e: X/Y, mobile: X/Y";
PASS/FAIL de cada build/lint/teste; divergências; pendências; handoff da etapa 11) · 25. Definição de pronto.

Seja específico e verificável (pacotes com versão, nomes de arquivos, endpoints, YAML/config completos). Use placeholders
`<DOMINIO>` para o que depende do dono.

LEMBRETE FINAL: devolva APENAS o arquivo `10-app-mobile.md` pronto, começando pelo título da etapa. Não repita este prompt.
