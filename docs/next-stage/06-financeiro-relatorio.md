# 06 — Financeiro — Relatório de implementação

Branch: `feat/financeiro` (criada a partir de `feat/vendas`). As etapas 01–06 seguem **sem commit** (restrição da especificação).

Baseline antes de qualquer alteração (§2, item 10): backend 237 unitários + 177 IT e frontend 177, todos verdes.

## Banco
- **V8 aplicada**: `V8__financeiro_caixa_contas.sql`, com o SQL do §6 sem alterações de identificador.
  - Testcontainers: OK.
  - Banco local: V7 → V8, `Successfully applied 1 migration … now at version v8`.
  - V1–V7 intocadas; nenhuma constraint física divergiu.
- **Tabelas, colunas e constraints**:
  - `caixa`:
    - um ABERTO por usuário, via coluna gerada `usuario_aberto_id` + `uk_caixa_usuario_aberto`;
    - CHECKs de status, fundo e coerência do fechamento;
    - esperado, contado e diferença por forma, gravados no fechamento.
  - `caixa_movimento`: SANGRIA/SUPRIMENTO, valor > 0, motivo obrigatório.
  - `conta_pagar`:
    - `uk_conta_pagar_compra`: uma conta por compra;
    - CHECK de pagamento coerente (ABERTA sem dados de pagamento e saldo = valor; PAGA com dados de pagamento e saldo 0).
  - `venda.caixa_id` com `idx_venda_caixa_data`.
  - `movimentacao_financeira` ganhou:
    - `forma_pagamento`, `usuario_id`, `caixa_id`, `conta_pagar_id`;
    - `estorno_de_id` (UNIQUE: um estorno por lançamento);
    - `chave_origem` (UNIQUE) e `motivo_estorno`;
    - os 8 índices do §6.
- **Divergências**: nenhuma no SQL. As colunas novas continuam nulas no histórico. Todo lançamento novo preenche `usuario_id`, `caixa_id` e `chave_origem`; só o backfill grava sem caixa.

## Integrações
- **Serviço central**: `financeiro/application/FinanceiroService` é o único que grava `movimentacao_financeira` (entidade `Lancamento`, `@Immutable`). Chaves de origem: `VENDA:id`, `VENDA_ESTORNO:id`, `SERVICO:id`, `CONTA_PAGAR:id`, `MANUAL:uuid`, `MANUAL_ESTORNO:id`, `CAIXA_SUPRIMENTO:id`, `CAIXA_SANGRIA:id`.
- **Vendas**:
  - `VendaService.registrar` confere o caixa **antes** de travar produtos (409 sem nenhum efeito).
  - Depois trava produtos e caixa, nessa ordem, grava a venda com `caixa_id` e cria `ENTRADA / VENDA_PRODUTO` na mesma transação.
  - A resposta ganhou `caixaId` e `lancamentoFinanceiroId`.
  - O replay idempotente devolve a mesma venda e o mesmo lançamento, sem duplicar.
- **Cancelamento**: exige o caixa aberto de quem cancela (ADMIN). Estorna o estoque e cria `SAIDA / VENDA_PRODUTO` com `estorno_de_id` apontando para a entrada, com a mesma forma e o mesmo valor, na mesma transação. Sem caixa: 409 e nada muda.
- **Compras**:
  - `POST /compras/{id}/receber` passou a exigir `dataVencimento`, entre `dataEntrega` e `dataEntrega + 365` (400 em `dataVencimento`).
  - Cria exatamente uma `conta_pagar` ABERTA, sem saída financeira.
  - A resposta, e o detalhe da compra, trazem `contaPagar`.
- **Pagamento**: `POST /financeiro/contas-a-pagar/{id}/pagar` trava a conta, exige o caixa aberto de quem paga, paga integralmente e cria `SAIDA / COMPRA_MERCADORIA` (com `conta_pagar_id` e `compra_id`). Pagar de novo: 409 "Esta conta já foi paga.".
- **Serviços**:
  - A conclusão (`POST /agendamentos/{id}/status` com `CONCLUIDO`) exige `formaPagamento` (400) e o caixa aberto de quem conclui (409), conferidos antes de qualquer efeito.
  - `servico_realizado` e `ENTRADA / SERVICO` são gravados na mesma transação; a vacina da conclusão também volta atrás se o lançamento falhar.
  - `formaPagamento` fora da conclusão: 400.
