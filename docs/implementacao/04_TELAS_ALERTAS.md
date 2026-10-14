# Etapa 04 — Telas, alertas e operação

> Torna o controle de lote usável pelo balcão e pela gestão, sobre a API da [etapa 03](03_MOVIMENTACOES_FEFO.md). **Com a flag `petconnect.estoque.lotes.habilitado` desligada (padrão) a interface é exatamente a de antes**: sem aba, sem menu, sem card, sem pergunta no cancelamento.

## 1. O que foi entregue, item a item

| Item da etapa | Entrega |
|---|---|
| 1. Produto: "Controlar por lote" e "Exigir validade" | Nova aba **Lotes** em `Produtos › [produto]` (só aparece com o controle ligado na instalação ou no produto). Produto sem controle e ADMIN: formulário com a explicação, a caixa **Exigir validade em todos os lotes** (marcada), a margem opcional de bloqueio e, havendo saldo, a **distribuição do saldo legado em lotes** com soma conferida ao vivo. Confirma com diálogo, grava `ESTOQUE_REGULARIZADO` na auditoria e não inventa lote nem validade. Saldo zero liga direto |
| 2. Compra/recebimento | `Receber compra` ganha **uma grade por item de produto controlado** (código, validade, quantidade; vários lotes por item). A soma precisa bater com a quantidade do item: a tela mostra "faltam N / sobram N / confere", bloqueia o envio, a Server Action confere de novo e a API é a última palavra. O que foi digitado **permanece na tela** se o servidor recusar. Lote já vencido é recusado no recebimento. Compra recebida lista os lotes de cada item |
| 3. Consulta de estoque | **`/estoque/lotes`** (menu "Lotes e validade"): busca por nome, código de barras ou **código do lote**, filtro de vencimento (já vencidos, "em até N dias" nas faixas configuradas, bloqueados), "incluir sem saldo", paginação de 20. **`/estoque/lotes/[id]`**: rastreabilidade (entradas por compra e fornecedor, saídas por venda, todos os movimentos com saldo do lote antes/depois) |
| 4. PDV | FEFO continua **invisível**: o balcão manda produto e quantidade. A busca rápida e o carrinho passam a usar o saldo **vendável** (`quantidadeVendavel`): lote vencido, bloqueado ou dentro da margem não conta. Sem lote vendável: "não tem lote disponível para venda (vencido ou bloqueado)". A prévia devolve a alocação que a venda faria (mesmo `AlocadorFefo`). A venda mostra de quais lotes cada item saiu |
| 5. Alertas | `GET /api/v1/estoque/alertas`, **calculado na hora** a partir dos lotes reais: vencidos com saldo, faixas **30/60/90 dias (configuráveis em `petconnect.estoque.lotes.faixas-alerta-dias`)**, bloqueados com saldo, produtos abaixo do mínimo e produtos com saldo sem controle de lote. Não existe tabela de alerta nem envio de notificação, então **nada dispara duas vezes** a cada atualização. Aparece na página de lotes e como card no painel |
| 6. Ações de lote | Bloquear/liberar (quarentena), perda/descarte e baixa de lote vencido, ajuste por lote, relatório de rastreabilidade. Botões só para ADMIN; **a API confere o papel** (testado: VENDAS e SERVICO recebem 403 em todas) |
| 7. Operação clínica | **Opção A (decidida pelo dono)**: cada vacina do catálogo pode ser **ligada a um produto de estoque** (aba Lotes do produto, só ADMIN; o produto precisa controlar lote). Aplicar a vacina, pela carteira do pet ou pela conclusão do agendamento de vacinação, **baixa uma dose do lote que vence primeiro** (FEFO, mesmas regras: vencido, bloqueado, sem saldo e dentro da margem ficam fora), grava o **lote físico** na carteira (`carteira_vacina.lote_id` e o texto do lote) e o movimento `CONSUMO_CLINICO`. Sem lote disponível, **409 e nada é gravado** (dose, serviço realizado, financeiro e estoque voltam juntos). Quando a vacina baixa estoque, a tela **não pede o lote** (ele vem do estoque; lote digitado é recusado). Vacina sem produto ligado segue como antes. Detalhes em §5 |

