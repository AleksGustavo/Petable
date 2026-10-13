-- Índices do módulo de agendamentos (etapa 02). Conferido contra V1–V3:
--   * agendamento (data_hora) já existe (idx_agendamento_data) e não é recriado;
--   * as FKs pet_id/funcionario_id/servico_id têm só índice simples; os compostos
--     abaixo atendem a verificação de conflito e os filtros da agenda;
--   * servico (ativo, nome) NÃO foi criado: o catálogo tem poucas linhas e já tem
--     UNIQUE (nome); um índice a mais só custaria escrita.

-- Conflito por pet e "próximos agendamentos" do pet.
CREATE INDEX idx_agendamento_pet_data_status ON agendamento (pet_id, data_hora, status);

-- Conflito por funcionário (quando houver funcionário no agendamento).
CREATE INDEX idx_agendamento_funcionario_data_status ON agendamento (funcionario_id, data_hora, status);

-- Agenda filtrada por status no período.
CREATE INDEX idx_agendamento_status_data ON agendamento (status, data_hora);

-- Agenda filtrada por serviço no período.
CREATE INDEX idx_agendamento_servico_data ON agendamento (servico_id, data_hora);

-- Histórico de serviços realizados do pet.
CREATE INDEX idx_servico_realizado_pet_data ON servico_realizado (pet_id, data_servico);
