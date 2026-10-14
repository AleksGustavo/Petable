# Liberação do controle de lote e validade (por instalação)

O controle de lote vem **desligado** (`PETCONNECT_ESTOQUE_LOTES=false`) e é ligado **uma instalação de cada vez**, depois da homologação. Contrato e decisões: [`docs/implementacao/01_DECISOES_LOTES.md`](../implementacao/01_DECISOES_LOTES.md). Resultado da homologação: [`05_RELATORIO_HOMOLOGACAO.md`](../implementacao/05_RELATORIO_HOMOLOGACAO.md). **Não ligue em produção enquanto o relatório tiver algum item crítico diferente de PASS.**

Tudo parte de `/opt/petconnect`, como nos outros runbooks. Comandos de banco: `docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot petconnect_web'`.

## 1. Antes de qualquer coisa: backup e prova de restauração

1. `./scripts/backup.sh` (o `deploy.sh` já faz um antes de aplicar migration, mas faça o seu na véspera da liberação).
2. `./scripts/restore-test.sh` e confirme todos os itens `OK`. **Sem restore testado não se liga lote**: é o único caminho de volta se algo corromper dados (§5, nível 3).
3. Anote a versão atual: `cat .ultimo-deploy` e `docker compose exec mysql … -e "select version, description from flyway_schema_history order by installed_rank desc limit 3"`.

## 2. Migração controlada (as migrations vão com a versão, com a flag desligada)

As migrations de lote são **aditivas** e a versão anterior continua funcionando sobre elas (o Hibernate ignora o que não mapeia):

| Migration | O que faz | Altera dado existente? |
|---|---|---|
| `V11__lotes_estoque.sql` | colunas em `produto` e `movimentacao_estoque`; tabelas `lote_estoque`, `item_venda_lote`, `item_compra_lote`; índices; amplia o CHECK de tipo | **não** |
| `V12__consumo_clinico_vacina.sql` | `vacina.produto_id`, `carteira_vacina.lote_id`; amplia o CHECK de tipo (`CONSUMO_CLINICO`) | **não** |

Procedimento (deploy normal, [deploy.md](deploy.md), **flag desligada**):

1. `./scripts/deploy.sh <tag>`; ele faz backup, aplica as migrations e roda o smoke.
2. Confirme as versões 11 e 12 e que nada mudou:
   ```sql
   select version, success from flyway_schema_history where version in ('11','12');   -- success = 1
   select count(*) from lote_estoque;                                                   -- 0
   select count(*) from produto where controla_lote = true;                             -- 0
   select count(*) from movimentacao_estoque where lote_id is not null;                 -- 0
   ```
3. Confirme que a API informa a flag desligada: `GET /api/v1/estoque/configuracao` → `{"lotesHabilitado": false, …}`. A interface deve estar **idêntica à de antes** (sem aba Lotes, sem menu "Lotes e validade", sem pergunta no cancelamento).
4. Deixe rodar um dia nesse estado. Se algo estranho aparecer, o problema não é de lote (a flag está desligada): siga [rollback.md](rollback.md).

