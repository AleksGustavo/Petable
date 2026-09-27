# 04 — Produtos, Estoque, Fornecedores e Compras

> **Arquivo:** `04-produtos-estoque.md`  
> **Projeto:** PetConnect Web  
> **Stack:** Spring Boot 4.1 + Java 21 + Spring MVC/REST + Next.js 16 + TypeScript + MySQL 8.4 + MongoDB 7  
> **Regra:** preservar a arquitetura MVC/REST e os padrões existentes. Nunca editar migrations V1–V5.

## 1. Objetivo

Implementar Produtos, Estoque, Fornecedores e Compras com rastreabilidade integral de saldo, preparando a base para o PDV da etapa 05.

Entregar: categorias somente leitura; CRUD lógico de produtos; código de barras único; busca rápida por nome/código; estoque mínimo; histórico de movimentações; ajuste manual; fornecedores com CNPJ/endereço; compras com múltiplos itens; recebimento que atualiza saldo e custo; cancelamento; concorrência protegida; dashboard; autorização; auditoria; frontend e testes.

MVC: Model = domínio/application/repositórios; View = Next.js; Controller = Spring MVC REST. Não criar JSP/Thymeleaf.

## 2. Análise obrigatória

Antes de alterar código, inspecionar `backend/`, `frontend/`, V1–V5, tabelas `categoria_produto`, `produto`, `fornecedor`, `compra`, `item_compra`, `movimentacao_financeira`, endereço, funcionários, `AuditoriaService`, `SecurityConfig`, locks existentes, `PageResponse`, `apiFetch`, Server Actions, Zod, `Campo`, `Aviso`, `BotaoConfirmar`, `AppShell`, dashboard e permissões. Consultar PHP legado apenas como referência. Executar baseline de testes, lint, typecheck e builds. Não editar V1–V5.

## 3. Decisões arquiteturais

### DA-01 — Histórico de estoque
Criar `movimentacao_estoque` em V6. `produto.quantidade_estoque` é o saldo atual; a movimentação é a trilha imutável de como ele mudou.

### DA-02 — Saldo não é campo de cadastro
Produto novo nasce com estoque 0. `quantidade_estoque` nunca é editada pelo CRUD. Saldo muda apenas por compra recebida, ajuste manual e, futuramente, venda.

### DA-03 — Custo médio ponderado móvel
Ao receber compra:

`novoCusto = ((saldoAnterior × custoAnterior) + (qtdRecebida × custoCompra)) / (saldoAnterior + qtdRecebida)`.

Se saldo anterior = 0, usar custo da compra. Usar `BigDecimal`, escala 2, `HALF_UP`.

### DA-04 — Custo no cadastro
Produto novo pode ter `precoCusto` inicial como referência. Depois, edição comum não altera custo; recebimento atualiza pelo custo médio.

### DA-05 — Total da compra
`valor_total` é calculado exclusivamente no backend pela soma dos subtotais e congelado na criação. Itens não são editados depois; correção exige cancelar e recriar.

### DA-06 — Máquina de estados da compra
Permitidas: `PEDIDO → RECEBIDO` e `PEDIDO → CANCELADO`. Qualquer outra transição retorna 409.

### DA-07 — Recebimento atômico
Receber compra: lock da compra; validar PEDIDO; bloquear produtos em ID crescente; calcular custos; atualizar saldos; criar movimentações; definir entrega; status RECEBIDO; auditar. Qualquer falha faz rollback total.

### DA-08 — Concorrência
Toda alteração de saldo usa `PESSIMISTIC_WRITE`/`SELECT ... FOR UPDATE`. Nunca ler e gravar saldo sem lock.

### DA-09 — Ajuste manual
Usar delta: `+10` entrada, `-3` saída/correção. Quantidade diferente de zero e motivo obrigatório 5–500. Não permitir saldo negativo. Ajuste não muda custo.

### DA-10 — Produto desativado com estoque
Permitir desativação sem zerar saldo. Preservar histórico; impedir novas compras/PDV. Reativação somente ADMIN.

