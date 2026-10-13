# 05 — PDV e Vendas: relatório

Branch: `feat/vendas` (criada a partir de `feat/produtos`). As etapas 01–05 seguem **sem commit**, conforme a restrição da especificação.

## Implementação concluída

### Banco
- `V7__vendas_status_idempotencia.sql`: V1–V6 intocadas; SQL idêntico ao §6 da spec.
- Colunas novas em `venda`:
  - `status` (CONCLUIDA/CANCELADA, padrão CONCLUIDA);
  - `usuario_id` (operador);
  - `valor_recebido`;
  - `chave_idempotencia` (UNIQUE `uk_venda_idempotencia`);
  - `cancelada_em`, `cancelada_por`, `motivo_cancelamento`.
- CHECKs: `ck_venda_status`, `ck_venda_valores` (`valor_total > 0 AND desconto >= 0`), `ck_venda_cancelamento`.
- FKs sem ação referencial: `fk_venda_usuario`, `fk_venda_cancelada_por`.
- Índices: `idx_venda_status_data`, `idx_venda_cliente_data`, `idx_venda_pagamento_data`.
- `chk_mov_estoque_tipo` recriada com `ESTORNO_VENDA` (DROP CHECK + ADD, sem editar a V6).
- Aplicada no Testcontainers e no banco local existente (V6 → V7: `Successfully applied 1 migration … now at version v7`).

### Vendas
- Pacote `venda/`:
  - `domain`: `Venda`, `ItemVenda`, `StatusVenda`, `FormaPagamento`;
  - `infrastructure`: `VendaRepository` (busca paginada, detalhe com EntityGraph, `findByChaveIdempotencia`, `travar`, resumo agregado por projeção);
  - `application`: `VendaService`, `RegistroVendaIdempotente`, `ResultadoVenda`, `VendasProperties`;
  - `web`: `VendaController` e DTOs `VendaRequest`, `VendaResponse`, `PreviaVendaResponse`, `CancelamentoVendaRequest`, `ResumoDiaResponse`.
- Endpoints:
  - `POST /api/v1/vendas/previa`;
  - `POST /api/v1/vendas` (header `Idempotency-Key`);
  - `GET /api/v1/vendas` (filtros `dataInicio`, `dataFim`, `status`, `formaPagamento`, `clienteId`; ordenado por `dataVenda desc, id desc`);
  - `GET /api/v1/vendas/{id}`;
  - `GET /api/v1/vendas/resumo-dia?data=`;
  - `POST /api/v1/vendas/{id}/cancelar`.
- **Preço** do produto lido com lock e congelado em `item_venda`. O DTO não tem preço, total, status, data nem operador.
- **Desconto** em valor, menor que o subtotal. VENDAS ≤ `petconnect.vendas.desconto-maximo-percentual-vendas` (padrão 10). ADMIN sem teto além de total > 0.
- **Dinheiro**: exige `valorRecebido ≥ total`; o troco é calculado na resposta e só `valor_recebido` é persistido. Nas demais formas, o valor recebido é ignorado (gravado NULL).
- **Idempotência**:
  - chave UUID obrigatória (400 em `idempotencyKey`);
  - mesma chave do mesmo operador → 200 + `Idempotent-Replayed: true`, sem nova baixa e sem nova auditoria;
  - chave de outro operador → 409.
  - Corrida: a UNIQUE derruba a 2ª gravação (rollback total) e `RegistroVendaIdempotente`, fora da transação, devolve a 1ª.
- **Operador e funcionário**: operador = usuário do JWT; `funcionario_id` = funcionário vinculado ao usuário, se houver.
- **Cliente** opcional; precisa estar ativo (400 em `clienteId`).
- **Prévia**: mesmos cálculos e validações de campo, sem gravar nem travar. Falta de estoque vira `suficiente: false` / `podeFinalizar: false`.