Regras de UX atendidas: soma dos lotes nunca diverge do recebido; vencimento em **DD/MM/AAAA** (persistido como data, "hoje" no fuso de São Paulo); erros do servidor aparecem na tela; consultas paginadas e sem N+1 (`join fetch` do produto na lista, uma consulta para os lotes de uma venda/compra); rótulos ligados aos campos (`label for`), `fieldset`/`legend` na grade e nas opções de destino, soma e erros em `role="status"`/`role="alert"`, **situação sempre em ícone + texto** (nunca só cor), layout em cartões no celular e tabela a partir de `md`.

## 2. Mudanças de API (todas aditivas)

| Rota | Papel | Observação |
|---|---|---|
| `GET /api/v1/lotes` | ADMIN, VENDAS | Paginada. Filtros: `busca`, `status`, `vencido`, `venceEmDias`, `produtoId`, `comSaldo` (padrão verdadeiro). Ordem: validade mais próxima primeiro, sem validade por último |
| `GET /api/v1/lotes/{id}/rastreio` | ADMIN, VENDAS | Entradas, saídas e movimentos (até 500). **Sem dado de cliente** (privacidade): a venda é citada pelo número |
| `GET /api/v1/estoque/alertas` | ADMIN, VENDAS | Ver §1.5 |
| `GET /api/v1/estoque/configuracao` | qualquer autenticado | `{lotesHabilitado, faixasAlertaDias}`; só decide se a tela mostra aba, menu e card |
| `GET /api/v1/produtos/{id}` | existente | + `controlaLote`, `permiteSemValidade`, `diasBloqueioValidade` |
| `GET /api/v1/produtos/busca-rapida` | existente | + `quantidadeVendavel`, `controlaLote`; `disponivel` segue o vendável |
| `GET /api/v1/vendas/{id}` | existente | cada item traz `lotes` (de onde saiu) |
| `GET /api/v1/compras/{id}` | existente | cada item traz `controlaLote`, `exigeValidade` e, se recebida, `lotes` |

## 3. Arquivos de frontend

Novos: `lib/lotes.ts` (tipos, validação da grade, schemas, situação/prazo), `components/lotes-grade.tsx`, `produto-lotes.tsx`, `lotes-lista.tsx`, `lotes-alertas.tsx`, `dashboard-validade.tsx`, páginas `app/(app)/estoque/lotes/page.tsx` e `[id]/page.tsx`. Alterados: `produtos/[id]/page.tsx` (aba), `produto-detalhes.tsx` (ajuste por lote), `compra-detalhes.tsx` (grade), `venda-detalhes.tsx` (lotes por item e destino no cancelamento), `app-shell.tsx` + `(app)/layout.tsx` (item de menu condicionado à flag), `dashboard/page.tsx`, `lib/estoque.ts`, `lib/vendas.ts`, `lib/permissoes.ts`, `actions/estoque.ts`, `actions/vendas.ts`, `pdv-leitor.tsx`.

## 4. O que **não** foi feito, e por quê

| Item | Motivo |
|---|---|
| Seleção manual de lote no PDV com permissão, motivo e auditoria de override | A etapa diz "caso haja seleção manual". A decisão D8 é **FEFO do servidor**, sem escolha no balcão; então não existe caminho de override para proteger. Se o dono quiser (ex.: devolver ao cliente o lote exato), vira decisão nova |
| Local, saldo reservado | O sistema não tem local nem reserva (D2, D19) |
| Notificação por e-mail/WhatsApp de vencimento | Fora do escopo ("alertas derivados do dado real"); o painel e a página mostram o estado atual |
| Captura de tela automatizada | Não há ferramenta de teste visual de componentes no repositório (só Playwright E2E, que roda com a flag desligada). Veja §6 |