### DA-11 — Fornecedor com compra aberta
Fornecedor com compra `PEDIDO` não pode ser desativado: 409 `O fornecedor possui compras em aberto e não pode ser desativado.`

### DA-12 — Categorias
Somente leitura nesta etapa. Não criar CRUD.

### DA-13 — Permissões
Leitura de produtos/categorias para todos autenticados. ADMIN/VENDAS mantêm produto; ajuste somente ADMIN. Fornecedores: leitura ADMIN/VENDAS, escrita ADMIN. Compras: leitura ADMIN/VENDAS; criar/receber/cancelar somente ADMIN. SERVICO não acessa fornecedores/compras.

### DA-14 — Financeiro fora do escopo
Receber compra não cria `movimentacao_financeira`. Recebimento físico não equivale a pagamento.

## 4. Escopo

Categorias: listar. Produtos: listar, pesquisar, paginar, filtrar, criar, detalhar, editar, ativar/desativar, busca rápida, histórico e estoque mínimo. Fornecedores: CRUD lógico com endereço/CNPJ. Compras: criar PEDIDO com itens, listar/detalhar, receber e cancelar. Estoque: movimentações e ajuste manual. Dashboard: contador abaixo do mínimo.

## 5. Fora de escopo

PDV/vendas, financeiro, contas a pagar, NFe/XML, lote/validade, múltiplos estoques, transferências, inventário, reserva, recebimento parcial, devolução, edição de compra criada, CRUD de categorias, imagem de produto, importação e migração automática do legado.

## 6. Banco — V6

Criar `V6__movimentacao_estoque_indices.sql`:

```sql
CREATE TABLE movimentacao_estoque (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    produto_id BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    quantidade INT NOT NULL,
    saldo_anterior INT NOT NULL,
    saldo_resultante INT NOT NULL,
    compra_id BIGINT NULL,
    venda_id BIGINT NULL,
    usuario_id BIGINT NOT NULL,
    motivo VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mov_estoque_produto FOREIGN KEY (produto_id) REFERENCES produto(id),
    CONSTRAINT fk_mov_estoque_compra FOREIGN KEY (compra_id) REFERENCES compra(id),
    CONSTRAINT fk_mov_estoque_venda FOREIGN KEY (venda_id) REFERENCES venda(id),
    CONSTRAINT fk_mov_estoque_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT chk_mov_estoque_tipo CHECK (tipo IN ('ENTRADA_COMPRA','SAIDA_VENDA','AJUSTE')),
    CONSTRAINT chk_mov_estoque_quantidade CHECK (quantidade <> 0),
    CONSTRAINT chk_mov_estoque_saldos CHECK (saldo_anterior >= 0 AND saldo_resultante >= 0)
);
```

`SAIDA_VENDA` e `venda_id` ficam preparados porque `venda` já existe e a etapa 05 usará a mesma trilha.

Adicionar somente índices ausentes/equivalentes não existentes:

```sql
CREATE INDEX idx_produto_categoria_ativo ON produto (categoria_id, ativo);
CREATE INDEX idx_produto_ativo_estoque ON produto (ativo, quantidade_estoque, estoque_minimo);
CREATE INDEX idx_fornecedor_ativo_nome ON fornecedor (ativo, nome_fantasia);
CREATE INDEX idx_compra_status_data ON compra (status, data_compra);
CREATE INDEX idx_compra_fornecedor_status ON compra (fornecedor_id, status);
CREATE INDEX idx_mov_estoque_produto_data ON movimentacao_estoque (produto_id, created_at);
CREATE INDEX idx_mov_estoque_compra ON movimentacao_estoque (compra_id);
```

## 7. API — Categorias

`GET /api/v1/categorias-produto` — qualquer autenticado. Ordenar por nome. Sem POST/PUT/DELETE.

Exemplo:
```json
[{"id":1,"nome":"Ração","descricao":"Alimentos para animais"}]
```

## 8. API — Produtos

