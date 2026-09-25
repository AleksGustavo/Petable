# 00 — Fundação (concluída)

Registro do que foi entregue na etapa que tirou o projeto do PHP/XAMPP e montou a base nova.

## Entregue

- **Mudança de local**: projeto copiado de `C:\xampp\htdocs\PI 2° Semestre 2025` para `C:\Users\Aleksander\Projetos\Mobile - Flutter\PetConnect-Web`, com o histórico git preservado e o remoto apontando para `AleksGustavo/PetConnect-Web`. O original no XAMPP não foi alterado.
- **Reorganização**: PHP em `legacy/php/`, documentação do PI em `docs/legacy/`.
- **Banco**: schema MySQL normalizado (`V1`), com tabela `endereco` separada e as demais correções listadas em [banco-de-dados.md](../banco-de-dados.md), e catálogos de referência (`V2`).
- **Backend** (Spring Boot 4.1.1, Java 21): segurança JWT stateless com papéis ADMIN/VENDAS/SERVICO, bloqueio por tentativas, bootstrap do primeiro ADMIN, erros RFC 9457, auditoria no MongoDB, módulo **clientes + endereço** completo, OpenAPI.
- **Frontend** (Next.js 16): login com cookie httpOnly (BFF), `proxy.ts`, layout responsivo (barra lateral ou gaveta), dashboard, clientes (lista com busca e paginação, cadastro, edição, inativação), autopreenchimento pelo CEP via ViaCEP, máscaras, modo escuro.
- **Testes**: backend com 19 unitários e 13 de integração (Testcontainers com MySQL e Mongo reais). Frontend com 11 (Vitest).
- **DevOps**: Dockerfiles multi-stage não-root, `compose.yaml` da stack completa, GitHub Actions (backend, frontend, CodeQL, entrega no GHCR), Dependabot, template de PR, CODEOWNERS.

## Verificado

- `./mvnw verify`: BUILD SUCCESS (32 testes).
- `npm run lint && npm run typecheck && npm test && npm run build`: OK.
- `docker compose up`: login → cadastro de cliente com CEP → busca por CPF parcial → auditoria gravada no Mongo, no desktop e no celular.

## Pendências conhecidas (fora desta etapa)

- Redefinição de senha por e-mail (a tabela `token_redefinicao_senha` já existe).
- CRUD de usuários/funcionários (hoje só existe o ADMIN do bootstrap).
- Job agregador de CI, para permitir checks obrigatórios com filtro de caminho.
- Content-Security-Policy com nonce no Next.
- Deploy (staging/produção) consumindo as imagens do GHCR.
- Migração dos dados do banco legado.

---

## Handoff para a etapa 01: prompt para o ChatGPT

Copie o bloco abaixo inteiro no ChatGPT. Ele devolve o arquivo `01-pets.md`, que deve ser salvo nesta pasta.

````text
Você é o arquiteto de software do projeto PetConnect Web. Gere UM arquivo Markdown chamado `01-pets.md`, que será executado por outro agente de IA (Claude Code) direto no repositório. Seja específico e verificável: nada de "a definir". Responda somente com o conteúdo do arquivo, dentro de um único bloco ```markdown.

## Contexto do projeto
- Sistema web de gestão de petshop, migrado de PHP+MySQL para: backend Spring Boot 4.1 (Java 21) + frontend Next.js 16 (App Router, Server Actions, TypeScript, Tailwind 4, Zod). Monorepo: `backend/`, `frontend/`, `legacy/php/` (sistema antigo, só referência).
- MySQL 8.4 (Flyway; já aplicadas V1 schema e V2 dados de referência) para dados de negócio. MongoDB 7 só para a auditoria (coleção `auditoria`, via `AuditoriaService.registrar(acao, entidade, id, detalhes)`).
- Backend em monólito modular: pacote `br.com.tecnodreamer.petconnect.<modulo>` com subpacotes `domain` (entidade JPA sem setters públicos, métodos de intenção), `application` (serviço @Transactional + auditoria), `infrastructure` (Spring Data), `web` (controller + DTO record com Bean Validation). Erros: `NotFoundException` → 404, `ConflictException` → 409, validação → 400 com `errors: {campo: msg}` (ProblemDetail RFC 9457). Paginação: `PageResponse<T>{content,page,size,totalElements,totalPages}`. Exclusão lógica (`ativo=false`). Rotas em `/api/v1/...`.
- Segurança: JWT stateless. Papéis ADMIN, VENDAS (clientes, pets, agendamentos, PDV) e SERVICO (vê agendamentos e registra serviços). Autorização com `@PreAuthorize`. Hoje, clientes: leitura para qualquer autenticado, escrita para ADMIN/VENDAS.
- Frontend (BFF): o JWT fica em cookie httpOnly; Server Components leem via `apiFetch<T>(path)` (server-only); Server Actions escrevem, validam com Zod espelhando a API e usam `useActionState`; o erro 400 da API é mapeado para os campos. Mobile-first: tabela em `md+`, cartões no celular. Componentes existentes: `AppShell` (menu com item "Pets" hoje marcado "em breve"), `Campo`, `Aviso`, utilitários `cartao`, `campo`, `botao-primario`/`botao-secundario`/`botao-perigo`. Rotas atuais: `/dashboard`, `/clientes`, `/clientes/novo`, `/clientes/[id]`.
- Testes: backend `*Test` (Mockito) e `*IT` (estende `IntegrationTest`: SpringBootTest + MockMvc + Testcontainers MySQL/Mongo; autenticação nos testes com `jwt().authorities(new SimpleGrantedAuthority("ROLE_VENDAS"))`). Frontend com Vitest (`*.test.ts[x]`, ambiente node; componentes com `// @vitest-environment jsdom`).
- Regra do banco: nunca editar migration aplicada; mudanças entram em `V3__...sql` em diante.

