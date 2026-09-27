# 04 — Produtos, Estoque, Fornecedores e Compras: relatório

Branch: `feat/produtos` (criada a partir de `feat/usuarios`; etapas 01–04 seguem **sem commit**, conforme a restrição da especificação).

## Implementação concluída

### Banco
- `V6__movimentacao_estoque_indices.sql` (V1–V5 intocadas).
- `movimentacao_estoque`: `produto_id`, `tipo` (`ENTRADA_COMPRA`/`SAIDA_VENDA`/`AJUSTE`), `quantidade` (≠ 0), `saldo_anterior`, `saldo_resultante` (≥ 0), `compra_id`, `venda_id`, `usuario_id` (obrigatório), `motivo`, `created_at`. FKs para produto, compra, venda e usuário. `CHECK`s no tipo, na quantidade e nos saldos.
- Índices: `idx_mov_estoque_produto_data (produto_id, created_at)`, `idx_produto_categoria_ativo`, `idx_produto_ativo_estoque`, `idx_fornecedor_ativo_nome`, `idx_compra_status_data`, `idx_compra_fornecedor_status`.
- Migração testada sobre o banco local existente (V5 → V6, `Successfully applied 1 migration`).

### Produtos
- Pacote `produto/`: `Produto` (sem setters; `movimentar(delta)` é o único caminho do saldo), `CategoriaProduto` (somente leitura), `ProdutoService`, `ProdutoController`, DTOs `ProdutoRequest` (cadastro, com custo inicial opcional), `ProdutoAtualizacaoRequest` (sem custo), `ProdutoResponse`, `ProdutoRapidoResponse`.
- Endpoints:
  - `GET /api/v1/categorias-produto`;
  - `GET /api/v1/produtos` (q, categoriaId, ativo, abaixoMinimo, paginação);
  - `GET /api/v1/produtos/busca-rapida?q=`;
  - `GET /api/v1/produtos/resumo-estoque`;
  - `GET /api/v1/produtos/{id}`;
  - `POST` e `PUT /api/v1/produtos`;
  - `PATCH /{id}/desativar` e `PATCH /{id}/ativar`.
- Busca rápida: só produtos ativos, mínimo de 2 caracteres, limite de 50, código de barras exato primeiro.
- Código de barras opcional, único, com 8–14 dígitos. Saldo, ativo e timestamps enviados no corpo são ignorados. O produto nasce com saldo 0.

### Estoque
- `EstoqueService` é o **único** ponto que altera `quantidade_estoque`. Sempre trava o produto com `@Lock(PESSIMISTIC_WRITE)` e grava uma `MovimentacaoEstoque` `@Immutable` com saldo anterior e resultante.
- Ajuste manual (`POST /api/v1/produtos/{id}/ajustes-estoque`, só ADMIN): delta ≠ 0, motivo de 5–500 caracteres. Não mexe no custo. Resultado negativo → 409 "O ajuste resultaria em estoque negativo.".
- Histórico: `GET /api/v1/produtos/{id}/movimentacoes?tipo=` paginado (mais recente primeiro), com usuário e origem.
- `CustoMedioService`: média ponderada em `BigDecimal`, 2 casas, `HALF_UP`. Sem saldo anterior (ou sem custo), usa o custo da compra. Exemplo da spec: 10 × 20,00 + 5 × 26,00 = 22,00.
- Concorrência: o IT `doisAjustesConcorrentesNaoDevemPerderAtualizacao` roda 2 threads × 15 ajustes no mesmo produto e prova que `saldoFinal = saldoInicial + Σdeltas`. O último `saldo_resultante` é igual ao saldo do produto.

### Fornecedores
- Pacote `fornecedor/`: `Fornecedor` (endereço 1:1 obrigatório, reaproveitando `EnderecoRequest`), `FornecedorService`, `FornecedorController`.
- Endpoints: `GET /api/v1/fornecedores` (busca por nome, razão social ou dígitos do CNPJ; filtro por ativo), `GET /opcoes` (ativos, para selects), `GET /{id}`, `POST`, `PUT`, `PATCH /{id}/desativar` e `PATCH /{id}/ativar`.
- `@Cnpj` e `Documentos.cnpjValido` (dígitos verificadores; recusa sequências repetidas). CNPJ e telefone únicos → 409.
- Fornecedor com compra `PEDIDO` em aberto não pode ser desativado → 409.

