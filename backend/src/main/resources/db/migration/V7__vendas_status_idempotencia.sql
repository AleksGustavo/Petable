-- Etapa 05 — PDV e vendas.
-- venda ganha status (CONCLUIDA/CANCELADA), operador, valor recebido (troco),
-- dados do cancelamento e chave de idempotência. A movimentação de estoque passa
-- a aceitar ESTORNO_VENDA (cancelamento), sem editar a V6.

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
