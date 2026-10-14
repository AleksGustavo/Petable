-- Módulos por cliente, etapa 07: histórico das listas de módulos ativos a cada partida.
--
-- Tabela GERAL (igual para todo cliente; as migrations seguem a versão do produto, não o contrato do cliente). Serve para detectar
-- um downgrade (módulo que saiu da lista) e checar se a remoção viola operação pendente. Só é gravada pela API, na partida.
-- ADITIVA: tabela nova, nenhum dado existente muda.
CREATE TABLE modulo_instalacao_historico (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    modulos         VARCHAR(500) NOT NULL COMMENT 'Ids dos módulos ativos, separados por vírgula, em ordem alfabética (núcleo é implícito).',
    modo_todos      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'PETCONNECT_MODULOS=todos.',
    versao_catalogo INT          NOT NULL,
    registrado_em   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_modulo_historico_data (registrado_em)
);
