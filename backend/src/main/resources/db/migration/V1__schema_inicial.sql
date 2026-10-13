-- =============================================================================
-- PetConnect Web — schema relacional (MySQL 8.4)
--
-- Evolução do banco legado `pet_e_pet_db` (PHP). Mudanças de normalização:
--   * endereco            -> tabela própria; cliente/funcionario/fornecedor
--                            apontam para ela (antes: cep/rua/bairro/numero/
--                            complemento repetidos em 3 tabelas, sem cidade).
--   * super_usuario       -> removida; o nível de acesso vem só de papel.
--   * token de senha      -> tabela token_redefinicao_senha (guarda só o hash),
--                            antes eram colunas soltas em usuario.
--   * pet.vacinado        -> removida; é derivada de carteira_vacina.
--   * carteira_vacina     -> referencia vacina (FK) em vez de nome livre.
--   * subtotal (itens)    -> coluna gerada (quantidade * preço), não digitada.
--   * movimentacao_financeira.referencia_id (polimórfica, sem FK)
--                         -> venda_id / compra_id / servico_realizado_id com FK.
--   * CPF/CNPJ/CEP/telefone guardados só com dígitos.
--   * ENUMs viraram VARCHAR + CHECK (mapeamento estável com JPA).
-- =============================================================================

-- ---------------------------------------------------------------- acesso ----

CREATE TABLE papel (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    nome        VARCHAR(30)  NOT NULL,
    descricao   VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_papel_nome UNIQUE (nome)
);

CREATE TABLE usuario (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL,
    email       VARCHAR(120) NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    papel_id    BIGINT       NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_username UNIQUE (username),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT fk_usuario_papel FOREIGN KEY (papel_id) REFERENCES papel (id)
);

