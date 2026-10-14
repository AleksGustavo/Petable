# Etapa 05 — Relatório de homologação do controle de lotes

> Resultado de **execuções reais**, feitas em 2026-10-14 sobre a `main` com a etapa 04 mesclada (`c07e01d`) mais as alterações desta etapa. Onde um item não pôde ou não se aplica, está marcado `NÃO EXECUTADO` ou `NÃO APLICÁVEL`, com o motivo. O CI do PR repete tudo na nuvem (ver o PR).
>
> **Veredito: PASS em todos os cenários de bloqueio de liberação**, com os riscos residuais da §6. O controle de lote **continua desligado por padrão**; ligar numa instalação segue o guia [`docs/runbooks/lotes-liberacao.md`](../runbooks/lotes-liberacao.md) (backup e restore testado antes, 1 a 3 produtos, reconciliação diária na primeira semana).

## 1. Ambiente e comandos

| Item | Valor |
|---|---|
| Banco | MySQL 8.4 e MongoDB 7 reais (Testcontainers nos ITs; compose na E2E) |
| Backend | Java 21, Spring Boot 4.1; `cd backend && ./mvnw verify` |
| Frontend | Next 16, vitest e Playwright (Chromium); `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` |
| E2E | stack isolada `docker compose -p lotes05 up -d --build mysql mongo mailpit api web` (portas próprias), `npm run e2e` com `E2E_BASE_URL`, `E2E_API_URL`, `E2E_ADMIN_*` e `PETCONNECT_ESTOQUE_LOTES=true` no `.env`. O workflow `e2e.yml` agora liga a flag |
| Máquina | notebook do dono, Windows 11, Docker Desktop. Os tempos de §4 são dessa máquina, com outros processos ativos |

Totais desta etapa:

| Suíte | Resultado |
|---|---|
| Backend (`./mvnw verify`) | **365 unitários + 360 ITs, 0 falhas, BUILD SUCCESS** (inclui os ITs de homologação e o de desempenho com 30 mil lotes) |
| Frontend (`npm test`, lint, typecheck, build) | **lint e typecheck limpos, 75 arquivos e 300 testes passando, build ok** |
| E2E com a flag **ligada** (12 testes: 6 fluxos de antes + 6 de lotes) | **12 PASS** |
| E2E com a flag **desligada** (6 fluxos de antes + verificação de que nenhuma tela de lote aparece) | **7 PASS** |

## 2. Cenários mínimos de bloqueio de liberação

| # | Cenário | Evidência (teste) | Resultado |
|---|---|---|---|
| B1 | Não vende lote vencido ou bloqueado, nem para ADMIN | `LoteFluxoIT.loteVencidoOuBloqueadoNaoEntraNoFefoENaoHaExcecao`, `fronteiraDaValidadeVendeAteADataInclusive`, `margemDeBloqueioDoProdutoTiraDoFefoOLoteQueVenceEmBreve`; busca rápida do PDV informa `quantidadeVendavel` (`LoteConsultaIT`); **H-01** | **PASS** |
| B2 | Não vende mais que o disponível sob concorrência | `LoteConcorrenciaIT` (5 cenários), **H-05**: 20 caixas simultâneos sobre 12 unidades em 3 lotes confirmam exatamente 12 e recusam 8; **H-06**: 20 vendas de 2 produtos em ordens opostas, sem deadlock | **PASS** |
| B3 | Não contabiliza duas vezes confirmação ou repetição | mesma `Idempotency-Key` (sequencial e em paralelo), recebimento repetido (409), cancelamento repetido (409): uma baixa, uma entrada, um lançamento (`LoteFluxoIT`, `LoteConcorrenciaIT`) | **PASS** |
| B4 | Não perde histórico nem altera saldos antigos após migrations | `MigracaoLotesV11IT`: banco na V10 com dados legados → V11 → V12; produtos, movimentos, itens e vacinas idênticos antes e depois, nenhum lote criado, escrita no formato antigo ainda válida, reexecução não migra, histórico V1–V12 sem falha | **PASS** |
| B5 | Não permite saída de produto controlado sem lote em nenhuma rota | **H-04** (ajuste sem lote, só código, passa do saldo, lote inexistente, cancelamento sem destino: todos recusados e saldo intacto; nenhum movimento do produto sem lote); `EstoqueService` recusa produto controlado; `EscritaDeSaldoSoNoEstoqueTest` (build reprova escrita de saldo fora de `produto.application`); regra **I7** da reconciliação detecta movimento sem lote (**H-09b**) | **PASS** |
| B6 | Estorno restaura o lote correto, mas impede revenda automática de item em quarentena | **H-01** (venda, lote bloqueado depois, cancelar `RETORNAR`: saldo volta ao lote, lote segue indisponível, venda recusada, volta a vender só após liberar); `retornarParaLoteQueVenceuNaoOTornaVendavel`; `DESCARTAR` grava estorno e perda e o saldo vendável não sobe | **PASS** |
| B7 | Compra com vários lotes mantém valor e total recebido | **H-02**: 3 lotes numa linha; `compra.valor_total` e `conta_pagar.valor_original` = 70,00, custo 7,00, soma dos lotes = 10; segunda compra a 9,00 leva o custo médio a 8,00 e cada lote guarda o custo da sua entrada | **PASS** |
| B8 | Falha após débito do saldo não deixa caixa ou financeiro inconsistentes | **H-03** (falha forçada no financeiro depois de debitar os lotes: lançamentos, movimentos de caixa e vendas idênticos, saldos intactos, nova tentativa gera exatamente 1 lançamento); **H-03b** (falha ao criar a conta a pagar no recebimento: compra volta a PEDIDO, nenhum lote, nenhum movimento); `LoteFalhaTransacaoIT` (venda e cancelamento com destino) | **PASS** |

