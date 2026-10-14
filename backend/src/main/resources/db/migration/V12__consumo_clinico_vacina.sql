-- Lotes, etapa 04 (item 7, opção A): vacina do catálogo ligada a um produto de estoque e aplicação que baixa o lote.
--
-- ADITIVA: coluna nula em vacina, coluna nula em carteira_vacina e o CHECK de tipo recriado com mais um valor.
-- Nenhuma linha existente muda: vacina sem produto e lançamento sem lote seguem como sempre.

ALTER TABLE vacina
    ADD COLUMN produto_id BIGINT NULL
        COMMENT 'Produto de estoque desta vacina. Nulo = a aplicação não baixa estoque (comportamento anterior).',
    ADD CONSTRAINT fk_vacina_produto FOREIGN KEY (produto_id) REFERENCES produto (id);

ALTER TABLE carteira_vacina
    ADD COLUMN lote_id BIGINT NULL
        COMMENT 'Lote físico do estoque usado nesta dose (FEFO). Nulo = lote digitado à mão (coluna lote).',
    ADD CONSTRAINT fk_carteira_lote FOREIGN KEY (lote_id) REFERENCES lote_estoque (id);

ALTER TABLE movimentacao_estoque DROP CHECK chk_mov_estoque_tipo;
ALTER TABLE movimentacao_estoque ADD CONSTRAINT chk_mov_estoque_tipo
    CHECK (tipo IN ('ENTRADA_COMPRA', 'SAIDA_VENDA', 'AJUSTE', 'ESTORNO_VENDA',
                    'PERDA', 'VENCIMENTO', 'REGULARIZACAO_LOTE', 'CONSUMO_CLINICO'));
