# Etapa 01 — Decisões e contratos do controle de lote e validade

> Contrato para as etapas 02 a 05. **Nada aqui está implementado.** Base: [01_MAPA_ATUAL.md](01_MAPA_ATUAL.md).
> Decisões comerciais ainda sem resposta usam o padrão marcado com **(padrão)** e estão listadas na seção 8.

## 1. Modelo proposto (sem duplicar o livro-razão)

`movimentacao_estoque` continua sendo o **único** livro-razão. Lote entra como uma dimensão a mais nele e num saldo materializado por lote. A chave do saldo é **produto + lote** (ver decisão D2 sobre o local).

```
produto (já existe)            + controla_lote BOOLEAN NOT NULL DEFAULT FALSE
                               + permite_sem_validade BOOLEAN NOT NULL DEFAULT FALSE
lote_estoque (nova)            id, produto_id FK, codigo_lote VARCHAR(50) NOT NULL, validade DATE NULL,
                               saldo INT NOT NULL CHECK (saldo >= 0), custo_unitario DECIMAL(10,2) NULL,
                               status VARCHAR(12) NOT NULL DEFAULT 'DISPONIVEL' CHECK IN ('DISPONIVEL','BLOQUEADO'),
                               compra_id FK NULL (origem), created_at, updated_at,
                               UNIQUE (produto_id, codigo_lote), INDEX (produto_id, validade, id)
movimentacao_estoque (já)      + lote_id FK NULL, + lote_saldo_anterior INT NULL, + lote_saldo_resultante INT NULL
                               + tipos novos: PERDA, VENCIMENTO, REGULARIZACAO_LOTE (ajuste e entrada/saída passam a aceitar lote_id)
item_venda_lote (nova)         id, item_venda_id FK, lote_id FK, quantidade INT CHECK (quantidade > 0)    -- alocação FEFO da venda
item_compra_lote (nova)        id, item_compra_id FK, codigo_lote, validade DATE NULL, quantidade INT > 0 -- lotes informados no recebimento
```

Tudo é **aditivo** (colunas nulas ou com padrão, tabelas novas). A única alteração em constraint existente é recriar `chk_mov_estoque_tipo` com os tipos novos, como a V7 já fez com `ESTORNO_VENDA`.

## 2. Matriz de decisões