- **Caixa**:
  - `POST /caixas/abrir`; `GET /caixas/atual` (404 sem caixa); `GET /caixas/resumo-atual`;
  - `POST /caixas/atual/suprimentos` e `/sangrias` (movimento + lançamento `OUTROS` em dinheiro, sangria limitada ao esperado em dinheiro);
  - `GET /caixas/atual/previa-fechamento` e `POST /caixas/atual/fechar` (lock, esperado/contado/diferença por forma, motivo obrigatório se houver diferença, definitivo);
  - `GET /caixas` (ADMIN) e `GET /caixas/{id}` (ADMIN, qualquer caixa; demais papéis só o próprio, senão 403).
- **Backfill**:
  - `FinanceiroBackfill` + `FinanceiroBackfillRunner`, este só com `--petconnect.financeiro.backfill=true` (variável `PETCONNECT_FINANCEIRO_BACKFILL=true`); sem endpoint público.
  - Idempotente por `chave_origem` e `compra_id`. Gera:
    - entradas de vendas históricas, com a data e a forma originais e sem caixa;
    - o estorno vinculado das vendas canceladas;
    - contas ABERTAS das compras recebidas, com vencimento = `data_entrega`.
  - Serviços históricos ficam **pendentes** (não inventa a forma), a menos que `petconnect.financeiro.backfill-servico-forma` seja configurado.
  - Executado no banco local: 3 vendas, 1 estorno, 2 contas, 2 serviços pendentes. A segunda execução deu tudo zero.
- **Fluxo e resumo**: `GET /financeiro/fluxo` (totais, por categoria, por forma) e `GET /financeiro/resumo-dia` (saldo, contas vencidas, a vencer em 7 dias, caixas abertos), agregados em SQL.
- **Lançamentos**:
  - `GET /financeiro/lancamentos`, com filtros de período, tipo, categoria, forma e origem (VENDA, SERVICO, CONTA_PAGAR, MANUAL, CAIXA);
  - `GET /financeiro/lancamentos/{id}`, com `estornadoPorId` e `estornavel`;
  - `POST /financeiro/lancamentos`: manual, ADMIN, `Idempotency-Key`, categorias `DESPESA_FUNCIONARIO`/`DESPESA_OPERACIONAL`/`OUTROS`, exige caixa;
  - `POST /financeiro/lancamentos/{id}/estornar`: só manual; automático → 409; segundo estorno → 409.

## Segurança
- **SecurityConfig**:
  - `/api/v1/financeiro/**` → ADMIN;
  - `GET /api/v1/caixas` → ADMIN;
  - `/api/v1/caixas/**` → ADMIN, VENDAS, SERVICO.
  - As regras de vendas (etapa 05) seguem valendo. O 403 sai antes da validação do corpo.
- **@PreAuthorize**:
  - `FinanceiroController` só ADMIN;
  - `CaixaController` para os três papéis, com a listagem só para ADMIN;
  - a checagem de dono do caixa fica no serviço (`AccessDeniedException` → 403).
- O operador e o dono do caixa vêm sempre do `uid` do JWT; valor, tipo, categoria e origem dos lançamentos automáticos nunca vêm do frontend.

## Auditoria
- **Eventos**:
  - `LANCAMENTO_CRIADO`: todo lançamento, inclusive os automáticos, com tipo, categoria, valor, forma, origem e caixa;
  - `LANCAMENTO_ESTORNADO`;
  - `CONTA_PAGAR_CRIADA` e `CONTA_PAGA`;
  - `CAIXA_ABERTO`, `CAIXA_SUPRIMENTO`, `CAIXA_SANGRIA`;
  - `CAIXA_FECHADO`, com esperado, contado e diferença.
- Os eventos de origem (VENDA_REALIZADA, AGENDAMENTO_CONCLUIDO...) continuam. Replay idempotente não audita de novo. Sem JWT, tokens nem dados bancários.

## Frontend
- **Rotas**:
  - `/financeiro`: cards do dia, filtros, fluxo por categoria e forma, lançamentos paginados, tabela no md+ e cartões no celular;
  - `/financeiro/lancamentos/novo`: chave gerada uma vez e reenviada em retry; bloqueado sem caixa;
  - `/financeiro/lancamentos/[id]`: estorno de manual com motivo e confirmação; vínculos de estorno;
  - `/financeiro/contas-a-pagar` (abas de situação com texto) e `/financeiro/contas-a-pagar/[id]` (pagamento integral com confirmação; bloqueado sem caixa);
  - `/caixa`: abertura; esperado por forma; suprimento e sangria com confirmação; fechamento com diferença ao vivo, motivo obrigatório e confirmação; para ADMIN, caixas recentes de todos;
  - `/caixa/[id]`: histórico;
  - `(impressao)/caixa/[id]/comprovante`: 80 mm, documento não fiscal.
