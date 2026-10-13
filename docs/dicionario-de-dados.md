# Dicionário de dados — PetConnect Web (MySQL)

> **Gerado a partir do banco real** (`information_schema`) com as migrations Flyway aplicadas até a V10. A fonte da verdade continua sendo [`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration). Para regenerar: `python scripts/gerar-dicionario.py` com o MySQL de desenvolvimento no ar.

Convenções: dinheiro em `DECIMAL(10,2)`; datas e horas em horário local de São Paulo; enums como `VARCHAR` + `CHECK`; sem exclusão física (campo `ativo`). Veja o diagrama em [banco-de-dados.md](banco-de-dados.md).

## Migrations aplicadas

| Versão | Descrição |
|---|---|
| V1 | schema inicial |
| V2 | dados referencia |
| V3 | indices modulo pets |
| V4 | indices agendamentos |
| V5 | seguranca usuarios funcionarios |
| V6 | movimentacao estoque indices |
| V7 | vendas status idempotencia |
| V8 | financeiro caixa contas |
| V9 | indices relatorios |
| V10 | migracao legado |

**30 tabelas** em 8 grupos.

## Acesso

### `papel`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `nome` | `varchar(30)` | não | UK |  |  |
| `descricao` | `varchar(255)` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_papel_nome`: (nome)

### `usuario`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `username` | `varchar(50)` | não | UK |  |  |
| `email` | `varchar(120)` | não | UK |  |  |
| `senha_hash` | `varchar(100)` | não |  |  |  |
| `papel_id` | `bigint` | não | índice |  | FK → `papel.id` |
| `ativo` | `tinyint(1)` | não | índice | 1 |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `token_version` | `int` | não |  | 0 |  |

- **Único** `uk_usuario_email`: (email)
- **Único** `uk_usuario_username`: (username)

### `token_redefinicao_senha`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `usuario_id` | `bigint` | não | índice |  | FK → `usuario.id` |
| `token_hash` | `char(64)` | não | UK |  | SHA-256 do token enviado por e-mail; o token puro nunca é gravado. |
| `expira_em` | `datetime(6)` | não |  |  |  |
| `usado_em` | `datetime(6)` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_token_hash`: (token_hash)

## Pessoas e endereço

### `endereco`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `cep` | `char(8)` | não | índice |  |  |
| `logradouro` | `varchar(150)` | não |  |  |  |
| `numero` | `varchar(20)` | sim |  |  |  |
| `complemento` | `varchar(100)` | sim |  |  |  |
| `bairro` | `varchar(100)` | sim |  |  |  |
| `cidade` | `varchar(100)` | não |  |  |  |
| `uf` | `char(2)` | não |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

### `cliente`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `nome` | `varchar(100)` | não | índice |  |  |
| `cpf` | `char(11)` | sim | UK |  |  |
| `data_nascimento` | `date` | sim |  |  |  |
| `telefone` | `varchar(11)` | não | UK |  |  |
| `email` | `varchar(120)` | sim |  |  |  |
| `endereco_id` | `bigint` | sim | UK |  | FK → `endereco.id` |
| `observacoes` | `text` | sim |  |  |  |
| `ativo` | `tinyint(1)` | não |  | 1 |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_cliente_cpf`: (cpf)
- **Único** `uk_cliente_endereco`: (endereco_id)
- **Único** `uk_cliente_telefone`: (telefone)

### `funcionario`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `usuario_id` | `bigint` | não | UK |  | FK → `usuario.id` |
| `nome` | `varchar(100)` | não | índice |  |  |
| `cpf` | `char(11)` | não | UK |  |  |
| `data_nascimento` | `date` | sim |  |  |  |
| `sexo` | `varchar(10)` | não |  | OUTRO |  |
| `telefone` | `varchar(11)` | sim | UK |  |  |
| `endereco_id` | `bigint` | sim | UK |  | FK → `endereco.id` |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_funcionario_cpf`: (cpf)
- **Único** `uk_funcionario_endereco`: (endereco_id)
- **Único** `uk_funcionario_telefone`: (telefone)
- **Único** `uk_funcionario_usuario`: (usuario_id)
- **Regra** `ck_funcionario_sexo`: `(`sexo` in (_utf8mb4\\'MASCULINO\\',_utf8mb4\\'FEMININO\\',_utf8mb4\\'OUTRO\\'))`

### `fornecedor`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `nome_fantasia` | `varchar(100)` | não |  |  |  |
| `razao_social` | `varchar(150)` | sim |  |  |  |
| `cnpj` | `char(14)` | não | UK |  |  |
| `telefone` | `varchar(11)` | sim | UK |  |  |
| `email` | `varchar(120)` | sim |  |  |  |
| `contato` | `varchar(100)` | sim |  |  |  |
| `endereco_id` | `bigint` | sim | UK |  | FK → `endereco.id` |
| `observacoes` | `text` | sim |  |  |  |
| `ativo` | `tinyint(1)` | não | índice | 1 |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_fornecedor_cnpj`: (cnpj)
- **Único** `uk_fornecedor_endereco`: (endereco_id)
- **Único** `uk_fornecedor_telefone`: (telefone)

## Pets

### `especie`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `nome` | `varchar(50)` | não | UK |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_especie_nome`: (nome)

### `raca`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `especie_id` | `bigint` | não | índice |  | FK → `especie.id` |
| `nome` | `varchar(50)` | não |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_raca_especie_nome`: (especie_id,nome)

### `pet`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `cliente_id` | `bigint` | não | índice |  | FK → `cliente.id` |
| `nome` | `varchar(50)` | não | índice |  |  |
| `especie_id` | `bigint` | não | índice |  | FK → `especie.id` |
| `raca_id` | `bigint` | sim | índice |  | FK → `raca.id` |
| `data_nascimento` | `date` | sim |  |  |  |
| `peso` | `decimal(5,2)` | sim |  |  | kg |
| `cor` | `varchar(30)` | sim |  |  |  |
| `porte` | `varchar(10)` | sim |  |  |  |
| `castrado` | `tinyint(1)` | não |  | 0 |  |
| `foto_url` | `varchar(255)` | sim |  |  |  |
| `observacoes` | `text` | sim |  |  |  |
| `ativo` | `tinyint(1)` | não |  | 1 |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Regra** `ck_pet_porte`: `((`porte` is null) or (`porte` in (_utf8mb4\\'PEQUENO\\',_utf8mb4\\'MEDIO\\',_utf8mb4\\'GRANDE\\')))`

### `vacina`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `especie_id` | `bigint` | não | índice |  | FK → `especie.id` |
| `nome` | `varchar(100)` | não |  |  |  |
| `doenca_protecao` | `varchar(150)` | sim |  |  |  |
| `validade_padrao_meses` | `int` | não |  | 12 |  |
| `preco` | `decimal(10,2)` | sim |  |  |  |
| `observacoes` | `text` | sim |  |  |  |
| `ativo` | `tinyint(1)` | não |  | 1 |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_vacina_especie_nome`: (especie_id,nome)

### `carteira_vacina`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `pet_id` | `bigint` | não | índice |  | FK → `pet.id` |
| `vacina_id` | `bigint` | não | índice |  | FK → `vacina.id` |
| `data_aplicacao` | `date` | não |  |  |  |
| `data_proxima` | `date` | sim | índice |  |  |
| `veterinario` | `varchar(100)` | sim |  |  |  |
| `lote` | `varchar(50)` | sim |  |  |  |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

### `historico_consulta`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `pet_id` | `bigint` | não | índice |  | FK → `pet.id` |
| `data_consulta` | `datetime(6)` | não |  |  |  |
| `veterinario` | `varchar(100)` | não |  |  |  |
| `motivo` | `text` | sim |  |  |  |
| `diagnostico` | `text` | sim |  |  |  |
| `tratamento` | `text` | sim |  |  |  |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

## Serviços e agenda

### `servico`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `nome` | `varchar(100)` | não | UK |  |  |
| `descricao` | `text` | sim |  |  |  |
| `preco` | `decimal(10,2)` | não |  |  | Preço padrão; preco_servico_porte sobrescreve por porte. |
| `duracao_minutos` | `int` | sim |  |  |  |
| `ativo` | `tinyint(1)` | não |  | 1 |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_servico_nome`: (nome)

### `preco_servico_porte`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `servico_id` | `bigint` | não | índice |  | FK → `servico.id` |
| `porte` | `varchar(10)` | não |  |  |  |
| `preco` | `decimal(10,2)` | não |  |  |  |

- **Único** `uk_servico_porte`: (servico_id,porte)
- **Regra** `ck_preco_porte`: `(`porte` in (_utf8mb4\\'PEQUENO\\',_utf8mb4\\'MEDIO\\',_utf8mb4\\'GRANDE\\'))`

### `agendamento`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `pet_id` | `bigint` | não | índice |  | FK → `pet.id` |
| `servico_id` | `bigint` | não | índice |  | FK → `servico.id` |
| `funcionario_id` | `bigint` | sim | índice |  | FK → `funcionario.id` |
| `data_hora` | `datetime(6)` | não | índice |  |  |
| `status` | `varchar(20)` | não | índice | AGENDADO |  |
| `total_estimado` | `decimal(10,2)` | não |  | 0.00 |  |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Regra** `ck_agendamento_status`: `(`status` in (_utf8mb4\\'AGENDADO\\',_utf8mb4\\'CONFIRMADO\\',_utf8mb4\\'EM_ANDAMENTO\\',_utf8mb4\\'CONCLUIDO\\',_utf8mb4\\'CANCELADO\\',_utf8mb4\\'NAO_COMPARECEU\\'))`

### `servico_realizado`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `agendamento_id` | `bigint` | sim | UK |  | FK → `agendamento.id` |
| `pet_id` | `bigint` | não | índice |  | FK → `pet.id` |
| `servico_id` | `bigint` | não | índice |  | FK → `servico.id` |
| `funcionario_id` | `bigint` | sim | índice |  | FK → `funcionario.id` |
| `data_servico` | `datetime(6)` | não | índice | CURRENT_TIMESTAMP(6) | coluna gerada |
| `valor` | `decimal(10,2)` | não |  |  |  |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_realizado_agendamento`: (agendamento_id)

## Produtos, estoque e compras

### `categoria_produto`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `nome` | `varchar(50)` | não | UK |  |  |
| `descricao` | `varchar(255)` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_categoria_nome`: (nome)

### `produto`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `nome` | `varchar(100)` | não | índice |  |  |
| `descricao` | `text` | sim |  |  |  |
| `categoria_id` | `bigint` | não | índice |  | FK → `categoria_produto.id` |
| `fornecedor_padrao_id` | `bigint` | sim | índice |  | FK → `fornecedor.id` |
| `codigo_barras` | `varchar(50)` | sim | UK |  |  |
| `preco_custo` | `decimal(10,2)` | sim |  |  |  |
| `preco_venda` | `decimal(10,2)` | não |  |  |  |
| `quantidade_estoque` | `int` | não |  | 0 |  |
| `estoque_minimo` | `int` | não |  | 5 |  |
| `ativo` | `tinyint(1)` | não | índice | 1 |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_produto_codigo_barras`: (codigo_barras)
- **Regra** `ck_produto_estoque`: `(`quantidade_estoque` >= 0)`

### `movimentacao_estoque`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `produto_id` | `bigint` | não | índice |  | FK → `produto.id` |
| `tipo` | `varchar(30)` | não |  |  |  |
| `quantidade` | `int` | não |  |  |  |
| `saldo_anterior` | `int` | não |  |  |  |
| `saldo_resultante` | `int` | não |  |  |  |
| `compra_id` | `bigint` | sim | índice |  | FK → `compra.id` |
| `venda_id` | `bigint` | sim | índice |  | FK → `venda.id` |
| `usuario_id` | `bigint` | não | índice |  | FK → `usuario.id` |
| `motivo` | `varchar(500)` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Regra** `chk_mov_estoque_quantidade`: `(`quantidade` <> 0)`
- **Regra** `chk_mov_estoque_saldos`: `((`saldo_anterior` >= 0) and (`saldo_resultante` >= 0))`
- **Regra** `chk_mov_estoque_tipo`: `(`tipo` in (_utf8mb4\\'ENTRADA_COMPRA\\',_utf8mb4\\'SAIDA_VENDA\\',_utf8mb4\\'AJUSTE\\',_utf8mb4\\'ESTORNO_VENDA\\'))`

### `compra`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `fornecedor_id` | `bigint` | não | índice |  | FK → `fornecedor.id` |
| `funcionario_id` | `bigint` | sim | índice |  | FK → `funcionario.id` |
| `data_compra` | `date` | não |  |  |  |
| `data_entrega` | `date` | sim |  |  |  |
| `valor_total` | `decimal(10,2)` | não |  |  |  |
| `status` | `varchar(20)` | não | índice | PEDIDO |  |
| `forma_pagamento` | `varchar(50)` | sim |  |  |  |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Regra** `ck_compra_status`: `(`status` in (_utf8mb4\\'PEDIDO\\',_utf8mb4\\'RECEBIDO\\',_utf8mb4\\'CANCELADO\\'))`

### `item_compra`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `compra_id` | `bigint` | não | índice |  | FK → `compra.id` |
| `produto_id` | `bigint` | não | índice |  | FK → `produto.id` |
| `quantidade` | `int` | não |  |  |  |
| `preco_custo_unitario` | `decimal(10,2)` | não |  |  |  |
| `subtotal` | `decimal(12,2)` | sim |  |  | coluna gerada |

- **Regra** `ck_item_compra_qtd`: `(`quantidade` > 0)`

## Vendas

### `venda`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `cliente_id` | `bigint` | sim | índice |  | FK → `cliente.id`; Nulo = venda de balcão sem cliente identificado. |
| `funcionario_id` | `bigint` | sim | índice |  | FK → `funcionario.id` |
| `usuario_id` | `bigint` | sim | índice |  | FK → `usuario.id` |
| `caixa_id` | `bigint` | sim | índice |  | FK → `caixa.id` |
| `data_venda` | `datetime(6)` | não | índice | CURRENT_TIMESTAMP(6) | coluna gerada |
| `valor_total` | `decimal(10,2)` | não |  |  | Soma dos itens menos o desconto, congelada no fechamento. |
| `desconto` | `decimal(10,2)` | não |  | 0.00 |  |
| `valor_recebido` | `decimal(10,2)` | sim |  |  |  |
| `forma_pagamento` | `varchar(20)` | não | índice |  |  |
| `status` | `varchar(20)` | não | índice | CONCLUIDA |  |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `chave_idempotencia` | `char(36)` | sim | UK |  |  |
| `cancelada_em` | `datetime(6)` | sim |  |  |  |
| `cancelada_por` | `bigint` | sim | índice |  | FK → `usuario.id` |
| `motivo_cancelamento` | `varchar(500)` | sim |  |  |  |

- **Único** `uk_venda_idempotencia`: (chave_idempotencia)
- **Regra** `ck_venda_cancelamento`: `(((`status` = _utf8mb4\\'CANCELADA\\') and (`cancelada_em` is not null) and (`motivo_cancelamento` is not null)) or ((`status` = _utf8mb4\\'CONCLUIDA\\') and (`cancelada_em` is null)))`
- **Regra** `ck_venda_pagamento`: `(`forma_pagamento` in (_utf8mb4\\'DINHEIRO\\',_utf8mb4\\'CARTAO_CREDITO\\',_utf8mb4\\'CARTAO_DEBITO\\',_utf8mb4\\'PIX\\',_utf8mb4\\'TRANSFERENCIA\\'))`
- **Regra** `ck_venda_status`: `(`status` in (_utf8mb4\\'CONCLUIDA\\',_utf8mb4\\'CANCELADA\\'))`
- **Regra** `ck_venda_valores`: `((`valor_total` > 0) and (`desconto` >= 0))`

### `item_venda`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `venda_id` | `bigint` | não | índice |  | FK → `venda.id` |
| `produto_id` | `bigint` | não | índice |  | FK → `produto.id` |
| `quantidade` | `int` | não |  |  |  |
| `preco_unitario` | `decimal(10,2)` | não |  |  | Preço no momento da venda. |
| `subtotal` | `decimal(12,2)` | sim |  |  | coluna gerada |

- **Regra** `ck_item_venda_qtd`: `(`quantidade` > 0)`

## Financeiro e caixa

### `caixa`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `usuario_id` | `bigint` | não | índice |  | FK → `usuario.id` |
| `status` | `varchar(20)` | não | índice | ABERTO |  |
| `aberto_em` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `fundo_abertura` | `decimal(10,2)` | não |  | 0.00 |  |
| `fechado_em` | `datetime(6)` | sim |  |  |  |
| `esperado_dinheiro` | `decimal(10,2)` | sim |  |  |  |
| `contado_dinheiro` | `decimal(10,2)` | sim |  |  |  |
| `diferenca_dinheiro` | `decimal(10,2)` | sim |  |  |  |
| `esperado_cartao_credito` | `decimal(10,2)` | sim |  |  |  |
| `contado_cartao_credito` | `decimal(10,2)` | sim |  |  |  |
| `diferenca_cartao_credito` | `decimal(10,2)` | sim |  |  |  |
| `esperado_cartao_debito` | `decimal(10,2)` | sim |  |  |  |
| `contado_cartao_debito` | `decimal(10,2)` | sim |  |  |  |
| `diferenca_cartao_debito` | `decimal(10,2)` | sim |  |  |  |
| `esperado_pix` | `decimal(10,2)` | sim |  |  |  |
| `contado_pix` | `decimal(10,2)` | sim |  |  |  |
| `diferenca_pix` | `decimal(10,2)` | sim |  |  |  |
| `esperado_transferencia` | `decimal(10,2)` | sim |  |  |  |
| `contado_transferencia` | `decimal(10,2)` | sim |  |  |  |
| `diferenca_transferencia` | `decimal(10,2)` | sim |  |  |  |
| `diferenca_total` | `decimal(10,2)` | sim |  |  |  |
| `motivo_diferenca` | `varchar(500)` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `usuario_aberto_id` | `bigint` | sim | UK |  | coluna gerada |

- **Único** `uk_caixa_usuario_aberto`: (usuario_aberto_id)
- **Regra** `ck_caixa_fechamento`: `(((`status` = _utf8mb4\\'ABERTO\\') and (`fechado_em` is null)) or ((`status` = _utf8mb4\\'FECHADO\\') and (`fechado_em` is not null)))`
- **Regra** `ck_caixa_fundo`: `(`fundo_abertura` >= 0)`
- **Regra** `ck_caixa_status`: `(`status` in (_utf8mb4\\'ABERTO\\',_utf8mb4\\'FECHADO\\'))`

### `caixa_movimento`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `caixa_id` | `bigint` | não | índice |  | FK → `caixa.id` |
| `tipo` | `varchar(20)` | não |  |  |  |
| `valor` | `decimal(10,2)` | não |  |  |  |
| `motivo` | `varchar(500)` | não |  |  |  |
| `usuario_id` | `bigint` | não | índice |  | FK → `usuario.id` |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Regra** `ck_caixa_mov_tipo`: `(`tipo` in (_utf8mb4\\'SANGRIA\\',_utf8mb4\\'SUPRIMENTO\\'))`
- **Regra** `ck_caixa_mov_valor`: `(`valor` > 0)`

### `conta_pagar`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `compra_id` | `bigint` | sim | UK |  | FK → `compra.id` |
| `descricao` | `varchar(100)` | não |  |  |  |
| `valor_original` | `decimal(10,2)` | não |  |  |  |
| `saldo` | `decimal(10,2)` | não |  |  |  |
| `data_vencimento` | `date` | não |  |  |  |
| `status` | `varchar(20)` | não | índice | ABERTA |  |
| `paga_em` | `datetime(6)` | sim |  |  |  |
| `forma_pagamento` | `varchar(20)` | sim |  |  |  |
| `usuario_pagamento_id` | `bigint` | sim | índice |  | FK → `usuario.id` |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_conta_pagar_compra`: (compra_id)
- **Regra** `ck_conta_pagar_forma`: `((`forma_pagamento` is null) or (`forma_pagamento` in (_utf8mb4\\'DINHEIRO\\',_utf8mb4\\'CARTAO_CREDITO\\',_utf8mb4\\'CARTAO_DEBITO\\',_utf8mb4\\'PIX\\',_utf8mb4\\'TRANSFERENCIA\\')))`
- **Regra** `ck_conta_pagar_pagamento`: `(((`status` = _utf8mb4\\'ABERTA\\') and (`paga_em` is null) and (`forma_pagamento` is null) and (`usuario_pagamento_id` is null) and (`saldo` = `valor_original`)) or ((`status` = _utf8mb4\\'PAGA\\') and (`paga_em` is not null) and (`forma_pagamento` is not null) and (`usuario_pagamento_id` is not null) and (`saldo` = 0)))`
- **Regra** `ck_conta_pagar_status`: `(`status` in (_utf8mb4\\'ABERTA\\',_utf8mb4\\'PAGA\\'))`
- **Regra** `ck_conta_pagar_valor`: `((`valor_original` > 0) and (`saldo` >= 0))`

### `movimentacao_financeira`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `tipo` | `varchar(10)` | não | índice |  |  |
| `categoria` | `varchar(30)` | não | índice |  |  |
| `descricao` | `varchar(100)` | não |  |  |  |
| `valor` | `decimal(10,2)` | não |  |  |  |
| `forma_pagamento` | `varchar(20)` | sim | índice |  |  |
| `usuario_id` | `bigint` | sim | índice |  | FK → `usuario.id` |
| `caixa_id` | `bigint` | sim | índice |  | FK → `caixa.id` |
| `conta_pagar_id` | `bigint` | sim | índice |  | FK → `conta_pagar.id` |
| `estorno_de_id` | `bigint` | sim | UK |  | FK → `movimentacao_financeira.id` |
| `chave_origem` | `varchar(100)` | sim | UK |  |  |
| `motivo_estorno` | `varchar(500)` | sim |  |  |  |
| `data_movimentacao` | `date` | não | índice |  |  |
| `venda_id` | `bigint` | sim | índice |  | FK → `venda.id` |
| `compra_id` | `bigint` | sim | índice |  | FK → `compra.id` |
| `servico_realizado_id` | `bigint` | sim | índice |  | FK → `servico_realizado.id` |
| `observacoes` | `text` | sim |  |  |  |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_mov_fin_chave`: (chave_origem)
- **Único** `uk_mov_fin_estorno`: (estorno_de_id)
- **Regra** `ck_mov_categoria`: `(`categoria` in (_utf8mb4\\'VENDA_PRODUTO\\',_utf8mb4\\'SERVICO\\',_utf8mb4\\'DESPESA_FUNCIONARIO\\',_utf8mb4\\'DESPESA_OPERACIONAL\\',_utf8mb4\\'COMPRA_MERCADORIA\\',_utf8mb4\\'OUTROS\\'))`
- **Regra** `ck_mov_fin_forma`: `((`forma_pagamento` is null) or (`forma_pagamento` in (_utf8mb4\\'DINHEIRO\\',_utf8mb4\\'CARTAO_CREDITO\\',_utf8mb4\\'CARTAO_DEBITO\\',_utf8mb4\\'PIX\\',_utf8mb4\\'TRANSFERENCIA\\')))`
- **Regra** `ck_mov_tipo`: `(`tipo` in (_utf8mb4\\'ENTRADA\\',_utf8mb4\\'SAIDA\\'))`
- **Regra** `ck_mov_uma_origem`: `((((`venda_id` is not null) + (`compra_id` is not null)) + (`servico_realizado_id` is not null)) <= 1)`
- **Regra** `ck_mov_valor`: `(`valor` > 0)`

## Migração do legado

### `migracao_legado_execucao`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `iniciada_em` | `datetime(6)` | não | índice | CURRENT_TIMESTAMP(6) | coluna gerada |
| `finalizada_em` | `datetime(6)` | sim |  |  |  |
| `modo` | `varchar(20)` | não |  |  |  |
| `status` | `varchar(20)` | não |  |  |  |
| `total_lidos` | `bigint` | não |  | 0 |  |
| `total_migrados` | `bigint` | não |  | 0 |  |
| `total_ajustados` | `bigint` | não |  | 0 |  |
| `total_ignorados` | `bigint` | não |  | 0 |  |
| `total_ja_migrados` | `bigint` | não |  | 0 |  |
| `total_pendencias` | `bigint` | não |  | 0 |  |
| `mensagem` | `varchar(500)` | sim |  |  |  |

- **Regra** `chk_migracao_exec_modo`: `(`modo` in (_utf8mb4\\'DRY_RUN\\',_utf8mb4\\'REAL\\'))`
- **Regra** `chk_migracao_exec_status`: `(`status` in (_utf8mb4\\'EM_ANDAMENTO\\',_utf8mb4\\'CONCLUIDA\\',_utf8mb4\\'FALHOU\\'))`

### `migracao_legado_mapa`

| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |
|---|---|---|---|---|---|
| `id` | `bigint` | não | PK |  | auto incremento |
| `entidade` | `varchar(50)` | não | índice |  |  |
| `id_legado` | `bigint` | não |  |  |  |
| `id_novo` | `bigint` | sim |  |  |  |
| `situacao` | `varchar(20)` | não |  |  |  |
| `motivo` | `varchar(500)` | sim |  |  |  |
| `execucao_id` | `bigint` | não | índice |  | FK → `migracao_legado_execucao.id` |
| `created_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |
| `updated_at` | `datetime(6)` | não |  | CURRENT_TIMESTAMP(6) | coluna gerada |

- **Único** `uk_migracao_mapa_entidade_legado`: (entidade,id_legado)
- **Regra** `chk_migracao_mapa_situacao`: `(`situacao` in (_utf8mb4\\'MIGRADO\\',_utf8mb4\\'IGNORADO\\',_utf8mb4\\'AJUSTADO\\'))`