| # | Tema | Decisão | Justificativa | Impacto |
|---|---|---|---|---|
| D1 | Quem controla lote | **Opt-in por produto** (`controla_lote`, padrão falso). Produto sem controle segue exatamente o fluxo de hoje | Pet shops têm itens sem lote (brinquedos); mantém o PDV atual intacto | Zero mudança para produtos existentes |
| D2 | Chave do saldo | **Produto + lote**; sem `local` | Hoje não existe conceito de local (saldo único). Adicionar local exigiria cadastro, tela e transferência. Fica um caminho aditivo: `local_id` nulo + nova unicidade em migration futura | Documentado como extensão; fora deste ciclo |
| D3 | Identidade do lote | `UNIQUE (produto_id, codigo_lote)`. Receber de novo o mesmo código **soma** ao lote existente, desde que a validade seja igual; validade diferente = `409` | O código identifica a produção; validade diferente sob o mesmo código é erro de digitação | Regra no `EstoqueService` (etapa 03) |
| D4 | Validade | `DATE` sem horário. Vendável **até a data inclusive**; vencido a partir do dia seguinte. Calculada com o `Clock` de São Paulo. Margem configurável `petconnect.estoque.lotes.dias-bloqueio-antes-validade` **(padrão 0)**. **Bloqueio de vencidos no PDV por padrão** | Sem hora, sem ambiguidade de fuso; margem atende medicamento/vacina | Filtro único no alocador, reaproveitado na prévia |
| D5 | Lote sem validade | **Só** se `produto.permite_sem_validade = true` (política explícita, padrão falso); vai para o fim do FEFO | Evita lote sem validade por descuido | Validação no recebimento e no ajuste |
| D6 | Alocação | **FEFO**: `validade ASC` (nulos por último), depois `created_at ASC`, depois `id ASC` (estável). Exclui vencidos e `BLOQUEADO` | Regra pedida; desempate determinístico | Índice `(produto_id, validade, id)` |
| D7 | Quantidade | **`INT`**, como todo o sistema hoje. Decimal/unidade (granel, kg, litro) fica para migration própria **(padrão: fora do ciclo)** | Não há campo de unidade; mudar o tipo agora tocaria PDV, compras, relatórios e migração | Pergunta Q1 |
| D8 | Venda | O cliente do PDV continua enviando só `produtoId` e `quantidade`. O servidor aloca por FEFO e grava `item_venda_lote`; um item pode usar vários lotes | Mantém o contrato do PDV; evita lote escolhido errado no balcão | Prévia e venda usam o mesmo alocador |
| D9 | Cancelamento/devolução | Devolve **ao lote original** (`item_venda_lote`). Se o lote está vencido ou bloqueado, a quantidade volta ao lote mas **continua não vendável**. Opção de descarte (`PERDA`) no cancelamento fica para a etapa 03 **(padrão: retornar ao lote)** | Rastreio permanente; não recompõe saldo vendável de vencido | Pergunta Q3 |
| D10 | Estoque negativo | **Proibido** (`CHECK` no produto e no lote) | Já é regra do produto | Sem exceção neste ciclo |
| D11 | Ligar o controle | Só com **regularização do saldo existente** (seção 4). Nunca reclassifica em silêncio | Regra pedida | Operação explícita e auditada |
| D12 | Desligar o controle | **Bloqueado** enquanto algum lote tiver saldo > 0 | Evita perder o rastreio | `409` com mensagem |
| D13 | Ligar com venda cancelável | **Bloqueado** se o produto tem venda `CONCLUIDA` dentro da janela de cancelamento (7 dias) sem alocação | Cancelar essa venda não teria lote de origem | Caso raro; mensagem orienta esperar ou cancelar antes |
| D14 | Total do produto | `produto.quantidade_estoque` **continua sendo o total** e a autoridade de relatórios, mínimo e dashboard | Não quebra `RelatorioService`, dashboard nem migração | Passa a ser mantido junto com o lote |
| D15 | Mutex | **A linha do produto travada (`FOR UPDATE`) é o mutex dos seus lotes.** Nenhuma mudança de lote acontece sem o produto travado | Preserva a ordem `produtos (por id) → caixa`; sem deadlock novo; lote não precisa de trava própria | Teste de concorrência obrigatório (etapa 03/05) |
| D16 | Custo | `produto.preco_custo` segue como média ponderada. `lote_estoque.custo_unitario` é **informativo** (custo da entrada) | Relatórios de margem e valor de estoque continuam válidos | Relatório por lote fica para etapa 04 |
| D17 | Recebimento | Para item de produto controlado, `receber` exige os lotes (soma = quantidade do item). Produto não controlado: sem mudança | Entrada sem lote não existe para controlado | `RecebimentoRequest` ganha lista opcional por item (aditivo) |
| D18 | Ajuste, perda, vencimento | Para produto controlado o ajuste exige `loteId` (ou dados de lote novo, no positivo). Novos tipos `PERDA` e `VENCIMENTO` (baixa de lote vencido) | Hoje só há `AJUSTE`; perda precisa ser distinguível | Etapa 03 |
| D19 | Transferência e reserva | **Fora do ciclo** | Não há local nem reserva | — |
| D20 | Setores | Lote **físico** (estoque) e lote **clínico** (`carteira_vacina.lote`, texto livre) são coisas diferentes; **sem FK** entre eles. Vincular aplicação de vacina a lote do estoque é evolução futura | Não confundir o que foi movimentado com o que foi aplicado | Sem mudança em `pet/` |
| D21 | Auditoria | Eventos novos: `LOTE_CRIADO`, `LOTE_BLOQUEADO`, `LOTE_DESBLOQUEADO`, `ESTOQUE_REGULARIZADO`, `ESTOQUE_PERDA`, `ESTOQUE_VENCIMENTO` (sem dado pessoal) | Padrão atual de auditoria | Etapa 03 |
| D22 | Liberação | Feature flag `petconnect.estoque.lotes.habilitado` **(padrão falso)**. Desligada, o sistema se comporta como hoje | Regra 9 do plano; permite homologar | Etapa 05 liga por instalação |

## 3. Invariantes (viram testes de reconciliação)

| # | Invariante | Observação |
|---|---|---|
| I1 | `produto.quantidade_estoque` = Σ `movimentacao_estoque.quantidade` do produto, **exceto** o tipo `REGULARIZACAO_LOTE` | já vale hoje (10/10 produtos no banco de dev) |
| I2 | `lote_estoque.saldo` = Σ `movimentacao_estoque.quantidade` com `lote_id` = lote | novo; `REGULARIZACAO_LOTE` conta aqui |
| I3 | Produto com `controla_lote = true`: `quantidade_estoque` = Σ `lote_estoque.saldo` (**saldo sem lote = 0**) | garantido ao ligar o controle |
| I4 | `lote_estoque.saldo >= 0`, `produto.quantidade_estoque >= 0` | `CHECK` |
| I5 | Σ `item_venda_lote.quantidade` do item = `item_venda.quantidade` (venda de produto controlado) | alocação completa |
| I6 | Por movimento: `lote_saldo_anterior + quantidade = lote_saldo_resultante` | como já vale para o saldo total |

`REGULARIZACAO_LOTE` move saldo da faixa "sem lote" para um lote sem alterar o total: por isso entra em I2 mas não em I1.