### Listar
`GET /api/v1/produtos?page=0&size=20&q=racao&categoriaId=1&ativo=true&abaixoMinimo=false` — autenticado. Busca nome/código, `PageResponse`, nome ASC. `page>=0`, `1<=size<=100`, `q<=120`.

`abaixoDoMinimo = quantidadeEstoque <= estoqueMinimo`.

### Busca rápida
`GET /api/v1/produtos/busca-rapida?q=789123` — autenticado. Somente ativos, máximo 50, nome/código, código exato primeiro. Mínimo 2 caracteres salvo código completo.

```json
[{"id":10,"nome":"Ração Premium 10kg","codigoBarras":"7891234567890","precoVenda":169.90,"quantidadeEstoque":4,"disponivel":true}]
```

### Detalhar
`GET /api/v1/produtos/{id}` — autenticado; 404 inexistente. Retornar categoria, fornecedor padrão, custo, venda, saldo, mínimo, abaixo do mínimo, ativo e timestamps.

### Criar
`POST /api/v1/produtos` — ADMIN/VENDAS.

```json
{
  "nome":"Ração Premium 10kg",
  "descricao":"Ração seca para cães adultos.",
  "categoriaId":1,
  "fornecedorPadraoId":4,
  "codigoBarras":"7891234567890",
  "precoCusto":120.50,
  "precoVenda":169.90,
  "estoqueMinimo":5
}
```

Não aceitar quantidade, ativo ou timestamps. Nome 2–150; descrição <=2000; categoria válida; fornecedor opcional ativo; código opcional, 8–14 dígitos e único; custo >=0; venda >0; mínimo 0–1.000.000. Nasce estoque 0/ativo. Código duplicado → 409. Auditar `PRODUTO_CRIADO`.

### Atualizar
`PUT /api/v1/produtos/{id}` — ADMIN/VENDAS. Permitir nome, descrição, categoria, fornecedor padrão, código, preço venda e mínimo. Não aceitar saldo, custo ou ativo. Auditar `PRODUTO_ATUALIZADO`.

### Desativar
`PATCH /api/v1/produtos/{id}/desativar` — ADMIN/VENDAS. Preservar saldo/histórico. Auditar `PRODUTO_DESATIVADO` incluindo saldo.

### Reativar
`PATCH /api/v1/produtos/{id}/ativar` — somente ADMIN. Auditar `PRODUTO_ATIVADO`.

## 9. API — Estoque

### Histórico
`GET /api/v1/produtos/{id}/movimentacoes?page=0&size=20&tipo=AJUSTE` — autenticado. `created_at DESC, id DESC`; tipos ENTRADA_COMPRA/SAIDA_VENDA/AJUSTE.

### Ajuste
`POST /api/v1/produtos/{id}/ajustes-estoque` — somente ADMIN.

```json
{"quantidade":-3,"motivo":"Correção de três unidades avariadas."}
```

Produto pode estar inativo. Delta !=0, motivo 5–500, lock pessimista, saldo final >=0, não alterar custo. Saldo insuficiente → 409 `O ajuste resultaria em estoque negativo.` Retornar 201 com movimentação. Auditar `ESTOQUE_AJUSTADO` com produto, delta, saldo anterior/resultante e motivo.

## 10. API — Fornecedores

### Listar
`GET /api/v1/fornecedores?page=0&size=20&q=pet&ativo=true` — ADMIN/VENDAS. Buscar nome fantasia, razão social, CNPJ e contato.

### Opções
`GET /api/v1/fornecedores/opcoes?q=pet` — ADMIN/VENDAS. Somente ativos, máximo 50.

### Detalhar
`GET /api/v1/fornecedores/{id}` — ADMIN/VENDAS.

### Criar
`POST /api/v1/fornecedores` — ADMIN.

```json
{
  "nomeFantasia":"Pet Distribuidora",
  "razaoSocial":"Pet Distribuidora Ltda",
  "cnpj":"12345678000195",
  "telefone":"1935000000",
  "email":"contato@petdistribuidora.com.br",
  "contato":"Carlos Souza",
  "endereco":{"cep":"13610000","logradouro":"Rua Exemplo","numero":"500","complemento":null,"bairro":"Distrito Industrial","cidade":"Leme","uf":"SP"},
  "observacoes":"Entrega às terças e quintas."
}
```