### Compras
- Pacote `compra/`: `Compra`, `ItemCompra` (subtotal calculado em Java; a coluna gerada da V1 não é mapeada), `StatusCompra` (transições permitidas: só `PEDIDO → RECEBIDO` e `PEDIDO → CANCELADO`), `CompraService`, `CompraController`.
- Criação (`POST /api/v1/compras`, ADMIN):
  - fornecedor ativo, funcionário disponível e data de compra até hoje;
  - 1–100 itens, sem produto repetido, só produtos ativos;
  - quantidade de 1–1.000.000 e custo > 0 com 2 casas;
  - o **total é calculado no backend** (total ou status enviados no corpo são ignorados);
  - não altera o estoque.
- Recebimento (`POST /{id}/receber`, ADMIN), numa transação só:
  1. trava a compra e exige a transição para RECEBIDO;
  2. valida a data de entrega (até hoje e não antes da compra);
  3. trava os produtos **em ordem de id** (evita deadlock);
  4. para cada item, uma `ENTRADA_COMPRA` e o recálculo do custo médio;
  5. só então marca `RECEBIDO`.
  Qualquer falha desfaz tudo (teste unitário `deveFazerRollbackIntegralSeRecebimentoFalhar`). Receber de novo → 409.
- Cancelamento (`POST /{id}/cancelar`, ADMIN): motivo de 5–500 caracteres, acrescentado às observações. Não altera estoque. Compra recebida ou cancelada → 409.
- Listagem com filtros de status, fornecedor e período; período invertido → 400 em `dataFim`.

### Segurança
- Matriz aplicada:

  | Recurso | ADMIN | VENDAS | SERVICO |
  |---|---|---|---|
  | Ler produtos, categorias, histórico, busca rápida, resumo | ✅ | ✅ | ✅ |
  | Criar/editar/desativar produto | ✅ | ✅ | ❌ |
  | Reativar produto, ajustar estoque | ✅ | ❌ | ❌ |
  | Ler fornecedores e compras | ✅ | ✅ | ❌ |
  | Escrever fornecedores; criar, receber e cancelar compras | ✅ | ❌ | ❌ |

- `SecurityConfig`: regras por URL para todos os itens acima, para responder 403 **antes** da validação do corpo. Controllers com `@PreAuthorize`, em defesa em profundidade.
- Ajuste e recebimento usam o `uid` do JWT (`UsuarioAtual.id()`) para o usuário da movimentação. O token continua validado contra o banco a cada requisição.

### Auditoria
- MongoDB:
  - produto: `PRODUTO_CRIADO`, `PRODUTO_ATUALIZADO`, `PRODUTO_DESATIVADO` (com o saldo), `PRODUTO_ATIVADO`;
  - estoque: `ESTOQUE_AJUSTADO` (quantidade, saldo anterior/resultante, motivo);
  - fornecedor: `FORNECEDOR_CRIADO`, `FORNECEDOR_ATUALIZADO`, `FORNECEDOR_DESATIVADO`, `FORNECEDOR_ATIVADO`;
  - compra: `COMPRA_CRIADA`, `COMPRA_RECEBIDA`, `COMPRA_CANCELADA` (com motivo).

### Frontend
- Rotas:
  - `/produtos` (busca, categoria, situação, "abaixo do mínimo", paginação);
  - `/produtos/novo`;
  - `/produtos/[id]?aba=dados|estoque`: saldo, mínimo, badge, histórico paginado com filtro por tipo, e **Ajustar estoque** só para ADMIN, com confirmação que mostra o saldo resultante e bloqueia negativo;
  - `/fornecedores`, `/fornecedores/novo`, `/fornecedores/[id]` (com compras recentes);
  - `/compras` (filtros de status, fornecedor e período);
  - `/compras/nova`: itens dinâmicos, busca rápida via route handler `/api/produtos/busca`, duplicado bloqueado, subtotal/total visual em centavos;
  - `/compras/[id]`: receber (data + confirmação) e cancelar (motivo + confirmação) só em PEDIDO e para ADMIN.
  - Skeletons de carregamento nas três seções.
