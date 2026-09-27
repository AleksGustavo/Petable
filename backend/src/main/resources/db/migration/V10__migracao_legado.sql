-- Etapa 08: controle da migração do sistema PHP legado (petshop_db).
-- Só IDs, situações, códigos de motivo e contagens: nenhum dado pessoal.

CREATE TABLE migracao_legado_execucao (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    iniciada_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finalizada_em DATETIME(6) NULL,
    modo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_lidos BIGINT NOT NULL DEFAULT 0,
    total_migrados BIGINT NOT NULL DEFAULT 0,
    total_ajustados BIGINT NOT NULL DEFAULT 0,
    total_ignorados BIGINT NOT NULL DEFAULT 0,
    total_ja_migrados BIGINT NOT NULL DEFAULT 0,
    total_pendencias BIGINT NOT NULL DEFAULT 0,
    mensagem VARCHAR(500) NULL,
    CONSTRAINT chk_migracao_exec_modo CHECK (modo IN ('DRY_RUN','REAL')),
    CONSTRAINT chk_migracao_exec_status CHECK (status IN ('EM_ANDAMENTO','CONCLUIDA','FALHOU'))
);

CREATE INDEX idx_migracao_exec_inicio
    ON migracao_legado_execucao(iniciada_em);

CREATE TABLE migracao_legado_mapa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entidade VARCHAR(50) NOT NULL,
    id_legado BIGINT NOT NULL,
    id_novo BIGINT NULL,
    situacao VARCHAR(20) NOT NULL,
    motivo VARCHAR(500) NULL,
    execucao_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_migracao_mapa_execucao
        FOREIGN KEY (execucao_id) REFERENCES migracao_legado_execucao(id),
    CONSTRAINT chk_migracao_mapa_situacao
        CHECK (situacao IN ('MIGRADO','IGNORADO','AJUSTADO')),
    CONSTRAINT uk_migracao_mapa_entidade_legado
        UNIQUE (entidade, id_legado)
);

CREATE INDEX idx_migracao_mapa_novo
    ON migracao_legado_mapa(entidade, id_novo);

CREATE INDEX idx_migracao_mapa_execucao
    ON migracao_legado_mapa(execucao_id, entidade, situacao);