Nome 2–150; razão 2–200; CNPJ 14 dígitos com verificadores e único; telefone opcional 10/11 e único; e-mail válido <=150; contato <=150; endereço obrigatório reutilizando `EnderecoRequest`; observações <=2000. Duplicidades →409. Auditar `FORNECEDOR_CRIADO`.

### Atualizar
`PUT /api/v1/fornecedores/{id}` — ADMIN. Auditar `FORNECEDOR_ATUALIZADO`.

### Desativar
`PATCH /api/v1/fornecedores/{id}/desativar` — ADMIN. Compra PEDIDO existente →409. Caso contrário ativo=false. Auditar `FORNECEDOR_DESATIVADO`.

### Ativar
`PATCH /api/v1/fornecedores/{id}/ativar` — ADMIN. Auditar `FORNECEDOR_ATIVADO`.

## 11. API — Compras

### Listar
`GET /api/v1/compras?page=0&size=20&status=PEDIDO&fornecedorId=4&dataInicio=2026-09-01&dataFim=2026-09-30` — ADMIN/VENDAS. Validar início<=fim. Ordenar data/id DESC.

### Detalhar
`GET /api/v1/compras/{id}` — ADMIN/VENDAS. Retornar fornecedor, funcionário, datas, status, pagamento, observações, total e itens.

### Criar PEDIDO
`POST /api/v1/compras` — ADMIN.

```json
{
  "fornecedorId":4,
  "funcionarioId":null,
  "dataCompra":"2026-09-25",
  "formaPagamento":"BOLETO",
  "observacoes":"Entrega prevista em 3 dias.",
  "itens":[
    {"produtoId":10,"quantidade":10,"precoCustoUnitario":120.50},
    {"produtoId":12,"quantidade":10,"precoCustoUnitario":30.50}
  ]
}
```

Não aceitar total/status/dataEntrega/subtotal. Fornecedor ativo; funcionário opcional ativo; data não futura; forma pagamento 2–50; observações <=2000; 1–100 itens; produto ativo; sem produto repetido; quantidade 1–1.000.000; custo unitário >0 com até 2 casas. Backend calcula total. Nasce PEDIDO/dataEntrega null. Não altera estoque. Auditar `COMPRA_CRIADA`.

### Receber
`POST /api/v1/compras/{id}/receber` — ADMIN.

```json
{"dataEntrega":"2026-09-26"}
```

Data obrigatória, não futura e >= dataCompra. Executar fluxo transacional/locks. Para cada item criar `ENTRADA_COMPRA`, atualizar saldo/custo médio. Status RECEBIDO. Transição inválida →409. Auditar `COMPRA_RECEBIDA` com compra, total, quantidade de itens e entrega.

### Cancelar
`POST /api/v1/compras/{id}/cancelar` — ADMIN.

```json
{"motivo":"Fornecedor informou indisponibilidade dos itens."}
```

Motivo 5–500. Somente PEDIDO. Não alterar estoque. Status CANCELADO. Registrar motivo na auditoria e anexar `Cancelamento: ...` às observações preservando texto anterior. Auditar `COMPRA_CANCELADA`.

## 12. Custo médio — exemplo

Saldo 10 a R$20,00 + entrada 5 a R$26,00: `(10×20 + 5×26)/15 = 22,00`. Novo saldo 15; novo custo R$22,00.

## 13. Autorização

| Operação | ADMIN | VENDAS | SERVICO |
|---|---:|---:|---:|
| Ler categorias/produtos/busca | Sim | Sim | Sim |
| Criar/editar/desativar produto | Sim | Sim | Não |
| Reativar produto | Sim | Não | Não |
| Ver movimentações | Sim | Sim | Sim |
| Ajustar estoque | Sim | Não | Não |
| Listar fornecedores | Sim | Sim | Não |
| Criar/editar/ativar/desativar fornecedor | Sim | Não | Não |
| Listar/detalhar compras | Sim | Sim | Não |
| Criar/receber/cancelar compra | Sim | Não | Não |