## Tabelas já existentes (V1), relevantes para esta etapa
- `especie(id, nome UNIQUE)`; `raca(id, especie_id FK, nome, UNIQUE(especie_id,nome))`. A V2 já traz 6 espécies e raças de cão e gato, incluindo "SRD (Sem Raça Definida)".
- `pet(id, cliente_id FK ON DELETE CASCADE, nome, especie_id FK, raca_id FK NULL, data_nascimento, peso DECIMAL(5,2), cor, porte VARCHAR CHECK IN ('PEQUENO','MEDIO','GRANDE') NULL, castrado BOOL, foto_url VARCHAR(255), observacoes, ativo, created_at, updated_at)`. A coluna `vacinado` foi removida de propósito: o status vem da carteira.
- `vacina(id, especie_id FK, nome, doenca_protecao, validade_padrao_meses DEFAULT 12, preco, observacoes, ativo, UNIQUE(especie_id,nome))`. A V2 já traz V8, V10, Gripe, Raiva e Giardia (cão) e V3, V4, V5 e Raiva (gato).
- `carteira_vacina(id, pet_id FK CASCADE, vacina_id FK NOT NULL, data_aplicacao, data_proxima NULL, veterinario, lote, observacoes, created_at)`.
- `historico_consulta(id, pet_id FK CASCADE, data_consulta DATETIME, veterinario NOT NULL, motivo, diagnostico, tratamento, observacoes, created_at)`.

## Comportamento no legado (PHP) a preservar ou melhorar
- `pets_cadastro.php`, `pets_editar.php`, `pets_processar.php`: cadastro com upload de foto para uma pasta local (`uploads/fotos_pets/`), raças carregadas por espécie (`fetch_racas.php`, AJAX), exclusão física do pet.
- `pets_detalhes.php`: ficha do pet com dados do tutor.
- `pets_carteira_vacinas.php`, `pets_processar_vacina_manual.php`: carteira com status por dose: **vencida** (data_proxima < hoje), **vence em até 30 dias**, **em dia**. Lançamento manual de vacina com data da próxima dose opcional. No legado a vacina era texto livre; agora é escolhida do catálogo da espécie do pet.
- `clientes_detalhes.php`: lista os pets do cliente.

## O que a especificação `01-pets.md` deve cobrir
1. Objetivo e escopo (entra / não entra). Entram: pets (CRUD com exclusão lógica), espécies/raças (endpoints de leitura para os selects), carteira de vacinas (lançar, listar com status, remover lançamento), histórico de consultas (lançar e listar), foto do pet, pets na tela do cliente, e liberar o item "Pets" do menu. Decida e justifique onde a foto é armazenada (ex.: volume local servido pela API com validação de tipo e tamanho, ou um provedor externo); a solução deve funcionar no docker compose sem credenciais externas.
2. Banco: a migration `V3__...sql` que for necessária (índices, ajustes), ou justificar que não precisa.
3. API: cada endpoint com método, rota, papel exigido, request/response (JSON de exemplo), validações e códigos de erro. Inclua o cálculo de `idade` e do status de vacinação, e a regra de que a vacina lançada precisa ser da mesma espécie do pet (409 ou 400: escolha e justifique). Paginação e busca na lista de pets (por nome do pet ou do tutor).
4. Auditoria: quais ações vão para o Mongo.
5. Frontend: rotas (ex.: `/pets`, `/pets/novo`, `/pets/[id]` com abas ou seções Dados / Vacinas / Consultas; `/clientes/[id]` passa a listar os pets e ter "Adicionar pet"), comportamento no celular, estados vazio/carregando/erro, selects dependentes espécie → raça, badges de status de vacina com cores acessíveis (não depender só da cor).
6. Regras de negócio numeradas.
7. Testes: lista nominal de casos unitários, de integração (*IT) e de frontend.
8. Critérios de aceite em checklist.
9. Riscos e o que fica fora de escopo, para as próximas etapas: agendamentos, PDV, estoque e migração de dados.
````
