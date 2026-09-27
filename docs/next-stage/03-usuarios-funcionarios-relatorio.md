# 03 — Usuários e funcionários: relatório de execução

Especificação: [03-usuarios-funcionarios.md](03-usuarios-funcionarios.md). Branch `feat/usuarios`, **sem commit** (seção 26). Ela foi criada a partir de `feat/agendamentos`, então as etapas 01, 02 e 03 estão juntas, sem commit, na mesma cópia de trabalho.

## Implementação concluída

### Banco
- `V5__seguranca_usuarios_funcionarios.sql` com a coluna `usuario.token_version INT NOT NULL DEFAULT 0` e 3 índices: `idx_usuario_ativo_papel`, `idx_funcionario_nome` e `idx_token_redefinicao_usuario_expira`.
- `idx_token_redefinicao_expira_usado` **não** foi criado: serviria a um job de limpeza que ainda não existe.
- V1–V4 e `legacy/` estão intactos.
- A V5 foi validada em banco limpo (Testcontainers) e no upgrade V4 → V5 (banco do compose).

### Segurança
- **Política de senha:** `PoliticaSenha` é a regra única (10–72 caracteres, maiúscula, minúscula, número, especial, diferente do username, do e-mail e da parte antes do `@`). O BCrypt tem custo 12. O frontend espelha a regra (`violacaoSenha`) com lista de requisitos atualizada enquanto se digita.
- **`token_version` e JWT:**
  - o JWT ganhou a claim `tv`;
  - `UsuarioJwtAuthenticationConverter` roda dentro do filtro de Bearer e confere no banco, a cada requisição, se o usuário existe, está ativo e tem a mesma versão;
  - o papel da autorização vem do banco, e qualquer divergência dá 401;
  - a versão sobe em troca ou redefinição de senha, troca de papel, desativação e reativação.
- **Invalidação no front:**
  - 401 da API leva a `/login?expirada=1`, e o `proxy.ts` apaga o cookie (corrigi um **loop de redirecionamento** que aconteceria com qualquer sessão revogada);
  - a troca de senha apaga o cookie e manda para o login.
- **Último ADMIN:** um ADMIN ativo não pode ser desativado nem rebaixado se for o último; a checagem trava os ADMINs ativos (`FOR UPDATE`) e responde 409 com a mensagem da spec. Além disso, **nenhum ADMIN altera o próprio papel nem se desativa** (409).
- **Defesa por URL:** `/api/v1/usuarios/**` e as escritas de funcionários e serviços também são barradas no filtro de segurança, então um papel sem permissão recebe 403 antes da validação do corpo. Um IT pegou esse vazamento de 400.

### Backend — usuários e funcionários
- **Usuários** (`/api/v1/usuarios`, só ADMIN): listar com `q`, `ativo`, `papel` e paginação; detalhar; criar; editar conta; `PATCH /papel`; `PATCH /desativar`; `PATCH /ativar`. Nunca devolve `senha_hash` nem `token_version`.
- **Funcionários** (`/api/v1/funcionarios`): a lista de disponíveis (conta ativa) vale para qualquer autenticado; detalhar, criar e atualizar são só para ADMIN.
  - Vínculo 1:1: um segundo funcionário para o mesmo usuário dá 409.
  - CPF válido e único; telefone único; nascimento não futuro; endereço reaproveitando `EnderecoRequest`.
  - A entidade `Funcionario` virou completa, com o `usuario` 1:1.

### Recuperação de senha
- **Token:** 32 bytes de `SecureRandom` em Base64 URL-safe, e só o SHA-256 vai para `token_redefinicao_senha`. Vale 30 minutos, é de uso único, e um novo pedido ou uma troca de senha invalida os pendentes.
- **Rate limit:** `LimitadorRedefinicao` (interface, pronta para trocar por Redis) com implementação em memória: 3 pedidos por e-mail e 10 por IP a cada 30 minutos. O IP vem em `X-Forwarded-For`, repassado pelo Next.
- **Resposta sempre neutra:** 202 com a mensagem da spec para e-mail existente, inexistente, conta inativa ou limite atingido.
- **Mailpit/SMTP:** `EmailService` com a implementação `SmtpEmailService` (JavaMailSender).
  - No compose, o `mailpit` roda com UI em http://localhost:8025.
  - Em produção, bastam `SMTP_HOST/PORT/USERNAME/PASSWORD/AUTH/STARTTLS`, sem mudar código.
  - O health de mail foi desligado para um SMTP fora do ar não derrubar o readiness da API.