## 5. Operação clínica (opção A, implementada)

Decisão do dono: **A**. Migration `V12__consumo_clinico_vacina.sql` (aditiva).

- **Vínculo** (`PUT/DELETE /api/v1/vacinas/{id}/produto`, só ADMIN; `GET /api/v1/vacinas/estoque` ADMIN; `GET /api/v1/produtos/{id}/vacinas` ADMIN e VENDAS): exige a flag de lotes ligada, produto ativo e **com controle de lote**. Auditoria `VACINA_PRODUTO_VINCULADO`/`DESVINCULADO`.
- **Aplicação** (`CarteiraVacinaService.registrar`, usada pela carteira e pela conclusão do agendamento): se a vacina tem produto controlado e a flag está ligada, trava o produto (mesma ordem de travas das vendas: produto, depois caixa), escolhe o primeiro lote vendável, tira 1 unidade (`CONSUMO_CLINICO`), grava o lote na carteira e audita `ESTOQUE_CONSUMO_CLINICO`.
- **Privacidade:** o movimento de estoque e a auditoria do estoque citam só o número do lançamento (`Aplicação de vacina (lançamento N)`), **sem pet nem tutor**; a rastreabilidade do lote não traz dado de cliente.
- **Corrigir a carteira não devolve a dose:** remover um lançamento que baixou estoque **não restitui** a unidade (ela foi usada); a auditoria `VACINA_PET_REMOVIDA` registra `estoqueNaoRestituido`. Devolver ao estoque seria um ajuste manual com motivo.
- **Uma dose = 1 unidade** (quantidade inteira neste ciclo, D7); frascos com várias doses ou frações dependem da etapa futura de unidade/decimal (D24).
- **Outros medicamentos** (não vacina): ainda sem fluxo de consumo; o mesmo `baixarConsumoClinico` serve de base quando houver o cadastro clínico correspondente.

## 6. Testes

| Teste | Prova |
|---|---|
| `LoteConsultaIT` (10, backend) | busca por código do lote/nome/código de barras, ordem e paginação, filtros (vencido, janela, status, saldo, combinação inválida = 400), **alertas com contagem exata por faixa** e que consultar de novo não muda nada, papéis (SERVICO 403, VENDAS lê, ações só ADMIN), rastreabilidade completa sem cliente, lotes no detalhe de venda e compra, `quantidadeVendavel` na busca rápida |
| `lotes.test.ts` (vitest) | soma, validação da grade (código, repetição, validade, vencido, quantidade), regularização com soma exata, schemas, situação/prazo, PDV com saldo vendável |
| `produto-lotes.test.tsx` | desligado na instalação; só ADMIN liga; soma divergente bloqueia sem chamar a ação; soma exata confirma e envia as linhas; validade conforme a política; lista com DD/MM/AAAA; ações por papel e por situação |
| `compra-lotes.test.tsx` | grade só no item controlado; soma divergente e lote vencido bloqueiam; vários lotes por item; compra recebida mostra os lotes |
| `venda-lotes.test.tsx` | destino sem opção pré-marcada e envio bloqueado; confirmação descreve RETORNAR e DESCARTAR; venda sem lote inalterada |
| `lotes-telas.test.tsx` | alertas, card do painel, lista, grade |
| `VacinaEstoqueIT` (8) e `AgendamentoVacinaEstoqueIT` (3), backend | vínculo (papéis, produto precisa controlar lote), catálogo informa `baixaEstoque`, aplicação tira a dose do lote que vence primeiro e grava o lote na carteira, FEFO entre lotes, sem lote disponível nada é gravado, lote digitado recusado, vacina sem vínculo como antes, remoção não devolve a dose, **duas aplicações da última dose: só uma confirma**, conclusão de agendamento baixa a dose e falha sem lote desfaz tudo |
| `vacina-estoque.test.tsx` | campo de lote some quando a vacina baixa estoque; tela de vínculo por papel |

Comandos: `cd backend && ./mvnw verify` e `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`.
