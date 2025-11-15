-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1:3307
-- Tempo de geração: 15/11/2025 às 08:35
-- Versão do servidor: 10.4.32-MariaDB
-- Versão do PHP: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Banco de dados: `petshop_db`
--

-- --------------------------------------------------------

--
-- Estrutura para tabela `agendamento`
--

CREATE TABLE `agendamento` (
  `id` int(11) NOT NULL,
  `pet_id` int(11) NOT NULL COMMENT 'FK: Qual pet está agendado (exclusão em cascata).',
  `servico_id` int(11) NOT NULL COMMENT 'FK: Qual serviço foi agendado.',
  `funcionario_id` int(11) DEFAULT NULL COMMENT 'FK: Funcionário designado. Pode ser nulo.',
  `data_agendamento` datetime NOT NULL COMMENT 'Data e hora marcadas para o agendamento.',
  `status` enum('agendado','confirmado','em_andamento','concluido','cancelado','nao_compareceu') DEFAULT 'agendado' COMMENT 'Situação atual do agendamento.',
  `observacoes` text DEFAULT NULL COMMENT 'Notas adicionais sobre o agendamento.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Gerencia compromissos para serviços de pets (banho, tosa, consulta).';

-- --------------------------------------------------------

--
-- Estrutura para tabela `carteira_vacina`
--

CREATE TABLE `carteira_vacina` (
  `id` int(11) NOT NULL,
  `pet_id` int(11) NOT NULL COMMENT 'FK: O pet que recebeu a vacina (exclusão em cascata).',
  `nome_vacina` varchar(100) NOT NULL COMMENT 'Nome da vacina aplicada.',
  `data_aplicacao` date NOT NULL COMMENT 'Data em que a vacina foi aplicada.',
  `data_proxima` date DEFAULT NULL COMMENT 'Data recomendada para a próxima dose ou reforço.',
  `veterinario` varchar(100) DEFAULT NULL COMMENT 'Nome do veterinário responsável.',
  `observacoes` text DEFAULT NULL COMMENT 'Notas sobre a reação ou lote.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Armazena o histórico de vacinação de cada pet.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `categoria_produto`
--

CREATE TABLE `categoria_produto` (
  `id` int(11) NOT NULL,
  `nome` varchar(50) NOT NULL COMMENT 'Nome da categoria (Ex: Ração, Brinquedo).',
  `descricao` text DEFAULT NULL COMMENT 'Descrição detalhada da categoria.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Define as categorias para organização dos produtos.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `cliente`
--

CREATE TABLE `cliente` (
  `id` int(11) NOT NULL,
  `nome` varchar(100) NOT NULL COMMENT 'Nome completo do cliente.',
  `cpf` varchar(14) DEFAULT NULL COMMENT 'Número de CPF (único).',
  `data_nascimento` date DEFAULT NULL COMMENT 'Data de nascimento.',
  `telefone` varchar(15) NOT NULL COMMENT 'Telefone de contato.',
  `email` varchar(100) DEFAULT NULL COMMENT 'Endereço de e-mail.',
  `cep` varchar(10) DEFAULT NULL,
  `rua` varchar(150) DEFAULT NULL,
  `bairro` varchar(100) DEFAULT NULL,
  `numero` varchar(20) DEFAULT NULL,
  `complemento` varchar(100) DEFAULT NULL,
  `observacoes` text DEFAULT NULL,
  `ativo` tinyint(1) DEFAULT 1 COMMENT 'Indica se o cliente está ativo (1=Sim, 0=Não).',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Dados cadastrais dos proprietários dos pets.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `compra`
--

CREATE TABLE `compra` (
  `id` int(11) NOT NULL,
  `fornecedor_id` int(11) NOT NULL COMMENT 'FK: Fornecedor de quem a compra foi feita.',
  `funcionario_id` int(11) DEFAULT NULL COMMENT 'FK: Funcionário que realizou o pedido.',
  `data_compra` date NOT NULL COMMENT 'Data em que a compra foi registrada.',
  `data_entrega` date DEFAULT NULL,
  `valor_total` decimal(10,2) NOT NULL COMMENT 'Valor total da compra.',
  `status` enum('pedido','recebido','cancelado') DEFAULT 'pedido' COMMENT 'Situação da compra (pedido, recebido, cancelado).',
  `forma_pagamento` varchar(50) DEFAULT NULL,
  `observacoes` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Registra pedidos de compra de mercadorias a fornecedores.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `especie`
--

CREATE TABLE `especie` (
  `id` int(11) NOT NULL,
  `nome` varchar(50) NOT NULL COMMENT 'Nome da espécie.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Lista as espécies de pets atendidas (Cão, Gato, etc.).';

-- --------------------------------------------------------

--
-- Estrutura para tabela `fornecedor`
--

CREATE TABLE `fornecedor` (
  `id` int(11) NOT NULL,
  `nome_fantasia` varchar(100) NOT NULL COMMENT 'Nome comercial do fornecedor.',
  `razao_social` varchar(150) DEFAULT NULL,
  `cnpj` varchar(18) NOT NULL COMMENT 'Número de CNPJ (único).',
  `telefone` varchar(15) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `contato` varchar(100) DEFAULT NULL,
  `cep` varchar(10) DEFAULT NULL,
  `rua` varchar(150) DEFAULT NULL,
  `bairro` varchar(100) DEFAULT NULL,
  `numero` varchar(20) DEFAULT NULL,
  `complemento` varchar(100) DEFAULT NULL,
  `observacoes` text DEFAULT NULL,
  `ativo` tinyint(1) DEFAULT 1 COMMENT 'Indica se o fornecedor está ativo.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Dados cadastrais das empresas fornecedoras de produtos.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `funcionario`
--

CREATE TABLE `funcionario` (
  `id` int(11) NOT NULL,
  `usuario_id` int(11) NOT NULL COMMENT 'FK: Vínculo com a conta de acesso ao sistema (único).',
  `nome` varchar(100) NOT NULL COMMENT 'Nome completo do funcionário.',
  `cpf` varchar(14) NOT NULL COMMENT 'CPF do funcionário (único).',
  `data_nascimento` date DEFAULT NULL,
  `sexo` enum('Masculino','Feminino','Outro') NOT NULL DEFAULT 'Outro',
  `telefone` varchar(15) DEFAULT NULL,
  `cep` varchar(10) DEFAULT NULL,
  `rua` varchar(150) DEFAULT NULL,
  `bairro` varchar(100) DEFAULT NULL,
  `numero` varchar(20) DEFAULT NULL,
  `complemento` varchar(100) DEFAULT NULL,
  `observacoes` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Dados pessoais dos funcionários, vinculados a um usuário de sistema.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `historico_consulta`
--

CREATE TABLE `historico_consulta` (
  `id` int(11) NOT NULL,
  `pet_id` int(11) NOT NULL COMMENT 'FK: O pet que foi atendido (exclusão em cascata).',
  `data_consulta` datetime NOT NULL,
  `veterinario` varchar(100) NOT NULL COMMENT 'Nome do veterinário responsável.',
  `motivo` text DEFAULT NULL COMMENT 'Motivo da consulta.',
  `diagnostico` text DEFAULT NULL COMMENT 'Diagnóstico do veterinário.',
  `tratamento` text DEFAULT NULL COMMENT 'Tratamento prescrito.',
  `observacoes` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Histórico detalhado de atendimentos veterinários.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `item_compra`
--

CREATE TABLE `item_compra` (
  `id` int(11) NOT NULL,
  `compra_id` int(11) NOT NULL COMMENT 'FK: Pedido de compra ao qual o item pertence (exclusão em cascata).',
  `produto_id` int(11) NOT NULL COMMENT 'FK: Produto comprado.',
  `quantidade` int(11) NOT NULL COMMENT 'Quantidade comprada deste produto.',
  `preco_custo_unitario` decimal(10,2) NOT NULL COMMENT 'Preço pago por uma unidade do produto.',
  `subtotal` decimal(10,2) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Detalhes dos produtos comprados em um pedido de compra.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `item_venda`
--

CREATE TABLE `item_venda` (
  `id` int(11) NOT NULL,
  `venda_id` int(11) NOT NULL COMMENT 'FK: Venda à qual o item pertence (exclusão em cascata).',
  `produto_id` int(11) NOT NULL COMMENT 'FK: Produto vendido.',
  `quantidade` int(11) NOT NULL COMMENT 'Quantidade vendida deste produto.',
  `preco_unitario` decimal(10,2) NOT NULL COMMENT 'Preço de venda unitário no momento da transação.',
  `subtotal` decimal(10,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Detalhes dos produtos vendidos em uma transação.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `movimentacao_financeira`
--

CREATE TABLE `movimentacao_financeira` (
  `id` int(11) NOT NULL,
  `tipo` enum('entrada','saida') NOT NULL COMMENT 'Tipo de movimento: Entrada (receita) ou Saída (despesa).',
  `descricao` varchar(100) NOT NULL COMMENT 'Descrição resumida da movimentação.',
  `valor` decimal(10,2) NOT NULL COMMENT 'Valor da transação.',
  `data_movimentacao` date NOT NULL,
  `categoria` enum('venda_produto','servico','despesa_funcionario','despesa_operacional','compra_mercadoria','outros') DEFAULT NULL COMMENT 'Categoria financeira da movimentação.',
  `referencia_id` int(11) DEFAULT NULL,
  `observacoes` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Registro de todas as entradas e saídas de dinheiro.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `papel`
--

CREATE TABLE `papel` (
  `id` int(11) NOT NULL,
  `nome` varchar(50) NOT NULL COMMENT 'Nome do papel (Ex: SuperAdmin, FuncionarioVendas).',
  `descricao` text DEFAULT NULL COMMENT 'Permissões e responsabilidades do papel.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Define os níveis de acesso (funções/roles) dos usuários no sistema.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `pet`
--

CREATE TABLE `pet` (
  `id` int(11) NOT NULL,
  `nome` varchar(50) NOT NULL,
  `cliente_id` int(11) NOT NULL COMMENT 'FK: Proprietário do pet (exclusão em cascata).',
  `especie_id` int(11) NOT NULL COMMENT 'FK: Espécie do pet (Cão, Gato, etc.).',
  `raca_id` int(11) DEFAULT NULL COMMENT 'FK: Raça do pet. Pode ser nulo.',
  `data_nascimento` date DEFAULT NULL,
  `peso` decimal(5,2) DEFAULT NULL COMMENT 'Peso em kg.',
  `cor` varchar(30) DEFAULT NULL,
  `castrado` tinyint(1) DEFAULT 0 COMMENT 'Indicador de castração (1=Sim, 0=Não).',
  `vacinado` tinyint(1) DEFAULT 0 COMMENT 'Indicador de vacinação (1=Sim, 0=Não).',
  `observacoes` text DEFAULT NULL,
  `foto` varchar(255) DEFAULT NULL,
  `ativo` tinyint(1) DEFAULT 1 COMMENT 'Indica se o pet está ativo no cadastro.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `porte` enum('Pequeno','Medio','Grande') DEFAULT NULL COMMENT 'Porte do animal, relevante principalmente para cães.'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Dados cadastrais dos animais.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `preco_servico_porte`
--

CREATE TABLE `preco_servico_porte` (
  `id` int(11) NOT NULL,
  `servico_id` int(11) NOT NULL,
  `porte` enum('Pequeno','Medio','Grande') NOT NULL,
  `preco` decimal(10,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estrutura para tabela `produto`
--

CREATE TABLE `produto` (
  `id` int(11) NOT NULL,
  `nome` varchar(100) NOT NULL,
  `descricao` text DEFAULT NULL,
  `categoria_id` int(11) NOT NULL COMMENT 'FK: Categoria do produto.',
  `fornecedor_padrao_id` int(11) DEFAULT NULL COMMENT 'FK: Fornecedor preferencial para este produto. Pode ser nulo.',
  `preco_custo` decimal(10,2) DEFAULT NULL COMMENT 'Preço médio de custo.',
  `preco_venda` decimal(10,2) NOT NULL COMMENT 'Preço de venda ao público.',
  `quantidade_estoque` int(11) DEFAULT 0 COMMENT 'Quantidade atual no estoque.',
  `estoque_minimo` int(11) DEFAULT 5 COMMENT 'Alerta para reposição de estoque.',
  `codigo_barras` varchar(50) DEFAULT NULL,
  `ativo` tinyint(1) DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Catálogo e estoque de produtos para venda.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `raca`
--

CREATE TABLE `raca` (
  `id` int(11) NOT NULL,
  `nome` varchar(50) NOT NULL,
  `especie_id` int(11) NOT NULL COMMENT 'FK: Espécie à qual a raça pertence (exclusão em cascata).',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Lista as raças de pets disponíveis, vinculadas à espécie.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `servico`
--

CREATE TABLE `servico` (
  `id` int(11) NOT NULL,
  `nome` varchar(100) NOT NULL,
  `descricao` text DEFAULT NULL,
  `preco` decimal(10,2) NOT NULL COMMENT 'Preço padrão do serviço.',
  `duracao_media` int(11) DEFAULT NULL COMMENT 'Tempo médio de duração em minutos.',
  `ativo` tinyint(1) DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Catálogo de serviços oferecidos (banho, tosa, consultas).';

-- --------------------------------------------------------

--
-- Estrutura para tabela `servico_realizado`
--

CREATE TABLE `servico_realizado` (
  `id` int(11) NOT NULL,
  `agendamento_id` int(11) DEFAULT NULL COMMENT 'FK: Vínculo opcional com um agendamento prévio.',
  `pet_id` int(11) NOT NULL COMMENT 'FK: Pet que recebeu o serviço.',
  `servico_id` int(11) NOT NULL COMMENT 'FK: Serviço que foi realizado.',
  `funcionario_id` int(11) DEFAULT NULL,
  `data_servico` datetime DEFAULT current_timestamp(),
  `valor` decimal(10,2) NOT NULL COMMENT 'Valor final cobrado pelo serviço.',
  `observacoes` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Registro de um serviço que foi efetivamente concluído.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `super_usuario`
--

CREATE TABLE `super_usuario` (
  `id` int(11) NOT NULL,
  `usuario_id` int(11) NOT NULL COMMENT 'FK: Usuário com permissão de SuperAdmin (único).',
  `cargo` varchar(50) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Vínculo de usuários com o papel de SuperAdmin.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `usuario`
--

CREATE TABLE `usuario` (
  `id` int(11) NOT NULL,
  `usuario` varchar(50) NOT NULL COMMENT 'Nome de usuário (login).',
  `senha_hash` varchar(255) NOT NULL COMMENT 'Hash da senha para segurança.',
  `token_senha` varchar(255) DEFAULT NULL,
  `token_expira` datetime DEFAULT NULL,
  `email` varchar(100) NOT NULL COMMENT 'Email de contato (único).',
  `papel_id` int(11) NOT NULL COMMENT 'FK: Nível de permissão/acesso no sistema.',
  `ativo` tinyint(1) DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Contas de acesso ao sistema.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `vacina`
--

CREATE TABLE `vacina` (
  `id` int(11) NOT NULL,
  `nome` varchar(100) NOT NULL COMMENT 'Nome comercial da vacina (Ex: V8, Raiva, Gripe).',
  `doenca_protecao` varchar(150) DEFAULT NULL COMMENT 'Doença(s) que a vacina previne.',
  `validade_padrao_meses` int(11) DEFAULT 12 COMMENT 'Validade padrão em meses para a próxima dose/reforço.',
  `preco` decimal(10,2) DEFAULT NULL COMMENT 'Preço de venda sugerido.',
  `observacoes` text DEFAULT NULL,
  `ativo` tinyint(1) DEFAULT 1 COMMENT 'Indica se a vacina está ativa no catálogo.',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `especie_id` int(11) NOT NULL COMMENT 'FK: Espécie do pet para a qual a vacina se aplica.'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Catálogo de vacinas disponíveis para aplicação.';

-- --------------------------------------------------------

--
-- Estrutura para tabela `venda`
--

CREATE TABLE `venda` (
  `id` int(11) NOT NULL,
  `cliente_id` int(11) DEFAULT NULL COMMENT 'FK: Cliente que realizou a compra. Pode ser nulo (venda anônima).',
  `funcionario_id` int(11) DEFAULT NULL,
  `data_venda` datetime DEFAULT current_timestamp(),
  `valor_total` decimal(10,2) NOT NULL COMMENT 'Valor total da venda.',
  `desconto` decimal(10,2) DEFAULT 0.00 COMMENT 'Valor total de desconto aplicado.',
  `forma_pagamento` enum('dinheiro','cartao_credito','cartao_debito','pix','transferencia') DEFAULT NULL COMMENT 'Forma de pagamento utilizada.',
  `observacoes` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Registro de vendas de produtos e/ou serviços.';

--
-- Índices para tabelas despejadas
--

--
-- Índices de tabela `agendamento`
--
ALTER TABLE `agendamento`
  ADD PRIMARY KEY (`id`),
  ADD KEY `pet_id` (`pet_id`),
  ADD KEY `servico_id` (`servico_id`),
  ADD KEY `funcionario_id` (`funcionario_id`);

--
-- Índices de tabela `carteira_vacina`
--
ALTER TABLE `carteira_vacina`
  ADD PRIMARY KEY (`id`),
  ADD KEY `pet_id` (`pet_id`);

--
-- Índices de tabela `categoria_produto`
--
ALTER TABLE `categoria_produto`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `nome_unico` (`nome`);

--
-- Índices de tabela `cliente`
--
ALTER TABLE `cliente`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `telefone_unico` (`telefone`),
  ADD UNIQUE KEY `cpf` (`cpf`);

--
-- Índices de tabela `compra`
--
ALTER TABLE `compra`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fornecedor_id` (`fornecedor_id`),
  ADD KEY `funcionario_id` (`funcionario_id`);

--
-- Índices de tabela `especie`
--
ALTER TABLE `especie`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `nome_unico` (`nome`);

--
-- Índices de tabela `fornecedor`
--
ALTER TABLE `fornecedor`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `cnpj` (`cnpj`),
  ADD UNIQUE KEY `telefone_unico` (`telefone`);

--
-- Índices de tabela `funcionario`
--
ALTER TABLE `funcionario`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `usuario_id` (`usuario_id`),
  ADD UNIQUE KEY `cpf` (`cpf`),
  ADD UNIQUE KEY `telefone_unico` (`telefone`);

--
-- Índices de tabela `historico_consulta`
--
ALTER TABLE `historico_consulta`
  ADD PRIMARY KEY (`id`),
  ADD KEY `pet_id` (`pet_id`);

--
-- Índices de tabela `item_compra`
--
ALTER TABLE `item_compra`
  ADD PRIMARY KEY (`id`),
  ADD KEY `compra_id` (`compra_id`),
  ADD KEY `produto_id` (`produto_id`);

--
-- Índices de tabela `item_venda`
--
ALTER TABLE `item_venda`
  ADD PRIMARY KEY (`id`),
  ADD KEY `venda_id` (`venda_id`),
  ADD KEY `produto_id` (`produto_id`);

--
-- Índices de tabela `movimentacao_financeira`
--
ALTER TABLE `movimentacao_financeira`
  ADD PRIMARY KEY (`id`);

--
-- Índices de tabela `papel`
--
ALTER TABLE `papel`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `nome` (`nome`);

--
-- Índices de tabela `pet`
--
ALTER TABLE `pet`
  ADD PRIMARY KEY (`id`),
  ADD KEY `cliente_id` (`cliente_id`),
  ADD KEY `especie_id` (`especie_id`),
  ADD KEY `raca_id` (`raca_id`),
  ADD KEY `idx_porte` (`porte`);

--
-- Índices de tabela `preco_servico_porte`
--
ALTER TABLE `preco_servico_porte`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_servico_porte` (`servico_id`,`porte`);

--
-- Índices de tabela `produto`
--
ALTER TABLE `produto`
  ADD PRIMARY KEY (`id`),
  ADD KEY `categoria_id` (`categoria_id`),
  ADD KEY `fk_produto_fornecedor` (`fornecedor_padrao_id`);

--
-- Índices de tabela `raca`
--
ALTER TABLE `raca`
  ADD PRIMARY KEY (`id`),
  ADD KEY `especie_id` (`especie_id`);

--
-- Índices de tabela `servico`
--
ALTER TABLE `servico`
  ADD PRIMARY KEY (`id`);

--
-- Índices de tabela `servico_realizado`
--
ALTER TABLE `servico_realizado`
  ADD PRIMARY KEY (`id`),
  ADD KEY `agendamento_id` (`agendamento_id`),
  ADD KEY `pet_id` (`pet_id`),
  ADD KEY `servico_id` (`servico_id`),
  ADD KEY `funcionario_id` (`funcionario_id`);

--
-- Índices de tabela `super_usuario`
--
ALTER TABLE `super_usuario`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `usuario_id` (`usuario_id`);

--
-- Índices de tabela `usuario`
--
ALTER TABLE `usuario`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `usuario` (`usuario`),
  ADD UNIQUE KEY `email` (`email`),
  ADD KEY `papel_id` (`papel_id`);

--
-- Índices de tabela `vacina`
--
ALTER TABLE `vacina`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_vacina_especie` (`especie_id`);

--
-- Índices de tabela `venda`
--
ALTER TABLE `venda`
  ADD PRIMARY KEY (`id`),
  ADD KEY `cliente_id` (`cliente_id`),
  ADD KEY `funcionario_id` (`funcionario_id`);

--
-- AUTO_INCREMENT para tabelas despejadas
--

--
-- AUTO_INCREMENT de tabela `agendamento`
--
ALTER TABLE `agendamento`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `carteira_vacina`
--
ALTER TABLE `carteira_vacina`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `categoria_produto`
--
ALTER TABLE `categoria_produto`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `cliente`
--
ALTER TABLE `cliente`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `compra`
--
ALTER TABLE `compra`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `especie`
--
ALTER TABLE `especie`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `fornecedor`
--
ALTER TABLE `fornecedor`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `funcionario`
--
ALTER TABLE `funcionario`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `historico_consulta`
--
ALTER TABLE `historico_consulta`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `item_compra`
--
ALTER TABLE `item_compra`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `item_venda`
--
ALTER TABLE `item_venda`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `movimentacao_financeira`
--
ALTER TABLE `movimentacao_financeira`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `papel`
--
ALTER TABLE `papel`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `pet`
--
ALTER TABLE `pet`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `preco_servico_porte`
--
ALTER TABLE `preco_servico_porte`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `produto`
--
ALTER TABLE `produto`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `raca`
--
ALTER TABLE `raca`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `servico`
--
ALTER TABLE `servico`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `servico_realizado`
--
ALTER TABLE `servico_realizado`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `super_usuario`
--
ALTER TABLE `super_usuario`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `usuario`
--
ALTER TABLE `usuario`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `vacina`
--
ALTER TABLE `vacina`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de tabela `venda`
--
ALTER TABLE `venda`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- Restrições para tabelas despejadas
--

--
-- Restrições para tabelas `agendamento`
--
ALTER TABLE `agendamento`
  ADD CONSTRAINT `agendamento_ibfk_1` FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `agendamento_ibfk_2` FOREIGN KEY (`servico_id`) REFERENCES `servico` (`id`),
  ADD CONSTRAINT `agendamento_ibfk_3` FOREIGN KEY (`funcionario_id`) REFERENCES `funcionario` (`id`) ON DELETE SET NULL;

--
-- Restrições para tabelas `carteira_vacina`
--
ALTER TABLE `carteira_vacina`
  ADD CONSTRAINT `carteira_vacina_ibfk_1` FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`) ON DELETE CASCADE;

--
-- Restrições para tabelas `compra`
--
ALTER TABLE `compra`
  ADD CONSTRAINT `compra_ibfk_1` FOREIGN KEY (`fornecedor_id`) REFERENCES `fornecedor` (`id`),
  ADD CONSTRAINT `compra_ibfk_2` FOREIGN KEY (`funcionario_id`) REFERENCES `funcionario` (`id`) ON DELETE SET NULL;

--
-- Restrições para tabelas `funcionario`
--
ALTER TABLE `funcionario`
  ADD CONSTRAINT `funcionario_ibfk_1` FOREIGN KEY (`usuario_id`) REFERENCES `usuario` (`id`) ON DELETE CASCADE;

--
-- Restrições para tabelas `historico_consulta`
--
ALTER TABLE `historico_consulta`
  ADD CONSTRAINT `historico_consulta_ibfk_1` FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`) ON DELETE CASCADE;

--
-- Restrições para tabelas `item_compra`
--
ALTER TABLE `item_compra`
  ADD CONSTRAINT `item_compra_ibfk_1` FOREIGN KEY (`compra_id`) REFERENCES `compra` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `item_compra_ibfk_2` FOREIGN KEY (`produto_id`) REFERENCES `produto` (`id`);

--
-- Restrições para tabelas `item_venda`
--
ALTER TABLE `item_venda`
  ADD CONSTRAINT `item_venda_ibfk_1` FOREIGN KEY (`venda_id`) REFERENCES `venda` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `item_venda_ibfk_2` FOREIGN KEY (`produto_id`) REFERENCES `produto` (`id`);

--
-- Restrições para tabelas `preco_servico_porte`
--
ALTER TABLE `preco_servico_porte`
  ADD CONSTRAINT `fk_servico_porte_servico` FOREIGN KEY (`servico_id`) REFERENCES `servico` (`id`) ON DELETE CASCADE;

--
-- Restrições para tabelas `vacina`
--
ALTER TABLE `vacina`
  ADD CONSTRAINT `fk_vacina_especie` FOREIGN KEY (`especie_id`) REFERENCES `especie` (`id`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