Atualizar também `SecurityConfig` nas rotas administrativas para 403 antes da validação do corpo.

## 14. Auditoria

Eventos: `PRODUTO_CRIADO`, `PRODUTO_ATUALIZADO`, `PRODUTO_DESATIVADO`, `PRODUTO_ATIVADO`, `ESTOQUE_AJUSTADO`, `FORNECEDOR_CRIADO`, `FORNECEDOR_ATUALIZADO`, `FORNECEDOR_DESATIVADO`, `FORNECEDOR_ATIVADO`, `COMPRA_CRIADA`, `COMPRA_RECEBIDA`, `COMPRA_CANCELADA`.

Movimentação MySQL é fonte operacional do estoque; Mongo registra ação administrativa. Não registrar JWT/cookies/credenciais.

## 15. Frontend

Criar:

```text
/produtos
/produtos/novo
/produtos/[id]
/fornecedores
/fornecedores/novo
/fornecedores/[id]
/compras
/compras/nova
/compras/[id]
```

Liberar **Produtos** no AppShell. Organizar Produtos/Fornecedores/Compras em seção/submenu conforme padrão existente. Produtos visível a todos autenticados; Fornecedores/Compras ADMIN/VENDAS. **Vendas (PDV)** continua “em breve”.

### `/produtos`
Busca, categoria, ativo, abaixo do mínimo, paginação. Tabela md+ e cards mobile. Badges textuais `⚠ Abaixo do mínimo` e `✓ Estoque adequado`.

### `/produtos/novo`
Nome, descrição, categoria, fornecedor padrão, código, custo, venda, mínimo. Sem quantidade inicial.

### `/produtos/[id]`
Abas `?aba=dados` e `?aba=estoque`. Dados + ações. Estoque mostra saldo/mínimo/badge, histórico paginado e **Ajustar estoque** somente ADMIN com confirmação.

### `/fornecedores*`
Lista/busca/status; formulário com endereço. Novo/editar/ativar/desativar somente ADMIN. Ficha pode reutilizar filtro de compras por fornecedor para mostrar recentes.

### `/compras`
Filtros status/fornecedor/datas; tabela/cards. Botão Nova compra só ADMIN.

### `/compras/nova`
Fornecedor, funcionário opcional, data, pagamento, observações e itens dinâmicos. Impedir produto duplicado. Subtotal/total no frontend apenas para UX; backend é fonte de verdade.

### `/compras/[id]`
Detalhes, itens e total. PEDIDO + ADMIN mostra **Receber** e **Cancelar**. Receber exige confirmação/data; cancelar exige confirmação/motivo. Sem transições para RECEBIDO/CANCELADO.

## 16. Dashboard

Adicionar `Produtos abaixo do estoque mínimo`, contando apenas ativos com `quantidade_estoque <= estoque_minimo`. Não carregar produtos para contar. Estender endpoint agregado existente ou criar `GET /api/v1/produtos/resumo-estoque`:

```json
{"produtosAbaixoDoMinimo":7}
```

## 17. Erros/UI

Usar `Aviso`: 400 campos; 401 fluxo global; 403 sem permissão; 404 não encontrado; 409 mensagem API; 500/rede mensagem genérica. Loading/skeleton em listas/detalhes. Estados vazios para produtos, fornecedores, compras e movimentações.

## 18. Regras numeradas

