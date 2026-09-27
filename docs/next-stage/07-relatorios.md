# 07 — Relatórios gerenciais

> Especificação escrita pelo Claude Code a pedido do usuário (o ChatGPT vinha devolvendo o próprio prompt). Mesmo formato das etapas 04–06; tudo verificável.

## 1. Objetivo

Entregar relatórios gerenciais **somente leitura** sobre os fatos já gravados (vendas, estoque, serviços, financeiro, caixa e contas), com filtros por período, gráficos acessíveis, exportação CSV e impressão, respeitando papéis: quem não pode ver custo e margem nunca recebe esses números.

## 2. Análise obrigatória

1. Rodar a suíte existente (baseline) antes de qualquer alteração.
2. Confirmar colunas em V1–V8: `venda` (status, data_venda, valor_total, desconto, forma_pagamento, usuario_id), `item_venda` (produto_id, quantidade, preco_unitario, subtotal gerado), `produto` (categoria_id, preco_custo = custo médio atual, quantidade_estoque, ativo), `servico_realizado` (servico_id, funcionario_id, data_servico, valor), `movimentacao_financeira` (tipo, categoria, valor, data_movimentacao, chave_origem, estorno_de_id), `caixa` (fechado_em, diferenças), `conta_pagar` (status, paga_em, data_vencimento, saldo).
3. Conferir índices existentes (`idx_venda_status_data`, `idx_mov_fin_categoria_data`, `idx_caixa_status_abertura`, `idx_conta_pagar_status_vencimento`) e o que falta para os filtros por período.
4. Ler `AppShell` (item "Relatórios" em "Em breve"), `FluxoResumo`, badges, `(impressao)`, helpers de dinheiro em centavos.

## 3. Decisões arquiteturais

### DA-01 — Somente leitura, agregação no banco
Novo módulo `relatorio`. Consultas nativas com `GROUP BY` via `NamedParameterJdbcTemplate`, sem carregar entidades e sem nenhuma escrita em tabelas de negócio. Serviços `@Transactional(readOnly = true)`.

### DA-02 — Período obrigatório e limitado
`dataInicio` e `dataFim` (datas locais de São Paulo) obrigatórios; `dataFim >= dataInicio`; no máximo **366 dias** (400 em `dataFim`). Filtro semiaberto `[dataInicio 00:00, dataFim+1 00:00)` para colunas DATETIME; `BETWEEN` para DATE.

### DA-03 — Vendas canceladas
Relatórios de faturamento contam **só vendas CONCLUIDA**. Canceladas aparecem apenas como indicador separado (quantidade e valor). Motivo: o estorno financeiro existe só a partir da V8; filtrar por status é exato para qualquer período, inclusive o histórico.

### DA-04 — Custo e margem estimados
O sistema **não grava custo por item de venda**. Margem e giro usam o **custo médio atual** do produto (`produto.preco_custo`); produto sem custo entra com custo 0 e é contado em `produtosSemCusto`. Toda resposta de margem traz `aviso` explícito dessa limitação.

### DA-05 — Curva ABC
Por faturamento líquido do produto no período (Σ subtotal dos itens de vendas concluídas), ordem decrescente. Classe pelo percentual acumulado **antes** do item: < 80% → A; < 95% → B; demais → C. Assim o item que cruza 80% ainda é A (cortes clássicos 80/15/5).

### DA-06 — Giro e cobertura
Por produto ativo: `quantidadeVendida` no período, `saldoAtual`, `valorEstoque = saldo × custo médio`. `giro = vendida / saldoAtual` (nulo se saldo 0) e `coberturaDias = saldoAtual / (vendida / dias do período)` (nulo sem venda). Aproximação com o saldo atual, declarada no `aviso`.

### DA-07 — Despesas
Categorias de despesa: `COMPRA_MERCADORIA`, `DESPESA_FUNCIONARIO`, `DESPESA_OPERACIONAL`, `OUTROS`. Valor = Σ SAIDA − Σ ENTRADA de estornos (`estorno_de_id` não nulo) da mesma categoria no período. **Sangria/suprimento (`chave_origem LIKE 'CAIXA_%'`) são excluídos**: movimento interno de numerário, não despesa.

