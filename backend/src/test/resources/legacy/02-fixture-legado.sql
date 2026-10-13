SET NAMES utf8mb4;
-- Fixture 100% SINTÉTICA do banco legado (etapa 08). Nenhum dado real: nomes genéricos,
-- e-mails em exemplo.test, CPFs/CNPJ gerados só para passar no dígito verificador.
-- Cada linha exercita uma regra da migração (comentário ao lado).

INSERT INTO papel (id, nome) VALUES (1, 'SuperAdmin'), (2, 'FuncionarioVendas'), (3, 'FuncionarioServico');

INSERT INTO usuario (id, usuario, senha_hash, token_senha, email, papel_id, ativo) VALUES
 (1, 'admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'token-antigo-nao-migra', 'dono.legado@exemplo.test', 1, 1), -- colide com o bootstrap
 (2, 'maria.vendas', 'fc0f4331c1012874b06aa53bc30bf7e30610449a7208ff8ec8cf24ad8d61f278', NULL, 'maria.vendas@exemplo.test', 2, 1),
 (3, 'joao.servico', '8eca7d973e18b4763d316f035f60d7ba8992f3f9ba845450717d3fd152e40dc6', NULL, '', 3, 1),                                       -- sem e-mail
 (4, 'ana.caixa', '9c66d97d2ae2b70631b7f5c85baf9c42499d13ec264cd90cf704dfff35151aa2', NULL, 'ana.caixa@exemplo.test', 2, 0);                      -- inativa
INSERT INTO super_usuario (id, usuario_id, cargo) VALUES (1, 1, 'Proprietário');

INSERT INTO funcionario (id, usuario_id, nome, cpf, sexo, telefone, cep, rua, bairro, numero) VALUES
 (1, 2, 'Maria Exemplo', '274.831.956-73', 'Feminino', '(11) 98888-0001', '01001-000', 'Praça da Sé', 'Sé', '1'),
 (2, 3, 'João Exemplo', '111.111.111-11', 'Masculino', NULL, NULL, NULL, NULL, NULL);        -- CPF inválido (obrigatório)

INSERT INTO cliente (id, nome, cpf, telefone, email, cep, rua, bairro, numero, estado, ativo) VALUES
 (1, 'Cliente Um', '613.508.247-71', '(11) 97777-0001', 'cliente.um@exemplo.test', '01310-100', 'Avenida Paulista', 'Bela Vista', '1000', 'SP', 1),
 (2, 'Cliente Dois', '123.456.789-00', '(21) 97777-0002', 'email-invalido', NULL, NULL, NULL, NULL, NULL, 1), -- CPF e e-mail inválidos
 (3, 'Cliente Três', '613.508.247-71', '(31) 97777-0003', NULL, '30130-000', NULL, NULL, NULL, 'Minas Gerais', 1),        -- CPF repetido, endereço incompleto
 (4, 'Cliente Quatro', '840.265.173-90', '(11) 97777-0001', NULL, NULL, NULL, NULL, NULL, NULL, 1);                     -- telefone repetido

INSERT INTO fornecedor (id, nome_fantasia, cnpj, telefone, contato, cep, rua, numero, ativo) VALUES
 (1, 'Fornecedor Exemplo', '45.128.367/0001-23', '(11) 3333-0001', 'Setor Comercial', '04538-133', 'Avenida Faria Lima', '100', 1),
 (2, 'Fornecedor Inválido', '00.000.000/0000-00', NULL, NULL, NULL, NULL, NULL, 1);             -- CNPJ inválido (obrigatório)

INSERT INTO especie (id, nome) VALUES (1, 'Cachorro'), (2, 'GATO'), (3, 'Hamster');              -- sinônimo, caixa, nova
INSERT INTO raca (id, nome, especie_id) VALUES (1, 'labrador', 1), (2, 'Vira-lata', 1), (3, 'siames', 2);
INSERT INTO vacina (id, nome, especie_id, validade_padrao_meses) VALUES
 (1, 'v10 polivalente', 1, 12), (2, 'Antirrábica Hamster', 3, 12);
INSERT INTO servico (id, nome, preco, duracao_media) VALUES (1, 'banho', 35.00, 45), (2, 'Tosa Bebê', 50.00, 40);
INSERT INTO preco_servico_porte (id, servico_id, porte, preco) VALUES
 (1, 1, 'Pequeno', 30.00),  -- já existe no seed: mantém o preço atual
 (2, 2, 'Medio', 55.00),
 (3, 2, 'Gigante', 70.00);  -- porte desconhecido
INSERT INTO categoria_produto (id, nome) VALUES (1, 'Racao'), (2, 'Petiscos');

INSERT INTO pet (id, nome, cliente_id, especie_id, raca_id, peso, castrado, vacinado, foto, porte, ativo) VALUES
 (1, 'Rex', 1, 1, 1, 30.50, 1, 1, 'uploads/pets/rex.png', 'Grande', 1),
 (2, 'Mimi', 2, 2, 3, NULL, 0, 0, 'uploads/pets/nao-existe.jpg', NULL, 1),  -- foto ausente
 (3, 'Bolt', 3, 1, 2, NULL, 0, 0, '../../segredo.txt', 'Enorme', 1),        -- path traversal, porte desconhecido
 (4, 'Tom', 4, 2, NULL, NULL, 0, 0, NULL, 'Pequeno', 1);                     -- tutor não migrado

INSERT INTO carteira_vacina (id, pet_id, nome_vacina, data_aplicacao, data_proxima, veterinario) VALUES
 (1, 1, 'V10 Polivalente', '2019-01-10', '2020-01-10', 'Dra. Exemplo'),
 (2, 1, 'Vacina Experimental X', '2019-02-10', NULL, 'Dra. Exemplo'),       -- cria no catálogo
 (3, 4, 'V10 Polivalente', '2019-02-11', NULL, NULL);                        -- pet não migrado
INSERT INTO historico_consulta (id, pet_id, data_consulta, veterinario, motivo) VALUES
 (1, 1, '2019-03-10 10:00:00', 'Dra. Exemplo', 'Check-up anual');

INSERT INTO produto (id, nome, categoria_id, fornecedor_padrao_id, preco_custo, preco_venda, quantidade_estoque, estoque_minimo, codigo_barras) VALUES
 (1, 'Ração Premium 10kg', 1, 1, 80.00, 120.00, 10, 3, '7899990800011'),
 (2, 'Bola de Borracha', 2, NULL, 5.00, 12.50, 0, 5, '7899990800028'),
 (3, 'Coleira Antiga', 1, 2, 10.00, 25.00, -3, 5, NULL),                   -- saldo negativo, fornecedor não migrado
 (4, 'Petisco Dental', 2, NULL, 3.00, 8.00, 7, 5, '7899990800011');         -- código de barras repetido

INSERT INTO compra (id, fornecedor_id, funcionario_id, data_compra, data_entrega, valor_total, status, forma_pagamento) VALUES
 (1, 1, 1, '2019-02-01', '2019-02-03', 800.00, 'recebido', 'boleto'),
 (2, 2, 1, '2019-02-05', NULL, 100.00, 'pedido', NULL);                      -- fornecedor não migrado
INSERT INTO item_compra (id, compra_id, produto_id, quantidade, preco_custo_unitario, subtotal) VALUES
 (1, 1, 1, 10, 80.00, 800.00), (2, 2, 3, 10, 10.00, 100.00);

INSERT INTO agendamento (id, pet_id, servico_id, funcionario_id, data_agendamento, status, total_estimado) VALUES
 (1, 1, 1, 1, '2019-03-01 09:00:00', 'concluido', 35.00),
 (2, 2, 2, NULL, '2019-03-02 10:00:00', 'Não compareceu', 50.00),
 (3, 3, 1, 2, '2019-03-03 11:00:00', 'em andamento', 45.00),               -- funcionário não migrado
 (4, 1, 2, NULL, '2019-03-04 09:00:00', 'desmarcado', 50.00);                -- status desconhecido
INSERT INTO servico_realizado (id, agendamento_id, pet_id, servico_id, funcionario_id, data_servico, valor) VALUES
 (1, 1, 1, 1, 1, '2019-03-01 10:00:00', 35.00),
 (2, NULL, 2, 2, NULL, '2019-03-05 15:00:00', 55.00);

INSERT INTO venda (id, cliente_id, funcionario_id, data_venda, valor_total, desconto, forma_pagamento) VALUES
 (1, 1, 1, '2019-03-10 14:00:00', 256.00, 0.00, 'pix'),
 (2, NULL, NULL, '2019-03-11 09:00:00', 0.00, 0.00, 'dinheiro'),           -- total zero
 (3, 2, 1, '2019-03-12 10:00:00', 24.00, 0.00, 'cartao_debito'),           -- item sem produto
 (4, 3, 1, '2019-03-13 16:00:00', 20.00, 5.00, NULL);                        -- forma desconhecida
INSERT INTO item_venda (id, venda_id, produto_id, quantidade, preco_unitario, subtotal) VALUES
 (1, 1, 1, 2, 120.00, 240.00), (2, 1, 4, 2, 8.00, 16.00),
 (3, 2, 2, 1, 12.50, 12.50),
 (4, 3, 999, 2, 12.00, 24.00),
 (5, 4, 2, 2, 12.50, 25.00);

INSERT INTO movimentacao_financeira (id, tipo, descricao, valor, data_movimentacao, categoria, referencia_id) VALUES
 (1, 'entrada', 'Venda #1', 256.00, '2019-03-10', 'venda_produto', 1),     -- regenerada pelo backfill
 (2, 'entrada', 'Serviço banho', 35.00, '2019-03-01', 'servico', 1),        -- regenerada pelo backfill
 (3, 'saida', 'Conta de luz', 150.00, '2019-03-15', 'despesa_operacional', NULL),
 (4, 'saida', 'Material de limpeza', 30.00, '2019-03-16', NULL, NULL),     -- categoria desconhecida
 (5, 'saida', 'Compra fornecedor', 800.00, '2019-02-03', 'compra_mercadoria', 1);
