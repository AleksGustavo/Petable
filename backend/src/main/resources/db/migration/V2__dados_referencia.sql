-- Dados de referência (catálogos). Nada de dados pessoais aqui: clientes, pets
-- e vendas do banco legado entram pela migração de dados (etapa própria).
-- O primeiro usuário ADMIN é criado pela aplicação a partir de variáveis de
-- ambiente (AdminBootstrap), nunca com senha fixa em script.

INSERT INTO papel (nome, descricao) VALUES
    ('ADMIN',   'Acesso total, inclusive usuários e financeiro. (legado: SuperAdmin)'),
    ('VENDAS',  'Clientes, pets, agendamentos e PDV. Sem financeiro. (legado: FuncionarioVendas)'),
    ('SERVICO', 'Consulta agendamentos e registra serviços realizados. (legado: FuncionarioServico)');

INSERT INTO especie (nome) VALUES ('Cão'), ('Gato'), ('Ave'), ('Roedor'), ('Réptil'), ('Peixe');

INSERT INTO raca (especie_id, nome)
SELECT e.id, r.nome
FROM especie e
JOIN (SELECT 'Cão' AS especie, 'SRD (Sem Raça Definida)' AS nome
      UNION ALL SELECT 'Cão', 'Labrador'
      UNION ALL SELECT 'Cão', 'Poodle'
      UNION ALL SELECT 'Cão', 'Bulldog Francês'
      UNION ALL SELECT 'Cão', 'Golden Retriever'
      UNION ALL SELECT 'Cão', 'Pastor Alemão'
      UNION ALL SELECT 'Cão', 'Yorkshire Terrier'
      UNION ALL SELECT 'Cão', 'Shih Tzu'
      UNION ALL SELECT 'Cão', 'Chihuahua'
      UNION ALL SELECT 'Cão', 'Dachshund'
      UNION ALL SELECT 'Cão', 'Terra Nova'
      UNION ALL SELECT 'Cão', 'Chow Chow'
      UNION ALL SELECT 'Gato', 'SRD (Sem Raça Definida)'
      UNION ALL SELECT 'Gato', 'Siamês'
      UNION ALL SELECT 'Gato', 'Persa'
      UNION ALL SELECT 'Gato', 'Maine Coon') r ON r.especie = e.nome;

INSERT INTO categoria_produto (nome, descricao) VALUES
    ('Ração',       'Alimentos para animais'),
    ('Medicamento', 'Medicamentos veterinários'),
    ('Brinquedo',   'Brinquedos para pets'),
    ('Higiene',     'Produtos de higiene e limpeza'),
    ('Acessório',   'Coleiras, guias, roupas, etc.');

INSERT INTO servico (nome, descricao, preco, duracao_minutos) VALUES
    ('Banho',                'Banho completo com produtos adequados', 35.00, 45),
    ('Tosa',                 'Tosa higiênica ou tosa completa',       45.00, 60),
    ('Banho e Tosa',         'Pacote completo de banho e tosa',       70.00, 90),
    ('Consulta Veterinária', 'Consulta com veterinário',              80.00, 30),
    ('Hidratação',           'Hidratação para pelos ressecados',      25.00, 30);

INSERT INTO preco_servico_porte (servico_id, porte, preco)
SELECT s.id, p.porte, p.preco
FROM servico s
JOIN (SELECT 'Banho' AS servico, 'PEQUENO' AS porte, 35.00 AS preco
      UNION ALL SELECT 'Banho', 'MEDIO', 45.00
      UNION ALL SELECT 'Banho', 'GRANDE', 60.00
      UNION ALL SELECT 'Tosa', 'PEQUENO', 45.00
      UNION ALL SELECT 'Tosa', 'MEDIO', 60.00
      UNION ALL SELECT 'Tosa', 'GRANDE', 80.00
      UNION ALL SELECT 'Banho e Tosa', 'PEQUENO', 70.00
      UNION ALL SELECT 'Banho e Tosa', 'MEDIO', 100.00
      UNION ALL SELECT 'Banho e Tosa', 'GRANDE', 130.00) p ON p.servico = s.nome;

INSERT INTO vacina (especie_id, nome, doenca_protecao, validade_padrao_meses, preco)
SELECT e.id, v.nome, v.doenca, v.meses, v.preco
FROM especie e
JOIN (SELECT 'Cão' AS especie, 'V8 Polivalente' AS nome,
             'Cinomose, Parvovirose, Adenovirose, Hepatite, Parainfluenza, Coronavírus, Leptospirose' AS doenca,
             12 AS meses, 80.00 AS preco
      UNION ALL SELECT 'Cão', 'V10 Polivalente', 'V8 + Leptospirose (L. grippotyphosa e L. pomona)', 12, 95.00
      UNION ALL SELECT 'Cão', 'Gripe Canina (Bordetella)', 'Tosse dos Canis', 6, 60.00
      UNION ALL SELECT 'Cão', 'Raiva (Antirrábica Canina)', 'Raiva', 12, 50.00
      UNION ALL SELECT 'Cão', 'Giardia Canina', 'Giardia sp.', 6, 75.00
      UNION ALL SELECT 'Gato', 'Tríplice Felina (V3)', 'Panleucopenia, Rinotraqueíte, Calicivirose', 12, 70.00
      UNION ALL SELECT 'Gato', 'Quádrupla Felina (V4)', 'V3 + Clamidiose', 12, 85.00
      UNION ALL SELECT 'Gato', 'Quíntupla Felina (V5)', 'V4 + Leucemia Felina (FeLV)', 12, 120.00
      UNION ALL SELECT 'Gato', 'Raiva (Antirrábica Felina)', 'Raiva', 12, 50.00) v ON v.especie = e.nome;