### Estoque
- `EstoqueService.registrarSaidaVenda` e `registrarEstornoVenda` exigem o produto já travado e não mexem no custo.
  - A saída grava `SAIDA_VENDA` com quantidade negativa.
  - O estorno grava `ESTORNO_VENDA` com quantidade positiva.
  - Os dois gravam o `venda_id`.
- `MovimentacaoEstoque` ganhou um construtor com `vendaId`. O módulo de estoque guarda só o id da venda.
- `EstoqueInsuficienteException` (409) leva `produtoId`, `disponivel` e `solicitado` no ProblemDetail. `ConflictException` passou a aceitar propriedades extras, e o `GlobalExceptionHandler` as publica.
- A venda trava os produtos com `travarEmOrdem` (ordem crescente de id, a mesma do recebimento de compra) e confere o saldo de **todos** antes de gravar qualquer coisa.

### Cancelamento
- Só ADMIN, só `CONCLUIDA`, até 7 dias após `data_venda` (409 "O prazo para cancelar esta venda expirou."), com motivo de 5–500 caracteres.
- Trava a venda (`PESSIMISTIC_WRITE`) e os produtos em ordem de id, e estorna item a item, inclusive produto desativado.
- Registra quem cancelou, quando e o motivo. Cancelar de novo → 409 "A venda já está cancelada.".

### Segurança
- Matriz aplicada:

  | Endpoint | ADMIN | VENDAS | SERVICO |
  |---|---|---|---|
  | `POST /vendas/previa`, `POST /vendas` | ✅ | ✅ | ❌ |
  | `GET /vendas`, `GET /vendas/{id}`, `GET /vendas/resumo-dia` | ✅ | ✅ | ❌ |
  | `POST /vendas/{id}/cancelar` | ✅ | ❌ | ❌ |

- `SecurityConfig`: `POST /api/v1/vendas/*/cancelar` → ADMIN; `/api/v1/vendas/**` → ADMIN, VENDAS. O 403 sai antes da validação do corpo.
- `@PreAuthorize` na classe (ADMIN/VENDAS) e no cancelamento (ADMIN).

### Auditoria
- `VENDA_REALIZADA`: vendaId, quantidadeItens, valorTotal, desconto, formaPagamento, clienteId.
- `VENDA_CANCELADA`: vendaId, motivo, valorTotal.
- Replay idempotente não audita. Nenhum dado pessoal além de ids.

### Frontend
- **Rotas**:
  - `/pdv`: renderizado só no cliente via `dynamic(ssr:false)`, porque o carrinho vem do `sessionStorage`;
  - `/vendas`: período padrão "hoje", filtros de status e pagamento, paginação, estado vazio, skeleton;
  - `/vendas/[id]`: detalhe e cancelamento para ADMIN dentro do prazo; o botão some fora dele e a API confere de novo;
  - `/vendas/[id]/comprovante`: grupo de rotas `(impressao)`, autenticado e sem AppShell, com `@page 80mm` na impressão.
- **PDV**:
  - leitor: Enter ou Tab com código de 8–14 dígitos adiciona direto; texto mostra a lista; produto sem estoque fica desabilitado;
  - carrinho com − e + limitados ao saldo conhecido, valores em centavos, persistido em `sessionStorage` (`pc_pdv_carrinho`: itens + chave, sem token);
  - cliente opcional (reaproveita o `TutorPicker`, que ganhou `rotulo`/`obrigatorio`);
  - desconto em R$ ou %, com dica e checagem do teto para VENDAS;
  - pagamento em botões grandes; em dinheiro, valor recebido e troco;
  - ao finalizar: **prévia no backend**. Preço alterado atualiza o carrinho e avisa "Preços atualizados pelo sistema"; em seguida, confirmação com o **total do backend** e envio com a `Idempotency-Key`.
    - Sucesso: painel "Venda #N concluída" com troco, comprovante e "Nova venda"; o carrinho é limpo e a chave, trocada.
    - 409: a linha do produto fica destacada e o carrinho é mantido.
  - No celular e no tablet, o total e o botão "Finalizar" ficam fixos no rodapé.
