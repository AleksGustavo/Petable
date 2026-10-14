# Etapa 03 — Movimentações, FEFO e consistência transacional

> Liga o núcleo da [etapa 02](02_DOMINIO_E_BANCO.md) aos fluxos reais. **Produto sem `controla_lote` segue exatamente o fluxo de antes.** A flag `petconnect.estoque.lotes.habilitado` continua falsa por padrão: desligada, não é possível ligar o controle nem movimentar lote (409).

## 1. Equivalências com o texto da etapa

| O arquivo da etapa pede | O que foi feito |
|---|---|
| FEFO "por **local**" | Não existe local no sistema (D2): FEFO por **produto**. Entrada mais antiga e `id` como desempate (o `id` cresce junto com `created_at`) |
| Entrada com "quantidade **e unidade**"; "unidade fracionária sem erro de arredondamento" | Quantidade `INT` neste ciclo (D7). Unidade e decimal são a **etapa 10 futura** (D24), pré-requisito do pacote agropecuário. Nada de float foi introduzido |
| Recebimento "parcial/total" | O recebimento é **total** (`PEDIDO → RECEBIDO`, uma vez). Parcial não existe hoje e não foi criado; os lotes de um item podem ser vários, a soma é a quantidade do item |
| "Venda **parcialmente** cancelada" | O cancelamento de venda é **total** (regra existente, até 7 dias). Item a item não existe e não foi criado; o cancelamento recompõe **cada linha no seu lote** |
| Transferências, consumo clínico, solicitações internas | **Não existem** no sistema (verificado na etapa 01). A garantia de que nenhum caminho ignore lote é estrutural (§4) |
| Reserva | Não existe reserva (D19); a trava do produto é o controle de concorrência |
| "bloqueio otimista **ou** locks de linha" | **Lock de linha**: o produto travado (`FOR UPDATE`) é o mutex dos seus lotes (D15), e os lotes são lidos com leitura travada (§3) |
| Idempotência para confirmação, recebimento e estorno | Venda: `Idempotency-Key` (existente). Recebimento e cancelamento: a transição de status (`PEDIDO → RECEBIDO`, `CONCLUIDA → CANCELADA`) só acontece uma vez; a repetição devolve `409` e **não** duplica movimento nem financeiro (testado) |
| Lote vencido "hoje": vende até a véspera ou até a data? | **Até a data, inclusive** (D4). Vencido a partir do dia seguinte. Testes de fronteira nos dois lados |

## 2. API (todas aditivas)

| Rota | Quem | O que faz |
|---|---|---|
| `GET /api/v1/produtos/{id}/lotes` | ADMIN, VENDAS | Lotes do produto: saldo, validade, status, `vencido`, `vendavel`, origem |
| `POST /api/v1/produtos/{id}/controle-lote` | ADMIN | Liga o controle e **regulariza** o saldo: `{permiteSemValidade, diasBloqueioValidade, lotes:[{codigoLote, validade, quantidade}]}`. Soma exata = saldo atual, senão `409` e nada é gravado. Grava `REGULARIZACAO_LOTE` por lote (total inalterado) e `ESTOQUE_REGULARIZADO` |
| `POST /api/v1/produtos/{id}/controle-lote/desativar` | ADMIN | Só se o produto **nunca teve lote** (D12, endurecido: qualquer lote, mesmo zerado, bloqueia) |
| `POST /api/v1/compras/{id}/receber` | ADMIN | Campo novo `lotes:[{produtoId, lotes:[...]}]`. Produto controlado exige os lotes (soma = quantidade do item); produto sem controle com lotes = `400`. Lote já vencido não é recebido |
| `POST /api/v1/vendas` | ADMIN, VENDAS | **Mesmo contrato.** O servidor aloca por FEFO e grava `item_venda_lote` |
| `POST /api/v1/vendas/previa` | ADMIN, VENDAS | `disponivel` passa a ser o saldo **vendável**; cada item traz `lotes` (a alocação que a venda faria agora) |
| `POST /api/v1/vendas/{id}/cancelar` | ADMIN | Campo novo `destinoEstoque` (`RETORNAR`/`DESCARTAR`), **obrigatório e sem padrão** se algum item saiu de lote (D9); ausente = `400` |
| `POST /api/v1/produtos/{id}/ajustes-estoque` | ADMIN | Campos novos `loteId` ou `codigoLote`+`validade`. Produto controlado exige lote; negativo nunca passa do saldo do lote |
| `POST /api/v1/produtos/{id}/lotes/{loteId}/perdas` | ADMIN | `PERDA` com motivo |
| `POST /api/v1/produtos/{id}/lotes/{loteId}/vencimentos` | ADMIN | `VENCIMENTO`, só de lote vencido |
| `POST /api/v1/produtos/{id}/lotes/{loteId}/bloquear` / `desbloquear` | ADMIN | Quarentena/recolhimento; bloqueado nunca entra no FEFO |
| `GET /api/v1/produtos/{id}/movimentacoes` | existente | Resposta ganha `loteId`, `codigoLote`, `loteSaldoAnterior`, `loteSaldoResultante`, `operacaoId` |