1. Categoria é somente leitura.
2. Produto novo inicia estoque zero.
3. Saldo não é editável no CRUD.
4. Toda mudança de saldo gera movimentação.
5. Movimentação é imutável.
6. Saldo nunca fica negativo.
7. Alteração de saldo usa lock pessimista.
8. Código é opcional e único.
9. Código informado tem 8–14 dígitos.
10. Venda >0; custo >=0.
11. Abaixo do mínimo significa saldo <= mínimo.
12. Produto desativado preserva saldo/histórico.
13. Produto desativado não entra em nova compra.
14. Só ADMIN reativa produto.
15. Ajuste exige motivo e não muda custo.
16. CNPJ válido/único.
17. Fornecedor com PEDIDO aberto não pode ser desativado.
18. Fornecedor inativo não entra em nova compra.
19. Compra possui 1–100 itens.
20. Produto não repete na compra.
21. Total é calculado no backend.
22. Criar PEDIDO não altera estoque.
23. Somente PEDIDO pode ser recebido/cancelado.
24. Recebimento é atômico.
25. Recebimento gera ENTRADA_COMPRA por produto.
26. Recebimento atualiza custo médio.
27. Compra recebida não pode ser cancelada.
28. Cancelamento não altera estoque.
29. Compra criada não é editada nesta etapa.
30. Financeiro não é movimentado no recebimento.
31. Busca rápida retorna somente ativos.
32. SERVICO não acessa fornecedores/compras.
33. Autorização nunca depende apenas do frontend.

## 19. Testes unitários

### ProdutoServiceTest
`deveCriarProdutoComEstoqueZero`, `deveCriarProdutoSemCodigoDeBarras`, `deveRejeitarCodigoDeBarrasDuplicado`, `deveRejeitarCodigoDeBarrasInvalido`, `deveRejeitarPrecoVendaZero`, `deveRejeitarEstoqueMinimoNegativo`, `deveAtualizarDadosPermitidos`, `deveDesativarProdutoPreservandoEstoque`, `deveReativarProduto`, `deveIdentificarProdutoAbaixoDoMinimo`, `deveConsiderarIgualAoMinimoComoAbaixoDoMinimo`.

### EstoqueServiceTest
`deveRealizarAjustePositivo`, `deveRealizarAjusteNegativo`, `deveRejeitarAjusteZero`, `deveRejeitarAjusteSemMotivo`, `deveRejeitarAjusteNegativoMaiorQueSaldo`, `devePreservarPrecoCustoNoAjuste`, `deveRegistrarSaldoAnteriorEResultante`.

### CustoMedioServiceTest
`deveCalcularCustoMedioPonderado`, `deveUsarCustoDaCompraQuandoSaldoAnteriorForZero`, `deveArredondarCustoParaDuasCasasHalfUp`.

### FornecedorServiceTest
`deveCriarFornecedorComCnpjValido`, `deveRejeitarCnpjInvalido`, `deveRejeitarCnpjDuplicado`, `deveRejeitarTelefoneDuplicado`, `deveAtualizarFornecedor`, `deveDesativarFornecedorSemCompraAberta`, `deveImpedirDesativacaoComCompraPedido`, `deveReativarFornecedor`.

### CompraServiceTest
`deveCriarPedidoComItens`, `deveCalcularValorTotalDosItens`, `deveRejeitarCompraSemItens`, `deveRejeitarProdutoDuplicado`, `deveRejeitarFornecedorInativo`, `deveRejeitarProdutoInativo`, `deveReceberCompraPedido`, `deveAtualizarSaldoAoReceber`, `deveAtualizarCustoMedioAoReceber`, `deveCriarMovimentacaoParaCadaProduto`, `deveCancelarCompraPedido`, `deveRejeitarRecebimentoDeCompraRecebida`, `deveRejeitarRecebimentoDeCompraCancelada`, `deveRejeitarCancelamentoDeCompraRecebida`, `deveRejeitarCancelamentoDeCompraCancelada`, `deveFazerRollbackIntegralSeRecebimentoFalhar`.

## 20. Testes de integração

### ProdutoControllerIT
`autenticadoDeveListarProdutos`, `deveBuscarProdutoPorNome`, `deveBuscarProdutoPorCodigoDeBarras`, `deveFiltrarProdutosAbaixoDoMinimo`, `adminDeveCriarProduto`, `vendasDeveCriarProduto`, `servicoNaoDeveCriarProduto`, `deveRetornar409ParaCodigoDuplicado`, `vendasDeveDesativarProduto`, `vendasNaoDeveReativarProduto`, `adminDeveReativarProduto`, `buscaRapidaDeveRetornarSomenteAtivos`, `codigoExatoDeveTerPrioridadeNaBuscaRapida`.