### DA-08 — Dias sem movimento
Séries diárias (vendas por dia) preenchidas com zero para todos os dias do período, no backend.

### DA-09 — Autorização
| Relatório | ADMIN | VENDAS | SERVICO |
|---|---|---|---|
| vendas, produtos mais vendidos, curva ABC (só faturamento) | ✅ | ✅ | ❌ |
| estoque (custo/valor), margem, serviços, despesas, caixas, contas | ✅ | ❌ | ❌ |

SERVICO não tem acesso a relatórios. Regras por URL no `SecurityConfig` + `@PreAuthorize`.

### DA-10 — CSV
`GET .../{relatorio}/csv` com os mesmos filtros: `text/csv; charset=UTF-8`, **BOM** (Excel), separador `;`, decimais com vírgula, datas `dd/MM/yyyy`, cabeçalhos em português, `Content-Disposition: attachment; filename="relatorio-<tipo>-<inicio>-<fim>.csv"`. Campos com `;`, aspas ou quebra de linha entre aspas. Auditoria `RELATORIO_EXPORTADO`.

### DA-11 — Gráficos e impressão
Sem biblioteca de gráficos: barras horizontais em CSS (`aria-hidden`) sempre acompanhadas da tabela com os mesmos números. Impressão pelo route group `(impressao)` (`/relatorios/[tipo]/imprimir`), A4, sem AppShell.

### DA-12 — Sem cache
Consultas sob demanda (`cache: "no-store"`). Índices novos só onde falta cobertura (V9).

## 4. Escopo
Backend: V9 (2 índices), módulo `relatorio` (9 relatórios + CSV), segurança, auditoria de exportação. Frontend: `/relatorios` (hub), `/relatorios/[tipo]` (9 tipos), `/relatorios/[tipo]/imprimir`, route handler de CSV, AppShell e testes.

## 5. Fora de escopo
Relatórios agendados/por e-mail, BI externo, PDF gerado no servidor, comparação entre períodos, custo histórico por item, DRE contábil, filtros por cliente/pet, migração do legado (etapa 08).

## 6. Banco — V9

`V9__indices_relatorios.sql` (V1–V8 intocadas):

```sql
-- Serviços realizados por período (o índice existente começa por pet_id).
CREATE INDEX idx_servico_realizado_data ON servico_realizado (data_servico);
-- Contas pagas no período (o índice existente é status + vencimento).
CREATE INDEX idx_conta_pagar_status_pagamento ON conta_pagar (status, paga_em);
```

Vendas (`idx_venda_status_data`), itens (FK `item_venda.venda_id`), financeiro (`idx_mov_fin_categoria_data`) e caixas (`fechado_em` filtrado junto de `status`) já têm cobertura suficiente.

## 7. API — Vendas (ADMIN, VENDAS)

`GET /api/v1/relatorios/vendas?dataInicio=2026-09-01&dataFim=2026-09-30`

```json
{"dataInicio":"2026-09-01","dataFim":"2026-09-30","quantidade":42,"bruto":5120.00,"descontos":120.00,"liquido":5000.00,
 "ticketMedio":119.05,"canceladas":{"quantidade":2,"valor":80.00},
 "porDia":[{"data":"2026-09-01","quantidade":0,"liquido":0.00}],
 "porForma":[{"forma":"PIX","quantidade":20,"liquido":2500.00}],
 "porOperador":[{"operador":"joana","quantidade":30,"liquido":3600.00}]}
```
400: datas ausentes/invertidas/> 366 dias. 403: SERVICO.

## 8. API — Produtos (ADMIN, VENDAS)

