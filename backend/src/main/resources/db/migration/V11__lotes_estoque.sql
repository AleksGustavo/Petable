-- Lotes, etapa 02: núcleo persistente de rastreabilidade por lote e validade.
-- Contrato: docs/implementacao/01_DECISOES_LOTES.md (D1-D24, I1-I6).
--
-- ADITIVA: só colunas nulas ou com padrão, tabelas novas e a recriação do CHECK de tipo.
-- Nenhuma linha existente é alterada, nenhum saldo muda, nenhum lote ou validade é inventado.
-- A versão anterior do sistema continua funcionando sobre este schema (ddl-auto=validate
-- ignora colunas e tabelas que ela não mapeia). Nenhum fluxo de venda/compra usa lotes ainda:
-- o controle fica desligado (produto.controla_lote = FALSE) e a flag
-- petconnect.estoque.lotes.habilitado nasce FALSE.

-- ------------------------------------------------------------------ produto ----
ALTER TABLE produto
    ADD COLUMN controla_lote          BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'Opt-in por produto (D1). Só liga com saldo regularizado em lotes (D11).',
    ADD COLUMN permite_sem_validade   BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'Política explícita (D5): FALSE = todo lote do produto exige validade.',
    ADD COLUMN dias_bloqueio_validade INT     NULL
        COMMENT 'Margem de bloqueio antes do vencimento (D4). NULL = usa a margem global.',
    ADD CONSTRAINT ck_produto_dias_bloqueio CHECK (dias_bloqueio_validade IS NULL OR dias_bloqueio_validade >= 0);

-- Telas e alertas (etapa 04): "quais produtos têm controle de lote".
CREATE INDEX idx_produto_controla_lote ON produto (controla_lote, ativo);

-- -------------------------------------------------------------- lote_estoque ----
-- Saldo MATERIALIZADO por lote, reconciliável com o livro-razão (I2): o saldo do lote é
-- sempre a soma de movimentacao_estoque.quantidade do lote. Quem altera o saldo é o
-- EstoqueService (etapa 03), com a linha do produto travada (D15).
CREATE TABLE lote_estoque (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    produto_id       BIGINT        NOT NULL,
    codigo_lote      VARCHAR(50)   NOT NULL COMMENT 'Código do fabricante. Imutável.',
    validade         DATE          NULL     COMMENT 'Sem horário (D4). NULL só se o produto permite_sem_validade. Imutável.',
    saldo            INT           NOT NULL DEFAULT 0,
    custo_unitario   DECIMAL(10,2) NULL     COMMENT 'Custo da PRIMEIRA entrada do lote. Informativo; nunca sobrescrito (D16).',
    status           VARCHAR(12)   NOT NULL DEFAULT 'DISPONIVEL',
    data_recebimento DATE          NOT NULL COMMENT 'Data da primeira entrada física do lote.',
    compra_id        BIGINT        NULL     COMMENT 'Compra da primeira entrada, quando houver.',
    fornecedor_id    BIGINT        NULL     COMMENT 'Fornecedor da primeira entrada, quando houver.',
    usuario_id       BIGINT        NOT NULL COMMENT 'Quem cadastrou o lote.',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_lote_produto_codigo UNIQUE (produto_id, codigo_lote),
    CONSTRAINT ck_lote_saldo CHECK (saldo >= 0),
    CONSTRAINT ck_lote_status CHECK (status IN ('DISPONIVEL', 'BLOQUEADO')),
    CONSTRAINT ck_lote_codigo CHECK (CHAR_LENGTH(TRIM(codigo_lote)) > 0),
    CONSTRAINT fk_lote_produto FOREIGN KEY (produto_id) REFERENCES produto (id),
    CONSTRAINT fk_lote_compra FOREIGN KEY (compra_id) REFERENCES compra (id),
    CONSTRAINT fk_lote_fornecedor FOREIGN KEY (fornecedor_id) REFERENCES fornecedor (id) ON DELETE SET NULL,
    CONSTRAINT fk_lote_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    -- FEFO (D6): lotes de um produto por validade e depois id. O desempate por created_at
    -- coincide com o de id (os dois crescem juntos), então o índice atende a consulta inteira.
    INDEX idx_lote_fefo (produto_id, status, validade, id),
    -- Alertas de vencimento (D23): todos os produtos, por data.
    INDEX idx_lote_validade (validade, saldo),
    INDEX idx_lote_compra (compra_id),
    INDEX idx_lote_fornecedor (fornecedor_id),
    INDEX idx_lote_usuario (usuario_id)
);

