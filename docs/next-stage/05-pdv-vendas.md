# 05 — PDV e Vendas

> Etapa executada pelo Claude Code direto no repositório. Tudo aqui é verificável; nada fica "a definir".

## 1. Objetivo

Colocar no ar a **frente de caixa (PDV)** e o **módulo de vendas**:

- vender produtos com leitor de código de barras;
- ter preço, desconto e total sempre calculados no backend;
- dar baixa no estoque pelo `EstoqueService` (lock + histórico `SAIDA_VENDA`);
- proteger a venda contra duplo envio (idempotência);
- permitir cancelamento com estorno rastreável;
- oferecer listagem, detalhe, comprovante imprimível e o resumo de vendas do dia no painel.

## 2. Análise obrigatória (antes de codar)

Ler e confirmar:

1. `V1__schema_inicial.sql`: tabelas `venda`, `item_venda` e `movimentacao_financeira`.
   - `venda` **não tem** status, operador, cancelamento nem chave de idempotência.
   - `forma_pagamento` tem `CHECK` em `DINHEIRO`, `CARTAO_CREDITO`, `CARTAO_DEBITO`, `PIX` e `TRANSFERENCIA`.
   - `item_venda.subtotal` é coluna gerada; `produto_id` é `NOT NULL`.
2. `V6__movimentacao_estoque_indices.sql`: a constraint `chk_mov_estoque_tipo` só aceita `ENTRADA_COMPRA`, `SAIDA_VENDA` e `AJUSTE`. Já existe a FK `fk_mov_estoque_venda`.
3. `produto/application/EstoqueService.java`: `ajustar`, `registrarEntradaCompra` e o padrão de lock. Ler também `ProdutoRepository.travarEmOrdem`.
4. `compra/application/CompraService.java`: padrão de transação atômica com vários produtos travados em ordem de id.
5. `shared/security/UsuarioAtual`, `SecurityConfig`, `GlobalExceptionHandler` (ProblemDetail) e `CampoInvalidoException`.
6. `funcionario/`: como achar o funcionário vinculado a um usuário.
7. Legado: `legacy/php/vendas_pdv.php`, `vendas_pdv_buscaritem.php`, `pdv_buscar_itens.php`, `pdv_buscar_clientes.php`, `vendas_processar.php` e `relatorios_detalhe_venda.php`. O legado:
   - aceitava desconto e preço vindos do navegador;
   - fazia `UPDATE produto SET quantidade_estoque = quantidade_estoque - ?` sem checar saldo;
   - misturava serviços no carrinho.
8. Frontend: `app-shell.tsx` ("Vendas (PDV)" em breve), `compra-form.tsx` (busca de produto e itens dinâmicos), `tutor-picker.tsx` (busca de cliente), `lib/estoque.ts` (dinheiro em centavos) e `lib/datas.ts`.

## 3. Decisões arquiteturais

### DA-01 — Status e operador da venda
A V7 adiciona à `venda`:
- `status` (`CONCLUIDA` | `CANCELADA`, padrão `CONCLUIDA`);
- `usuario_id` (operador; FK `usuario`);
- `cancelada_em`, `cancelada_por` (FK `usuario`) e `motivo_cancelamento`;
- `chave_idempotencia` e `valor_recebido`.

`usuario_id` fica `NULL` no banco só para acomodar a futura migração do legado. O backend **sempre** preenche. Não há estado "aberta": o carrinho vive no cliente e a venda nasce concluída, em uma única transação.

### DA-02 — Preço sempre do backend
O request traz só `produtoId` e `quantidade`. O preço unitário é o `preco_venda` atual do produto, lido **com o produto travado**, e fica congelado em `item_venda.preco_unitario`. Preço, subtotal ou total vindos do cliente são ignorados (Jackson ignora campos desconhecidos; o DTO não os tem).

### DA-03 — Desconto
- Valor absoluto em reais (`BigDecimal`, 2 casas), ≥ 0 e **menor** que o subtotal: o total precisa ser maior que 0.
- VENDAS pode dar até `petconnect.vendas.desconto-maximo-percentual-vendas` (padrão **10**) % do subtotal. ADMIN não tem teto além de total > 0.
- Acima do teto → 400 em `desconto` com "Desconto acima do permitido para o seu perfil (máximo 10%).".
- O percentual é convertido para valor **só no frontend** (UX). A API recebe e valida o valor.