- `GET /api/v1/relatorios/produtos-mais-vendidos?dataInicio&dataFim&limite=20` (1–100) → `{"itens":[{"produtoId":10,"nome":"Ração","categoria":"Ração","quantidade":35,"faturamento":5946.50,"participacao":42.10}],"total":14125.00}`
- `GET /api/v1/relatorios/curva-abc?dataInicio&dataFim` → `{"itens":[{"produtoId":10,"nome":"Ração","faturamento":5946.50,"participacao":42.10,"acumulado":42.10,"classe":"A"}],"resumo":[{"classe":"A","produtos":3,"faturamento":11200.00,"participacao":79.29}],"total":14125.00}`

## 9. API — Estoque e margem (ADMIN)

- `GET /api/v1/relatorios/estoque?dataInicio&dataFim` → `{"itens":[{"produtoId":10,"nome":"Ração","saldoAtual":12,"custoMedio":22.00,"valorEstoque":264.00,"quantidadeVendida":30,"giro":2.50,"coberturaDias":12.0}],"valorTotalEstoque":5320.00,"aviso":"..."}`
- `GET /api/v1/relatorios/margem?dataInicio&dataFim` → `{"receita":5000.00,"custoEstimado":3100.00,"margem":1900.00,"margemPercentual":38.00,"produtosSemCusto":1,"porProduto":[{"produtoId":10,"nome":"Ração","receita":2000.00,"custoEstimado":1320.00,"margem":680.00,"margemPercentual":34.00}],"aviso":"Custo estimado pelo custo médio ATUAL do produto: o sistema não grava custo por item vendido."}`

Receita da margem = Σ subtotal dos itens (antes do desconto da venda). O desconto aparece separado no relatório de vendas.

## 10. API — Serviços, despesas, caixas e contas (ADMIN)

- `GET /api/v1/relatorios/servicos` → `{"quantidade":18,"total":990.00,"porServico":[{"servico":"Banho","quantidade":10,"valor":550.00}],"porFuncionario":[{"funcionario":"Joana","quantidade":8,"valor":440.00}]}` (sem funcionário → "Sem funcionário").
- `GET /api/v1/relatorios/despesas` → `{"total":1830.00,"porCategoria":[{"categoria":"DESPESA_OPERACIONAL","valor":230.00}]}` (DA-07).
- `GET /api/v1/relatorios/caixas` → caixas FECHADOS no período: `{"quantidade":5,"diferencaTotal":-7.50,"porOperador":[{"operador":"joana","caixas":3,"diferenca":-5.00,"faltas":2,"sobras":0}],"caixas":[{"id":18,"operador":"joana","abertoEm":"...","fechadoEm":"...","diferencaTotal":-5.00}]}`
- `GET /api/v1/relatorios/contas` → `{"pagas":{"quantidade":4,"valor":2100.00},"vencidasEmAberto":{"quantidade":1,"valor":90.99},"aVencerEmAberto":{"quantidade":2,"valor":350.00},"itensPagos":[{"id":1,"descricao":"Compra #3","valor":50.00,"pagaEm":"...","formaPagamento":"PIX"}]}` (pagas por `paga_em` no período; vencidas/a vencer = situação atual).

## 11. API — CSV

`GET /api/v1/relatorios/{tipo}/csv?...` para `vendas`, `produtos-mais-vendidos`, `curva-abc`, `estoque`, `margem`, `servicos`, `despesas`, `caixas`, `contas` — mesma autorização do relatório. 200 `text/csv`; 400/403 como acima.

## 12. Exemplo de cálculo
Vendas concluídas: 100,00 (desconto 0), 50,00 (desconto 10,00) → bruto 160,00, descontos 10,00, líquido 150,00, ticket 75,00. Uma cancelada de 30,00 fica fora (canceladas: 1 / 30,00).
ABC: faturamentos 700, 200, 60, 40 (total 1000) → acumulado antes: 0% A, 70% A, 90% B, 96% C.
Margem: item 2 × 50,00 de produto com custo médio 30,00 → receita 100, custo 60, margem 40 (40%).