- Componentes: `produtos-lista`, `produto-form`, `produto-detalhes`, `fornecedores-lista`, `fornecedor-form`, `compras-lista`, `compra-form`, `compra-detalhes`, `estoque-badges`, `alternar-ativo`, `dashboard-estoque`.
- Lib: `lib/estoque.ts` (tipos, schemas Zod e dinheiro em centavos), `cnpjValido`/`mascaraCnpj` em `lib/format.ts`, `formatarInstante` (fuso de São Paulo) em `lib/datas.ts`, permissões novas em `lib/permissoes.ts`.
- Server Actions em `app/actions/estoque.ts`.
- AppShell:
  - seção **Estoque**: Produtos para todos; Fornecedores e Compras só para ADMIN/VENDAS;
  - seção **Em breve**: Vendas (PDV) e Relatórios;
  - seção **Administração**: Usuários.
  - O filtro por papel virou `papeis: string[]`.
- Dashboard: card "Produtos abaixo do estoque mínimo" via `GET /api/v1/produtos/resumo-estoque` (contagem no banco, sem carregar produtos), com link para a lista filtrada.
- Responsivo: tabela no md+ e cartões no celular. Conferido a 375 px sem rolagem horizontal.

### Testes
- **unitários: 211/211** (46 novos):
  - `CustoMedioServiceTest` 3;
  - `ProdutoServiceTest` 11;
  - `EstoqueServiceTest` 7;
  - `FornecedorServiceTest` 8;
  - `CompraServiceTest` 17 (os 16 da spec + `deveRejeitarDatasInvalidasNoRecebimento`).
- **integração: 158/158** (45 novos):
  - `ProdutoControllerIT` 13;
  - `EstoqueControllerIT` 8;
  - `FornecedorControllerIT` 8;
  - `CompraControllerIT` 16 (os 15 da spec + `validacoesDaCompra`).
- **frontend: 138/138** (41 novos, nos 10 arquivos do §21):
  - `produto-schema`, `ajuste-estoque-schema`, `fornecedor-schema`, `compra-schema`;
  - `produtos-list`, `produto-detalhes`, `fornecedores-list`, `compra-form`, `compra-detalhes`, `dashboard-estoque`.