### Perfil
- `GET/PUT /api/v1/me` (username e e-mail; papel, ativo e senha no corpo são ignorados).
- `PUT /api/v1/me/funcionario` (sem CPF e sem vínculo; 404 se não houver funcionário).
- `POST /api/v1/me/trocar-senha` (senha atual, política, confirmação, nova diferente da atual).

### Agendamentos
- O seletor **Profissional** só aparece quando há funcionários ativos, com a opção "Sem funcionário definido".
- A API valida se o funcionário existe e se a conta está ativa; inativo dá 409 com a mensagem da spec.
- A regra de conflito por funcionário continua valendo.
- Agendamentos existentes mantêm o funcionário, com `funcionario.disponivel=false`, e a tela mostra "Funcionário indisponível — reatribuição necessária".

### Frontend
- Rotas novas:
  - `/usuarios`, `/usuarios/novo` e `/usuarios/[id]` (seções Conta, Funcionário e Segurança), só para ADMIN;
  - `/perfil`;
  - `/esqueci-senha` e `/redefinir-senha`, públicas.
- O item **Usuários** do menu aparece só para ADMIN, e o nome do usuário no menu leva ao perfil.
- O login ganhou o link "Esqueci minha senha" e avisos de senha alterada, senha redefinida e sessão expirada.
- O token de redefinição fica em campo escondido (nunca aparece na tela), e a página usa `referrer: no-referrer`.

### Auditoria
- `USUARIO_CRIADO`, `USUARIO_ATUALIZADO`, `USUARIO_PAPEL_ALTERADO` (anterior e novo), `USUARIO_DESATIVADO`, `USUARIO_ATIVADO`.
- `FUNCIONARIO_CRIADO`, `FUNCIONARIO_ATUALIZADO`, `PERFIL_ATUALIZADO`, `PERFIL_FUNCIONARIO_ATUALIZADO`.
- `SENHA_ALTERADA`, `REDEFINICAO_SENHA_SOLICITADA`, `SENHA_REDEFINIDA`.
- Conferido no Mongo: **0** registros com senha, hash, token ou JWT.

### Testes
- Unitários: **165 aprovados / 0 falharam**. Antes eram 115; entraram 50, com todos os casos nominais da seção 19 e extras.
- Integração: **113 aprovados / 0 falharam**. Antes eram 71; entraram 42 em 5 ITs, com todos os casos da seção 20 e extras. Os ITs usam login real e um `EmailService` de teste que captura o link.
- Frontend: **97 aprovados / 0 falharam**. Antes eram 65; entraram 32 nos 9 arquivos da seção 21, mais `proxy.test.ts`.

### Validações
- Backend build (`./mvnw verify`): PASS
- Frontend build: PASS
- Lint: PASS (0 avisos)
- Typecheck: PASS
- Ponta a ponta no `docker compose`:
  - cookie antigo (sem `tv`) → `/login?expirada=1`, sem loop;
  - ADMIN criou a "joana" (Serviço) e o funcionário dela;
  - a Joana apareceu no seletor de profissional e recebeu um agendamento;
  - "Esqueci a senha" → e-mail no Mailpit → link → senha redefinida → login;
  - perfil com CPF só para leitura e sem o menu Usuários;
  - troca de senha → sessão encerrada;
  - pela API: reuso do token dá 400, senha antiga dá 401, JWT da Joana dá 401 depois da desativação, e o agendamento dela foi mantido com o funcionário indisponível;
  - admin tentando se desativar → 409;
  - celular sem rolagem horizontal.

## Divergências em relação à spec (decisões tomadas)
1. **Nome do funcionário até 100 caracteres, não 150.** É o tamanho da coluna na V1. A spec só previa `token_version` e índices na V5; ampliar a coluna fica para uma migration futura, se necessário.
2. **Autoproteção do ADMIN:** além do "último ADMIN", nenhum ADMIN altera o próprio papel nem se desativa (regra 29 aplicada também ao endpoint administrativo).
3. **Referências inexistentes no corpo → 400 no campo:** `usuarioId` inexistente na criação de funcionário e papel inexistente respondem 400 no campo (`CampoInvalidoException`), no mesmo padrão das etapas anteriores.
4. **Nome do teste de conversor:** `JwtAuthenticationFilterTest` testa `UsuarioJwtAuthenticationConverter`, que é onde a validação por requisição acontece (dentro do filtro de Bearer do Spring Security).
5. **Bootstrap com a política:** o `AdminBootstrap` passou a aplicar a política de senha. `ADMIN_PASSWORD` fraca impede a API de subir. `.env.example` e a senha de teste foram ajustados.