## 13. Autorização
Ver DA-09. URL: `/api/v1/relatorios/{vendas,produtos-mais-vendidos,curva-abc}/**` → ADMIN, VENDAS; `/api/v1/relatorios/**` → ADMIN.

## 14. Auditoria
`RELATORIO_EXPORTADO` com `tipo`, `dataInicio`, `dataFim`, `linhas`. Consultas em tela não são auditadas (leituras não são auditadas no projeto).

## 15. Frontend
- **AppShell**: item "Relatórios" liberado (seção "Gestão") para ADMIN e VENDAS.
- **`/relatorios`**: hub com um card por relatório permitido ao papel.
- **`/relatorios/[tipo]`**: filtros de período (padrão: mês corrente até hoje; atalhos "Hoje", "7 dias", "Mês", "30 dias"), indicadores, gráfico de barras em CSS + tabela (md+) / cartões (celular), botões **Exportar CSV** e **Imprimir**. Tipo sem permissão → redireciona ao hub; tipo inexistente → 404.
- **`/relatorios/[tipo]/imprimir`**: `(impressao)`, A4, com período e data de emissão.
- **CSV**: route handler `/api/relatorios/[tipo]/csv` repassa ao backend com o token do cookie e devolve o arquivo.
- Estados vazio ("Sem dados no período."), erro e loading; 375 px sem rolagem horizontal.

## 16. Dashboard
Sem card novo; o hub de relatórios fica no menu. (O dashboard já tem vendas do dia e resumo financeiro.)

## 17. Erros/UI
400: mensagem do período no topo. 403: redireciona ao hub. Demais: `Aviso` com mensagem padrão.

## 18. Regras numeradas
1. Relatórios nunca escrevem em tabelas de negócio.
2. Agregação sempre no banco.
3. Período obrigatório, `fim >= início`, máximo 366 dias.
4. Datas de negócio em horário local de São Paulo.
5. Faturamento só de vendas CONCLUIDA.
6. Canceladas aparecem só como indicador.
7. Ticket médio = líquido / quantidade (HALF_UP, 2 casas); zero vendas → 0.
8. Série diária preenchida com zero.
9. ABC por faturamento; classe pelo acumulado anterior (80/95).
10. Margem e giro com custo médio atual e aviso explícito.
11. Produto sem custo entra com 0 e é contado.
12. Despesas excluem `CAIXA_%` e descontam estornos.
13. Caixas: só fechados no período.
14. Contas pagas pelo `paga_em`.
15. VENDAS nunca recebe custo, margem, despesas, caixas ou contas.
16. SERVICO não acessa relatórios.
17. CSV com BOM, `;`, vírgula decimal e datas dd/MM/yyyy.
18. Exportação é auditada.
19. Dinheiro em BigDecimal no backend e centavos no frontend.

## 19. Testes unitários
`RelatorioCalculosTest`: `ticketMedioComZeroVendasDeveSerZero`, `ticketMedioArredondaHalfUp`, `deveClassificarCurvaAbcPeloAcumuladoAnterior`, `curvaAbcVaziaNaoFalha`, `devePreencherDiasSemMovimentoComZero`, `giroECoberturaNulosSemSaldoOuSemVenda`, `margemPercentualComReceitaZeroDeveSerZero`, `deveRejeitarPeriodoInvertido`, `deveRejeitarPeriodoMaiorQue366Dias`, `deveAceitarPeriodoDeUmDia`.
`CsvTest`: `deveComecarComBomEUsarPontoEVirgula`, `deveFormatarDecimalComVirgulaEDataBrasileira`, `deveEscaparCamposComSeparadorOuAspas`.

