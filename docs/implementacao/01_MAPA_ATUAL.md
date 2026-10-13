# Etapa 01 — Mapa atual do estoque, compras e vendas

> Diagnóstico **somente leitura**: nenhuma linha de código de produção foi alterada. Tudo abaixo foi confirmado
> no repositório (commit base `7ee3936`, `main`) e, onde indicado, no banco de desenvolvimento.
> Prefixo dos caminhos: `J` = `backend/src/main/java/br/com/tecnodreamer/petconnect`, `T` = `backend/src/test/java/br/com/tecnodreamer/petconnect`, `F` = `frontend/src`.

## 1. Ambiente confirmado

| Item | Valor |
|---|---|
| Repositório / branch base | `AleksGustavo/PetConnect-Web`, `main` (`7ee3936`). Trabalho da etapa na branch `feat/lotes-01-diagnostico` |
| Backend | Spring Boot 4.1.1, Java 21, monólito modular (`<modulo>.{domain,application,infrastructure,web}`) |
| Frontend | Next.js 16 (BFF, JWT em cookie httpOnly), React 19, Tailwind 4, Zod |
| Bancos | MySQL 8.4 (Flyway **V1–V10**, `ddl-auto=validate`); MongoDB só para auditoria. **Próxima migration: V11** |
| Testes (linha de base) | backend 346 unitários + 267 integração (Testcontainers), frontend 249, E2E 6 fluxos; cobertura 91,8% instruções / 72,5% ramos |
| CI | `backend.yml`, `frontend.yml`, `e2e.yml`, `seguranca.yml`, `codeql.yml`, `delivery.yml`, `deploy.yml` |
| Deploy (etapa 09) | `compose.prod.yaml` + Caddy; `scripts/deploy.sh` faz backup antes e rollback automático |

## 2. Fluxo ponta a ponta

| # | Passo | Onde (classe.método) | Transação / travas | Auditoria |
|---|---|---|---|---|
| 1 | Cadastrar produto (saldo nasce 0) | `J/produto/application/ProdutoService` · `POST /api/v1/produtos` | uma transação | `PRODUTO_CRIADO` |
| 2 | Pedido de compra | `J/compra/application/CompraService.criar` · `POST /api/v1/compras` (ADMIN) | uma transação; sem efeito no estoque | `COMPRA_CRIADA` |
| 3 | Recebimento | `CompraService.receber` · `POST /api/v1/compras/{id}/receber` (ADMIN) | trava a **compra**, depois os **produtos em ordem de id** (`ProdutoRepository.travarEmOrdem`); para cada item `EstoqueService.registrarEntradaCompra` (soma saldo e recalcula custo médio); cria a conta a pagar na mesma transação (`FinanceiroService.criarContaPagar`) | `COMPRA_RECEBIDA`, `LANCAMENTO_CRIADO`/`CONTA_PAGAR_CRIADA` |
| 4 | Cancelar compra | `CompraService.cancelar` | **só status PEDIDO**; compra recebida não pode ser cancelada nem devolvida | `COMPRA_CANCELADA` |
| 5 | Venda no PDV | `J/venda/application/VendaService.registrar` · `POST /api/v1/vendas` (cabeçalho `Idempotency-Key` UUID) | ordem: idempotência → `CaixaService.exigirAberto` → produtos travados em ordem de id → confere saldo → grava venda → **trava o caixa depois dos produtos** → `EstoqueService.registrarSaidaVenda` por item → `FinanceiroService.registrarVenda`. Tudo numa transação; falta de saldo = `EstoqueInsuficienteException` (409), nada é gravado | `VENDA_REALIZADA`, `LANCAMENTO_CRIADO` (a baixa de estoque da venda **não** gera evento próprio: fica no livro-razão `movimentacao_estoque`) |
| 6 | Cancelar venda | `VendaService.cancelar` · `POST /api/v1/vendas/{id}/cancelar` (ADMIN) | só `CONCLUIDA`, até **7 dias**; exige caixa aberto de quem cancela; produtos travados em ordem; `EstoqueService.registrarEstornoVenda` devolve **a quantidade total** por item (inclusive produto desativado); estorno financeiro no caixa do operador | `VENDA_CANCELADA`, `LANCAMENTO_CRIADO` (estorno) |
| 7 | Ajuste manual | `EstoqueService.ajustar` · `POST /api/v1/produtos/{id}/ajustes-estoque` (ADMIN) | trava o produto; delta ≠ 0, motivo 5–500 caracteres, saldo final ≥ 0 | `ESTOQUE_AJUSTADO` |
| 8 | Histórico | `GET /api/v1/produtos/{id}/movimentacoes` | leitura | — |

**Regras já em vigor que a evolução precisa preservar:**
- Cada venda tem de ter produto **ativo**; o mesmo produto não pode repetir na venda (`VendaService.idsValidados`).
- Quantidade por item: venda 1–10.000, compra 1–1.000.000, ajuste ±1.000.000 (**inteiros**).
- Desconto: teto percentual configurável para VENDAS (`VendasProperties`); ADMIN sem teto.
- Idempotência de venda: `venda.chave_idempotencia` UNIQUE + `RegistroVendaIdempotente` (corrida devolve a primeira venda).
- Movimentação nunca é editada: `movimentacao_estoque` é um livro-razão imutável.

