# Etapa 02 — Domínio e banco de lotes (V11)

> Implementa o contrato de [01_DECISOES_LOTES.md](01_DECISOES_LOTES.md). **Nenhum fluxo comercial usa lote ainda**: venda, compra, ajuste e cancelamento são os de antes. A flag `petconnect.estoque.lotes.habilitado` nasce **falsa** e todo produto nasce com `controla_lote = false`.

## 1. Equivalências com o texto da etapa

| O arquivo da etapa pede | O que foi feito (e por quê) |
|---|---|
| Chave por produto + local/depósito + código + validade | **`produto + codigo_lote`** (UNIQUE `uk_lote_produto_codigo`). O sistema não tem local nem depósito (D2); a validade é atributo imutável do lote, e o mesmo código com outra validade é `409` (D3). Local entra depois com `local_id` nulo + nova unicidade, de forma aditiva |
| Saldo apurado ou materializado, documentar a escolha | **Materializado** em `lote_estoque.saldo`, com reconciliação contra o livro-razão (I2/I3). Razão: o FEFO lê saldo por lote a cada venda; somar movimentos seria lento e sem trava natural. As consultas de reconciliação já estão em `LoteEstoqueRepository` e nos testes |
| `exige_validade` (default conforme regra aprovada) | **`permite_sem_validade`** (padrão `false`), o inverso do mesmo conceito: por padrão **todo lote exige validade** (D5) |
| Identificador imutável, código obrigatório, validade, datas, status, fornecedor/compra, custo, usuário | `id`; `codigo_lote` (não vazio, ≤ 50, aparado); `validade DATE`; `data_recebimento`, `created_at`, `updated_at`; `status` DISPONIVEL/BLOQUEADO; `compra_id`, `fornecedor_id`; `custo_unitario`; `usuario_id`. Código, validade, custo e origem são `updatable = false` na entidade |
| Movimentação registra lote, tipo, origem, **ID de operação**, usuário | `movimentacao_estoque` ganha `lote_id`, `lote_saldo_anterior`, `lote_saldo_resultante` e **`operacao_id`** (UUID que agrupa o par `ESTORNO_VENDA` + `PERDA`, uma regularização etc.). Origem continua em `compra_id`/`venda_id`; usuário já era obrigatório |
| Custo conforme a contabilidade existente; não sobrescrever custo histórico | `produto.preco_custo` segue como média ponderada (inalterado). `lote_estoque.custo_unitario` é o custo da **primeira** entrada e nunca é regravado; o custo de cada entrada fica em `item_compra.preco_custo_unitario` (ligado por `item_compra_lote` e `movimentacao_estoque.compra_id`) |
| Mesmo código e validade em compras distintas, custos e depósitos distintos | O serviço **reaproveita** o lote (soma por movimento, sem duplicar) e mantém o custo da primeira entrada. Depósitos: não existem (acima) |
| Checks de quantidade positiva, FKs, índices FEFO | Ver §2 |
| Saldo legado: não criar validade falsa nem zerar | A migration **não altera nenhuma linha**. A regularização explícita (§4) é a etapa 03 |
| Serviços de domínio e DTOs internos mínimos; nenhuma rota nova | `LoteEstoqueService` (identidade/política), `LotesProperties`; **nenhum controller novo** |

## 2. Schema (`V11__lotes_estoque.sql`)

| Objeto | Conteúdo |
|---|---|
| `produto` | `+ controla_lote` BOOLEAN NOT NULL DEFAULT FALSE; `+ permite_sem_validade` BOOLEAN NOT NULL DEFAULT FALSE; `+ dias_bloqueio_validade` INT NULL (`CHECK >= 0`); índice `idx_produto_controla_lote (controla_lote, ativo)` |
| `lote_estoque` (nova) | `produto_id`, `codigo_lote`, `validade DATE NULL`, `saldo INT` (`CHECK >= 0`), `custo_unitario`, `status` (`CHECK IN`), `data_recebimento`, `compra_id`, `fornecedor_id`, `usuario_id`, timestamps. UNIQUE `(produto_id, codigo_lote)` (o collation do MySQL ignora caixa: `L1` = `l1`). Índices: **`idx_lote_fefo (produto_id, status, validade, id)`**, `idx_lote_validade (validade, saldo)` para os alertas, e um por FK |
| `movimentacao_estoque` | `+ lote_id` (FK), `+ lote_saldo_anterior`, `+ lote_saldo_resultante`, `+ operacao_id CHAR(36)`. `ck_mov_estoque_lote`: lote e os dois saldos andam juntos (todos nulos ou todos preenchidos), saldos ≥ 0 e `anterior + quantidade = resultante` (**I6**). `chk_mov_estoque_tipo` recriado com `PERDA`, `VENCIMENTO`, `REGULARIZACAO_LOTE` (a V7 fez o mesmo com `ESTORNO_VENDA`). Índices `(lote_id, created_at)` e `(operacao_id)` |
| `item_venda_lote` (nova) | `item_venda_id` (ON DELETE CASCADE), `lote_id`, `quantidade > 0`; UNIQUE `(item_venda_id, lote_id)` |
| `item_compra_lote` (nova) | `item_compra_id` (ON DELETE CASCADE), `lote_id` NULL (preenchido ao efetivar o recebimento), `codigo_lote`, `validade`, `quantidade > 0`; UNIQUE `(item_compra_id, codigo_lote)` |