## 20. Testes de integração
`RelatorioControllerIT` (dados inseridos em datas isoladas no futuro para números exatos): `vendasDeveSomarSoConcluidasComCanceladasSeparadas`, `vendasDevePreencherDiasSemMovimento`, `vendasPorFormaEOperador`, `produtosMaisVendidosOrdenadoPorQuantidade`, `curvaAbcDeveClassificarPorFaturamento`, `margemDeveUsarCustoMedioAtualComAviso`, `estoqueDeveCalcularValorGiroECobertura`, `servicosPorServicoEFuncionario`, `despesasDevemExcluirSangriaESuprimentoEDescontarEstornos`, `caixasFechadosNoPeriodoPorOperador`, `contasPagasNoPeriodo`, `periodoInvalidoDeveRetornar400`, `vendasPodeVerVendasMasNaoMargem`, `servicoNaoAcessaRelatorios`, `csvDeveTerBomCabecalhoEFormatoBrasileiro`, `exportacaoDeveSerAuditada`.

## 21. Testes frontend
- `relatorio-periodo.test.ts`: período padrão, atalhos, validação de inversão e limite.
- `relatorio-graficos.test.tsx`: barras proporcionais e tabela equivalente.
- `relatorios-hub.test.tsx`: cards por papel (VENDAS não vê margem).
- `relatorio-vendas.test.tsx`: indicadores, canceladas separadas, vazio.
- `relatorio-abc.test.tsx`: classes com texto e resumo.
- `relatorio-margem.test.tsx`: aviso de custo estimado sempre visível.
- `relatorio-filtros.test.tsx`: atalhos atualizam as datas e o link de CSV.

## 22. Segurança e integridade
Somente leitura; parâmetros sempre nomeados (sem concatenar SQL); limite de período e de `limite`; autorização no backend (URL + método); CSV sem dados pessoais além de nomes de operador/funcionário; route handler repassa o token sem expô-lo.

## 23. Critérios de aceite
- [ ] V9 aplicada sobre V8 sem editar V1–V8.
- [ ] Os 9 relatórios retornam números exatos nos IT com dados controlados.
- [ ] Canceladas fora do faturamento; `CAIXA_%` fora das despesas.
- [ ] VENDAS vê só vendas/produtos/ABC; SERVICO recebe 403.
- [ ] CSV abre no Excel com acentos e decimais corretos.
- [ ] Hub e relatórios funcionam em 375 px sem rolagem horizontal; impressão sem AppShell.
- [ ] Todos os testes verdes; lint, typecheck e builds PASS.

## 24. Riscos
Volume (agregações indexadas e período ≤ 366 dias); custo histórico ausente (aviso); fuso (datas SP e intervalo semiaberto); arredondamento (BigDecimal HALF_UP; percentuais com 2 casas).

## 25. Próximas etapas
**08 — Migração dos dados do legado**: importar o dump do banco PHP antigo (`pet_e_pet_db`) para o schema novo (clientes/endereços, pets, produtos, vendas...), com reconciliação de totais, idempotência e relatório de divergências.

## 26. Ordem de implementação
Baseline → V9 → módulo relatorio (validação de período, cálculos, consultas) → CSV → controller/segurança/auditoria → testes backend → frontend (tipos, período, gráficos, hub, páginas, CSV, impressão, AppShell) → testes frontend → validações → Docker/E2E → docs → relatório.

## 27. Restrições
NÃO editar V1–V8; NÃO alterar `legacy/`; relatórios SOMENTE leitura; NÃO carregar entidades para somar em memória; NÃO usar double/float para dinheiro; NÃO expor custo/margem/despesas/caixas/contas a quem não pode; NÃO desabilitar testes/lint; NÃO usar `any`; NÃO commit/push automático.

## 28. Relatório final obrigatório
`07-relatorios-relatorio.md` com: banco; relatórios/endpoints; segurança; auditoria; frontend; testes (`unitários: X/Y, integração: X/Y, frontend: X/Y`); validações (backend build, frontend build, lint, typecheck: PASS/FAIL); divergências; pendências; handoff da etapa 08 (com o prompt).

## 29. Definição de pronto
```text
período válido → agregação no banco → números exatos (IT) → tela com gráfico + tabela → CSV (BOM ; vírgula) → impressão
papéis: ADMIN tudo · VENDAS vendas/produtos/ABC · SERVICO nada
```
