-- Etapa 07 — Relatórios gerenciais (somente leitura). Só os índices que faltavam
-- para os filtros por período; V1–V8 intocadas.

-- Serviços realizados por período (o índice existente começa por pet_id).
CREATE INDEX idx_servico_realizado_data ON servico_realizado (data_servico);
-- Contas pagas no período (o índice existente é status + vencimento).
CREATE INDEX idx_conta_pagar_status_pagamento ON conta_pagar (status, paga_em);