- **Componentes**: `caixa.tsx`, `contas-pagar.tsx`, `lancamento-form.tsx`, `lancamento-detalhes.tsx`, `lancamentos-lista.tsx`, `financeiro-badges.tsx`, `dashboard-financeiro.tsx`.
- **Lib**: `lib/financeiro.ts` (tipos, rótulos, schemas Zod, dinheiro em centavos) e `lib/financeiro-server.ts`.
- **Actions** em `app/actions/financeiro.ts`.
- **Integrações nas telas existentes**:
  - **PDV**: sem caixa mostra "Abra o caixa para realizar vendas." e bloqueia a finalização. Um 409 de caixa fechado em outra aba bloqueia e preserva carrinho e chave.
  - **Conclusão do agendamento**: select de forma de pagamento; sem caixa, bloqueia com link para `/caixa`.
  - **Recebimento de compra**: campo de vencimento (padrão +30 dias) e link para a conta.
- **AppShell**: seção **Financeiro**, com Caixa para todos e Financeiro e Contas a pagar só para ADMIN. Destaca só o item mais específico, e a barra de rolagem do menu segue o tema.
- **Dashboard**: "Meu caixa" para todos; para ADMIN, saldo do dia, contas vencidas, a vencer em 7 dias e caixas abertos. Cada card só chama o endpoint que o papel pode acessar.
- **Impressão**: comprovante de fechamento em 80 mm, com um bloco por forma (esperado, contado, diferença), movimentos e assinatura.

## Testes
- **unitários: 273/273** (36 novos):
  - `FinanceiroServiceTest` 15 e `CaixaServiceTest` 12 (os 11 da spec + líquido negativo em forma eletrônica);
  - ajustes: `VendaServiceTest` +3, `CompraServiceTest` +2, `AgendamentoServiceTest` +4.
- **integração: 216/216** (39 novos):
  - `FinanceiroVendaIT` 5, `ContaPagarControllerIT` 5, `CaixaControllerIT` 10, `FinanceiroControllerIT` 7;
  - `FinanceiroServicoIT` 4: o rollback é provado com lançamentos-sentinela ocupando as próximas chaves `SERVICO:n`;
  - `FinanceiroBackfillIT` 4 e `FinanceiroConcorrenciaIT` 4.
- **frontend: 217/217** (40 novos), nos 12 arquivos do §21:
  - `financeiro-schema`, `lancamento-manual`, `lancamento-detalhes`;
  - `contas-a-pagar`, `conta-pagar-detalhes`;
  - `caixa-abertura`, `caixa-operacao`, `caixa-fechamento`, `comprovante-caixa`;
  - `pdv-caixa`, `agendamento-conclusao-financeiro`, `financeiro-dashboard`.
- **Testes anteriores ajustados, com a mesma cobertura de regras**:
  - `AgendaIntegrationTest.mudarStatus`: na conclusão, usa um usuário real do papel com caixa aberto e envia PIX se o JSON não trouxer forma;
  - `EstoqueIntegrationTest.admin()`: abre o caixa do ADMIN;
  - `VendaIntegrationTest.vendas()`: abre o caixa do vendedor;
  - o recebimento envia `dataVencimento`.

## Validações
- backend build (`./mvnw verify`, com JaCoCo): **PASS**
- frontend build (`next build`): **PASS**
- lint (`eslint --max-warnings=0`): **PASS**
- typecheck (`next typegen && tsc --noEmit`): **PASS**
- Docker Compose (`up -d --build`, migração V7 → V8 no banco local): **PASS**
- **E2E pela API** (admin):
  - venda sem caixa → 409; abrir caixa com fundo 100; abrir de novo → 409;
  - venda Pix 39,90 → caixa 1, lançamento 1; venda em dinheiro com troco 10,10;
  - suprimento 50 e sangria 20; sangria de 9.999 → 409; esperado em dinheiro **169,90** (100 + 39,90 + 50 − 20);
  - receber sem vencimento → 400; conta criada; pagar Pix → PAGA; pagar de novo → 409;
  - manual idempotente (replay 200) com a categoria automática recusada; estorno vinculado; segundo estorno → 409; estornar lançamento de venda direto → 409;
  - cancelar a venda Pix → `SAIDA` com `estornoDeId` apontando para a entrada;
  - fechar com falta de R$ 5 sem motivo → 400; com motivo → FECHADO, diferença −5,00; fechar de novo → 409; vender depois do fechamento → 409.