## 3. Pirâmide

**Unidade:** `AlocadorFefoTest` (3 do lote A + 2 do B, exato, falta de saldo, lote zerado), `LoteEstoqueTest` (vencimento, vendável até a data inclusive, margem, bloqueio, saldo nunca negativo), `LotesPropertiesTest`, `EscritaDeSaldoSoNoEstoqueTest`, `lotes.test.ts` e demais do frontend. **NÃO APLICÁVEL:** unidades fracionárias (a quantidade é inteira neste ciclo, D7; entra na etapa futura de unidade/decimal) e compra parcial (o recebimento é total).

**Integração (banco real):** migrations em base vazia (todo IT sobe aplicando V1–V12) e legada (`MigracaoLotesV11IT`); venda distribuída em lotes, estoque consolidado = soma dos lotes após cada teste (`LoteIntegrationTest`); estorno; **inventário = ajuste por lote** (`LoteFluxoIT`, E2E); rollback por falha no financeiro (B8); idempotência (B3); locks e concorrência (B2); permissões (`LoteConsultaIT`: SERVICO 403, VENDAS só lê, ações só ADMIN); consumo clínico de vacina (`VacinaEstoqueIT`, `AgendamentoVacinaEstoqueIT`); flag desligada (`LotesDesligadosIT`). **NÃO APLICÁVEL:** transferência (o sistema não tem local nem transferência, D2/D19).

**E2E (navegador, stack real):** `frontend/e2e/lotes.spec.ts`, em série, usuário ADMIN efêmero:
1. produto controlado: liga o controle, recebe compra com **dois lotes** (soma errada bloqueia, soma certa confere), consulta de estoque por código do lote, alerta de validade (faixa de 30 dias) e card do painel;
2. **venda no PDV**: baixa FEFO (4 do lote que vence antes + 1 do outro) conferida na venda e nos saldos; **cancelar** pede o destino, devolve ao lote de origem; **auditoria** (`VENDA_REALIZADA`, `VENDA_CANCELADA`), lançamento financeiro e caixa aberto conferidos pela API;
3. **inventário**: ajuste por lote com confirmação;
4. **produto sem lote**: venda no PDV, histórico e a aba Lotes só oferecendo ligar; saldo e `controlaLote` corretos;
5. **estoque legado a regularizar**: saldo 6, soma 2 bloqueada ("faltam 4"), soma 6 liga o controle; total inalterado, lotes reais, nenhum inventado;
6. **reconciliação** sem divergência nos produtos do teste.

## 4. Não funcionais

Volume: **300 produtos × 100 lotes = 30.000 lotes** (20% zerados, 5% bloqueados, validades de 60 dias vencido a ~13 meses), inseridos consistentes (lote + movimento) e removidos no fim (`LoteDesempenhoIT`).

| Medida | Resultado | Limite do teste |
|---|---|---|
| Consulta FEFO (300 execuções, produtos sorteados) | mediana **7 ms**, p95 **19 ms**; plano `type=ref, key=idx_lote_fefo, rows=98` (usa o índice) | p95 < 150 ms |
| Trava do produto + leitura travada dos lotes + alocação FEFO de 40 unidades (4 lotes usados), revertida | **47 ms** | < 1.500 ms |
| `GET /lotes` com busca por produto (paginado) | **228 ms** | < 1.500 ms |
| `GET /lotes?venceEmDias=90` sobre 30 mil lotes | **280 ms** | < 2.500 ms |
| `GET /estoque/alertas` | **113 ms** | < 2.500 ms |
| **Reconciliação** I1, I2, I3, I5 e I7 sobre 30 mil lotes | **320 ms**, consistente | < 10.000 ms |