Consultas de reconciliação (a automatizar na etapa 05):
```sql
-- I2: divergência de saldo por lote
SELECT l.id FROM lote_estoque l
 LEFT JOIN (SELECT lote_id, SUM(quantidade) s FROM movimentacao_estoque WHERE lote_id IS NOT NULL GROUP BY lote_id) m ON m.lote_id = l.id
 WHERE l.saldo <> IFNULL(m.s, 0);
-- I3: produto controlado com saldo diferente da soma dos lotes
SELECT p.id FROM produto p
 LEFT JOIN (SELECT produto_id, SUM(saldo) s FROM lote_estoque GROUP BY produto_id) l ON l.produto_id = p.id
 WHERE p.controla_lote AND p.quantidade_estoque <> IFNULL(l.s, 0);
```

## 4. Contrato do estoque legado sem lote

- Todo saldo existente é "sem lote". **Nenhum lote nem validade é inventado**.
- Ligar `controla_lote` num produto com saldo > 0 exige a operação **regularizar** (etapa 03/04): o operador informa os lotes reais (`codigo_lote`, `validade`, quantidade) cuja soma é **exatamente** o saldo atual. Soma diferente ou lote sem validade (sem `permite_sem_validade`) = `409`; nada é gravado.
- A regularização grava um movimento `REGULARIZACAO_LOTE` por lote (total do produto inalterado) e o evento `ESTOQUE_REGULARIZADO`, numa transação com o produto travado.
- Produto com saldo 0 liga o controle direto.
- A migração do legado (módulo `migracao`) continua criando produtos **sem** controle de lote; nada nela muda.
- Produtos que continuam sem controle seguem sem lote para sempre, sem efeito colateral.

## 5. Concorrência, atomicidade e idempotência

- Ordem de travas **inalterada**: `compra`/`venda` → `produto` (ordem de id) → `caixa`. Lote é lido e alterado **sob o produto travado**.
- Alocação FEFO: com o produto travado, ler os lotes vendáveis do produto, ordenar (D6), consumir em memória e gravar `lote_estoque`, `item_venda_lote` e `movimentacao_estoque` na mesma transação da venda. Falta de saldo vendável = `EstoqueInsuficienteException`, nada gravado.
- **Prévia (`/previa`) e venda usam o mesmo alocador**, para o PDV nunca prometer um lote e a venda usar outro.
- Idempotência: venda continua por `Idempotency-Key`; recebimento continua protegido pela transição de status da compra (`PEDIDO → RECEBIDO` só uma vez). Cancelamento só uma vez (`CONCLUIDA → CANCELADA`).
- Cancelamento restaura exatamente as linhas de `item_venda_lote` (mesmos lotes e quantidades), com o produto travado.

## 6. Plano de migrations (a executar na etapa 02, não agora)

| Migration | Conteúdo | Reversão / mitigação |
|---|---|---|
| `V11__lotes_estoque.sql` | colunas novas em `produto` e `movimentacao_estoque`; tabelas `lote_estoque`, `item_venda_lote`, `item_compra_lote`; recria `chk_mov_estoque_tipo` com os tipos novos; índices | **Aditiva e compatível com a versão anterior** (`ddl-auto=validate` ignora colunas não mapeadas e todas têm padrão/nulo). Rollback = voltar a imagem; o schema novo fica sem uso. Restore do backup do deploy só se houver corrupção |

Flyway Community não tem *undo*. Por isso nenhuma migration apaga ou altera dado, e o deploy já faz backup antes (`scripts/deploy.sh`).

## 7. Riscos e como tratar

| Risco | Tratamento |
|---|---|
| FEFO lento com muitos lotes | índice `(produto_id, validade, id)`; só lotes com `saldo > 0` no filtro |
| Prévia diferente da venda | alocador único + teste que compara as duas |
| Venda grande dividida em muitos lotes | `item_venda_lote` permite N lotes por item; limite de itens da venda continua 100 |
| Relatórios divergentes | D14: o total do produto não muda de significado; novos relatórios (vencimento, rastreio) só na etapa 04 |
| Tela do PDV mostrar saldo total em vez de vendável | `ProdutoRapidoResponse` ganha `quantidadeVendavel` e `controlaLote` (aditivo) |
| Divergência por caminho de escrita esquecido | só `EstoqueService` escreve (verificado); teste de arquitetura ou de varredura impede novo caminho |

## 8. Pontos abertos (nenhum bloqueia a etapa 02)

| # | Pergunta | Padrão adotado até resposta |
|---|---|---|
| Q1 | Produtos **a granel** (kg, litro) entram neste ciclo? | **Não**. Quantidades inteiras (saco, frasco, unidade) |
| Q2 | Bloquear a venda alguns dias **antes** do vencimento? Quantos? | **0 dia** (vende até a data de validade), configurável |
| Q3 | No cancelamento de venda com produto danificado, o operador escolhe descartar? | Devolve ao lote original; a opção de descarte entra na etapa 03 |
