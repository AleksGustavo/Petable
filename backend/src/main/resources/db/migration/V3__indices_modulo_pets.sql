-- Índices do módulo de pets (etapa 01). Conferido contra a V1 para não duplicar:
--   * raca (especie_id, nome) e vacina (especie_id, nome) já têm UNIQUE equivalente;
--   * pet (nome) já tem idx_pet_nome, e a busca usa LIKE '%termo%', que não aproveita
--     um índice (nome, ativo);
--   * as FKs pet_id/cliente_id têm índice simples; os compostos abaixo servem os
--     filtros + ordenações usados pelas telas.

-- Pets ativos de um tutor (ficha do cliente).
CREATE INDEX idx_pet_cliente_ativo ON pet (cliente_id, ativo);

-- Carteira do pet ordenada por data de aplicação.
CREATE INDEX idx_carteira_vacina_pet_data ON carteira_vacina (pet_id, data_aplicacao);

-- Histórico de consultas do pet, mais recente primeiro.
CREATE INDEX idx_historico_consulta_pet_data ON historico_consulta (pet_id, data_consulta);