## Pendências
- **Três etapas sem commit na mesma branch:** 01, 02 e 03 estão juntas em `feat/usuarios`.
- **Todos precisam entrar de novo depois do deploy:** tokens emitidos antes da V5 não têm `tv` e são recusados. Na tela, cai em "sessão expirada".
- **Nome no menu desatualizado após trocar o username:** o menu continua mostrando o username antigo, que vem do JWT, até o próximo login. A autorização e a auditoria já usam o nome atual do banco.
- **Canal de tempo no "esqueci a senha":** para um e-mail existente, a resposta demora mais (grava o token e envia o e-mail). Mitigação futura: enviar de forma assíncrona.
- **`X-Forwarded-For` e limite em memória:**
  - a API confia no `X-Forwarded-For` para o limite por IP;
  - em produção, a API deve ficar acessível só pelo Next ou pelo proxy reverso (no compose de dev a porta 8081 está exposta);
  - o limite por e-mail vale sempre;
  - o limitador é em memória, então com mais de uma réplica precisa virar Redis (interface pronta).

---

## Handoff para a etapa 04: prompt para o ChatGPT

A próxima etapa natural é **Produtos, fornecedores, compras e estoque**, pré-requisito do PDV (etapa 05).

````text
Você é o arquiteto de software do projeto PetConnect Web. Gere UM arquivo Markdown chamado `04-produtos-estoque.md`, que será executado por outro agente de IA (Claude Code) direto no repositório. Seja específico e verificável: nada de "a definir". Responda somente com o conteúdo do arquivo, dentro de um único bloco ```markdown. Mantenha o formato das etapas anteriores: objetivo, análise obrigatória, decisões arquiteturais justificadas, escopo, fora de escopo, banco, API endpoint a endpoint com JSON, regras numeradas, autorização em tabela, auditoria, frontend, testes com nomes de casos, critérios de aceite, riscos, ordem de implementação, restrições e relatório final.

## Contexto do projeto (estado atual)
- Monorepo: `backend/` (Spring Boot 4.1, Java 21, monólito modular `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`; Spring só expõe REST), `frontend/` (Next.js 16 App Router, Server Actions, Zod, Tailwind 4; BFF com JWT em cookie httpOnly), `legacy/php/` (só referência).
- MySQL 8.4 com Flyway (aplicadas V1–V5; nunca editar migration aplicada). MongoDB só para auditoria (`AuditoriaService.registrar`, sem dados sensíveis).
- Padrões: entidades sem setters públicos; exclusão lógica; `NotFoundException` → 404; `ConflictException` → 409; `CampoInvalidoException(campo, msg)` → 400 com `errors`; `PageResponse<T>`; `Clock` America/Sao_Paulo; `@PreAuthorize` + regras por URL no `SecurityConfig` para rotas administrativas (para 403 antes da validação do corpo); `SELECT ... FOR UPDATE` onde há concorrência (já usado em agendamentos e no "último ADMIN"); testes `*Test` (Mockito) e `*IT` (Testcontainers; base `PetIntegrationTest` com `comPapel`, `criarCliente`, `criarUsuario`, `login`/`bearer`).
- Segurança: JWT validado contra o banco a cada requisição (usuário ativo + `token_version` + papel atual). Papéis: ADMIN, VENDAS, SERVICO.
- Frontend: `AppShell` (itens "Vendas (PDV)" e "Produtos" ainda "em breve"), `Campo`, `Aviso` (info/erro), `BotaoConfirmar`, formulários com `useActionState` + Zod, layout mobile-first (tabela md+, cartões no celular), `mensagemDeErro(status)`.
- Módulos prontos: clientes + endereço, pets (carteira, consultas, foto), serviços + agendamentos (máquina de estados, `servico_realizado`), usuários/funcionários/perfil/recuperação de senha. Financeiro ainda não existe.

## Tabelas já existentes (V1)
- `categoria_produto(id, nome UNIQUE, descricao)` — V2 traz Ração, Medicamento, Brinquedo, Higiene, Acessório.
- `produto(id, nome, descricao, categoria_id FK, fornecedor_padrao_id FK NULL ON DELETE SET NULL, codigo_barras UNIQUE NULL, preco_custo, preco_venda NOT NULL, quantidade_estoque INT NOT NULL DEFAULT 0 CHECK >= 0, estoque_minimo INT DEFAULT 5, ativo, created_at, updated_at)`.
- `fornecedor(id, nome_fantasia, razao_social, cnpj CHAR(14) UNIQUE NOT NULL, telefone UNIQUE, email, contato, endereco_id FK UNIQUE → endereco, observacoes, ativo, ...)`.
- `compra(id, fornecedor_id FK, funcionario_id FK NULL, data_compra DATE, data_entrega DATE NULL, valor_total, status CHECK PEDIDO|RECEBIDO|CANCELADO, forma_pagamento VARCHAR(50), observacoes)`.
- `item_compra(id, compra_id FK CASCADE, produto_id FK, quantidade CHECK > 0, preco_custo_unitario, subtotal GERADO = quantidade * preco_custo_unitario)`.
- `movimentacao_financeira(...)` com FKs opcionais para venda/compra/servico_realizado — financeiro ainda fora de escopo.
- Não existe tabela de movimentação de estoque (histórico de entradas/saídas/ajustes): decida se cria na V6 (recomendado para rastreabilidade: tipo ENTRADA_COMPRA/SAIDA_VENDA/AJUSTE, quantidade, saldo resultante, origem, usuário, motivo) e justifique.

## Legado (PHP) relevante
- `produtos_cadastro.php`, `produtos_editar.php`, `produtos_processar.php`, `produtos_excluir.php`, `produtos_listar.php`, `listar_produtos.php`, `produtos_buscar_rapido.php`, `produtos_dados.php`: CRUD de produto, busca rápida (usada pelo PDV) e exclusão física.
- O legado não tinha telas de fornecedor nem de compra; a entrada de estoque era manual (editar quantidade).

## O que a especificação `04-produtos-estoque.md` deve decidir e cobrir
1. Escopo: categorias (leitura; e decidir se ADMIN edita), produtos (CRUD com exclusão lógica, código de barras único, busca rápida por nome/código para o futuro PDV, filtro "abaixo do mínimo"), fornecedores (CRUD com CNPJ válido/único e endereço reaproveitando `EnderecoRequest`), compras (pedido com itens → recebimento que dá entrada no estoque e atualiza o custo → cancelamento) e ajuste manual de estoque com motivo obrigatório.
2. Decisões justificadas: política de custo ao receber compra (último custo vs. custo médio ponderado); se a quantidade em estoque pode ser editada direto no cadastro do produto (recomendado: não — só por compra/ajuste, com histórico); o que acontece ao desativar produto com estoque ou fornecedor com compras em aberto; se o `valor_total` da compra é calculado dos itens (recomendado) e congelado; concorrência no saldo (lock pessimista no produto ao movimentar).
3. Banco: V6 só com o necessário (ex.: tabela de movimentação de estoque, índices), sem editar V1–V5.
4. API endpoint a endpoint com JSON, validações, papéis (sugestão: leitura para todos; cadastro de produto/fornecedor e ajuste para ADMIN e VENDAS ou só ADMIN — decida; compras e recebimento para ADMIN) e códigos de erro.
5. Frontend: `/produtos` (lista com busca, badge textual "abaixo do mínimo"), `/produtos/novo`, `/produtos/[id]` (dados + histórico de movimentações + ajuste), `/fornecedores*`, `/compras*` (criar pedido adicionando itens, receber com confirmação, cancelar), liberar o item "Produtos" do menu (e decidir onde ficam Fornecedores/Compras), mobile-first e acessível; dashboard com contador de produtos abaixo do mínimo.
6. Auditoria: eventos por ação (produto, fornecedor, compra criada/recebida/cancelada, ajuste de estoque com quantidade e motivo).
7. Testes nominais: unitários (custo, recebimento, ajuste negativo além do saldo, transições da compra), IT (permissões, recebimento atualiza saldo e histórico, concorrência de ajuste, código de barras/CNPJ duplicados) e frontend.
8. Critérios de aceite, riscos e o que fica para depois: PDV/vendas (etapa 05, consumirá a busca rápida e a baixa de estoque via movimentação), financeiro (contas a pagar das compras), migração de dados do legado.
````