A reconciliação em lote é uma funcionalidade nova desta etapa (`GET /api/v1/estoque/reconciliacao`, ADMIN, somente leitura): **H-09** injeta divergências (saldo do lote, total do produto, movimento sem lote) e confirma que cada uma é detectada com esperado × encontrado, e que desfeita a adulteração volta a consistente. A carga de 30 mil lotes levou 5,6 min só para inserir (não é a operação do sistema).

**Tratamento seguro de erros (H-07):** corpos inválidos, números gigantes, texto de 600 caracteres, JSON quebrado e **texto de SQL no código do lote** não vazam `Exception`, pacote Java, SQL, `jdbc` ou `Hibernate` em nenhuma rota de lote; o texto de SQL foi gravado como dado e a tabela seguiu intacta. **Defeito achado e corrigido nesta etapa:** `GET /api/v1/lotes?size=5000` devolvia **500** (o `@Validated` de classe gerava uma exceção sem tratamento); agora devolve **400**, como os demais controllers.

**Sequência aleatória (H-08):** 60 operações (vendas, cancelamentos com RETORNAR/DESCARTAR, perdas, ajustes, bloqueios) com semente fixa; **depois de cada passo** o total do produto = soma dos lotes = soma dos movimentos, e nenhum lote fica negativo.

## 5. Achados da homologação (todos corrigidos nesta etapa)

| # | Achado | Correção |
|---|---|---|
| A1 | `GET /lotes?size=5000` respondia 500 | removido o `@Validated` de classe; 400 com mensagem do campo. Coberto por H-07 |
| A2 | Com a flag desligada **depois** de haver produto controlado, ajuste, perda, bloqueio e baixa de vencido ficavam bloqueados (409) e a aplicação de vacina parava de baixar estoque: o estoque ficaria sem como ser corrigido e divergiria | a flag passou a governar só **ligar o controle** e **vincular vacina**; produto que já controla lote segue íntegro. Coberto por `LotesDesligadosIT.produtoJaControladoSegueIntegroComAFlagDesligada`. Documentado no guia (reversão nível 1) |
| A3 | (etapa 03) leitura de lote com snapshot velho no `REPEATABLE READ` sob concorrência | leitura travada (já corrigido e testado na etapa 03; H-05 e H-06 o exercitam em escala maior) |

## 6. Riscos residuais

| Risco | Tratamento |
|---|---|
| **Voltar a imagem para versão anterior à etapa 03 com produtos controlados em uso** separaria o total dos lotes | guia: usar a reversão por flag (nível 1); se a imagem antiga for inevitável, reconciliar depois com ajustes por lote |
| **Desligar o controle de um produto** não é suportado (D12): o rastreio ficaria órfão | decisão aprovada pelo dono; procedimento só com nova decisão |
| Quantidade inteira: frasco com várias doses ou fração (kg, litro) | etapa futura de unidade/decimal (D24); 1 dose de vacina = 1 unidade |
| Remover lançamento da carteira não devolve a dose ao estoque | decisão registrada; auditoria marca `estoqueNaoRestituido`; devolver é ajuste manual |
| Medicamentos que não são vacina não têm fluxo de consumo | fora de escopo até existir o cadastro clínico correspondente |
| Tempos medidos num notebook com outros processos; produção pode ser mais lenta | limites do teste são folgados; acompanhar `http_server_requests` das rotas de lote após a liberação |
| Cobertura E2E é de Chromium desktop | layouts de celular cobertos por testes de componente, não por E2E |
| Nenhuma execução em ambiente de produção real | por regra, não há deploy sem autorização do dono; o guia exige backup, restore testado e liberação gradual |

## 7. Como repetir

```bash
cd backend  && ./mvnw verify                                   # inclui os ITs de homologação e desempenho
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
# E2E contra uma stack com PETCONNECT_ESTOQUE_LOTES=true (veja docs/runbooks/lotes-liberacao.md e .github/workflows/e2e.yml):
cd frontend && E2E_BASE_URL=… E2E_API_URL=… E2E_ADMIN_USERNAME=… E2E_ADMIN_PASSWORD=… npm run e2e
```