### DA-04 — Baixa de estoque centralizada
Novo `EstoqueService.registrarSaidaVenda(Produto travado, int quantidade, Venda venda, Long usuarioId)`:
- exige saldo ≥ quantidade;
- grava `SAIDA_VENDA` com `quantidade` **negativa** (−qtd), `venda_id`, saldo anterior e resultante.

`VendaService` trava os produtos com `travarEmOrdem` (ordem crescente de id, evitando deadlock com o recebimento de compra) e processa os itens nessa ordem.

### DA-05 — Estoque insuficiente
Se qualquer item não tiver saldo, **a venda inteira falha** e nada é gravado.
- Resposta 409 com `detail` "Estoque insuficiente para {nome}: disponível {x}, solicitado {y}.".
- Propriedades extras do ProblemDetail: `produtoId`, `disponivel` e `solicitado`.
- O frontend destaca a linha do produto.

### DA-06 — Idempotência
- `POST /api/v1/vendas` exige o header `Idempotency-Key` (UUID). Ausente ou inválido → 400 em `idempotencyKey`.
- A chave é gravada em `venda.chave_idempotencia` (UNIQUE).
- Repetir a mesma chave devolve a venda original com **200** (não 201), header `Idempotent-Replayed: true`, e **não** baixa estoque de novo.
- Em corrida, a violação da UNIQUE é capturada: rollback, e a venda existente é lida e devolvida com 200.
- A mesma chave de outro operador → 409 "Chave de idempotência já utilizada.".
- O frontend gera a chave (`crypto.randomUUID()`) ao montar o carrinho e só a troca depois de uma venda concluída.

### DA-07 — Cancelamento com estorno
- Só ADMIN, só venda `CONCLUIDA`, até **7 dias** após `data_venda` (senão 409 "O prazo para cancelar esta venda expirou.").
- Motivo de 5–500 caracteres.
- Estorna o estoque item a item (produtos travados em ordem de id) com o novo tipo `ESTORNO_VENDA`: quantidade positiva e `venda_id`.
- Produto desativado **também** recebe o estorno.
- Não apaga nada: status `CANCELADA` e registro de quem e quando.
- A V7 recria `chk_mov_estoque_tipo` incluindo `ESTORNO_VENDA`, sem editar a V6.
- **Por que não `AJUSTE`**: ajuste é manual, exige motivo digitado e não tem vínculo com a venda. O estorno precisa de `venda_id` e deve ser filtrável.

### DA-08 — Data, funcionário e cliente
- `data_venda` vem do `Clock` do servidor; o cliente não informa.
- `funcionario_id` é o funcionário vinculado ao usuário logado, se existir; caso contrário, `NULL`. Não é escolhido no PDV.
- Cliente é opcional e, se informado, precisa estar ativo (senão 400 em `clienteId`).

### DA-09 — Pagamento e troco
- `formaPagamento` usa o enum do `CHECK` da V1.
- Em `DINHEIRO`, `valorRecebido` é obrigatório e deve ser ≥ total (senão 400 em `valorRecebido`). O troco é calculado na resposta (`troco = valorRecebido − total`); só `valor_recebido` é persistido.
- Nas demais formas, `valorRecebido` é ignorado e gravado como `NULL`.

### DA-10 — Só produtos
`item_venda.produto_id` é obrigatório. Serviços continuam sendo cobrados pelo fluxo de agendamentos. Produto inativo não pode ser vendido (400 em `itens[i].produtoId`).

### DA-11 — Financeiro fora
A venda **não** gera `movimentacao_financeira` nesta etapa. A etapa 06 (financeiro) cria o lançamento a partir de `venda` com `status = CONCLUIDA`.

### DA-12 — Pré-visualização
`POST /api/v1/vendas/previa` recebe o mesmo corpo da venda (sem idempotência) e devolve preços atuais, subtotal, desconto validado, total e disponibilidade por item, **sem gravar nem travar**. O PDV chama a prévia antes do botão "Finalizar" para mostrar os números do backend. A prévia não garante nada: a finalização revalida tudo com lock.

### DA-13 — Permissões
ADMIN e VENDAS vendem, listam, detalham e veem o resumo. Só ADMIN cancela. SERVICO não acessa vendas (403 por URL no `SecurityConfig`).

