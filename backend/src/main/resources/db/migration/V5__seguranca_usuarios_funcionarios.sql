-- Etapa 03: invalidação de sessão por versão da credencial + índices.
-- Conferido contra V1–V4:
--   * usuario tem só UNIQUE(username), UNIQUE(email) e o índice da FK papel_id;
--   * funcionario tem UNIQUE em usuario_id/cpf/telefone/endereco_id, nada em nome;
--   * token_redefinicao_senha tem UNIQUE(token_hash) e o índice da FK usuario_id.
--   * NÃO criado: token_redefinicao_senha (expira_em, usado_em). Serviria a um job de
--     limpeza que não existe nesta etapa; entra junto com ele.

-- Sobe a cada troca/redefinição de senha, mudança de papel e (des)ativação;
-- o JWT carrega a versão (claim "tv") e é recusado se divergir.
ALTER TABLE usuario ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- Contagem de ADMINs ativos (regra do último ADMIN) e filtros da lista de usuários.
CREATE INDEX idx_usuario_ativo_papel ON usuario (ativo, papel_id);

-- Ordenação/busca de funcionários por nome (seleção na agenda).
CREATE INDEX idx_funcionario_nome ON funcionario (nome);

-- Invalidação dos pedidos pendentes de um usuário.
CREATE INDEX idx_token_redefinicao_usuario_expira ON token_redefinicao_senha (usuario_id, expira_em);