Erros: `400` dado inválido (lote ausente, soma errada, validade ausente sem política, destino ausente), `409` regra de negócio (saldo insuficiente/vendável, soma da regularização diferente do saldo, validade diferente do mesmo código, flag desligada, lote fora da validade/ainda válido), `404` lote de outro produto.

## 3. Concorrência e atomicidade

- Ordem de travas **inalterada**: compra/venda → produtos (por `id`) → caixa. Lote é lido e alterado **sob o produto travado**; não há trava própria nem deadlock novo.
- **Achado do teste de concorrência (corrigido):** o MySQL usa `REPEATABLE READ`. Uma leitura comum de lote feita *depois* de esperar a trava do produto ainda enxerga o *snapshot* do início da transação, isto é, o saldo que o outro caixa já consumiu. Resultado: o segundo caixa "via" saldo, tentava baixar e estourava `Saldo negativo não é permitido` (500) em vez de `409`. **Regra adotada:** em todo caminho que escreve, os lotes são lidos com **leitura travada** (`FOR UPDATE`, que lê o último commit): `travarVendaveisEmOrdemFefo`, `travar`, `travarPorProdutoECodigo`, `travarDoProduto` em `LoteEstoqueRepository`. A leitura comum fica só na prévia.
- Falta de saldo vendável: `EstoqueInsuficienteException` (`409`, com `disponivel` e `solicitado`) **antes** de gravar qualquer coisa; a transação inteira (lote, produto, alocação, movimentos, venda, caixa, financeiro) é desfeita em qualquer falha.
- Prévia e venda usam o mesmo `AlocadorFefo`.

## 4. Nenhum caminho ignora o lote

1. `EstoqueService` (fluxo antigo) **recusa** produto controlado: `ajustar` → `400`, `registrarEntradaCompra`/`SaidaVenda`/`EstornoVenda` → `409`.
2. Todo movimento de produto controlado passa por `EstoqueLoteService`.
3. `EscritaDeSaldoSoNoEstoqueTest` varre `src/main/java` e **reprova o build** se qualquer arquivo fora de `produto.application` (e `produto.domain`) chamar `movimentar(...)` ou atribuir `quantidadeEstoque`. Um caminho novo de saída (transferência, consumo clínico, integração) cai nessa regra.
4. A migração do legado (`migracao`) só cria produtos **sem** controle de lote e já usa o `EstoqueService`.

## 5. Auditoria