## 4. Escopo
- Backend: V7, entidades `Venda` e `ItemVenda`, `StatusVenda`, `FormaPagamento`, `VendaService`, `VendaController`, `EstoqueService.registrarSaidaVenda`/`registrarEstornoVenda`, `TipoMovimentacao.ESTORNO_VENDA`, segurança e auditoria.
- Frontend: `/pdv`, `/vendas`, `/vendas/[id]`, `/vendas/[id]/comprovante`, item "Vendas (PDV)" liberado no AppShell, card de vendas do dia no dashboard.
- Testes unitários, IT e frontend nominais (§19–§21).

## 5. Fora de escopo
Financeiro e caixa (abertura/fechamento), contas a receber, venda de serviços, venda a prazo ou parcelada com vencimentos, múltiplas formas de pagamento na mesma venda, devolução parcial, emissão fiscal (NF-e/NFC-e/SAT), impressora térmica (ESC/POS), relatórios, migração do legado, edição de venda concluída.

## 6. Banco — V7

Arquivo `V7__vendas_status_idempotencia.sql` (V1–V6 intocadas):

```sql
ALTER TABLE venda
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONCLUIDA' AFTER forma_pagamento,
    ADD COLUMN usuario_id BIGINT NULL AFTER funcionario_id,
    ADD COLUMN valor_recebido DECIMAL(10,2) NULL AFTER desconto,
    ADD COLUMN chave_idempotencia CHAR(36) NULL,
    ADD COLUMN cancelada_em DATETIME(6) NULL,
    ADD COLUMN cancelada_por BIGINT NULL,
    ADD COLUMN motivo_cancelamento VARCHAR(500) NULL,
    ADD CONSTRAINT ck_venda_status CHECK (status IN ('CONCLUIDA', 'CANCELADA')),
    ADD CONSTRAINT ck_venda_valores CHECK (valor_total > 0 AND desconto >= 0),
    ADD CONSTRAINT ck_venda_cancelamento CHECK (
        (status = 'CANCELADA' AND cancelada_em IS NOT NULL AND motivo_cancelamento IS NOT NULL)
        OR (status = 'CONCLUIDA' AND cancelada_em IS NULL)),
    ADD CONSTRAINT uk_venda_idempotencia UNIQUE (chave_idempotencia),
    ADD CONSTRAINT fk_venda_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    ADD CONSTRAINT fk_venda_cancelada_por FOREIGN KEY (cancelada_por) REFERENCES usuario (id);

CREATE INDEX idx_venda_status_data ON venda (status, data_venda);
CREATE INDEX idx_venda_cliente_data ON venda (cliente_id, data_venda);
CREATE INDEX idx_venda_pagamento_data ON venda (forma_pagamento, data_venda);

ALTER TABLE movimentacao_estoque DROP CHECK chk_mov_estoque_tipo;
ALTER TABLE movimentacao_estoque ADD CONSTRAINT chk_mov_estoque_tipo
    CHECK (tipo IN ('ENTRADA_COMPRA', 'SAIDA_VENDA', 'AJUSTE', 'ESTORNO_VENDA'));
```

Observações:
- Se `ck_venda_valores` conflitar com dados existentes, parar e reportar. A tabela deve estar vazia neste ponto.
- Confirmar no MySQL 8.4 que o `CHECK` não conflita com as FKs (lição da V1: `CHECK` em coluna com `ON DELETE SET NULL` falha). As FKs novas não têm ação referencial.
- `idx_venda_data` (V1) continua existindo; `fk_mov_estoque_venda` já cobre `movimentacao_estoque.venda_id`.

## 7. API — Pré-visualização

`POST /api/v1/vendas/previa` (ADMIN, VENDAS) → 200

```json
// request
{"clienteId": null, "itens": [{"produtoId": 10, "quantidade": 2}], "desconto": 5.00, "formaPagamento": "DINHEIRO", "valorRecebido": 400.00}
// response
{"itens": [{"produtoId": 10, "nome": "Ração Premium", "precoUnitario": 169.90, "quantidade": 2, "subtotal": 339.80,
            "disponivel": 12, "suficiente": true}],
 "subtotal": 339.80, "desconto": 5.00, "total": 334.80, "troco": 65.20, "podeFinalizar": true}
```

