# Etapa 01 — Decisões e contratos do controle de lote e validade

> Contrato para as etapas 02 a 05. **Nada aqui está implementado.** Base: [01_MAPA_ATUAL.md](01_MAPA_ATUAL.md).
> As três decisões comerciais em aberto foram **fechadas pelo dono** e estão na seção 8 (D4, D7 e D9 já refletem a resposta).

## 1. Modelo proposto (sem duplicar o livro-razão)

`movimentacao_estoque` continua sendo o **único** livro-razão. Lote entra como uma dimensão a mais nele e num saldo materializado por lote. A chave do saldo é **produto + lote** (ver decisão D2 sobre o local).

```
produto (já existe)            + controla_lote BOOLEAN NOT NULL DEFAULT FALSE
                               + permite_sem_validade BOOLEAN NOT NULL DEFAULT FALSE
                               + dias_bloqueio_validade INT NULL CHECK (>= 0)   -- sobrescreve a margem global (NULL = usa a global)
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
| D4 | Validade | `DATE` sem horário. Vendável **até a data inclusive**; vencido a partir do dia seguinte (`Clock` de São Paulo). **Lote vencido nunca é vendido, sem exceção neste ciclo** (nem por ADMIN), principalmente medicamento. Margem de bloqueio **antes** do vencimento: global `petconnect.estoque.lotes.dias-bloqueio-antes-validade` **(padrão 0)** com **sobrescrita opcional por produto** (`produto.dias_bloqueio_validade`, nulo = usa a global) | Sem hora, sem ambiguidade de fuso. A margem muda por negócio: uma clínica pode recusar vacina com menos de 30 dias; uma loja de ração pode vender perto do vencimento | Filtro único no alocador, reaproveitado na prévia. Lote dentro da margem sai do FEFO vendável, mas continua visível nos alertas |
| D5 | Lote sem validade | **Só** se `produto.permite_sem_validade = true` (política explícita, padrão falso); vai para o fim do FEFO | Evita lote sem validade por descuido | Validação no recebimento e no ajuste |
| D6 | Alocação | **FEFO**: `validade ASC` (nulos por último), depois `created_at ASC`, depois `id ASC` (estável). Exclui vencidos e `BLOQUEADO` | Regra pedida; desempate determinístico | Índice `(produto_id, validade, id)` |
| D7 | Quantidade | **`INT`** neste ciclo, como todo o sistema hoje. Granel (kg, litro) **não entra** nos lotes; vira uma **etapa própria** de unidade de medida e quantidade decimal (D24), registrada como **pré-requisito do pacote agropecuário** | Misturar lote e decimal na mesma etapa multiplica o risco: a mudança toca PDV, compras, relatórios, migração e testes. Gramas como inteiro é gambiarra e não será usada | Colunas de quantidade novas (`lote_estoque.saldo`, `item_venda_lote.quantidade`, `item_compra_lote.quantidade`, `lote_*_saldo_*`) nascem `INT` e entram na migration única de D24 |
| D8 | Venda | O cliente do PDV continua enviando só `produtoId` e `quantidade`. O servidor aloca por FEFO e grava `item_venda_lote`; um item pode usar vários lotes | Mantém o contrato do PDV; evita lote escolhido errado no balcão | Prévia e venda usam o mesmo alocador |
| D9 | Cancelamento de venda | Para **produto com lote**, o cancelamento passa a exigir uma **escolha explícita, sem opção pré-marcada**: `RETORNAR` (volta ao **lote original**, via `item_venda_lote`) ou `DESCARTAR` (perda). Campo ausente = `400`. `DESCARTAR` grava `ESTORNO_VENDA` (+n) e `PERDA` (−n) no mesmo lote, na mesma transação: o saldo vendável não muda e o rastro fica completo. Se o lote já está vencido ou bloqueado, `RETORNAR` devolve ao lote mas ele continua não vendável. **O dinheiro é estornado nos dois casos**: a escolha só decide o destino físico. **Produto sem lote: comportamento de hoje** (devolve o total; tratar o mesmo problema lá é outra etapa) | Produto devolvido pode estar aberto, danificado ou sem cadeia de frio (medicamento, vacina): voltar à prateleira seria risco sanitário. O cancelamento é só do ADMIN e só em até 7 dias, então o custo da pergunta é mínimo | Etapa 03: `POST /vendas/{id}/cancelar` ganha `destinoEstoque` (obrigatório se houver item alocado em lote); auditoria `VENDA_CANCELADA` registra o destino e `ESTOQUE_PERDA` quando descartado |
| D10 | Estoque negativo | **Proibido** (`CHECK` no produto e no lote) | Já é regra do produto | Sem exceção neste ciclo |
| D11 | Ligar o controle | Só com **regularização do saldo existente** (seção 4). Nunca reclassifica em silêncio | Regra pedida | Operação explícita e auditada |
| D12 | Desligar o controle | **Bloqueado** enquanto o produto tiver qualquer lote (endurecido na etapa 03: o rastreio não pode ficar órfão) | Evita perder o rastreio | `409` com mensagem |
| D13 | Ligar com venda cancelável | **Bloqueado** se o produto tem venda `CONCLUIDA` dentro da janela de cancelamento (7 dias) sem alocação | Cancelar essa venda não teria lote de origem | Caso raro; mensagem orienta esperar ou cancelar antes |
| D14 | Total do produto | `produto.quantidade_estoque` **continua sendo o total** e a autoridade de relatórios, mínimo e dashboard | Não quebra `RelatorioService`, dashboard nem migração | Passa a ser mantido junto com o lote |
| D15 | Mutex | **A linha do produto travada (`FOR UPDATE`) é o mutex dos seus lotes.** Nenhuma mudança de lote acontece sem o produto travado | Preserva a ordem `produtos (por id) → caixa`; sem deadlock novo; lote não precisa de trava própria | Teste de concorrência obrigatório (etapa 03/05) |
| D16 | Custo | `produto.preco_custo` segue como média ponderada. `lote_estoque.custo_unitario` é **informativo** (custo da entrada) | Relatórios de margem e valor de estoque continuam válidos | Relatório por lote fica para etapa 04 |
| D17 | Recebimento | Para item de produto controlado, `receber` exige os lotes (soma = quantidade do item). Produto não controlado: sem mudança | Entrada sem lote não existe para controlado | `RecebimentoRequest` ganha lista opcional por item (aditivo) |
| D18 | Ajuste, perda, vencimento | Para produto controlado o ajuste exige `loteId` (ou dados de lote novo, no positivo). Novos tipos `PERDA` e `VENCIMENTO` (baixa de lote vencido) | Hoje só há `AJUSTE`; perda precisa ser distinguível | Etapa 03 |
| D19 | Transferência e reserva | **Fora do ciclo** | Não há local nem reserva | — |
| D20 | Setores | Lote **físico** (estoque) e lote **clínico** (`carteira_vacina.lote`, texto livre) são coisas diferentes; **sem FK** entre eles. Vincular aplicação de vacina a lote do estoque é evolução futura | Não confundir o que foi movimentado com o que foi aplicado | Sem mudança em `pet/` |
| D21 | Auditoria | Eventos novos: `LOTE_CRIADO`, `LOTE_BLOQUEADO`, `LOTE_DESBLOQUEADO`, `ESTOQUE_REGULARIZADO`, `ESTOQUE_PERDA`, `ESTOQUE_VENCIMENTO` (sem dado pessoal) | Padrão atual de auditoria | Etapa 03 |
| D22 | Liberação | Feature flag `petconnect.estoque.lotes.habilitado` **(padrão falso)**. Desligada, o sistema se comporta como hoje: sem telas de lote e sem **novas** ativações nem vínculos de vacina. **Produto que já controla lote segue íntegro** (vende e recebe por lote e aceita ajuste, perda e bloqueio), porque a flag não é a verdade do produto (ver relatório de homologação, achado A2) | Regra 9 do plano; permite homologar | Etapa 05 liga por instalação |
| D23 | Alerta de vencimento | **Alerta, não bloqueio**, para lotes que vencem em **30, 60 e 90 dias** (faixas configuráveis), na tela de estoque e no dashboard. Lote já vencido aparece em destaque separado | O que mais evita prejuízo é enxergar o vencimento a tempo, não travar a venda | Etapa 04 (tela e relatório de vencimentos) |
| D24 | Unidade e decimal | **Etapa futura própria**: unidade de medida por produto e quantidade decimal (`DECIMAL(12,3)`), com **uma migration que altera todas as colunas de quantidade de uma vez**, inclusive as tabelas de lote. **Pré-requisito do pacote agropecuário** (ração, sal mineral e sementes a peso). Pet shops e clínicas operam com unidades inteiras | Evita duas mudanças grandes juntas e mantém o tipo coerente em todo o sistema | Entra no plano depois da etapa 09 e antes de vender para agropecuárias |

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
- Cancelamento com `RETORNAR` restaura exatamente as linhas de `item_venda_lote` (mesmos lotes e quantidades), com o produto travado. Com `DESCARTAR`, grava o par `ESTORNO_VENDA` (+n) e `PERDA` (−n) por lote: I2 e I3 continuam valendo e o saldo vendável não aumenta.

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

## 8. Decisões comerciais fechadas pelo dono

| # | Pergunta | Decisão | Onde está |
|---|---|---|---|
| Q1 | Produtos a granel (kg, litro) neste ciclo? | **Não.** Lotes com quantidade inteira agora; etapa própria de unidade e decimal depois, como pré-requisito do pacote agropecuário | D7, D24 |
| Q2 | Dias de bloqueio antes do vencimento | **Margem global (padrão 0) com sobrescrita por produto.** Lote vencido sempre bloqueado, sem exceção. Alertas de vencimento (30/60/90 dias) na etapa 04 | D4, D23 |
| Q3 | Cancelamento com produto danificado | **Escolha explícita, sem opção pré-marcada** (`RETORNAR` ou `DESCARTAR`) para produto com lote; `DESCARTAR` grava `PERDA`. Produto sem lote não muda | D9 |

Nenhum ponto aberto bloqueia a etapa 02.