## 3. Modelo de dados atual (estoque)

Fonte: [dicionário de dados](../dicionario-de-dados.md) e migrations V1, V6, V7.

| Tabela | Pontos relevantes |
|---|---|
| `produto` | `quantidade_estoque INT` com `CHECK >= 0` (**saldo único**, sem local nem unidade), `estoque_minimo INT`, `preco_custo` (média ponderada móvel), `codigo_barras` UNIQUE, `ativo` |
| `movimentacao_estoque` | `tipo` ∈ {`ENTRADA_COMPRA`, `SAIDA_VENDA`, `AJUSTE`, `ESTORNO_VENDA`}, `quantidade` ≠ 0 (com sinal), `saldo_anterior`/`saldo_resultante` ≥ 0, `compra_id`, `venda_id`, `usuario_id`, `motivo`, `created_at` |
| `item_venda`, `item_compra` | `quantidade INT`, preço/custo unitário congelado, `subtotal` coluna gerada; **sem qualquer campo de lote** |
| `categoria_produto` | seed: Ração, Medicamento, Brinquedo, Higiene, Acessório |

## 4. Onde o saldo é escrito e lido (raio de impacto)

**Escrita:** só `J/produto/application/EstoqueService` (chamado por `CompraService`, `VendaService`, `ProdutoController.ajustar` e `MigradorEstoqueInicial`). `Produto.movimentar(delta)` é o único método que altera `quantidadeEstoque`. Não há outro caminho.

**Leitura de `quantidade_estoque`** (qualquer mudança de semântica passa por aqui):

| Quem | Para quê |
|---|---|
| `J/venda/application/VendaService` | confere saldo antes de vender |
| `J/produto/web/ProdutoRapidoResponse`, `ProdutoResponse` | PDV (`disponivel = saldo > 0`) e telas de produto |
| `ProdutoRepository` | alerta "abaixo do mínimo", resumo do dashboard |
| `J/relatorio/application/RelatorioService` | valor em estoque, giro e cobertura (`saldo × preco_custo`) |
| `J/migracao/application/MigradorProduto`, `MigradorEstoqueInicial`, `MigracaoConferenciaService` | migração do legado (cria saldo 0 e ajusta por `EstoqueService.ajustar`) |
| `F/components/pdv*.tsx`, `produto-detalhes.tsx`, `produtos-lista.tsx`, `dashboard-estoque.tsx`, `compra-form.tsx`, `F/lib/estoque.ts`, `F/lib/vendas.ts` | telas |

## 5. Endpoints e telas do fluxo

| Área | Endpoints | Telas |
|---|---|---|
| Produtos | `GET/POST/PUT /api/v1/produtos`, `PATCH .../desativar`, `PATCH .../ativar` (ADMIN), `GET .../busca-rapida`, `GET .../resumo-estoque`, `GET .../{id}/movimentacoes`, `POST .../{id}/ajustes-estoque` (ADMIN) | `F/app/(app)/produtos/{page,novo,[id]}`, `F/app/api/produtos/busca/route.ts` |
| Compras | `GET/POST /api/v1/compras`, `POST .../{id}/receber`, `POST .../{id}/cancelar` (escrita só ADMIN) | `F/app/(app)/compras/{page,nova,[id]}` |
| Vendas/PDV | `POST /api/v1/vendas/previa`, `POST /api/v1/vendas`, `GET /api/v1/vendas`, `POST .../{id}/cancelar` (ADMIN) | `F/app/(app)/pdv`, `F/app/(app)/vendas/*`, comprovante em `F/app/(impressao)/vendas/[id]/comprovante` |

DTOs-chave: `VendaRequest.Item(produtoId, quantidade)`, `CompraRequest.Item(produtoId, quantidade, precoCustoUnitario)`, `RecebimentoRequest(dataEntrega, dataVencimento)`, `AjusteEstoqueRequest(quantidade, motivo)`, `ProdutoRapidoResponse(id, nome, codigoBarras, precoVenda, quantidadeEstoque, disponivel)`. **Nenhum carrega lote.**

## 6. O que existe e o que não existe