A prévia aplica as mesmas validações de campo da venda (400), exceto estoque: nela, falta de estoque vira `suficiente: false` e `podeFinalizar: false`, não 409.

## 8. API — Registrar venda

`POST /api/v1/vendas` (ADMIN, VENDAS). Header obrigatório: `Idempotency-Key: <uuid>`.

```json
{"clienteId": 3, "itens": [{"produtoId": 10, "quantidade": 2}, {"produtoId": 12, "quantidade": 1}],
 "desconto": 5.00, "formaPagamento": "PIX", "valorRecebido": null, "observacoes": "Cliente pediu sacola"}
```

Validações:
- `itens`: 1–100, sem `produtoId` repetido (400 em `itens`), `quantidade` de 1–10.000, produto existente e ativo (400 em `itens[i].produtoId`);
- `desconto`: ≥ 0, 2 casas, abaixo do subtotal e dentro do teto do papel;
- `formaPagamento` obrigatória e dentro do enum;
- `valorRecebido` conforme DA-09;
- `observacoes` ≤ 2000;
- total ≤ 99.999.999,99.

Respostas:
- **201** com `Location: /api/v1/vendas/{id}`;
- **200** em replay idempotente;
- **400** em validação;
- **409** em estoque insuficiente (DA-05) ou chave de outro usuário.

```json
{"id": 91, "dataVenda": "2026-09-26T14:03:11.120Z", "status": "CONCLUIDA",
 "cliente": {"id": 3, "nome": "Maria Silva"}, "funcionario": {"id": 2, "nome": "Joana"},
 "operador": {"id": 5, "nome": "joana"},
 "itens": [{"id": 1, "produto": {"id": 10, "nome": "Ração Premium"}, "quantidade": 2, "precoUnitario": 169.90, "subtotal": 339.80}],
 "subtotal": 369.80, "desconto": 5.00, "valorTotal": 364.80, "formaPagamento": "PIX",
 "valorRecebido": null, "troco": null, "observacoes": "Cliente pediu sacola",
 "canceladaEm": null, "canceladaPor": null, "motivoCancelamento": null}
```

`subtotal` é derivado (`valorTotal + desconto`).

## 9. API — Consultas

- `GET /api/v1/vendas`
  - filtros: `dataInicio`, `dataFim` (datas locais de São Paulo, inclusivas), `status`, `formaPagamento`, `clienteId`, `page`, `size ≤ 100`;
  - ordenação: `dataVenda desc, id desc`;
  - resposta: `PageResponse` com resumo (`itens: null`, `quantidadeItens: -1`, como em compras);
  - `dataFim < dataInicio` → 400 em `dataFim`.
- `GET /api/v1/vendas/{id}` → detalhe com itens (EntityGraph com itens e produto, cliente, funcionário, operador); 404 se não existir.
- `GET /api/v1/vendas/resumo-dia?data=YYYY-MM-DD` (padrão: hoje) → `{"data":"2026-09-26","quantidade":14,"total":2350.40}`. Só conta vendas `CONCLUIDA`, com agregação no banco, sem carregar entidades.

## 10. API — Cancelar

`POST /api/v1/vendas/{id}/cancelar` (só ADMIN) com `{"motivo": "Cliente desistiu na hora"}` → 200 com a venda atualizada.
- Trava a venda (`PESSIMISTIC_WRITE`).
- Venda já cancelada → 409 "A venda já está cancelada.".
- Prazo → 409 (DA-07).
- Estorno: `ESTORNO_VENDA` por item, em uma única transação.

## 11. Estoque — contratos novos

```java
MovimentacaoEstoque registrarSaidaVenda(Produto travado, int quantidade, Venda venda, Long usuarioId); // quantidade > 0; grava −quantidade
MovimentacaoEstoque registrarEstornoVenda(Produto travado, int quantidade, Venda venda, Long usuarioId); // grava +quantidade
```

Os dois exigem o produto já travado pelo chamador e não alteram o custo médio. `SAIDA_VENDA` com saldo insuficiente lança uma exceção de domínio (`EstoqueInsuficienteException`), mapeada para 409 com as propriedades de DA-05.

## 12. Cálculo — exemplo
Itens: 2 × 169,90 + 1 × 30,00 = 369,80. Desconto de 5,00 → total 364,80. Em `DINHEIRO` com recebido 400,00 → troco 35,20. VENDAS com desconto de 40,00 (10,8%) → 400; ADMIN com o mesmo desconto → OK.

