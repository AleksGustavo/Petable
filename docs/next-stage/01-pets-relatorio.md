# 01 — Pets: relatório de execução

Especificação: [01-pets.md](01-pets.md). Branch `feat/pets` (sem commit, conforme a seção 31 da spec).

## Implementação concluída

### Banco
- `V3__indices_modulo_pets.sql` com 3 índices: `idx_pet_cliente_ativo`, `idx_carteira_vacina_pet_data` e `idx_historico_consulta_pet_data`.
- Não foram criados os outros 3 índices sugeridos:
  - `raca (especie_id, nome)` e `vacina (especie_id, …)` já são atendidos pelos `UNIQUE (especie_id, nome)` da V1;
  - `pet (nome, ativo)` não ajuda, porque a busca é `LIKE '%termo%'`, e `idx_pet_nome` já existe.
- V1 e V2 intactas. O Flyway foi validado nos dois cenários: banco limpo (Testcontainers) e upgrade V2 → V3 (banco do compose).

### Backend
- Módulo `pet/` (domain, application, infrastructure, web), no padrão do módulo de clientes.
- Novo `shared/error/CampoInvalidoException`: regra de negócio violada num campo vira 400 com `errors`.
- `ClockConfig` passou a usar o fuso `America/Sao_Paulo`.
- `GlobalExceptionHandler` agora trata upload acima do limite com 400.
- Endpoints:

| Método | Rota | Papéis |
|---|---|---|
| GET | `/api/v1/pets?q&clienteId&page&size` | autenticado |
| GET | `/api/v1/pets/{id}` | autenticado |
| POST / PUT / DELETE | `/api/v1/pets[/{id}]` | ADMIN, VENDAS |
| PUT | `/api/v1/pets/{id}/foto` (multipart `foto`) | ADMIN, VENDAS |
| GET | `/api/v1/pets/{id}/foto` | autenticado |
| GET | `/api/v1/especies`, `/{id}/racas`, `/{id}/vacinas` | autenticado |
| GET | `/api/v1/pets/{petId}/vacinas` | autenticado |
| POST / DELETE | `/api/v1/pets/{petId}/vacinas[/{lancamentoId}]` | ADMIN, VENDAS |
| GET | `/api/v1/pets/{petId}/consultas` | autenticado |
| POST | `/api/v1/pets/{petId}/consultas` | ADMIN, VENDAS, SERVICO |

### Frontend
- Rotas:
  - `/pets`
  - `/pets/novo?clienteId=` (o tutor é validado no servidor antes de ser pré-selecionado)
  - `/pets/[id]?aba=dados|vacinas|consultas`, com `loading` e `not-found` ("Pet não encontrado.")
  - `/clientes/[id]`, que agora lista os pets e tem "Adicionar pet"
- Route handlers: `/api/pets/[id]/foto` (repassa a foto com o token do cookie) e `/api/clientes` (busca de tutor).
- Componentes: `PetForm` (select espécie → raça dependente, foto), `TutorPicker`, `VacinaForm` (catálogo da espécie, com sugestão da próxima dose pela validade), `ConsultaForm`, `StatusVacinaBadge`, `PetList`, `ClientePets`, `FotoPet`, `BotaoConfirmar`.
- Menu "Pets" liberado.
- `serverActions.bodySizeLimit = 6mb`.

### Segurança
- Autorização por `@PreAuthorize` conforme a tabela da seção 14. O front só esconde botões.
- Foto:
  - tipo detectado por *magic bytes*: JPEG, PNG e WebP (SVG e GIF recusados);
  - tamanho máximo de 5 MB;
  - nome interno `UUID.ext`;
  - chave validada por regex e `normalize()` contra path traversal;
  - o caminho físico nunca é exposto;
  - o arquivo antigo é apagado só depois do commit.
- O JWT continua no cookie httpOnly: nenhum Client Component o recebe.
- Os DTOs não aceitam `ativo`, `fotoUrl` nem timestamps.

### Auditoria
`PET_CRIADO`, `PET_ATUALIZADO`, `PET_DESATIVADO`, `PET_FOTO_ATUALIZADA`, `VACINA_PET_REGISTRADA`, `VACINA_PET_REMOVIDA`, `CONSULTA_PET_REGISTRADA` (`entidade = "pet"`). Leituras não são auditadas. Os bytes da imagem e o token não entram no registro.

### Testes
- Unitários backend: **55 aprovados / 0 falharam**. Antes eram 19; entraram 36, incluindo todos os casos nominais da seção 22.
- Integração backend: **34 aprovados / 0 falharam**. Antes eram 13; entraram 21 em 5 ITs.
- Frontend: **36 aprovados / 0 falharam**. Antes eram 11; entraram 25 em 7 arquivos da seção 24.