| Tema | Situação |
|---|---|
| Cadastro de produto, categoria, fornecedor padrão, código de barras | existe |
| Unidade de medida / variações | **não existe** (sem campo `unidade`; quantidades inteiras) |
| Estoque por local, reservas, transferências | **não existe** (saldo único por produto) |
| Perdas / baixa por vencimento | **não existe** como tipo (só `AJUSTE` negativo com motivo) |
| Ajustes | existe (`AJUSTE`, só ADMIN, motivo obrigatório) |
| Custo | média ponderada móvel no recebimento (`CustoMedioService`); venda e relatórios usam o custo médio **atual** |
| Devolução de compra ao fornecedor / devolução parcial de venda | **não existe** (cancelamento de venda é total; compra recebida não cancela) |
| Lote de estoque | **não existe**. O único "lote" é texto livre (`carteira_vacina.lote`, `VARCHAR(50)`) |
| Multiempresa / organização / `tenant` | **não existe** (busca por `tenant`, `organização`, `multiempresa`: 0 ocorrências) |
| Feature flags de módulo | **não existe** para módulos. Só flags técnicas: `petconnect.financeiro.backfill`, `petconnect.migracao.legado` |
| RBAC | papéis `ADMIN`, `VENDAS`, `SERVICO`; regras por URL em `SecurityConfig` + `@PreAuthorize` nos controllers |
| Leitor de código de barras | existe no PDV (`F/components/pdv-leitor.tsx`: Enter/Tab; busca por `codigo_barras`). O GTIN identifica o **produto**, não o lote |
| Importação de XML / NF-e | **não existe** (0 ocorrências de `NF-e`/`NotaFiscal`) |
| Offline / sincronização | **não existe** (carrinho em `sessionStorage`; sem service worker) |
| Lotes de produtos existentes | **não há nenhum**; todo o estoque atual é "sem lote" |
| Medicamentos / vacinas | `categoria_produto` "Medicamento" existe; o catálogo de `vacina` é separado e **não baixa estoque** |

## 7. Reconciliação do saldo (evidência)

Consulta no banco de desenvolvimento (V10 aplicada), 10 produtos:

| Verificação | Resultado |
|---|---|
| `produto.quantidade_estoque` = soma de `movimentacao_estoque.quantidade` do produto | **10 de 10 conferem, 0 divergentes** |
| `saldo_anterior + quantidade = saldo_resultante` em cada movimento | **0 encadeamentos quebrados** |
| Sinais: `ENTRADA_COMPRA` (+), `SAIDA_VENDA` (−), `ESTORNO_VENDA` (+), `AJUSTE` (±) | coerentes |

Isso vale por construção: o produto nasce com saldo 0 e **todo** movimento passa por `EstoqueService`. A reconciliação por lote (etapa 02) pode seguir o mesmo método.

## 8. Testes existentes e comandos

| O quê | Arquivos | Como rodar |
|---|---|---|
| Estoque (unit) | `T/produto/application/EstoqueServiceTest` (11), `CustoMedioServiceTest`, `ProdutoServiceTest` | `cd backend && ./mvnw -B test -Dtest=EstoqueServiceTest` |
| Estoque (integração) | `T/produto/web/EstoqueControllerIT` (8), `ProdutoControllerIT`, base `EstoqueIntegrationTest` | `./mvnw -B verify -Dtest=NaoExiste -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=EstoqueControllerIT -Dfailsafe.failIfNoSpecifiedTests=false` |
| Compras | `T/compra/application/CompraServiceTest`, `T/compra/web/CompraControllerIT` (16) | idem, com `-Dit.test=CompraControllerIT` |
| Vendas | `T/venda/application/VendaServiceTest` (25), `T/venda/web/VendaControllerIT` (16) | idem |
| **Concorrência** | `T/venda/web/VendaConcorrenciaIT`: duas vendas do último item (só uma passa), venda e recebimento simultâneos (sem atualização perdida), mesma chave em paralelo (uma venda) | `-Dit.test=VendaConcorrenciaIT` |
| Frontend | `F/components/{pdv-*,compra-*,produto-*,produtos-list,dashboard-estoque}.test.tsx`, `F/lib/{carrinho,compra-schema,ajuste-estoque-schema,produto-schema,venda-schema}.test.ts` | `cd frontend && npx vitest run <arquivo>`; completo: `npm run lint && npm run typecheck && npm test && npm run build` |
| E2E | `frontend/e2e/fluxos.spec.ts` (PDV com caixa aberto etc.) | `npm run e2e` (com a stack de dev no ar; ver `docs/producao.md`) |
| Suíte completa | — | `cd backend && ./mvnw -B clean verify` |

## 9. Riscos de regressão para a mudança de lotes

1. **PDV e estoque disponível:** `disponivel` e a checagem de saldo usam o total; com lote, o que vale é o saldo **vendável** (não vencido, não bloqueado).
2. **Ordem de travas:** hoje `produtos (por id) → caixa`. Lote não pode inverter nem criar uma terceira ordem.
3. **Cancelamento de venda antiga** (sem alocação por lote) de produto que depois passou a controlar lote.
4. **Relatórios de estoque** (`RelatorioService`) leem o total do produto e o custo médio: precisam continuar corretos.
5. **Migração do legado** (`MigradorEstoqueInicial`) escreve saldo sem lote: tem de continuar funcionando (produtos nascem sem controle).
6. **Rollback de aplicação:** com `ddl-auto=validate`, colunas/tabelas novas e **aditivas** não quebram a versão anterior.
7. **Frontend:** o PDV só envia `produtoId` e `quantidade`; a alocação por lote deve ficar no servidor (sem mudar o contrato do PDV).