## 13. Autorização

| Endpoint | ADMIN | VENDAS | SERVICO |
|---|---|---|---|
| `POST /vendas/previa` | ✅ | ✅ | ❌ |
| `POST /vendas` | ✅ | ✅ | ❌ |
| `GET /vendas`, `GET /vendas/{id}`, `GET /vendas/resumo-dia` | ✅ | ✅ | ❌ |
| `POST /vendas/{id}/cancelar` | ✅ | ❌ | ❌ |

Regras por URL no `SecurityConfig` (403 antes da validação do corpo): `/api/v1/vendas/*/cancelar` → ADMIN; `/api/v1/vendas/**` → ADMIN, VENDAS. `@PreAuthorize` nos métodos, em defesa em profundidade.

## 14. Auditoria
- `VENDA_REALIZADA`: `vendaId`, `quantidadeItens`, `valorTotal`, `desconto`, `formaPagamento`, `clienteId`.
- `VENDA_CANCELADA`: `vendaId`, `motivo`, `valorTotal`.
- Replay idempotente não gera evento novo.
- Nada de dados pessoais além de ids.

## 15. Frontend

Rotas: `/pdv`, `/vendas`, `/vendas/[id]`, `/vendas/[id]/comprovante`.

AppShell:
- "Vendas (PDV)" liberado (`/pdv`) para ADMIN e VENDAS;
- novo item "Vendas" (`/vendas`) para ADMIN e VENDAS, na mesma seção;
- "Relatórios" continua "em breve".

### `/pdv`
Tela única, mobile-first. No `lg+`, duas colunas: busca + carrinho | pagamento. No celular, carrinho em cima e pagamento embaixo, com botão fixo "Finalizar".

- **Campo de busca/leitor** com foco automático:
  - Enter com código de barras de 8–14 dígitos que bate exatamente com um resultado da busca rápida → adiciona 1 unidade (ou soma +1 se já estiver no carrinho);
  - texto → lista de resultados com botão "Adicionar";
  - produto indisponível (saldo 0) aparece desabilitado com "Sem estoque".
- **Carrinho**:
  - quantidade editável com botões − e +, respeitando o saldo conhecido;
  - remover item e limpar carrinho (com confirmação);
  - subtotal por linha e total **em centavos** (visual);
  - o carrinho persiste em `sessionStorage` (`pc_pdv_carrinho`, com a chave de idempotência) e sobrevive a um recarregamento;
  - só guarda ids, nomes, quantidades e preços exibidos, nunca token.
- **Cliente opcional**: reaproveitar a busca de tutor (`/api/clientes`).
- **Desconto** em R$ ou % (alternância). VENDAS vê a dica "máximo 10%".
- **Forma de pagamento**: botões grandes. Em Dinheiro, campo "Valor recebido" e troco.
- **Antes de finalizar**: chama a prévia por Server Action e mostra os valores **do backend**; se houver divergência de preço, avisa "Preços atualizados pelo sistema".
- **Finalizar**: pede confirmação com total e forma de pagamento e envia com a chave de idempotência.
  - Sucesso: limpa o carrinho, gera nova chave e mostra um painel com "Venda #N concluída", troco e botões "Imprimir comprovante" e "Nova venda".
  - 409 de estoque: destaca a linha e mantém o carrinho.

### `/vendas`
Filtros: período (padrão: hoje), status e forma de pagamento. Tabela no md+ e cartões no celular. Badges textuais "✓ Concluída" e "✕ Cancelada". Paginação. Estado vazio.

### `/vendas/[id]`
Dados, itens, subtotal, desconto, total, pagamento, recebido/troco, operador e cliente. Link para o comprovante. Para ADMIN, em venda `CONCLUIDA` dentro do prazo: **Cancelar venda** (motivo + confirmação, informando que o estoque será estornado). Venda cancelada mostra quem cancelou, quando e o motivo, sem nenhuma ação.

### `/vendas/[id]/comprovante`
Página limpa, sem AppShell (route group próprio, mas autenticada), largura ~80 mm via CSS `@media print`, com botão "Imprimir" (`window.print()`) oculto na impressão. Deve conter: nome do petshop, "Comprovante não fiscal", nº da venda, data e hora (São Paulo), itens, subtotal, desconto, total, pagamento, recebido/troco e operador. Venda cancelada imprime "CANCELADA" em destaque.