Eventos novos: `LOTE_CRIADO`, `LOTE_BLOQUEADO`, `LOTE_DESBLOQUEADO`, `ESTOQUE_REGULARIZADO`, `ESTOQUE_PERDA`, `ESTOQUE_VENCIMENTO`, `CONTROLE_LOTE_DESATIVADO`. `ESTOQUE_AJUSTADO` ganha `loteId`/`codigoLote`/saldos do lote. `VENDA_REALIZADA` lista as alocações (`produtoId:lotexquantidade`); `VENDA_CANCELADA` registra `destinoEstoque`. Cada movimento tem usuário, tipo, `compra_id`/`venda_id`, saldos do produto e do lote, e `operacao_id` (agrupa `ESTORNO_VENDA` + `PERDA` do descarte e a regularização).

## 6. Decisões tomadas e riscos

| # | Decisão | Motivo |
|---|---|---|
| E1 | Desligar o controle é bloqueado com **qualquer** lote (não só com saldo) | O texto da etapa pede "sem procedimento aprovado"; o procedimento não existe neste ciclo |
| E2 | Lote **já vencido não pode ser recebido** (`400`); na regularização do saldo legado pode (é a realidade do estoque) e depois sai por `VENCIMENTO` | Receber vencido é erro de digitação; o legado precisa refletir o que existe |
| E3 | `RETORNAR` em lote vencido devolve ao lote, que continua não vendável | D9 |
| E4 | Perda, vencimento, ajuste e bloqueio exigem a flag ligada | Coerente com D22 |
| E5 | Não foi alterado o frontend | A UI é a etapa 04; a tela de histórico mostra o tipo bruto para os tipos novos (comportamento já existente para `ESTORNO_VENDA`) |

Riscos conhecidos: `FOR UPDATE` em vendas muito grandes mantém as linhas do produto até o commit (mesmo comportamento do produto hoje); o PDV ainda não mostra o lote nem pergunta o destino no cancelamento (etapa 04) — **não ligue a flag em produção antes da etapa 04/05**.

## 7. Testes

| Teste | Prova |
|---|---|
| `AlocadorFefoTest` (unit) | 3 do lote A + 2 do B para vender 5; exato; falta de saldo; lote zerado; quantidade inválida |
| `LotesPropertiesTest` (unit) | flag falsa e margem 0 sem configuração e no `application.yml` |
| `EscritaDeSaldoSoNoEstoqueTest` (unit) | regra de arquitetura do §4 |
| `LoteFluxoIT` (27) | regularização (soma exata, validade, saldo zero, D13, papéis, desligar), recebimento (lotes obrigatórios, soma, duplicidade, vencido, repetição, mesmo código em outra compra, custo da 1ª entrada e média do produto), FEFO (3+2, prévia = venda, vencido/bloqueado/zerado fora, fronteira da validade, margem do produto, idempotência), cancelamento (sem destino = 400, `RETORNAR`, `DESCARTAR`, dinheiro estornado, produto sem lote, lote vencido), ajuste/perda/vencimento, lote de outro produto, guardas do `EstoqueService`, histórico com lote, venda mista |
| `LoteConcorrenciaIT` (5) | dois caixas vendem o último item (uma confirma); 8 vendas simultâneas sobre 5 unidades (exatamente 5); mesma chave em paralelo (uma venda, uma baixa); venda × recebimento; venda × cancelamento |
| `LoteFalhaTransacaoIT` (2) | falha forçada no financeiro após a alocação (venda) e no estorno (cancelamento com destino): nada fica gravado e a repetição passa |
| `LotesDesligadosIT` (2) | flag desligada: não liga o controle (409); venda e cancelamento sem lote como antes |
| Reconciliação | `LoteIntegrationTest` confere, após **cada** teste, I1 e I2 e `divergenciasDoProdutoControlado` (I3) dos produtos criados |

```bash
cd backend
./mvnw -q verify -Dit.test='LoteFluxoIT,LoteConcorrenciaIT,LoteFalhaTransacaoIT,LotesDesligadosIT' -Dtest='AlocadorFefoTest,LotesPropertiesTest,EscritaDeSaldoSoNoEstoqueTest' -Dsurefire.failIfNoSpecifiedTests=false
./mvnw verify    # suíte completa
```