- **Componentes**: `pdv`, `pdv-cliente`, `pdv-leitor`, `pdv-carrinho`, `pdv-pagamento`, `vendas-lista` (+ `BadgeStatusVenda`), `venda-detalhes`, `comprovante`, `dashboard-vendas`.
- **Lib**:
  - `lib/vendas.ts`: tipos, schema Zod, carrinho, persistência, desconto, troco, `novaChave` com fallback fora de contexto seguro;
  - `lib/vendas-server.ts`;
  - `apiRequest` em `lib/api.ts`, que devolve status e cabeçalhos para detectar o replay.
- **Actions** em `app/actions/vendas.ts`: `previaVenda`, `finalizarVenda`, `cancelarVenda`.
- **AppShell**: seção **Vendas** com "Vendas (PDV)" e "Vendas", só para ADMIN/VENDAS. "Relatórios" continua em "Em breve".
- **Dashboard**: card "Vendas hoje" (quantidade e total, só concluídas), buscado só para ADMIN/VENDAS.

### Testes
- **unitários: 237/237** (26 novos):
  - `VendaServiceTest`: os 22 da spec;
  - `EstoqueServiceTest`: +4.
- **integração: 177/177** (19 novos):
  - `VendaControllerIT`: os 16 da spec;
  - `VendaConcorrenciaIT`: os 3 da spec.
- **frontend: 177/177** (39 novos), nos 9 arquivos do §21:
  - `venda-schema`, `carrinho`;
  - `pdv-leitor`, `pdv-pagamento`, `pdv-finalizacao`;
  - `vendas-list`, `venda-detalhes`, `comprovante`, `dashboard-vendas`.
- **Concorrência** (Testcontainers, MySQL real):
  - duas vendas do último item → exatamente uma passa e a outra recebe `EstoqueInsuficienteException`; saldo 0 e uma única `SAIDA_VENDA`;
  - venda e recebimento de compra simultâneos → `saldoFinal = 20 + 7 − 3`;
  - mesma chave em paralelo → uma venda, uma baixa, um replay.

### Validações
- backend build (`./mvnw verify`, com JaCoCo): **PASS**
- frontend build (`next build`): **PASS**
- lint (`eslint --max-warnings=0`): **PASS**
- typecheck (`next typegen && tsc --noEmit`): **PASS**
- Docker Compose (`up -d --build`, migração V6 → V7 no banco local): **PASS**
- **E2E** no navegador e pela API, em http://localhost:3010:
  - leitor com Enter duas vezes → quantidade 2;
  - desconto R$ 5,00 + dinheiro R$ 100,00 → confirmação "R$ 74,80 em Dinheiro", troco R$ 25,20; saldo 15 → 13 com `SAIDA_VENDA −2`;
  - carrinho restaurado após recarregar;
  - comprovante de 80 mm com "Comprovante não fiscal";
  - `valorTotal: 0.01` no corpo ignorado;
  - mesma chave → 200 + `Idempotent-Replayed`, sem 2ª baixa;
  - sem chave → 400; quantidade 999 → 409 com `produtoId`, `disponivel` e `solicitado`;
  - VENDAS com desconto > 10% → 400 e com 3,99 → 201; VENDAS cancelar → 403; SERVICO → 403 em lista, resumo e prévia;
  - cancelamento pelo ADMIN com confirmação → `ESTORNO_VENDA +1`, saldo 11 → 12; cancelar de novo → 409;
  - dashboard "Vendas hoje: 2 · R$ 114,70" (a cancelada fica fora);
  - PDV a 375 px sem rolagem horizontal.