## 16. Dashboard
Card "Vendas hoje" com quantidade e total (`GET /api/v1/vendas/resumo-dia`), link para `/vendas` e visível só para ADMIN e VENDAS. SERVICO não chama o endpoint.

## 17. Erros/UI
`Aviso` para cada status:
- 400: mensagens nos campos (`itens.N.quantidade`, `desconto`, `valorRecebido`);
- 401: fluxo global;
- 403: sem permissão;
- 404: "Venda não encontrada.";
- 409: mensagem da API.

Loading/skeleton em `/vendas` e `/vendas/[id]`. Botão "Finalizar" desabilitado durante o envio (`useFormStatus`/pending). Um duplo clique também é coberto pela idempotência.

## 18. Regras numeradas
1. Preço unitário vem do produto no momento da venda (lido com lock) e fica congelado.
2. Total, subtotal e troco são calculados no backend com `BigDecimal` HALF_UP.
3. Desconto ≥ 0 e menor que o subtotal; total > 0.
4. VENDAS tem teto de desconto (padrão 10%); ADMIN não.
5. Venda tem 1–100 itens sem produto repetido; quantidade de 1–10.000.
6. Só produto ativo é vendido.
7. Baixa de estoque só pelo `EstoqueService`, com lock em ordem de id.
8. Toda baixa gera `SAIDA_VENDA` (quantidade negativa) com `venda_id`.
9. Estoque insuficiente em qualquer item falha a venda inteira (409).
10. Saldo nunca fica negativo.
11. `Idempotency-Key` é obrigatória; repetir devolve a mesma venda sem nova baixa.
12. `data_venda` e operador vêm do servidor.
13. Dinheiro exige valor recebido ≥ total.
14. Venda nasce `CONCLUIDA`; o único outro estado é `CANCELADA`.
15. Só ADMIN cancela, com motivo, até 7 dias.
16. Cancelamento estorna o estoque com `ESTORNO_VENDA` e `venda_id`, inclusive de produto desativado.
17. Venda cancelada não volta a `CONCLUIDA` e não é editável.
18. Nenhuma venda é apagada.
19. A venda não gera movimentação financeira nesta etapa.
20. SERVICO não acessa vendas.
21. Resumo do dia conta só `CONCLUIDA`.
22. Autorização nunca depende só do frontend.

## 19. Testes unitários

### VendaServiceTest
`deveRegistrarVendaComPrecoDoProduto`, `deveIgnorarPrecoEnviadoPeloCliente`, `deveCalcularSubtotalDescontoETotal`, `deveCalcularTrocoEmDinheiro`, `deveExigirValorRecebidoEmDinheiro`, `deveRejeitarValorRecebidoMenorQueTotal`, `deveRejeitarVendaSemItens`, `deveRejeitarProdutoDuplicado`, `deveRejeitarProdutoInativo`, `deveRejeitarDescontoMaiorOuIgualAoSubtotal`, `vendasNaoPodeDarDescontoAcimaDoTeto`, `adminPodeDarDescontoAcimaDoTeto`, `deveBaixarEstoqueDeCadaItemEmOrdemDeId`, `deveFalharVendaInteiraSeUmItemNaoTiverEstoque`, `deveDefinirOperadorEFuncionarioDoUsuarioLogado`, `deveDevolverVendaExistenteParaMesmaChave`, `deveRejeitarChaveDeOutroUsuario`, `deveCancelarVendaComEstorno`, `deveRejeitarCancelamentoDeVendaCancelada`, `deveRejeitarCancelamentoForaDoPrazo`, `deveEstornarProdutoDesativado`, `previaNaoDeveGravarNemTravar`.

### EstoqueServiceTest (acréscimos)
`deveRegistrarSaidaVendaComQuantidadeNegativa`, `deveRejeitarSaidaMaiorQueSaldo`, `deveRegistrarEstornoVenda`, `saidaEEstornoNaoAlteramCusto`.

## 20. Testes de integração