Domínio (Java): `LoteEstoque`, `StatusLote`, novos valores de `TipoMovimentacao`, campos de lote em `Produto` e `MovimentacaoEstoque`, `ItemVendaLote`, `ItemCompraLote`, repositórios e `LoteEstoqueService`. Regras no código: vencimento a partir do dia seguinte, vendável até a data inclusive, margem de bloqueio (do produto ou global), ordem FEFO com nulos por último e desempate por `id`.

## 3. Compatibilidade e segurança da migration

- **Aditiva**: só `ADD COLUMN` nulo/com padrão, tabelas novas, índices e a recriação de um `CHECK` (que passa a aceitar um superconjunto). Nenhum `UPDATE`, `DELETE` ou `DROP` de dado ou coluna.
- **A versão anterior segue funcionando** sobre o schema novo: `ddl-auto=validate` não reclama de colunas/tabelas que ela não mapeia, e todas têm padrão ou aceitam nulo. O `MigracaoLotesV11IT` grava no formato antigo depois da V11 para provar.
- **Reprocessar** a inicialização é seguro: o Flyway valida os checksums e não reaplica nada (`migrationsExecuted = 0`).
- Produto, saldo, movimento, venda e compra existentes ficam **bit a bit iguais** (o teste compara antes e depois).

## 4. O que **não** foi feito (de propósito)

Entrada de lote no recebimento, baixa FEFO na venda, cancelamento com retorno/descarte, ajuste/perda/vencimento, regularização do saldo legado e a auditoria dos eventos novos: **etapa 03**. Telas, alertas e relatórios: **etapa 04**. Por isso `Produto.ativarControleLote()` existe no domínio, mas nada o chama, e `LoteEstoqueService` não verifica a flag (quem expõe o recurso verifica).

## 5. Rollback operacional

Flyway Community não tem *undo*, e a migration não destrói nada, então o caminho é voltar a aplicação:

1. **Sem corrupção** (caso comum): faça o deploy da tag anterior (`./scripts/deploy.sh <tag-anterior>`). O schema da V11 fica no banco sem uso; as tabelas de lote estarão vazias porque o controle nunca foi ligado.
2. **Se a V11 falhar no meio do deploy**: o MySQL não faz *rollback* de DDL, então a tabela pode ficar parcial e o Flyway marca a V11 como falha. Restaure o backup feito pelo próprio `deploy.sh` antes da migration ([runbooks/rollback.md](../runbooks/rollback.md), [backup-restore.md](../runbooks/backup-restore.md#restore-real)) e refaça o deploy corrigido.
3. **Remover o schema de lote** (só se algum dia for preciso, e só com as tabelas vazias): `DROP TABLE item_compra_lote, item_venda_lote; ALTER TABLE movimentacao_estoque DROP FOREIGN KEY fk_mov_estoque_lote, DROP COLUMN ...; DROP TABLE lote_estoque;` e remover a linha da V11 do `flyway_schema_history`. Não faz parte do procedimento normal, **não foi executado** e exige autorização do dono.

Antes de ligar a flag em qualquer instalação (etapa 05), faça backup e rode `restore-test.sh`.

## 6. Como executar os testes desta etapa

```bash
cd backend
./mvnw -q -Dtest=LoteEstoqueTest -Dsurefire.failIfNoSpecifiedTests=false test                                  # unitários do domínio
./mvnw -q verify -Dit.test='LoteEstoqueIT,MigracaoLotesV11IT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false   # integração (Docker ligado)
./mvnw verify                                                                                                   # suíte completa
```

| Teste | O que prova |
|---|---|
| `MigracaoLotesV11IT` | banco na V10 com dados legados (produto com saldo 7, compra recebida, venda, 3 movimentos) → V11: dados idênticos, tudo desligado, I1 e I2 vazios, escrita no formato antigo ainda válida, segunda execução sem migrar, histórico V1–V11 sem falha. Banco vazio até a V11 também é exercitado por todos os `*IT` (o contexto sobe aplicando as 11) |
| `LoteEstoqueIT` | CRUD técnico; padrões (flag falsa, margem 0); reaproveitamento do lote e custo preservado; `409` por validade diferente; mesmo código em produtos diferentes; validade obrigatória salvo política; código inválido; serviço exige transação; FEFO (ordem, nulos por último, vencido/bloqueado/zerado fora, empate por id, margem do produto); constraints do banco (duplicidade, saldo negativo, status, código vazio, FK, movimento incoerente, quantidades das tabelas de alocação, margem negativa); índices e `EXPLAIN` usando `idx_lote_fefo`; rastreio lote → entrada de compra e saída de venda; detecção de divergência pelas consultas de reconciliação; regularização que não altera o total |
| `LoteEstoqueTest` | regras puras: vencimento, margem, bloqueio, saldo nunca negativo, política do produto |
