-- Etapa 04: trilha de estoque + índices. Conferido contra V1–V5.
--
-- movimentacao_estoque: histórico imutável de toda mudança de saldo (DA-01).
-- produto.quantidade_estoque continua sendo o saldo atual.
--   * created_at em DATETIME(6), como o resto do schema: ordena entradas do mesmo segundo.
--   * O índice (produto_id, created_at) é declarado na própria tabela e também atende a FK
--     de produto_id. As FKs compra_id/venda_id/usuario_id ganham índice automático do MySQL,
--     por isso idx_mov_estoque_compra NÃO é criado separadamente (seria duplicado).
CREATE TABLE movimentacao_estoque (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    produto_id       BIGINT       NOT NULL,
    tipo             VARCHAR(30)  NOT NULL,
    quantidade       INT          NOT NULL,
    saldo_anterior   INT          NOT NULL,
    saldo_resultante INT          NOT NULL,
    compra_id        BIGINT       NULL,
    venda_id         BIGINT       NULL,
    usuario_id       BIGINT       NOT NULL,
    motivo           VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_mov_estoque_produto_data (produto_id, created_at),
    CONSTRAINT fk_mov_estoque_produto FOREIGN KEY (produto_id) REFERENCES produto (id),
    CONSTRAINT fk_mov_estoque_compra FOREIGN KEY (compra_id) REFERENCES compra (id),
    CONSTRAINT fk_mov_estoque_venda FOREIGN KEY (venda_id) REFERENCES venda (id),
    CONSTRAINT fk_mov_estoque_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT chk_mov_estoque_tipo CHECK (tipo IN ('ENTRADA_COMPRA', 'SAIDA_VENDA', 'AJUSTE')),
    CONSTRAINT chk_mov_estoque_quantidade CHECK (quantidade <> 0),
    CONSTRAINT chk_mov_estoque_saldos CHECK (saldo_anterior >= 0 AND saldo_resultante >= 0)
);

-- Lista de produtos por categoria/situação.
CREATE INDEX idx_produto_categoria_ativo ON produto (categoria_id, ativo);
-- Contador e filtro "abaixo do mínimo" (ativos com saldo <= mínimo).
CREATE INDEX idx_produto_ativo_estoque ON produto (ativo, quantidade_estoque, estoque_minimo);
-- Lista/opções de fornecedores.
CREATE INDEX idx_fornecedor_ativo_nome ON fornecedor (ativo, nome_fantasia);
-- Compras por status/período e "fornecedor tem PEDIDO aberto?".
CREATE INDEX idx_compra_status_data ON compra (status, data_compra);
CREATE INDEX idx_compra_fornecedor_status ON compra (fornecedor_id, status);