### VendaControllerIT
`vendasDeveRegistrarVenda`, `adminDeveRegistrarVenda`, `servicoNaoDeveVender`, `servicoNaoDeveListarVendas`, `semIdempotencyKeyDeveRetornar400`, `mesmaChaveDeveRetornar200SemNovaBaixa`, `vendaDeveBaixarEstoqueEGerarSaidaVenda`, `estoqueInsuficienteDeveRetornar409SemGravarNada`, `totalDeveSerCalculadoNoBackend`, `descontoAcimaDoTetoParaVendasDeveRetornar400`, `deveListarVendasPorPeriodoEStatus`, `resumoDiaDeveContarSoConcluidas`, `adminDeveCancelarVendaComEstorno`, `vendasNaoDeveCancelarVenda`, `cancelamentoDuplicadoDeveRetornar409`, `previaDeveMostrarDisponibilidadeSemAlterarEstoque`.

### VendaConcorrenciaIT
- `duasVendasDoUltimoItemSoUmaPassa`: produto com saldo 1, duas threads vendendo 1. Exatamente uma recebe 201 e a outra 409. Saldo final 0 e exatamente uma `SAIDA_VENDA`.
- `vendaERecebimentoSimultaneosNaoPerdemAtualizacao`: `saldoFinal = inicial + entrada − saída`.
- `mesmaChaveEmParaleloGeraUmaVenda`: duas threads com a mesma `Idempotency-Key` resultam em uma venda e uma baixa.

## 21. Testes frontend
- `venda-schema.test.ts`: válido, sem itens, quantidade zero, desconto em % convertido para valor, dinheiro sem valor recebido, troco em centavos.
- `carrinho.test.ts`: adicionar, somar ao repetir, limite pelo saldo conhecido, remover, total em centavos sem erro de float, persistência e restauração do `sessionStorage`.
- `pdv-leitor.test.tsx`: Enter com código exato adiciona direto; texto mostra lista; produto sem estoque desabilitado.
- `pdv-pagamento.test.tsx`: alternância R$/%, dica de teto para VENDAS, troco em dinheiro, confirmação antes de finalizar, botão desabilitado durante o envio.
- `pdv-finalizacao.test.tsx`: sucesso limpa o carrinho e troca a chave; 409 destaca a linha e mantém o carrinho; replay (200) mostra a mesma venda.
- `vendas-list.test.tsx`: render, badges, vazio, filtros.
- `venda-detalhes.test.tsx`: itens e totais; cancelar só para ADMIN em concluída; confirmação; cancelada sem ações.
- `comprovante.test.tsx`: dados obrigatórios, "Comprovante não fiscal", "CANCELADA" quando for o caso.
- `dashboard-vendas.test.tsx`: contador e total do dia.

## 22. Segurança e integridade
- O DTO não tem preço, total, status, data nem operador.
- `BigDecimal` HALF_UP; nunca `double`/`float` no backend. No front, dinheiro em centavos inteiros.
- Lock pessimista em produtos (em ordem de id) e na venda ao cancelar.
- Transação única por venda e por cancelamento.
- Idempotência com UNIQUE no banco.
- Histórico de estoque sem edição nem exclusão.
- Autorização no backend (URL + método).
- Auditoria sem dados sensíveis.
- `sessionStorage` sem token.

## 23. Critérios de aceite
- [ ] V7 aplicada sobre V6 (Docker local e Testcontainers), sem editar V1–V6.
- [ ] Venda por VENDAS: saldo baixa, `SAIDA_VENDA` com `venda_id` e total calculado no backend.
- [ ] Reenviar a mesma venda (mesma chave) devolve 200 e não baixa de novo.
- [ ] Duas vendas simultâneas do último item: uma passa e a outra recebe 409; saldo 0.
- [ ] Desconto acima de 10% por VENDAS → 400; por ADMIN → OK.
- [ ] Dinheiro com recebido menor que o total → 400; troco correto na resposta e no comprovante.
- [ ] Cancelamento por ADMIN estorna com `ESTORNO_VENDA`; VENDAS → 403; cancelar de novo → 409.
- [ ] SERVICO recebe 403 em todos os endpoints de venda e não vê os itens no menu.
- [ ] PDV funciona com leitor (Enter), no celular (375 px sem rolagem horizontal) e no desktop.
- [ ] Comprovante imprime em ~80 mm e mostra "Comprovante não fiscal".
- [ ] Dashboard mostra vendas do dia (só concluídas).
- [ ] Todos os testes verdes; lint, typecheck e builds PASS.