### Validações finais
- Backend build (`./mvnw verify`): PASS
- Frontend build (`npm run build`): PASS
- Lint: PASS
- Typecheck: PASS
- Ponta a ponta no `docker compose`, no navegador:
  - cliente → Adicionar pet (tutor pré-selecionado) → espécie/raça → foto → ficha;
  - vacina "Em dia" e "Vencida";
  - remoção de vacina: cancelar mantém, confirmar remove;
  - consulta, incluindo o erro de campo;
  - busca por tutor;
  - pet → tutor → pet;
  - celular sem rolagem horizontal;
  - foto mantida depois de `docker compose restart api`;
  - auditoria conferida no Mongo.

## Divergências em relação à spec (decisões tomadas)
1. **Veterinário da consulta: 2–100 caracteres, não 2–150.** A coluna `historico_consulta.veterinario` é `VARCHAR(100)` na V1. Ampliar exigiria um `ALTER` na V3, e o critério de aceite é que a V3 contenha só índices.
2. **Tutor, espécie, raça ou vacina inexistentes no corpo do request → 400 no campo** (`errors.clienteId`, etc.), e não 404. Assim o formulário marca o campo. Raça incompatível e vacina incompatível continuam 409, como a spec pede. Vacina inativa → 409.
3. **`pet.foto_url` guarda a chave interna** (`uuid.png`). A API devolve `fotoUrl: "/api/v1/pets/{id}/foto"`.
4. **`GET /api/v1/pets` ganhou o filtro `clienteId`**, usado na ficha do cliente.

## Pendências
- **Inativar um cliente não desativa os pets dele.** Eles somem das listas (o filtro exige tutor ativo), mas a ficha continua acessível pelo ID. Decidir a regra: desativar em cascata ou bloquear a inativação.
- **Não há usuário com papel SERVICO para testar a interface por perfil**, porque ainda não existe CRUD de usuários/funcionários. As permissões estão cobertas pelos ITs.

---

## Handoff para a etapa 02: prompt para o ChatGPT

Copie o bloco abaixo no ChatGPT e salve a resposta como `docs/next-stage/02-agendamentos.md`.

