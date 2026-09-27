TAREFA: gere um ARQUIVO MARKDOWN NOVO chamado `08-migracao-legado.md`.
NÃO repita, NÃO resuma e NÃO devolva este prompt. Sua resposta deve começar exatamente com a linha
`# Etapa 08 — Migração dos dados legados (PHP/MySQL → PetConnect Web)` e conter SOMENTE o conteúdo do arquivo,
de preferência como anexo .md para download (se não puder anexar, entregue em um único bloco ```markdown).

======================================================================
CONTEXTO DO PROJETO (você não vê o repositório; tudo o que precisa está aqui)
======================================================================

Projeto: PetConnect Web — sistema de gestão de petshop, migrado de um PI em PHP/MySQL para:
- backend: Spring Boot 4.1, Java 21, monólito modular em `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`;
- banco: MySQL 8.4 com Flyway (migrations V1–V9 JÁ APLICADAS, nunca editar), `ddl-auto=validate`;
- MongoDB apenas para auditoria (`AuditoriaService.registrar(acao, entidade, entidadeId, detalhes)`);
- frontend: Next.js 16 (BFF, JWT em cookie httpOnly), React 19, Tailwind 4, Zod, Vitest;
- testes: unitários `*Test` (JUnit 5 + Mockito) e integração `*IT` (Testcontainers MySQL + Mongo);
- Docker Compose (mysql, mongo, mailpit, api, web); CI no GitHub Actions (`backend.yml`, `frontend.yml`).

Convenções já existentes:
- erros RFC 9457 (ProblemDetail): `NotFoundException` 404, `ConflictException` 409, `CampoInvalidoException` 400;
- dinheiro sempre BigDecimal (HALF_UP, escala 2), nunca double/float;
- `Clock` America/Sao_Paulo; datas de negócio gravadas como horário local de São Paulo (LocalDate/LocalDateTime);
- papéis: ADMIN, VENDAS, SERVICO; JWT com `token_version` (trocar senha/papel derruba sessões);
- senha nova: BCrypt custo 12, política `PoliticaSenha`; recuperação por "esqueci a senha" (token de uso único, e-mail via SMTP/Mailpit);
- admin inicial criado por bootstrap a partir do `.env` (ADMIN_USERNAME/ADMIN_PASSWORD);
- sem hard-delete (campo `ativo`);
- `EstoqueService` é o ÚNICO que altera saldo de produto, sempre gravando `movimentacao_estoque` (tipos ENTRADA_COMPRA, SAIDA_VENDA, AJUSTE, ESTORNO_VENDA; histórico imutável, saldo nunca negativo);
- `FinanceiroService` é o ÚNICO que grava `movimentacao_financeira`; cada lançamento tem `chave_origem` UNIQUE (`VENDA:id`, `VENDA_ESTORNO:id`, `SERVICO:id`, `CONTA_PAGAR:id`, `MANUAL:uuid`, `CAIXA_SANGRIA:id`...);
- existe `FinanceiroBackfillRunner` (`--petconnect.financeiro.backfill=true`, idempotente) que gera os lançamentos de vendas e serviços que ainda não têm;
- venda: status CONCLUIDA/CANCELADA, `chave_idempotencia` UNIQUE, `usuario_id` (operador), `caixa_id` (caixa por operador, obrigatório para vender no sistema novo);
- endereço fica em tabela própria `endereco` (cliente, funcionário e fornecedor apontam para ela);
- CPF/CNPJ gravados só com dígitos e validados (classes `Documentos`/`Cnpj`);
- foto do pet: upload validado (tipo/tamanho) por serviço próprio.

Tabelas do banco NOVO (V1): papel, usuario, token_redefinicao_senha, endereco, cliente, funcionario, fornecedor, especie, raca, pet, vacina, carteira_vacina, historico_consulta, servico, preco_servico_porte, agendamento, servico_realizado, categoria_produto, produto, venda, item_venda, compra, item_compra, movimentacao_financeira; + movimentacao_estoque (V6), caixa, caixa_movimento, conta_pagar (V8). V2 já semeia catálogos (papéis, espécies, raças, categorias, serviços, preço por porte, vacinas).

Etapas concluídas: 00 fundação/clientes, 01 pets/vacinas/consultas, 02 agendamentos/serviços, 03 usuários/funcionários/senha, 04 produtos/estoque/fornecedores/compras, 05 PDV/vendas, 06 financeiro/caixa/contas a pagar, 07 relatórios gerenciais (V9 só índices).

======================================================================
BANCO LEGADO (sistema PHP) — banco `petshop_db`
======================================================================
Esquema em `docs/legacy/documentacao/Banco de Dados/Scripts do Banco/pet_e_pet_DB - Script do Banco com Procedures e Functions.sql`.
Existe também um script de inserção com DADOS PESSOAIS REAIS (não pode ser usado em testes).

Tabelas: papel, usuario, super_usuario, funcionario, cliente, especie, raca, pet, vacina, carteira_vacina, historico_consulta, servico, preco_servico_porte, agendamento, servico_realizado, categoria_produto, fornecedor, produto, compra, item_compra, venda, item_venda, movimentacao_financeira.

Colunas principais:
- usuario: id, usuario, senha_hash, token_senha, token_expira, email, papel_id, ativo, created_at, updated_at
- super_usuario: id, usuario_id, cargo, created_at
- cliente: id, nome, cpf, data_nascimento, telefone, email, cep, rua, bairro, numero, complemento, observacoes, ativo, created_at, updated_at, estado
- funcionario: id, usuario_id, nome, cpf, data_nascimento, sexo, telefone, cep, rua, bairro, numero, complemento, observacoes, created_at, updated_at
- pet: id, nome, cliente_id, especie_id, raca_id, data_nascimento, peso, cor, castrado, vacinado, observacoes, foto (caminho de arquivo), ativo, created_at, updated_at, porte
- carteira_vacina: id, pet_id, nome_vacina (texto livre), data_aplicacao, data_proxima, veterinario, observacoes, created_at
- agendamento: id, pet_id, servico_id, funcionario_id, data_agendamento, status, observacoes, created_at, updated_at, total_estimado
- produto: id, nome, descricao, categoria_id, fornecedor_padrao_id, preco_custo, preco_venda, quantidade_estoque, estoque_minimo, codigo_barras, ativo, created_at, updated_at
- venda: id, cliente_id, funcionario_id, data_venda, valor_total, desconto, forma_pagamento, observacoes, created_at
- movimentacao_financeira: id, tipo, descricao, valor, data_movimentacao, categoria, referencia_id, observacoes, created_at

Problemas conhecidos do legado:
1. senha em SHA-256 puro sem sal (ex.: hash de "admin123") → NÃO pode ser migrada;
2. endereço embutido em cliente/funcionario/fornecedor, sem cidade (só CEP/estado);
3. enums em formatos variados ('Medio', 'cartao_credito', 'Concluído') → o novo usa MAIÚSCULAS sem acento;
4. nome de vacina é texto livre → casar com o catálogo `vacina` por espécie;
5. CPF com máscara, às vezes inválido ou duplicado;
6. dados pessoais reais versionados no repositório (LGPD).

======================================================================
DECISÕES QUE A ESPECIFICAÇÃO DEVE ADOTAR (detalhe cada uma como DA-xx)
======================================================================
- Módulo novo `migracao`; execução por ApplicationRunner ativado só com `--petconnect.migracao.legado=true` (mesmo padrão do backfill); sem tela e sem endpoint HTTP.
- Segunda DataSource SOMENTE LEITURA para o legado (`LEGADO_DB_URL`, `LEGADO_DB_USERNAME`, `LEGADO_DB_PASSWORD` por variável de ambiente); nunca escrever no legado.
- `V10__migracao_legado.sql`: tabela `migracao_legado_mapa` (entidade, id_legado, id_novo, situacao MIGRADO/IGNORADO/AJUSTADO, motivo; UNIQUE(entidade, id_legado)) para idempotência, e `migracao_legado_execucao` (histórico das execuções, resumo só com contagens).
- DRY-RUN é o padrão (transação com rollback + relatório); execução real exige `--petconnect.migracao.dry-run=false`.
- Ordem fixa respeitando FKs; lotes de 500; commit por entidade; falha interrompe as seguintes e reexecução continua de onde parou.
- Senhas: usuário migrado recebe BCrypt de segredo aleatório descartado e entra pelo "esqueci a senha"; sem e-mail → pendência; nunca sobrescrever o ADMIN do bootstrap (username repetido vira `<nome>_legado`); `super_usuario` → ADMIN.
- Endereço: gera linha em `endereco`; cidade via ViaCEP OPCIONAL (flag, timeout, cache, sem nunca derrubar a migração).
- CPF/CNPJ: limpar máscara e validar; inválido → nulo + observação no registro; duplicado → o segundo fica nulo + pendência.
- Enums e categorias: tabela de conversão explícita; valor desconhecido → padrão documentado + pendência.
- Catálogos (espécie, raça, vacina, serviço, categoria): casar por nome normalizado (sem acento, minúsculo); sem correspondência → criar e marcar AJUSTADO.
- Estoque: produto entra com saldo 0 e recebe um AJUSTE "Saldo inicial migrado do sistema antigo" via EstoqueService; saldo negativo → 0 + pendência; código de barras duplicado → nulo + pendência.
- Vendas legadas: CONCLUIDA, `chave_idempotencia = 'LEGADO:<id>'`, sem caixa, SEM gerar SAIDA_VENDA (o saldo migrado já é o final); total ≤ 0 ou item sem produto → IGNORADA.
- Compras legadas: sem gerar ENTRADA_COMPRA; conta a pagar só se o legado indicar compra não paga.
- Financeiro: após migrar vendas/serviços, rodar o backfill existente; do financeiro legado migrar só o que NÃO é venda/serviço, com `chave_origem = 'LEGADO:<id>'`; o resto IGNORADO ("regenerado pelo backfill") para não duplicar.
- Fotos: importar de uma pasta informada por flag, pelo serviço de foto existente; ausente → pendência.
- Auditoria: um evento `MIGRACAO_LEGADO_EXECUTADA` por execução real (só contagens); dry-run não audita.
- Relatório da execução em markdown numa pasta ignorada pelo git: contagens por entidade (lidos/migrados/ajustados/ignorados/já migrados) e pendências por ID LEGADO, SEM nome/CPF/e-mail/telefone.
- Conferências automáticas: contagens fecham; soma das vendas legadas = soma das vendas `LEGADO:%`; saldo de cada produto confere; lançamentos de venda após backfill = soma das vendas; relatório de vendas (etapa 07) bate; login com senha antiga → 401; segunda execução não migra nada novo.
- DevOps: perfil `migracao` no docker compose com `mysql-legado` (porta 3307, esquema legado + dump opcional do usuário em pasta ignorada) e serviço `api-migracao`; `.env.example` sem valores reais; runbook `docs/migracao-legado.md` (backup antes, dry-run, real, como ler o relatório, como reverter restaurando o backup, aviso LGPD).
- Testes: unitários (conversões, normalização, cliente/CPF/endereço, produto/estoque, financeiro, relatório sem dados pessoais) e integração com Testcontainers usando FIXTURE SINTÉTICA ANONIMIZADA criada nesta etapa (nunca o script real), incluindo idempotência, dry-run sem gravação e falha no meio com retomada.
- LGPD: não reescrever o histórico do git; apenas recomendar ao dono (git filter-repo).

======================================================================
RESTRIÇÕES (copie na seção de restrições)
======================================================================
NÃO editar migrations V1–V9; NÃO alterar `legacy/` nem `docs/legacy/`; NÃO hard-delete; NÃO double/float para dinheiro;
NÃO desabilitar testes/lint; NÃO usar `any` no front; NÃO fazer commit/push automático; NÃO migrar hash de senha;
NÃO logar/auditar senha, hash, token, CPF, e-mail ou telefone; NÃO usar dados pessoais reais em testes.

======================================================================
FORMATO OBRIGATÓRIO DO ARQUIVO (seções numeradas, nesta ordem)
======================================================================
1. Objetivo
2. Análise obrigatória (o que o executor deve ler/inspecionar antes de codificar, incluindo valores distintos de enums/categorias no legado)
3. Decisões arquiteturais (tabela DA-01, DA-02, ...)
4. Escopo
5. Fora de escopo
6. Banco (V10 com SQL COMPLETO)
7. Mapeamento legado → novo, tabela por tabela (coluna a coluna, com conversões)
8. Tabelas de conversão de enums/categorias/formas de pagamento/status
9. Ordem de execução e tratamento de falha/retomada
10. Dry-run, idempotência e relatório da execução (com exemplo do relatório em markdown)
11. Configuração (propriedades e variáveis de ambiente, com valores padrão)
12. Auditoria
13. Segurança e LGPD
14. DevOps (compose, .env.example, .gitignore, runbook)
15. Conferências automáticas
16. Regras numeradas (R-01, R-02, ...)
17. Testes unitários (nome de cada classe e cenários)
18. Testes de integração (nome de cada classe e cenários) + conteúdo resumido da fixture sintética
19. Frontend (declarar que não há mudança de tela e que a suíte deve continuar verde)
20. Documentação a atualizar (README, arquitetura.md, banco-de-dados.md, next-stage/README.md)
21. Critérios de aceite (checklist)
22. Riscos
23. Ordem de implementação
24. Restrições
25. Relatório final `08-migracao-legado-relatorio.md` no formato: "unitários: X/Y, integração: X/Y, frontend: X/Y", PASS/FAIL de backend build, frontend build, lint, typecheck, Docker, divergências, pendências e handoff da etapa 09
26. Definição de pronto
27. Sugestão da etapa 09 (endurecimento para produção: deploy, HTTPS, backups, observabilidade, E2E Playwright no CI, LGPD)

Seja específico e verificável (nomes de classes, propriedades, SQL, cenários de teste). Não deixe decisões em aberto:
quando algo depender dos dados reais, defina o comportamento padrão e mande registrar como pendência.

LEMBRETE FINAL: devolva APENAS o arquivo `08-migracao-legado.md` pronto, começando pelo título da etapa. Não repita este prompt.
