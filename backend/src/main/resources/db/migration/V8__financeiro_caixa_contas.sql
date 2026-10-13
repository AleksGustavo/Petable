-- Etapa 06 — Financeiro: caixa, movimentos de caixa, contas a pagar e colunas de
-- rastreio em movimentacao_financeira. V1–V7 intocadas. Sem backfill aqui: o
-- histórico é gerado por FinanceiroBackfillRunner (petconnect.financeiro.backfill=true).

CREATE TABLE caixa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    aberto_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    fundo_abertura DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fechado_em DATETIME(6) NULL,
    esperado_dinheiro DECIMAL(10,2) NULL,
    contado_dinheiro DECIMAL(10,2) NULL,
    diferenca_dinheiro DECIMAL(10,2) NULL,
    esperado_cartao_credito DECIMAL(10,2) NULL,
    contado_cartao_credito DECIMAL(10,2) NULL,
    diferenca_cartao_credito DECIMAL(10,2) NULL,
    esperado_cartao_debito DECIMAL(10,2) NULL,
    contado_cartao_debito DECIMAL(10,2) NULL,
    diferenca_cartao_debito DECIMAL(10,2) NULL,
    esperado_pix DECIMAL(10,2) NULL,
    contado_pix DECIMAL(10,2) NULL,
    diferenca_pix DECIMAL(10,2) NULL,
    esperado_transferencia DECIMAL(10,2) NULL,
    contado_transferencia DECIMAL(10,2) NULL,
    diferenca_transferencia DECIMAL(10,2) NULL,
    diferenca_total DECIMAL(10,2) NULL,
    motivo_diferenca VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    usuario_aberto_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ABERTO' THEN usuario_id ELSE NULL END
        ) STORED,
    CONSTRAINT fk_caixa_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT ck_caixa_status CHECK (status IN ('ABERTO','FECHADO')),
    CONSTRAINT ck_caixa_fundo CHECK (fundo_abertura >= 0),
    CONSTRAINT ck_caixa_fechamento CHECK (
        (status='ABERTO' AND fechado_em IS NULL)
        OR
        (status='FECHADO' AND fechado_em IS NOT NULL)
    ),
    CONSTRAINT uk_caixa_usuario_aberto UNIQUE (usuario_aberto_id)
);

CREATE INDEX idx_caixa_usuario_abertura ON caixa(usuario_id, aberto_em);
CREATE INDEX idx_caixa_status_abertura ON caixa(status, aberto_em);

CREATE TABLE caixa_movimento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    caixa_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor DECIMAL(10,2) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    usuario_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_caixa_mov_caixa FOREIGN KEY (caixa_id) REFERENCES caixa(id),
    CONSTRAINT fk_caixa_mov_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT ck_caixa_mov_tipo CHECK (tipo IN ('SANGRIA','SUPRIMENTO')),
    CONSTRAINT ck_caixa_mov_valor CHECK (valor > 0)
);

CREATE INDEX idx_caixa_mov_caixa_data ON caixa_movimento(caixa_id, created_at);

CREATE TABLE conta_pagar (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    compra_id BIGINT NULL,
    descricao VARCHAR(100) NOT NULL,
    valor_original DECIMAL(10,2) NOT NULL,
    saldo DECIMAL(10,2) NOT NULL,
    data_vencimento DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    paga_em DATETIME(6) NULL,
    forma_pagamento VARCHAR(20) NULL,
    usuario_pagamento_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_conta_pagar_compra FOREIGN KEY (compra_id) REFERENCES compra(id),
    CONSTRAINT fk_conta_pagar_usuario FOREIGN KEY (usuario_pagamento_id) REFERENCES usuario(id),
    CONSTRAINT uk_conta_pagar_compra UNIQUE (compra_id),
    CONSTRAINT ck_conta_pagar_valor CHECK (valor_original > 0 AND saldo >= 0),
    CONSTRAINT ck_conta_pagar_status CHECK (status IN ('ABERTA','PAGA')),
    CONSTRAINT ck_conta_pagar_forma CHECK (
        forma_pagamento IS NULL OR forma_pagamento IN
        ('DINHEIRO','CARTAO_CREDITO','CARTAO_DEBITO','PIX','TRANSFERENCIA')
    ),
    CONSTRAINT ck_conta_pagar_pagamento CHECK (
        (status='ABERTA' AND paga_em IS NULL AND forma_pagamento IS NULL
         AND usuario_pagamento_id IS NULL AND saldo=valor_original)
        OR
        (status='PAGA' AND paga_em IS NOT NULL AND forma_pagamento IS NOT NULL
         AND usuario_pagamento_id IS NOT NULL AND saldo=0)
    )
);

CREATE INDEX idx_conta_pagar_status_vencimento
    ON conta_pagar(status, data_vencimento);

ALTER TABLE venda
    ADD COLUMN caixa_id BIGINT NULL AFTER usuario_id,
    ADD CONSTRAINT fk_venda_caixa FOREIGN KEY (caixa_id) REFERENCES caixa(id);

CREATE INDEX idx_venda_caixa_data ON venda(caixa_id, data_venda);

ALTER TABLE movimentacao_financeira
    ADD COLUMN forma_pagamento VARCHAR(20) NULL AFTER valor,
    ADD COLUMN usuario_id BIGINT NULL AFTER forma_pagamento,
    ADD COLUMN caixa_id BIGINT NULL AFTER usuario_id,
    ADD COLUMN conta_pagar_id BIGINT NULL AFTER caixa_id,
    ADD COLUMN estorno_de_id BIGINT NULL AFTER conta_pagar_id,
    ADD COLUMN chave_origem VARCHAR(100) NULL AFTER estorno_de_id,
    ADD COLUMN motivo_estorno VARCHAR(500) NULL AFTER chave_origem,
    ADD CONSTRAINT fk_mov_fin_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    ADD CONSTRAINT fk_mov_fin_caixa FOREIGN KEY (caixa_id) REFERENCES caixa(id),
    ADD CONSTRAINT fk_mov_fin_conta FOREIGN KEY (conta_pagar_id) REFERENCES conta_pagar(id),
    ADD CONSTRAINT fk_mov_fin_estorno FOREIGN KEY (estorno_de_id) REFERENCES movimentacao_financeira(id),
    ADD CONSTRAINT ck_mov_fin_forma CHECK (
        forma_pagamento IS NULL OR forma_pagamento IN
        ('DINHEIRO','CARTAO_CREDITO','CARTAO_DEBITO','PIX','TRANSFERENCIA')
    ),
    ADD CONSTRAINT uk_mov_fin_chave UNIQUE (chave_origem),
    ADD CONSTRAINT uk_mov_fin_estorno UNIQUE (estorno_de_id);

CREATE INDEX idx_mov_fin_tipo_data
    ON movimentacao_financeira(tipo, data_movimentacao);
CREATE INDEX idx_mov_fin_categoria_data
    ON movimentacao_financeira(categoria, data_movimentacao);
CREATE INDEX idx_mov_fin_forma_data
    ON movimentacao_financeira(forma_pagamento, data_movimentacao);
CREATE INDEX idx_mov_fin_caixa
    ON movimentacao_financeira(caixa_id, id);
CREATE INDEX idx_mov_fin_conta
    ON movimentacao_financeira(conta_pagar_id);
CREATE INDEX idx_mov_fin_venda_tipo
    ON movimentacao_financeira(venda_id, tipo);
CREATE INDEX idx_mov_fin_compra_tipo
    ON movimentacao_financeira(compra_id, tipo);
CREATE INDEX idx_mov_fin_servico_tipo
    ON movimentacao_financeira(servico_realizado_id, tipo);