### Validações
- backend build (`./mvnw verify`, com JaCoCo): **PASS**
- frontend build (`next build`): **PASS**
- lint (`eslint --max-warnings=0`): **PASS**
- typecheck (`next typegen && tsc --noEmit`): **PASS**
- Docker Compose (`up -d --build`, migração V5 → V6): **PASS**. Na primeira tentativa o build da imagem web falhou só porque o download da fonte Geist no Google Fonts deu erro de rede; o rebuild passou.
- E2E manual (API + navegador em http://localhost:3010, ADMIN):
  - produto novo nasce com saldo 0 mesmo com `quantidadeEstoque: 999` no corpo;
  - compra com `valorTotal`/`status` falsos no corpo → PEDIDO, total 260,00 calculado;
  - pedido não altera saldo; recebimento → saldo 10, custo 26,00; receber de novo e cancelar recebida → 409;
  - ajuste −6 → 4; ajuste −50 → 409 com a mensagem da spec;
  - desativar fornecedor com PEDIDO → 409;
  - pela interface: ajuste +8 com confirmação (4 → 12); recebimento de 3 × 30,33 → saldo 15 e custo médio **26,87** (= (12 × 26,00 + 3 × 30,33) / 15);
  - nova compra: subtotal 3 × 0,10 = R$ 0,30 exato, duplicado bloqueado, validação sem fornecedor/pagamento;
  - dashboard com contador 1 enquanto o saldo ficou em 4 ≤ 5;
  - layout mobile.

### Divergências e decisões (em relação à spec)
- **Tamanhos de texto** seguem as colunas reais da V1, que não pode ser editada: `produto.nome` 100 (spec: 150); `fornecedor.nome_fantasia` 100 (150), `razao_social` 150 (200), `email` 120 (150), `contato` 100 (150). Ampliar exigiria uma migration própria.
- `movimentacao_estoque.created_at` é `DATETIME(6)` (spec: `DATETIME`), para ordenar com precisão movimentações do mesmo segundo.
- `idx_mov_estoque_compra` não foi criado: o índice automático da FK `compra_id` é equivalente.
- `valor_total` limitado a 99.999.999,99 (precisão da coluna); acima disso → 400 em `itens`.
- Receber um PEDIDO cujo produto foi desativado depois do pedido **é permitido**: a mercadoria chegou e precisa entrar no estoque. Desativado só bloqueia **novas** compras.
- Referência inexistente ou inativa no corpo (categoria, fornecedor, funcionário, produto do item) → 400 no campo (`CampoInvalidoException`), não 404, no padrão das etapas anteriores.
- Custo: só é informado no cadastro (opcional). A edição não aceita custo (DTO separado); depois ele só muda pelo custo médio das compras. A ficha mostra "Custo médio" somente leitura.

### Pendências
- Nenhuma pendência funcional da etapa.
- Operacional: etapas 01–04 continuam **sem commit** na branch `feat/produtos` (restrição das specs). O commit ou PR depende de autorização explícita.

---

## Handoff para a etapa 05: prompt para o ChatGPT

A próxima etapa natural é o **PDV / Vendas**, que consome a busca rápida e a baixa de estoque desta etapa.

````text
Você é o arquiteto de software do projeto PetConnect Web. Gere UM arquivo Markdown chamado `05-pdv-vendas.md`, que será executado por outro agente de IA (Claude Code) direto no repositório. Seja específico e verificável: nada de "a definir". Responda somente com o conteúdo do arquivo, dentro de um único bloco ```markdown. Mantenha o formato das etapas anteriores: objetivo, análise obrigatória, decisões arquiteturais justificadas (DA-xx), escopo, fora de escopo, banco, API endpoint a endpoint com JSON, regras numeradas, autorização em tabela, auditoria, frontend, testes com nomes de casos (unitários, IT e frontend), critérios de aceite, riscos, ordem de implementação, restrições e relatório final.

## Contexto do projeto (estado atual)
- Monorepo: `backend/` (Spring Boot 4.1, Java 21, monólito modular `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`), `frontend/` (Next.js 16 App Router, Server Actions, Zod 4, Tailwind 4; BFF com JWT em cookie httpOnly), `legacy/php/` (só referência, não alterar).
- MySQL 8.4 com Flyway (aplicadas V1–V6; nunca editar migration aplicada). MongoDB só para auditoria (`AuditoriaService.registrar`).
- Padrões: entidades sem setters públicos; exclusão lógica; `NotFoundException` 404, `ConflictException` 409, `CampoInvalidoException(campo,msg)` 400 com `errors`; `PageResponse<T>`; `Clock` America/Sao_Paulo; `@PreAuthorize` + regras por URL no `SecurityConfig` (403 antes da validação); `@Lock(PESSIMISTIC_WRITE)` onde há concorrência; `BigDecimal` com HALF_UP para dinheiro; `UsuarioAtual.id()` lê o `uid` do JWT; testes `*Test` (Mockito) e `*IT` (Testcontainers; bases `PetIntegrationTest` e `EstoqueIntegrationTest` com `comPapel`, `login`/`bearer`, `criarProduto`, `ajustar`, `criarCliente`, `cnpjAleatorio`).
- Papéis: ADMIN, VENDAS, SERVICO. JWT validado contra o banco a cada requisição.
- Frontend: `AppShell` (seções Estoque / Em breve / Administração; "Vendas (PDV)" ainda "em breve"; itens com `papeis`), `Campo`, `Aviso`, `AlternarAtivo`, `BotaoConfirmar`, `useActionState` + Zod, mobile-first (tabela md+, cartões no celular), dinheiro no front em centavos (`paraCentavos`, `moedaCentavos`), `formatarInstante`.

## O que a etapa 04 entregou (base para o PDV)
- `produto` com `quantidade_estoque` (CHECK >= 0), `preco_venda`, `preco_custo` (custo médio ponderado), `ativo`, `codigo_barras` único.
- `EstoqueService` é o ÚNICO ponto que altera saldo: trava o produto (PESSIMISTIC_WRITE), recusa saldo negativo (409) e grava `movimentacao_estoque` imutável (tipo ENTRADA_COMPRA | SAIDA_VENDA | AJUSTE, quantidade, saldo_anterior, saldo_resultante, compra_id, **venda_id** (já existe, FK para `venda`), usuario_id obrigatório, motivo). `SAIDA_VENDA` já é aceito pelo CHECK, mas ainda não é gerado por nenhum fluxo.
- Recebimento de compra trava vários produtos **em ordem de id** (`ProdutoRepository.travarEmOrdem`) para evitar deadlock: o mesmo padrão deve ser usado na venda.
- `GET /api/v1/produtos/busca-rapida?q=` (só ativos, código exato primeiro, retorna id, nome, codigoBarras, precoVenda, quantidadeEstoque, disponivel) e route handler do front `/api/produtos/busca`.
- `GET /api/v1/produtos/resumo-estoque` no dashboard.

## Tabelas já existentes na V1 (verificar colunas exatas nos arquivos de migration antes de decidir)
- `venda` (cliente opcional, funcionário/usuário, data, valor total, desconto, forma de pagamento, status) e `item_venda` (produto, quantidade, preço unitário, subtotal), além de `movimentacao_financeira` com FK opcional para venda/compra/servico_realizado. Decida o que falta (se algo) numa V7 mínima e justifique; não editar V1–V6.

## Legado (PHP) relevante
- Telas e scripts de PDV/vendas em `legacy/php/` (procure por `pdv`, `venda`, `carrinho`, `finalizar`): usavam `produtos_buscar_rapido.php` e baixavam estoque editando a quantidade diretamente.

## O que a especificação `05-pdv-vendas.md` deve decidir e cobrir
1. Escopo: tela de PDV (frente de caixa) mobile-first e usável com leitor de código de barras (Enter adiciona), carrinho no cliente, cliente opcional (reaproveitar busca de tutor), desconto (decidir regra: valor/percentual, limite, quem pode), forma de pagamento, finalização atômica, cancelamento de venda (decidir: só ADMIN, janela de tempo, estorno de estoque como ENTRADA/AJUSTE ou novo tipo — justificar, lembrando que o CHECK da V6 só aceita os 3 tipos), listagem e detalhe de vendas, comprovante simples (imprimível).
2. Decisões justificadas: preço vem sempre do backend (preço atual do produto no momento da venda, congelado no item); total e desconto calculados no backend; baixa de estoque via `EstoqueService` (novo método `registrarSaidaVenda`) com lock em ordem de id e SAIDA_VENDA por item; venda com produto sem saldo suficiente falha inteira (409 indicando o produto); idempotência contra duplo clique/reenvio (ex.: chave de idempotência do cliente) — decidir e especificar; se a venda gera `movimentacao_financeira` agora ou fica para a etapa de financeiro.
3. Banco: V7 só com o necessário (índices de venda por data/status/cliente, chave de idempotência se adotada), sem editar V1–V6.
4. API endpoint a endpoint com JSON, validações, papéis (sugestão: ADMIN e VENDAS vendem; SERVICO não; cancelamento só ADMIN) e códigos de erro.
5. Frontend: liberar "Vendas (PDV)" no AppShell; `/pdv` (busca/scan, carrinho com quantidade, remover, subtotal/total visual em centavos, desconto, cliente opcional, pagamento, finalizar com confirmação, feedback de estoque insuficiente), `/vendas` (lista com filtros de período/status/pagamento), `/vendas/[id]` (detalhe, comprovante, cancelar com motivo e confirmação); dashboard com vendas do dia (contagem e total) via endpoint agregado.
6. Auditoria: VENDA_REALIZADA, VENDA_CANCELADA (com motivo), sem dados sensíveis.
7. Testes nominais: unitários (cálculo de total/desconto, baixa de estoque, estoque insuficiente com rollback integral, cancelamento/estorno, idempotência), IT (permissões, venda atualiza saldo e gera SAIDA_VENDA com venda_id, concorrência: duas vendas simultâneas do último item — só uma passa, saldo nunca negativo), frontend (carrinho, desconto, leitor de código, confirmação).
8. Critérios de aceite, riscos e o que fica para depois: financeiro (caixa, contas a pagar/receber a partir de compras e vendas), relatórios, migração de dados do legado.
````