- **Navegador** (http://localhost:3010):
  - abertura pela tela, esperado por forma e diferença ao vivo ("Falta de R$ 5,00"), com o motivo exigido;
  - `/financeiro`, contas com situação em texto, comprovante de 80 mm, menu com o item certo ativo;
  - `/caixa` e `/financeiro` a 375 px sem rolagem horizontal.

## Divergências
- **Contado negativo nas formas eletrônicas**: o §9 não previa isso, e apareceu no E2E. Quando um caixa paga uma conta por Pix maior que o Pix recebido, o esperado em Pix fica negativo (−50,00), e o fechamento recusava contado negativo, então esse caixa nunca fecharia. Decisão: dinheiro físico continua ≥ 0; em cartão, Pix e transferência o "contado" é o **líquido do extrato** (recebido − pago) e pode ser negativo. A tela explica isso.
- **Esperado em dinheiro**: a fórmula do DA-14 é aplicada como fundo + Σ(entradas − saídas em DINHEIRO do caixa). Sangria e suprimento já são lançamentos em dinheiro (DA-15), então entram **uma única vez**; somar de novo `caixa_movimento` contaria em dobro. O resultado é o mesmo do exemplo do §12 (730), coberto por teste.
- **Serviço concluído com valor 0**: não gera receita, porque o CHECK da V1 exige valor > 0. A conclusão continua exigindo forma e caixa.
- **Cancelamento de venda anterior à V8 sem backfill**: não há entrada para estornar. O estoque é estornado e o financeiro é pulado; depois do backfill, o estorno é normal.
- **`GET /caixas/atual` sem caixa**: 404 com a mensagem padrão "Caixa aberto do usuário N não encontrado". As telas usam `resumo-atual` (`aberto: false`) e nunca mostram esse texto.
- **Correção de UX encontrada nos testes**: depois de um erro da Server Action, o React 19 reseta o formulário e os `<select>` com `defaultValue` voltavam ao valor inicial. Os selects novos (lançamento, pagamento, forma da conclusão) remontam com `key`. Os formulários das etapas anteriores têm o mesmo comportamento; está registrado como tarefa separada, fora do escopo desta etapa.
- **Usuários de teste no banco local**: nenhum novo. O E2E usou o ADMIN; o caixa #2 dele ficou **aberto** para uso no PDV.

## Pendências
- Nenhuma pendência funcional da etapa.
- Tarefa separada sugerida: aplicar a correção dos `<select>` (remontagem com `key`) nos formulários das etapas 01–05.
- Operacional: etapas 01–06 continuam **sem commit** nas branches locais. Commit e PR dependem de autorização explícita.

## Handoff da etapa 07
- **Migrations aplicadas**: V1–V8.
- **Schema financeiro final**:
  - `movimentacao_financeira`: tipo ENTRADA/SAIDA; categoria VENDA_PRODUTO, SERVICO, COMPRA_MERCADORIA, DESPESA_FUNCIONARIO, DESPESA_OPERACIONAL ou OUTROS; valor > 0; `data_movimentacao` (DATE, dia do reconhecimento em SP); forma; usuário; caixa; origens (`venda_id`, `compra_id`, `servico_realizado_id`, `conta_pagar_id`); `estorno_de_id`; `chave_origem`;
  - `caixa`, `caixa_movimento` e `conta_pagar`;
  - `venda.caixa_id`.
- **Endpoints**: `/api/v1/financeiro/{lancamentos,fluxo,resumo-dia,contas-a-pagar}` (ADMIN) e `/api/v1/caixas/**`.
- **Regras de caixa**:
  - um aberto por usuário; venda, serviço, pagamento, manual e cancelamento exigem caixa;
  - fechamento definitivo; estorno sempre no caixa de quem estorna;
  - sangria e suprimento são lançamentos `OUTROS` em dinheiro. **Relatórios de resultado devem excluí-los** (chaves `CAIXA_%`), porque são movimentação interna de numerário, não receita nem despesa.
- **Categorias e formas**: acima e no enum `FormaPagamento`.
- **Agregações existentes**: fluxo por categoria e por forma; saldo por forma do caixa; resumo de contas; resumo de vendas do dia (etapa 05).
- **Componentes reutilizáveis**: `FluxoResumo`, `LancamentosLista`, badges (`BadgeTipo`, `BadgeSituacao`, `BadgeCaixa`, `Diferenca`), `moeda`/`moedaCentavos`, comprovantes em `(impressao)`.
- **Helpers**: `lib/financeiro.ts` (rótulos, schemas, centavos com sinal), `lib/financeiro-server.ts`.
- **Bases de teste**: `FinanceiroIntegrationTest` (`operador(papel, abrir)`, `abrir`, `movimento`, `fechar`, `lancarManual`, `compraRecebida`, `lancamentos(where)`), `VendaIntegrationTest`, `AgendaIntegrationTest.operadorComCaixa`.
- **Limitações conhecidas**:
  - cartão reconhecido na operação, sem taxas nem data de liquidação;
  - pagamento só integral; sem contas a receber;
  - serviços anteriores à V8 sem lançamento (pendentes de forma);
  - vendas anteriores à V8 sem caixa.

````text
Você é o arquiteto de software do projeto PetConnect Web. Escreva o CONTEÚDO do arquivo `07-relatorios.md`, que será executado por outro agente de IA (Claude Code) direto no repositório.

REGRAS DE RESPOSTA (obrigatórias):
- Responda SOMENTE com o arquivo, dentro de um único bloco ```markdown.
- A primeira linha deve ser exatamente "# 07 — Relatórios gerenciais" e em seguida as seções "## 1. Objetivo" até "## 29. Definição de pronto", todas preenchidas com decisões já tomadas.
- NÃO repita este prompt nem estas instruções dentro do arquivo. Nada de "a definir".

Formato (igual às etapas 04–06): 1. Objetivo; 2. Análise obrigatória; 3. Decisões arquiteturais (DA-xx); 4. Escopo; 5. Fora de escopo; 6. Banco (V9 só se necessária — índices/views — com SQL completo, ou justificar que não há migration); 7–11. API endpoint a endpoint com JSON e códigos de erro; 12. Exemplo de cálculo; 13. Autorização em tabela; 14. Auditoria; 15. Frontend por rota; 16. Dashboard; 17. Erros/UI; 18. Regras numeradas; 19. Testes unitários (nomes); 20. Testes de integração (nomes); 21. Testes frontend por arquivo; 22. Segurança e integridade; 23. Critérios de aceite; 24. Riscos; 25. Próximas etapas; 26. Ordem de implementação; 27. Restrições; 28. Relatório final ("unitários: X/Y, integração: X/Y, frontend: X/Y", PASS/FAIL de backend build, frontend build, lint, typecheck, divergências, pendências, handoff da etapa 08); 29. Definição de pronto.

## Contexto (estado atual)
- Monorepo: backend Spring Boot 4.1 / Java 21 (monólito modular `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`), frontend Next.js 16 (App Router, Server Actions, Zod 4, Tailwind 4, BFF com JWT em cookie httpOnly), `legacy/php` só referência.
- MySQL 8.4 + Flyway V1–V8 (nunca editar migration aplicada). MongoDB só auditoria. Padrões: ProblemDetail, `CampoInvalidoException` 400, `ConflictException` 409, `PageResponse`, `Clock` America/Sao_Paulo, datas de negócio em horário local de SP (LocalDate/LocalDateTime), BigDecimal HALF_UP, `@PreAuthorize` + regras por URL, agregações em SQL (queries nativas quando preciso). Testes `*Test` (Mockito) e `*IT` (Testcontainers) com bases `PetIntegrationTest`, `EstoqueIntegrationTest`, `VendaIntegrationTest`, `FinanceiroIntegrationTest`, `AgendaIntegrationTest`; frontend Vitest + Testing Library.
- Papéis: ADMIN, VENDAS, SERVICO.
- Fatos disponíveis:
  - `venda` (status CONCLUIDA/CANCELADA, data_venda LocalDateTime SP, valor_total, desconto, forma_pagamento, cliente_id, usuario_id, funcionario_id, caixa_id) e `item_venda` (produto, quantidade, preco_unitario, subtotal gerado);
  - `produto` (categoria, preco_venda, preco_custo = custo médio ponderado atual, quantidade_estoque, estoque_minimo, ativo) e `movimentacao_estoque` (ENTRADA_COMPRA, SAIDA_VENDA negativa, AJUSTE, ESTORNO_VENDA; saldo anterior/resultante; venda_id/compra_id; created_at);
  - `compra`/`item_compra` e `conta_pagar`;
  - `servico_realizado` (servico, pet, funcionario, data_servico, valor) e `agendamento` (status, datas);
  - `movimentacao_financeira` (tipo, categoria, valor, data_movimentacao DATE, forma, caixa, origem por chave_origem: VENDA:, VENDA_ESTORNO:, SERVICO:, CONTA_PAGAR:, MANUAL:, MANUAL_ESTORNO:, CAIXA_SANGRIA:, CAIXA_SUPRIMENTO:);
  - `caixa` (esperado/contado/diferença por forma no fechamento).
- ATENÇÃO para relatórios de resultado: sangria/suprimento (chave `CAIXA_%`) são movimentação interna de numerário, não receita/despesa — devem ser excluídos; vendas canceladas não contam (ou contam líquidas via estorno — decida e justifique); custo da mercadoria vendida deve usar um custo definido (decida: custo médio atual do produto vs. custo na data — o sistema NÃO grava custo em item_venda; justifique e declare a limitação).
- Frontend: AppShell com seção "Em breve: Relatórios" (liberar), componentes `FluxoResumo`, `LancamentosLista`, badges, comprovantes em `(impressao)`, dinheiro em centavos.

## O que a especificação deve decidir e cobrir
1. Relatórios (todos por período com datas locais de SP, validação dataFim >= dataInicio e limite de período, ex.: 366 dias): vendas por período (quantidade, faturamento bruto, descontos, líquido, ticket médio, por dia, por forma de pagamento, por operador), produtos mais vendidos (quantidade e faturamento, top N), curva ABC de produtos (critério e cortes 80/15/5 justificados), giro de estoque e cobertura (dias) com a limitação do custo, margem estimada (receita − custo estimado) com aviso explícito, receitas de serviços (por serviço e por funcionário), despesas por categoria (a partir do financeiro, excluindo CAIXA_%), relatório de caixas (diferenças por operador e período) e contas pagas/vencidas no período.
2. Decisões: consultas somente leitura com agregação no banco (sem carregar entidades), índices novos só se necessários (V9 justificada ou nenhuma migration), paginação/top N, exportação CSV (decidir: server-side streaming com `text/csv; charset=UTF-8` e BOM para Excel, nomes de colunas em português, separador `;`, números com vírgula) e impressão (route group `(impressao)`), cache (decidir: nenhum ou curto), autorização (sugestão: ADMIN tudo; VENDAS só vendas/produtos sem margem nem custo; SERVICO sem acesso — decidir e justificar), gráficos no frontend (decidir: sem biblioteca externa, barras em CSS/SVG acessíveis com tabela equivalente, ou biblioteca permitida — justificar), fuso e dias sem movimento (preencher com zero).
3. API endpoint a endpoint (`/api/v1/relatorios/...`) com JSON de exemplo e erros.
4. Frontend: `/relatorios` (hub com cards), uma rota por relatório com filtros de período, tabela md+ e cartões no celular, gráfico acessível, exportar CSV e imprimir; liberar "Relatórios" no AppShell (papéis); estados vazio/erro/loading; 375 px sem rolagem horizontal.
5. Auditoria: RELATORIO_EXPORTADO (tipo, período, usuário) — sem dados sensíveis; consultas simples não auditadas (decidir).
6. Testes nominais: unitários (cálculos: ticket médio, ABC, giro, margem, exclusão de CAIXA_%, vendas canceladas, dias sem movimento), IT (cada endpoint com dados controlados e números exatos, permissões, período inválido, CSV com cabeçalho/formatação/BOM), frontend (filtros, tabela/gráfico, vazio, exportação, permissões).
7. Critérios de aceite, riscos (performance com volume, custo histórico ausente, fuso, arredondamento) e próximas etapas: 08 — migração dos dados do legado (`legacy/php` + dump MySQL antigo) com reconciliação.
8. Restrições na seção 27: NÃO editar V1–V8; NÃO alterar `legacy/`; relatórios SOMENTE leitura (nenhuma escrita em tabelas de negócio); NÃO carregar entidades para somar em memória; NÃO usar double/float para dinheiro; NÃO expor custo/margem a quem não pode; NÃO desabilitar testes/lint; NÃO usar `any`; NÃO commit/push automático.
````