### Divergências e decisões
- **`data_venda` e `cancelada_em` em horário local de São Paulo** (`LocalDateTime`, como `agendamento.data_hora`), não em Instant/UTC com `Z` como no JSON de exemplo da spec.
  - Motivo: o projeto não fixa o fuso do JDBC, então um `Instant` gravado em `DATETIME` dependeria do fuso da JVM, que é diferente no Docker e nos testes.
  - Assim, "hoje", os filtros por período e o prazo de 7 dias batem com o dia da loja. A API devolve `"2026-09-26T12:57:10.123"`.
- **Confirmação do PDV fica no orquestrador**, depois da prévia, e não no painel de pagamento, para mostrar o **total do backend** (spec §15). Por isso o caso "confirmação antes de finalizar" está em `pdv-finalizacao.test.tsx`, não em `pdv-pagamento.test.tsx`.
- **Prévia com dinheiro e valor recebido menor que o total → 400**, como na venda: a spec diz que a prévia aplica as mesmas validações de campo, exceto estoque.
- **Correções de layout encontradas no E2E**:
  - `AppShell` agora usa `md:grid-cols-[16rem_minmax(0,1fr)]`, porque a trilha `1fr` crescia além da tela quando o conteúdo tinha largura mínima grande. Beneficia todas as páginas.
  - O PDV só usa duas colunas a partir de `xl`, porque em 1024 px com a barra lateral a coluna do carrinho ficava estreita demais.
  - A linha do carrinho passou a ter duas faixas.
  - O rodapé fixo começa após a barra lateral (`md:left-64`).
  - A coluna "Operador" da lista aparece só a partir de `lg`.