-- --------------------------------------------------- movimentacao_estoque ----
-- O livro-razão continua único (DA-01). Lote é uma dimensão a mais; movimento sem lote
-- (todo o histórico existente) fica com as três colunas de lote nulas.
ALTER TABLE movimentacao_estoque
    ADD COLUMN lote_id               BIGINT   NULL,
    ADD COLUMN lote_saldo_anterior   INT      NULL,
    ADD COLUMN lote_saldo_resultante INT      NULL,
    ADD COLUMN operacao_id           CHAR(36) NULL
        COMMENT 'UUID que agrupa os movimentos de uma mesma operação (ex.: o par ESTORNO_VENDA + PERDA).',
    ADD CONSTRAINT fk_mov_estoque_lote FOREIGN KEY (lote_id) REFERENCES lote_estoque (id),
    -- I6: lote e seus dois saldos andam juntos; o saldo do lote nunca fica negativo e a conta fecha.
    ADD CONSTRAINT ck_mov_estoque_lote CHECK (
        (lote_id IS NULL AND lote_saldo_anterior IS NULL AND lote_saldo_resultante IS NULL)
        OR (lote_id IS NOT NULL AND lote_saldo_anterior IS NOT NULL AND lote_saldo_resultante IS NOT NULL
            AND lote_saldo_anterior >= 0 AND lote_saldo_resultante >= 0
            AND lote_saldo_anterior + quantidade = lote_saldo_resultante));

-- A FK de lote_id já cria o índice (lote_id). Este serve ao extrato do lote em ordem cronológica.
CREATE INDEX idx_mov_estoque_lote_data ON movimentacao_estoque (lote_id, created_at);
CREATE INDEX idx_mov_estoque_operacao ON movimentacao_estoque (operacao_id);

ALTER TABLE movimentacao_estoque DROP CHECK chk_mov_estoque_tipo;
ALTER TABLE movimentacao_estoque ADD CONSTRAINT chk_mov_estoque_tipo
    CHECK (tipo IN ('ENTRADA_COMPRA', 'SAIDA_VENDA', 'AJUSTE', 'ESTORNO_VENDA',
                    'PERDA', 'VENCIMENTO', 'REGULARIZACAO_LOTE'));

-- --------------------------------------------------------- item_venda_lote ----
-- Alocação FEFO da venda (D8): de quais lotes saiu cada item. Base do rastreio lote -> venda
-- e do retorno ao lote original no cancelamento (D9).
CREATE TABLE item_venda_lote (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    item_venda_id BIGINT NOT NULL,
    lote_id       BIGINT NOT NULL,
    quantidade    INT    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_venda_lote UNIQUE (item_venda_id, lote_id),
    CONSTRAINT ck_item_venda_lote_qtd CHECK (quantidade > 0),
    CONSTRAINT fk_item_venda_lote_item FOREIGN KEY (item_venda_id) REFERENCES item_venda (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_venda_lote_lote FOREIGN KEY (lote_id) REFERENCES lote_estoque (id),
    INDEX idx_item_venda_lote_lote (lote_id)
);

-- -------------------------------------------------------- item_compra_lote ----
-- Lotes informados no recebimento (D17): o que veio em cada item da compra.
CREATE TABLE item_compra_lote (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    item_compra_id BIGINT      NOT NULL,
    lote_id        BIGINT      NULL     COMMENT 'Preenchido quando o recebimento é efetivado (etapa 03).',
    codigo_lote    VARCHAR(50) NOT NULL,
    validade       DATE        NULL,
    quantidade     INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_item_compra_lote UNIQUE (item_compra_id, codigo_lote),
    CONSTRAINT ck_item_compra_lote_qtd CHECK (quantidade > 0),
    CONSTRAINT ck_item_compra_lote_codigo CHECK (CHAR_LENGTH(TRIM(codigo_lote)) > 0),
    CONSTRAINT fk_item_compra_lote_item FOREIGN KEY (item_compra_id) REFERENCES item_compra (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_compra_lote_lote FOREIGN KEY (lote_id) REFERENCES lote_estoque (id),
    INDEX idx_item_compra_lote_lote (lote_id)
);