### EstoqueControllerIT
`adminDeveAjustarEstoque`, `vendasNaoDeveAjustarEstoque`, `servicoNaoDeveAjustarEstoque`, `ajusteDeveAtualizarSaldo`, `ajusteDeveCriarMovimentacao`, `ajusteNegativoAlemDoSaldoDeveRetornar409`, `deveListarHistoricoDoProduto`, `doisAjustesConcorrentesNaoDevemPerderAtualizacao`.

Teste concorrente deve provar: `saldoFinal = saldoInicial + somaDosDeltasAceitos`, sem lost update.

### FornecedorControllerIT
`adminDeveCriarFornecedor`, `vendasNaoDeveCriarFornecedor`, `servicoNaoDeveListarFornecedores`, `deveRetornar400ParaCnpjInvalido`, `deveRetornar409ParaCnpjDuplicado`, `deveRetornar409ParaTelefoneDuplicado`, `adminDeveAtualizarFornecedor`, `deveImpedirDesativacaoComCompraAberta`.

### CompraControllerIT
`adminDeveCriarCompra`, `vendasNaoDeveCriarCompra`, `servicoNaoDeveListarCompras`, `valorTotalDeveSerCalculadoNoBackend`, `criarCompraNaoDeveAlterarEstoque`, `adminDeveReceberCompra`, `vendasNaoDeveReceberCompra`, `recebimentoDeveAtualizarSaldo`, `recebimentoDeveAtualizarCustoMedio`, `recebimentoDeveCriarMovimentacaoDeEstoque`, `recebimentoDeveDefinirStatusRecebido`, `recebimentoDuplicadoDeveRetornar409`, `adminDeveCancelarPedido`, `cancelamentoNaoDeveAlterarEstoque`, `compraCanceladaNaoPodeSerRecebida`.

## 21. Testes frontend

Criar:
- `produto-schema.test.ts`: válido, código vazio/inválido, preço zero, mínimo negativo, sem campo saldo.
- `ajuste-estoque-schema.test.ts`: positivo, negativo, zero, motivo curto.
- `fornecedor-schema.test.ts`: válido, CNPJ inválido, e-mail/endereço inválidos.
- `compra-schema.test.ts`: um item, sem itens, duplicado, quantidade/custo zero, total visual.
- `produtos-list.test.tsx`: render, badges, vazio, busca.
- `produto-detalhes.test.tsx`: dados, histórico, permissão ajuste, confirmação desativação.
- `fornecedores-list.test.tsx`: lista/vazio e botão por papel.
- `compra-form.test.tsx`: adicionar/remover, impedir duplicado, subtotal/total.
- `compra-detalhes.test.tsx`: itens/total, ações PEDIDO, nenhuma ação final, confirmações.
- `dashboard-estoque.test.tsx`: contador.

## 22. Segurança e integridade

DTO de produto não aceita saldo; compra não aceita total/status; subtotal do cliente não é fonte de verdade; usar `BigDecimal`, nunca double/float; `HALF_UP`; lock em saldo; transação no recebimento; IDs/CNPJ/código validados; histórico sem edit/delete; backend é fonte de autorização.

## 23. Critérios de aceite