- **Usuários de teste no banco local**: `vendas.e2e`, `vendas2.e2e` (VENDAS) e `servico.e2e` (SERVICO), criados para o E2E com senhas geradas e descartadas. Podem ser desativados em /usuarios.
- **Dados de teste**: o produto "E2E Racao" ganhou o código de barras 7891000000017. Há 3 vendas locais (#1 e #2 concluídas, #3 cancelada).

### Pendências
- Nenhuma pendência funcional da etapa.
- Operacional: etapas 01–05 continuam **sem commit** nas branches locais (restrição das specs). Commit e PR dependem de autorização explícita.

---

## Handoff para a etapa 06: prompt para o ChatGPT

A próxima etapa natural é o **Financeiro**, que consome vendas concluídas, compras recebidas e serviços realizados.

````text
Você é o arquiteto de software do projeto PetConnect Web. Escreva o CONTEÚDO do arquivo `06-financeiro.md`, que será executado por outro agente de IA (Claude Code) direto no repositório. Comece por "# 06 — Financeiro" e responda SOMENTE com o arquivo, num único bloco ```markdown, sem repetir estas instruções. Seja específico e verificável: nada de "a definir". Use exatamente o formato das etapas 04 e 05: 1. Objetivo; 2. Análise obrigatória; 3. Decisões arquiteturais justificadas (DA-xx); 4. Escopo; 5. Fora de escopo; 6. Banco (migration V8 com SQL completo); 7–11. API endpoint a endpoint com JSON de request/response e códigos de erro; 12. Exemplo de cálculo; 13. Autorização em tabela; 14. Auditoria; 15. Frontend por rota; 16. Dashboard; 17. Erros/UI; 18. Regras numeradas; 19. Testes unitários com nomes de casos; 20. Testes de integração com nomes de casos; 21. Testes frontend por arquivo; 22. Segurança e integridade; 23. Critérios de aceite (checklist); 24. Riscos; 25. Próximas etapas; 26. Ordem de implementação; 27. Restrições; 28. Relatório final obrigatório ("unitários: X/Y, integração: X/Y, frontend: X/Y", PASS/FAIL de backend build, frontend build, lint e typecheck, divergências, pendências, handoff da etapa 07); 29. Definição de pronto.

## Contexto do projeto (estado atual)
- Monorepo: `backend/` (Spring Boot 4.1, Java 21, monólito modular `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`), `frontend/` (Next.js 16 App Router, Server Actions, Zod 4, Tailwind 4; BFF com JWT em cookie httpOnly), `legacy/php/` (só referência, não alterar).
- MySQL 8.4 com Flyway: aplicadas V1–V7 (nunca editar migration aplicada; mudança entra como V8). MongoDB só para auditoria (`AuditoriaService.registrar`).
- Padrões: entidades sem setters públicos; exclusão lógica; `NotFoundException` 404, `ConflictException` 409 (aceita propriedades extras no ProblemDetail), `CampoInvalidoException(campo,msg)` 400; `PageResponse<T>`; `Clock` America/Sao_Paulo; datas de negócio como `LocalDate`/`LocalDateTime` locais de SP (o JDBC não fixa fuso); `BigDecimal` 2 casas HALF_UP (nunca double/float); `@Lock(PESSIMISTIC_WRITE)`; `@PreAuthorize` + regras por URL no `SecurityConfig` (403 antes da validação); `UsuarioAtual.id()` (uid do JWT); idempotência já usada na venda (`Idempotency-Key` + UNIQUE + `RegistroVendaIdempotente` fora da transação).
- Testes: `*Test` (Mockito, `Clock.fixed`) e `*IT` (Testcontainers MySQL + Mongo; bases `PetIntegrationTest`, `EstoqueIntegrationTest`, `VendaIntegrationTest` com `comPapel`, `login`/`bearer`, `admin()`, `vendas()`, `criarCliente`, `produto(preco, saldo)`, `criarFornecedor`, `criarCompra`, `receber`, `venderOk`, `cancelar`). Frontend: Vitest + Testing Library.
- Frontend: `AppShell` com seções (Estoque; Vendas: PDV e Vendas; Em breve: Relatórios; Administração) e itens com `papeis`; `Campo`, `Aviso`, `AlternarAtivo`, `BotaoConfirmar`; `useActionState` + Zod; mobile-first (tabela md+, cartões no celular; grid do AppShell com `minmax(0,1fr)`); dinheiro no front em centavos (`paraCentavos`, `moedaCentavos`); `apiRequest` devolve status/cabeçalhos.
- Papéis: ADMIN, VENDAS, SERVICO.

## Fontes de receita e despesa já existentes
- `venda` (V1 + V7): `status` CONCLUIDA|CANCELADA, `valor_total`, `desconto`, `forma_pagamento` (DINHEIRO, CARTAO_CREDITO, CARTAO_DEBITO, PIX, TRANSFERENCIA), `data_venda` (LocalDateTime SP), `cancelada_em`, `usuario_id`. Venda pode ser cancelada em até 7 dias (estorno de estoque já existe).
- `compra` (V1): `status` PEDIDO|RECEBIDO|CANCELADO, `valor_total`, `forma_pagamento` VARCHAR(50) livre (ex.: "Boleto 30 dias"), `data_compra`, `data_entrega` (recebimento), fornecedor.
- `servico_realizado` (V1, gerado na conclusão do agendamento): verificar colunas exatas (valor cobrado, data, pet/cliente) antes de decidir.
- `movimentacao_financeira` (V1): `tipo` ENTRADA|SAIDA, `categoria` VENDA_PRODUTO|SERVICO|DESPESA_FUNCIONARIO|DESPESA_OPERACIONAL|COMPRA_MERCADORIA|OUTROS, `descricao` VARCHAR(100), `valor` > 0, `data_movimentacao` DATE, no máximo UMA origem entre `venda_id`/`compra_id`/`servico_realizado_id` (FKs sem ação referencial), `observacoes`, `created_at`. Hoje nenhum fluxo grava nesta tabela. Não tem status (previsto/realizado), vencimento, forma de pagamento, usuário, estorno nem idempotência — decida o que a V8 acrescenta e justifique.
- Atenção MySQL: CHECK em coluna com FK `ON DELETE SET NULL` falha; FKs novas sem ação referencial.

## O que a especificação `06-financeiro.md` deve decidir e cobrir
1. Escopo: lançamentos automáticos (venda concluída → ENTRADA/VENDA_PRODUTO; compra recebida → SAIDA/COMPRA_MERCADORIA ou conta a pagar; serviço realizado → ENTRADA/SERVICO), estorno automático no cancelamento de venda (lançamento inverso, nunca apagar), lançamentos manuais (despesas operacionais/funcionário/outros) com motivo, contas a pagar (compras com vencimento, baixa/pagamento), caixa diário (abertura com fundo de troco, sangria/suprimento, fechamento com conferência por forma de pagamento e diferença), relatório de fluxo de caixa por período.
2. Decisões justificadas: se o lançamento é gravado na MESMA transação da venda/compra/serviço (recomendado: sim, via serviço financeiro central) e como fica a idempotência (uma venda gera exatamente um lançamento; replay não duplica); regime de caixa vs competência para vendas no cartão (data de recebimento/taxas — decidir se entra ou fica fora); se venda só é permitida com caixa aberto (impacto no PDV já existente) — justificar; como tratar as vendas já existentes antes da V8 (backfill na migration ou comando idempotente); precisão BigDecimal; imutabilidade dos lançamentos (correção só por estorno).
3. Banco: V8 só com o necessário (colunas novas em `movimentacao_financeira` e/ou tabelas `caixa`, `conta_pagar`, índices por data/tipo/categoria/origem, UNIQUE que garanta um lançamento por origem e tipo), SQL completo, sem editar V1–V7.
4. API endpoint a endpoint com JSON (lançamentos com filtros, lançamento manual, estorno manual, contas a pagar e baixa, caixa: abrir/sangria/suprimento/fechar/atual, fluxo de caixa por período com totais por categoria e forma), validações, papéis (sugestão: ADMIN tudo; VENDAS abre/fecha o próprio caixa e vê o caixa do dia; SERVICO sem acesso) e códigos de erro.
5. Frontend: liberar item "Financeiro" no AppShell (seção própria), `/financeiro` (fluxo por período, filtros, totais), `/financeiro/lancamentos/novo`, `/financeiro/contas-a-pagar`, `/caixa` (abrir/fechar/conferência), integração do PDV com o caixa se decidido; dashboard com saldo do dia e contas a vencer; mobile-first, estados vazio/erro/loading, confirmações.
6. Auditoria: LANCAMENTO_CRIADO, LANCAMENTO_ESTORNADO, CONTA_PAGA, CAIXA_ABERTO, CAIXA_FECHADO (com diferença), sem dados sensíveis.
7. Testes nominais: unitários (geração automática por venda/compra/serviço, estorno no cancelamento, idempotência, cálculo do fechamento e diferença, regras de caixa), IT (venda concluída gera 1 lançamento; replay e cancelamento; compra recebida gera conta/lançamento; permissões; concorrência em fechamento de caixa; backfill), frontend (fluxo, lançamento manual, caixa, contas a pagar, dashboard).
8. Critérios de aceite, riscos (duplicidade de lançamento, venda sem caixa aberto, fuso/datas, arredondamento, vendas antigas sem lançamento) e o que fica para depois: relatórios gerenciais (07), migração do legado (08), conciliação bancária/taxas de cartão e emissão fiscal.
9. Restrições obrigatórias na seção 27: NÃO editar V1–V7; NÃO alterar `legacy/`; NÃO hard-delete; NÃO editar/apagar lançamento (só estorno); NÃO aceitar valor/tipo/origem de lançamento automático vindos do frontend; NÃO usar double/float para dinheiro; NÃO duplicar lançamento de uma mesma origem; NÃO alterar estoque nesta etapa; NÃO desabilitar testes/lint; NÃO usar `any`; NÃO commit/push automático.
````