CREATE TABLE token_redefinicao_senha (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL COMMENT 'SHA-256 do token enviado por e-mail; o token puro nunca é gravado.',
    expira_em   DATETIME(6) NOT NULL,
    usado_em    DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

-- -------------------------------------------------------------- endereço ----

CREATE TABLE endereco (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    cep         CHAR(8)      NOT NULL,
    logradouro  VARCHAR(150) NOT NULL,
    numero      VARCHAR(20)  NULL,
    complemento VARCHAR(100) NULL,
    bairro      VARCHAR(100) NULL,
    cidade      VARCHAR(100) NOT NULL,
    uf          CHAR(2)      NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_endereco_cep (cep)
);

-- --------------------------------------------------------------- pessoas ----

CREATE TABLE cliente (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    nome            VARCHAR(100) NOT NULL,
    cpf             CHAR(11)     NULL,
    data_nascimento DATE         NULL,
    telefone        VARCHAR(11)  NOT NULL,
    email           VARCHAR(120) NULL,
    endereco_id     BIGINT       NULL,
    observacoes     TEXT         NULL,
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_cliente_cpf UNIQUE (cpf),
    CONSTRAINT uk_cliente_telefone UNIQUE (telefone),
    CONSTRAINT uk_cliente_endereco UNIQUE (endereco_id),
    CONSTRAINT fk_cliente_endereco FOREIGN KEY (endereco_id) REFERENCES endereco (id) ON DELETE SET NULL,
    INDEX idx_cliente_nome (nome)
);

CREATE TABLE funcionario (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id      BIGINT       NOT NULL,
    nome            VARCHAR(100) NOT NULL,
    cpf             CHAR(11)     NOT NULL,
    data_nascimento DATE         NULL,
    sexo            VARCHAR(10)  NOT NULL DEFAULT 'OUTRO',
    telefone        VARCHAR(11)  NULL,
    endereco_id     BIGINT       NULL,
    observacoes     TEXT         NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_funcionario_usuario UNIQUE (usuario_id),
    CONSTRAINT uk_funcionario_cpf UNIQUE (cpf),
    CONSTRAINT uk_funcionario_telefone UNIQUE (telefone),
    CONSTRAINT uk_funcionario_endereco UNIQUE (endereco_id),
    CONSTRAINT ck_funcionario_sexo CHECK (sexo IN ('MASCULINO', 'FEMININO', 'OUTRO')),
    CONSTRAINT fk_funcionario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_funcionario_endereco FOREIGN KEY (endereco_id) REFERENCES endereco (id) ON DELETE SET NULL
);

CREATE TABLE fornecedor (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nome_fantasia VARCHAR(100) NOT NULL,
    razao_social  VARCHAR(150) NULL,
    cnpj          CHAR(14)     NOT NULL,
    telefone      VARCHAR(11)  NULL,
    email         VARCHAR(120) NULL,
    contato       VARCHAR(100) NULL,
    endereco_id   BIGINT       NULL,
    observacoes   TEXT         NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_fornecedor_cnpj UNIQUE (cnpj),
    CONSTRAINT uk_fornecedor_telefone UNIQUE (telefone),
    CONSTRAINT uk_fornecedor_endereco UNIQUE (endereco_id),
    CONSTRAINT fk_fornecedor_endereco FOREIGN KEY (endereco_id) REFERENCES endereco (id) ON DELETE SET NULL
);

-- ------------------------------------------------------------------ pets ----

CREATE TABLE especie (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    nome       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_especie_nome UNIQUE (nome)
);

CREATE TABLE raca (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    especie_id BIGINT      NOT NULL,
    nome       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_raca_especie_nome UNIQUE (especie_id, nome),
    CONSTRAINT fk_raca_especie FOREIGN KEY (especie_id) REFERENCES especie (id) ON DELETE CASCADE
);

CREATE TABLE pet (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    cliente_id      BIGINT       NOT NULL,
    nome            VARCHAR(50)  NOT NULL,
    especie_id      BIGINT       NOT NULL,
    raca_id         BIGINT       NULL,
    data_nascimento DATE         NULL,
    peso            DECIMAL(5,2) NULL COMMENT 'kg',
    cor             VARCHAR(30)  NULL,
    porte           VARCHAR(10)  NULL,
    castrado        BOOLEAN      NOT NULL DEFAULT FALSE,
    foto_url        VARCHAR(255) NULL,
    observacoes     TEXT         NULL,
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_pet_porte CHECK (porte IS NULL OR porte IN ('PEQUENO', 'MEDIO', 'GRANDE')),
    CONSTRAINT fk_pet_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE CASCADE,
    CONSTRAINT fk_pet_especie FOREIGN KEY (especie_id) REFERENCES especie (id),
    CONSTRAINT fk_pet_raca FOREIGN KEY (raca_id) REFERENCES raca (id) ON DELETE SET NULL,
    INDEX idx_pet_nome (nome)
);

CREATE TABLE vacina (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    especie_id            BIGINT        NOT NULL,
    nome                  VARCHAR(100)  NOT NULL,
    doenca_protecao       VARCHAR(150)  NULL,
    validade_padrao_meses INT           NOT NULL DEFAULT 12,
    preco                 DECIMAL(10,2) NULL,
    observacoes           TEXT          NULL,
    ativo                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_vacina_especie_nome UNIQUE (especie_id, nome),
    CONSTRAINT fk_vacina_especie FOREIGN KEY (especie_id) REFERENCES especie (id)
);

CREATE TABLE carteira_vacina (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    pet_id         BIGINT       NOT NULL,
    vacina_id      BIGINT       NOT NULL,
    data_aplicacao DATE         NOT NULL,
    data_proxima   DATE         NULL,
    veterinario    VARCHAR(100) NULL,
    lote           VARCHAR(50)  NULL,
    observacoes    TEXT         NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_carteira_pet FOREIGN KEY (pet_id) REFERENCES pet (id) ON DELETE CASCADE,
    CONSTRAINT fk_carteira_vacina FOREIGN KEY (vacina_id) REFERENCES vacina (id),
    INDEX idx_carteira_proxima (data_proxima)
);

CREATE TABLE historico_consulta (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    pet_id        BIGINT       NOT NULL,
    data_consulta DATETIME(6)  NOT NULL,
    veterinario   VARCHAR(100) NOT NULL,
    motivo        TEXT         NULL,
    diagnostico   TEXT         NULL,
    tratamento    TEXT         NULL,
    observacoes   TEXT         NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_consulta_pet FOREIGN KEY (pet_id) REFERENCES pet (id) ON DELETE CASCADE
);

-- -------------------------------------------------------------- serviços ----

CREATE TABLE servico (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    nome            VARCHAR(100)  NOT NULL,
    descricao       TEXT          NULL,
    preco           DECIMAL(10,2) NOT NULL COMMENT 'Preço padrão; preco_servico_porte sobrescreve por porte.',
    duracao_minutos INT           NULL,
    ativo           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_servico_nome UNIQUE (nome)
);

CREATE TABLE preco_servico_porte (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    servico_id BIGINT        NOT NULL,
    porte      VARCHAR(10)   NOT NULL,
    preco      DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_servico_porte UNIQUE (servico_id, porte),
    CONSTRAINT ck_preco_porte CHECK (porte IN ('PEQUENO', 'MEDIO', 'GRANDE')),
    CONSTRAINT fk_preco_servico FOREIGN KEY (servico_id) REFERENCES servico (id) ON DELETE CASCADE
);

CREATE TABLE agendamento (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    pet_id           BIGINT        NOT NULL,
    servico_id       BIGINT        NOT NULL,
    funcionario_id   BIGINT        NULL,
    data_hora        DATETIME(6)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'AGENDADO',
    total_estimado   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    observacoes      TEXT          NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_agendamento_status CHECK (status IN
        ('AGENDADO', 'CONFIRMADO', 'EM_ANDAMENTO', 'CONCLUIDO', 'CANCELADO', 'NAO_COMPARECEU')),
    CONSTRAINT fk_agendamento_pet FOREIGN KEY (pet_id) REFERENCES pet (id) ON DELETE CASCADE,
    CONSTRAINT fk_agendamento_servico FOREIGN KEY (servico_id) REFERENCES servico (id),
    CONSTRAINT fk_agendamento_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario (id) ON DELETE SET NULL,
    INDEX idx_agendamento_data (data_hora)
);

CREATE TABLE servico_realizado (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    agendamento_id BIGINT        NULL,
    pet_id         BIGINT        NOT NULL,
    servico_id     BIGINT        NOT NULL,
    funcionario_id BIGINT        NULL,
    data_servico   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    valor          DECIMAL(10,2) NOT NULL,
    observacoes    TEXT          NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_realizado_agendamento UNIQUE (agendamento_id),
    CONSTRAINT fk_realizado_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamento (id) ON DELETE SET NULL,
    CONSTRAINT fk_realizado_pet FOREIGN KEY (pet_id) REFERENCES pet (id),
    CONSTRAINT fk_realizado_servico FOREIGN KEY (servico_id) REFERENCES servico (id),
    CONSTRAINT fk_realizado_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario (id) ON DELETE SET NULL
);

-- ----------------------------------------------------- produtos / estoque ----

CREATE TABLE categoria_produto (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    nome       VARCHAR(50)  NOT NULL,
    descricao  VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_categoria_nome UNIQUE (nome)
);

CREATE TABLE produto (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    nome                 VARCHAR(100)  NOT NULL,
    descricao            TEXT          NULL,
    categoria_id         BIGINT        NOT NULL,
    fornecedor_padrao_id BIGINT        NULL,
    codigo_barras        VARCHAR(50)   NULL,
    preco_custo          DECIMAL(10,2) NULL,
    preco_venda          DECIMAL(10,2) NOT NULL,
    quantidade_estoque   INT           NOT NULL DEFAULT 0,
    estoque_minimo       INT           NOT NULL DEFAULT 5,
    ativo                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_produto_codigo_barras UNIQUE (codigo_barras),
    CONSTRAINT ck_produto_estoque CHECK (quantidade_estoque >= 0),
    CONSTRAINT fk_produto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria_produto (id),
    CONSTRAINT fk_produto_fornecedor FOREIGN KEY (fornecedor_padrao_id) REFERENCES fornecedor (id) ON DELETE SET NULL,
    INDEX idx_produto_nome (nome)
);

-- --------------------------------------------------------- vendas (PDV) ----

CREATE TABLE venda (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    cliente_id      BIGINT        NULL COMMENT 'Nulo = venda de balcão sem cliente identificado.',
    funcionario_id  BIGINT        NULL,
    data_venda      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    valor_total     DECIMAL(10,2) NOT NULL COMMENT 'Soma dos itens menos o desconto, congelada no fechamento.',
    desconto        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    forma_pagamento VARCHAR(20)   NOT NULL,
    observacoes     TEXT          NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_venda_pagamento CHECK (forma_pagamento IN
        ('DINHEIRO', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'PIX', 'TRANSFERENCIA')),
    CONSTRAINT fk_venda_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE SET NULL,
    CONSTRAINT fk_venda_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario (id) ON DELETE SET NULL,
    INDEX idx_venda_data (data_venda)
);

CREATE TABLE item_venda (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    venda_id       BIGINT        NOT NULL,
    produto_id     BIGINT        NOT NULL,
    quantidade     INT           NOT NULL,
    preco_unitario DECIMAL(10,2) NOT NULL COMMENT 'Preço no momento da venda.',
    subtotal       DECIMAL(12,2) GENERATED ALWAYS AS (quantidade * preco_unitario) STORED,
    PRIMARY KEY (id),
    CONSTRAINT ck_item_venda_qtd CHECK (quantidade > 0),
    CONSTRAINT fk_item_venda_venda FOREIGN KEY (venda_id) REFERENCES venda (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_venda_produto FOREIGN KEY (produto_id) REFERENCES produto (id)
);

-- ---------------------------------------------------- compras (entrada) ----

CREATE TABLE compra (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    fornecedor_id   BIGINT        NOT NULL,
    funcionario_id  BIGINT        NULL,
    data_compra     DATE          NOT NULL,
    data_entrega    DATE          NULL,
    valor_total     DECIMAL(10,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PEDIDO',
    forma_pagamento VARCHAR(50)   NULL,
    observacoes     TEXT          NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_compra_status CHECK (status IN ('PEDIDO', 'RECEBIDO', 'CANCELADO')),
    CONSTRAINT fk_compra_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES fornecedor (id),
    CONSTRAINT fk_compra_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario (id) ON DELETE SET NULL
);

CREATE TABLE item_compra (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    compra_id            BIGINT        NOT NULL,
    produto_id           BIGINT        NOT NULL,
    quantidade           INT           NOT NULL,
    preco_custo_unitario DECIMAL(10,2) NOT NULL,
    subtotal             DECIMAL(12,2) GENERATED ALWAYS AS (quantidade * preco_custo_unitario) STORED,
    PRIMARY KEY (id),
    CONSTRAINT ck_item_compra_qtd CHECK (quantidade > 0),
    CONSTRAINT fk_item_compra_compra FOREIGN KEY (compra_id) REFERENCES compra (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_compra_produto FOREIGN KEY (produto_id) REFERENCES produto (id)
);

-- ------------------------------------------------------------ financeiro ----

CREATE TABLE movimentacao_financeira (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    tipo                 VARCHAR(10)   NOT NULL,
    categoria            VARCHAR(30)   NOT NULL,
    descricao            VARCHAR(100)  NOT NULL,
    valor                DECIMAL(10,2) NOT NULL,
    data_movimentacao    DATE          NOT NULL,
    venda_id             BIGINT        NULL,
    compra_id            BIGINT        NULL,
    servico_realizado_id BIGINT        NULL,
    observacoes          TEXT          NULL,
    created_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_mov_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_mov_categoria CHECK (categoria IN ('VENDA_PRODUTO', 'SERVICO', 'DESPESA_FUNCIONARIO',
        'DESPESA_OPERACIONAL', 'COMPRA_MERCADORIA', 'OUTROS')),
    CONSTRAINT ck_mov_valor CHECK (valor > 0),
    -- FKs sem ação referencial: o MySQL não aceita CHECK em coluna com ON DELETE SET NULL,
    -- e um lançamento financeiro não deve perder a origem silenciosamente.
    CONSTRAINT ck_mov_uma_origem CHECK (
        (venda_id IS NOT NULL) + (compra_id IS NOT NULL) + (servico_realizado_id IS NOT NULL) <= 1),
    CONSTRAINT fk_mov_venda FOREIGN KEY (venda_id) REFERENCES venda (id),
    CONSTRAINT fk_mov_compra FOREIGN KEY (compra_id) REFERENCES compra (id),
    CONSTRAINT fk_mov_servico FOREIGN KEY (servico_realizado_id) REFERENCES servico_realizado (id),
    INDEX idx_mov_data (data_movimentacao)
);