````text
Você é o arquiteto de software do projeto PetConnect Web. Gere UM arquivo Markdown chamado `02-agendamentos.md`, que será executado por outro agente de IA (Claude Code) direto no repositório. Seja específico e verificável: nada de "a definir". Responda somente com o conteúdo do arquivo, dentro de um único bloco ```markdown. Use o mesmo formato e rigor da especificação anterior (01-pets.md): objetivo, análise obrigatória, escopo, fora de escopo, banco, API endpoint a endpoint, regras numeradas, autorização em tabela, auditoria, frontend, testes com nomes de casos, critérios de aceite, riscos, ordem de implementação, restrições e relatório final.

## Contexto do projeto (estado atual)
- Monorepo: `backend/` (Spring Boot 4.1, Java 21, monólito modular `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`), `frontend/` (Next.js 16 App Router, Server Actions, Zod, Tailwind 4, padrão BFF com JWT em cookie httpOnly), `legacy/php/` (sistema antigo, só referência).
- MySQL 8.4 com Flyway. Aplicadas: V1 (schema), V2 (catálogos), V3 (índices de pets). MongoDB só para auditoria (`AuditoriaService.registrar(acao, entidade, id, detalhes)`).
- Padrões consolidados: entidades sem setters públicos; exclusão lógica; `NotFoundException` → 404; `ConflictException` → 409; `CampoInvalidoException(campo, msg)` → 400 com `errors: {campo: msg}`; `PageResponse<T>`; `Clock` injetado com fuso America/Sao_Paulo (datas calculadas na leitura, nunca persistidas); `@PreAuthorize` com papéis ADMIN, VENDAS e SERVICO; testes `*Test` (Mockito, `Clock.fixed`) e `*IT` (estendem `IntegrationTest` com Testcontainers; o módulo de pets tem a base `PetIntegrationTest` com helpers `criarCliente`/`criarPet`/`comPapel`).
- Frontend: leitura em Server Components com `apiFetch`; escrita em Server Actions com `useActionState`, usando `unstable_rethrow` e `mensagemDeErro(status)`; componentes `Campo`, `Aviso` (info/erro), `BotaoConfirmar`, `StatusVacinaBadge`, `TutorPicker`, `FotoPet`; `AppShell` com o item "Agendamentos" ainda marcado "em breve"; layout mobile-first (tabela em md+, cartões no celular); abas por `?aba=`.
- Módulos prontos: auth (login JWT, bloqueio por tentativas), clientes + endereço, pets (CRUD, foto, idade), catálogos (espécie, raça, vacina), carteira de vacinação (status VENCIDA / VENCE_EM_30_DIAS / EM_DIA / SEM_PROXIMA_DOSE) e histórico de consultas.
- Não existe CRUD de usuários/funcionários: há só o ADMIN criado por variável de ambiente.

## Tabelas já existentes (V1), relevantes
- `servico(id, nome UNIQUE, descricao, preco, duracao_minutos, ativo)`, com V2: Banho, Tosa, Banho e Tosa, Consulta Veterinária e Hidratação.
- `preco_servico_porte(id, servico_id, porte CHECK PEQUENO|MEDIO|GRANDE, preco, UNIQUE(servico_id, porte))`, com V2: preço por porte para Banho, Tosa e Banho e Tosa.
- `agendamento(id, pet_id FK, servico_id FK NOT NULL, funcionario_id FK NULL, data_hora DATETIME, status CHECK AGENDADO|CONFIRMADO|EM_ANDAMENTO|CONCLUIDO|CANCELADO|NAO_COMPARECEU, total_estimado DECIMAL, observacoes, created_at, updated_at, INDEX(data_hora))`.
- `servico_realizado(id, agendamento_id FK UNIQUE NULL, pet_id, servico_id, funcionario_id NULL, data_servico, valor, observacoes)`.
- `funcionario(id, usuario_id FK UNIQUE NOT NULL, nome, cpf, sexo, telefone, endereco_id, ...)` — hoje sem nenhum registro.
- `carteira_vacina` e `historico_consulta` (módulo de pets, com endpoints próprios).

## Comportamento no legado (PHP)
- `servicos_agendar_banhotosa.php`: agendamento de banho/tosa com preço calculado pelo porte do pet (`preco_servico_porte`; porte vazio assume Pequeno), escolha de funcionário, data e hora; também agenda consulta veterinária e vacinação.
- O legado usava uma tabela `agendamento_servico` (vários serviços por agendamento), que NÃO existe na V1 — o agendamento novo tem um `servico_id` só. Decida e justifique: manter um serviço por agendamento ou criar a tabela de itens numa V4 (com o `total_estimado` coerente).
- `servicos_agendamentos_listar.php`, `servicos_agendamentos_editar.php`, `servicos_agendamentos_salvar_edicao.php`, `servicos_agendamentos_buscar_rapido.php`: lista, edição e busca.
- `servicos_processar_finalizar_vacina*.php`: finalizar um agendamento de vacinação marca `concluido` e insere a dose na carteira. No novo sistema isso deve reutilizar a regra do módulo de pets (vacina do catálogo da mesma espécie, datas válidas) e não duplicá-la.
- O legado apagava agendamentos fisicamente.

## O que a especificação `02-agendamentos.md` deve decidir e cobrir
1. Escopo: catálogo de serviços (leitura, e se entra CRUD de serviços/preços para ADMIN), agendamentos (criar, listar por dia/semana, filtrar por status/pet/tutor, editar, cancelar), máquina de estados do status com transições permitidas e papéis por transição, conclusão gerando `servico_realizado` (e, se for vacinação, o lançamento na carteira), e marcação de não comparecimento.
2. Preço: como calcular `total_estimado` (preço por porte vs. preço padrão; pet sem porte), se o valor é congelado no agendamento e se pode ser alterado na conclusão.
3. Conflito de agenda: regra de sobreposição usando `duracao_minutos` (por pet e, se houver, por funcionário), código HTTP e mensagem.
4. Funcionário: como tratar `funcionario_id` sem CRUD de funcionários (opcional/nulo nesta etapa, ou incluir um cadastro mínimo de funcionário vinculado a usuário). Justifique.
5. Banco: V4 somente com o necessário (índices e, se decidir, a tabela de itens), sem editar V1–V3.
6. API endpoint a endpoint com JSON de exemplo, validações, papéis (incluindo o que SERVICO pode fazer) e códigos de erro.
7. Frontend: agenda do dia responsiva (lista/cartões no celular, visão por dia com navegação de datas), criar agendamento a partir do pet (`/pets/[id]`) e do menu, seleção de tutor → pet, preço exibido antes de confirmar, ações de status com confirmação, liberar o item "Agendamentos" do menu, e exibir os próximos agendamentos na ficha do pet.
8. Auditoria: eventos por ação e por transição de status.
9. Testes nominais (unitários, IT e frontend), incluindo transições inválidas, conflito de horário, preço por porte e conclusão de vacinação integrada à carteira.
10. Critérios de aceite, riscos e o que fica para as próximas etapas: PDV/vendas, estoque, financeiro (a movimentação financeira de serviços fica fora ou entra? decida) e migração de dados do legado.
````