## 24. Riscos
- **Preço muda entre a prévia e a finalização**: vale o preço lido com lock, e a resposta mostra o valor final. A UI avisa a divergência.
- **Deadlock entre venda e recebimento**: ambos travam em ordem crescente de id.
- **Chave de idempotência perdida pelo navegador**: persistida no `sessionStorage` junto com o carrinho.
- **Cancelamento tardio distorcendo o futuro financeiro**: prazo de 7 dias, e o financeiro (etapa 06) usa apenas `CONCLUIDA`.
- **Leitores de código que enviam Tab em vez de Enter**: tratar `Enter` e `Tab` no campo de busca.

## 25. Próximas etapas
- **06 — Financeiro**: lançamentos a partir de vendas concluídas (ENTRADA/VENDA_PRODUTO), compras recebidas (SAIDA/COMPRA_MERCADORIA) e serviços realizados; caixa diário.
- **07 — Relatórios**: vendas por período, produtos mais vendidos, curva ABC, giro de estoque.
- **08 — Migração do legado**.

## 26. Ordem de implementação
1. V7 + `TipoMovimentacao.ESTORNO_VENDA`.
2. Entidades `Venda`/`ItemVenda`, `StatusVenda`, `FormaPagamento`, repositórios (travar venda, busca paginada, resumo agregado, busca por chave).
3. `EstoqueService.registrarSaidaVenda`/`registrarEstornoVenda` + `EstoqueInsuficienteException` → 409.
4. `VendaService` (prévia, registrar com idempotência, cancelar, listar, resumo).
5. `VendaController`, `SecurityConfig`, auditoria.
6. Testes unitários e IT (incluindo concorrência) até `./mvnw verify` verde.
7. Frontend: `lib/vendas.ts` (tipos, schemas, carrinho em centavos), actions, `/pdv`.
8. `/vendas`, `/vendas/[id]`, comprovante.
9. AppShell e dashboard.
10. Testes frontend, lint, typecheck e build.
11. `docker compose up -d --build` e E2E no navegador (venda, replay, estoque insuficiente, cancelamento, comprovante, mobile).
12. Documentação (README, arquitetura, banco com V7 e eventos) e relatório `05-pdv-vendas-relatorio.md`.

## 27. Restrições
NÃO editar V1–V6; NÃO alterar `legacy/`; NÃO hard-delete; NÃO aceitar preço/total/status/data/operador do frontend; NÃO usar double/float para dinheiro; NÃO permitir saldo negativo; NÃO alterar saldo fora do `EstoqueService`; NÃO travar produtos fora de ordem de id; NÃO vender serviço; NÃO gerar movimentação financeira; NÃO editar venda concluída; NÃO implementar caixa/fiscal/impressora térmica; NÃO desabilitar testes/lint; NÃO usar `any`; NÃO commit/push automático.

## 28. Relatório final obrigatório

```text
## Implementação concluída

### Banco
- migration V7 (colunas, constraints, índices, novo tipo ESTORNO_VENDA)

### Vendas
- arquivos/endpoints/prévia/idempotência/desconto/troco

### Estoque
- saída e estorno pelo serviço central/locks

### Cancelamento
- regras/prazo/estorno

### Segurança
- matriz/SecurityConfig/@PreAuthorize

### Auditoria
- eventos

### Frontend
- rotas/componentes/PDV/comprovante/AppShell/dashboard

### Testes
- unitários: X/Y
- integração: X/Y
- frontend: X/Y

### Validações
- backend build: PASS/FAIL
- frontend build: PASS/FAIL
- lint: PASS/FAIL
- typecheck: PASS/FAIL

### Divergências e decisões

### Pendências
- somente pendências reais

### Handoff para a etapa 06 (prompt para o ChatGPT)
```

## 29. Definição de pronto

```text
PDV → scan/busca → carrinho → prévia (backend) → finalizar (Idempotency-Key)
  → locks em ordem → SAIDA_VENDA por item → CONCLUIDA → comprovante
Cancelar (ADMIN, ≤7 dias) → lock → ESTORNO_VENDA por item → CANCELADA
Etapa 06 → financeiro lê vendas CONCLUIDA
```

Considerar concluído somente com rastreabilidade integral, concorrência e idempotência provadas por teste, autorização no backend, auditoria, testes e builds verdes.