Se a migration **falhar no meio** (o MySQL não desfaz DDL), o Flyway marca a versão como falha: **não** edite a tabela à mão. Restaure o backup do deploy ([backup-restore.md](backup-restore.md#restore-real)) e publique a correção.

## 3. Ligar o controle (uma instalação, em janela de pouco movimento)

1. Defina no `.env` do servidor: `PETCONNECT_ESTOQUE_LOTES=true` (opcional: `PETCONNECT_ESTOQUE_LOTES_DIAS_BLOQUEIO=<dias>` e `PETCONNECT_ESTOQUE_LOTES_FAIXAS_ALERTA=30,60,90`). Reinicie só a API: `docker compose -f compose.prod.yaml up -d api`.
2. `GET /api/v1/estoque/configuracao` → `lotesHabilitado: true`. O menu "Lotes e validade" aparece para ADMIN e VENDAS.
3. **Comece por poucos produtos** (1 a 3: o que mais vence, ração e medicamento). Para cada um, **conte o estoque físico antes**, ajuste o saldo se preciso (ajuste normal, com motivo) e então use a aba **Lotes › Controlar por lote** informando os lotes que estão de fato na prateleira (soma exata do saldo; nenhum lote é inventado).
4. Faça uma compra e uma venda de teste desses produtos, conferindo: lote entrou com validade, a venda baixou o lote que vence antes, o cancelamento pediu o destino.
5. **Reconcilie** logo depois (§4) e ao fim de cada dia da primeira semana. Só amplie para mais produtos com a reconciliação limpa.
6. Treine quem recebe mercadoria (código e validade do lote na embalagem) e quem cancela venda (voltar ao estoque ou descartar).

## 4. Monitoramento

| O que | Como | Esperado | Se falhar |
|---|---|---|---|
| **Reconciliação do estoque** (I1, I2, I3, I5, I7) | `GET /api/v1/estoque/reconciliacao` (ADMIN) ou o SQL abaixo | `consistente: true`, lista de divergências vazia | **Pare de ampliar.** Veja `regra`, `produtoId`, `loteId`, esperado × encontrado. Corrija **só com ajuste auditado por lote** (aba Estoque › Ajustar), nunca editando o histórico. Abra incidente se a causa não for contagem física |
| Alertas de validade | painel e `/estoque/lotes` (vencidos, 30/60/90 dias, bloqueados) | vencidos com saldo = 0 (baixe com "Baixar vencido") | lote vencido com saldo parado é prejuízo e risco sanitário |
| Erros 5xx de lote | `monitorar.sh` (5xx por 5 min) e métricas `http_server_requests` das rotas `/api/v1/lotes`, `/api/v1/vendas`, `/api/v1/compras` | nenhum | `docker compose logs api` com o `X-Request-Id` da resposta; **não** reinicie antes de coletar o log |
| Vendas recusadas por falta de lote vendável | `409` com `disponivel` menor que o saldo total do produto | ocasional (lote vencido/bloqueado) | normal; se frequente, falta reposição ou baixa de lote vencido |
| Auditoria | `GET /api/v1/auditoria?entidade=produto&entidadeId=<id>` | `ESTOQUE_REGULARIZADO`, `LOTE_CRIADO`, `ESTOQUE_PERDA`… | — |

SQL de reconciliação (somente leitura; use se a API estiver fora). **Cada consulta deve devolver zero linhas**:

```sql
-- I1: total do produto = soma dos movimentos (exceto regularização)
select p.id, p.quantidade_estoque, coalesce(sum(m.quantidade),0) s
  from produto p left join movimentacao_estoque m on m.produto_id = p.id and m.tipo <> 'REGULARIZACAO_LOTE'
 group by p.id, p.quantidade_estoque having p.quantidade_estoque <> s;
-- I2: saldo do lote = soma dos movimentos do lote
select l.id, l.saldo, coalesce(sum(m.quantidade),0) s from lote_estoque l left join movimentacao_estoque m on m.lote_id = l.id
 group by l.id, l.saldo having l.saldo <> s;
-- I3: produto controlado: total = soma dos lotes
select p.id, p.quantidade_estoque, coalesce(sum(l.saldo),0) s from produto p left join lote_estoque l on l.produto_id = p.id
 where p.controla_lote = true group by p.id, p.quantidade_estoque having p.quantidade_estoque <> s;
-- I7: movimento sem lote depois de o produto ter lote (caminho que ignorou o lote)
select m.id, m.produto_id from movimentacao_estoque m join produto p on p.id = m.produto_id
 where m.lote_id is null and p.controla_lote = true
   and exists (select 1 from movimentacao_estoque x where x.produto_id = m.produto_id and x.lote_id is not null and x.id < m.id);
```

## 5. Reversão **operacional** (nunca apagar tabelas nem movimentos)

Escolha o **menor** nível que resolve. Nenhum nível apaga `lote_estoque`, `movimentacao_estoque`, `item_venda_lote`, `item_compra_lote` ou qualquer histórico.

| Nível | Quando | O que fazer | O que acontece |
|---|---|---|---|
| **0. Parar de ampliar** | divergência na reconciliação ou dúvida | não ligue o controle em mais produtos; corrija com ajuste auditado | nada muda para quem já usa |
| **1. Desligar a flag** | a equipe não está pronta, ou quer esconder as telas | `PETCONNECT_ESTOQUE_LOTES=false` no `.env` e reiniciar a API | somem aba, menu, card e perguntas; **novas ativações e novos vínculos de vacina ficam bloqueados (409)**. **Produto que já controla lote continua íntegro**: vende pelo lote que vence antes, recebe com lote, e aceita ajuste, perda e bloqueio (o estoque não pode ficar sem correção). Nada é apagado; religar volta tudo |
| **2. Voltar a imagem** | a **versão** nova tem defeito (não o conceito de lote) | `./scripts/deploy.sh <tag-anterior>` ([rollback.md](rollback.md)) | o schema de lote **fica** no banco, sem uso. **Atenção:** uma versão **anterior à etapa 03** não conhece lote e venderia pelo saldo total do produto, o que **separa o total dos lotes** (I2/I3). Se já existe produto controlado em uso, prefira o nível 1; se precisar mesmo da imagem antiga, reconcilie depois com ajustes por lote |
| **3. Restaurar o backup** | só se houver **corrupção** de dados | [backup-restore.md](backup-restore.md#restore-real), depois o deploy da versão correta | perde o que foi feito depois do backup: relance as operações. Registre no incidente |

**Não suportado (de propósito):** voltar um produto de "controla lote" para "não controla" (D12): o rastreio dos lotes ficaria órfão e o procedimento aprovado não existe. **Nunca** rode `DELETE`, `TRUNCATE`, `DROP` ou `UPDATE` em movimentos e lotes para "desfazer": é o que quebra a rastreabilidade e o saldo.

## 6. Critério de aceitação da liberação

- [ ] Backup do dia e `restore-test.sh` com todos os itens OK.
- [ ] Migrations 11 e 12 aplicadas com a flag desligada, sem alteração de dado, e um dia de operação normal.
- [ ] Relatório de homologação sem item crítico diferente de PASS.
- [ ] 1 a 3 produtos regularizados com contagem física, reconciliação limpa.
- [ ] Equipe treinada (recebimento com lote, cancelamento com destino, baixa de vencido).
- [ ] Alguém responsável por olhar a reconciliação e os vencidos todos os dias da primeira semana.