- [ ] V1–V5 intactas; V6 aplica limpa e upgrade.
- [ ] Produto nasce saldo zero; saldo não é editável no cadastro.
- [ ] Código único e busca rápida funcionam.
- [ ] Filtro abaixo do mínimo usa `<=`.
- [ ] Desativação preserva saldo; só ADMIN reativa.
- [ ] Toda alteração de saldo gera histórico.
- [ ] Ajuste exige motivo e não permite negativo.
- [ ] Concorrência não perde atualização.
- [ ] Fornecedor/CNPJ/endereço funcionam.
- [ ] Fornecedor com PEDIDO não desativa.
- [ ] Compra calcula total no backend e não altera estoque ao criar.
- [ ] Recebimento atualiza saldo/custo/histórico atomicamente.
- [ ] Cancelamento não altera estoque.
- [ ] Permissões da matriz são cumpridas.
- [ ] Produtos liberado no menu; PDV continua em breve.
- [ ] Rotas frontend responsivas/acessíveis.
- [ ] Dashboard mostra contador.
- [ ] Auditoria implementada.
- [ ] Testes unitários, IT e frontend PASS.
- [ ] Builds, lint e typecheck PASS.

## 24. Riscos

1. Concorrência entre compra/ajuste/futuro PDV: lock pessimista.
2. Deadlock em compra multi-item: bloquear produtos sempre por ID crescente.
3. Custo médio incorreto: BigDecimal + testes determinísticos.
4. Histórico crescente: índices + paginação.
5. Produto inativo com saldo residual: intencional; ajustar depois se necessário.
6. Financeiro desacoplado: compra recebida não significa paga.

## 25. Próximas etapas

Etapa 05 PDV/Vendas reutilizará `GET /api/v1/produtos/busca-rapida` e o serviço central de estoque para `SAIDA_VENDA` com lock/validação. Financeiro tratará contas a pagar/caixa. Migração do legado será etapa própria e deverá preservar rastreabilidade de saldo inicial.

## 26. Ordem de implementação

1. Inspecionar arquitetura/baseline.
2. Conferir V1–V5 e criar V6.
3. Criar domínio/repositório de movimentação.
4. Serviço central de estoque e custo médio.
5. Categorias leitura.
6. Produtos/busca rápida/ajuste.
7. Fornecedores.
8. Compras/recebimento/cancelamento.
9. Segurança/SecurityConfig/auditoria.
10. Testes unitários e IT.
11. Frontend produtos, fornecedores, compras.
12. AppShell/dashboard.
13. Testes frontend.
14. Testes completos, lint, typecheck, builds.
15. Revisar `git diff`.

## 27. Restrições

NÃO editar V1–V5; NÃO alterar legado; NÃO hard-delete; NÃO editar saldo diretamente; NÃO aceitar saldo/total/status do frontend como verdade; NÃO usar double/float para dinheiro; NÃO permitir saldo negativo; NÃO alterar saldo sem histórico/lock; NÃO recebimento parcial; NÃO editar compra criada; NÃO criar financeiro; NÃO liberar PDV; NÃO CRUD categoria; NÃO imagem de produto; NÃO desabilitar testes/lint; NÃO usar `any`; NÃO commit/push automático.

## 28. Relatório final obrigatório

```text
## Implementação concluída

### Banco
- migration V6
- movimentacao_estoque
- índices

### Produtos
- arquivos/endpoints/busca rápida

### Estoque
- serviço central/locks/ajustes/histórico/custo médio

### Fornecedores
- arquivos/endpoints/CNPJ

### Compras
- criação/total/recebimento/cancelamento

### Segurança
- matriz/SecurityConfig/@PreAuthorize

### Auditoria
- eventos

### Frontend
- rotas/componentes/AppShell/dashboard

### Testes
- unitários: X/Y
- integração: X/Y
- frontend: X/Y

### Validações
- backend build: PASS/FAIL
- frontend build: PASS/FAIL
- lint: PASS/FAIL
- typecheck: PASS/FAIL

### Pendências
- somente pendências reais
```

## 29. Definição de pronto

```text
Produto → cadastro → estoque 0 → ficha → ajuste ADMIN → movimentação

Compra → fornecedor ativo → itens → total backend → PEDIDO
  ├─ cancelar → CANCELADO sem estoque
  └─ receber → locks → custo médio → entradas → RECEBIDO

Etapa 05 → busca rápida → venda → serviço central → SAIDA_VENDA
```

Somente considerar concluído com rastreabilidade integral, concorrência protegida, autorização backend, auditoria, testes e builds verdes.
