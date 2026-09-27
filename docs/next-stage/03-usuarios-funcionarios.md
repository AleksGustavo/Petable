# 03 — Usuários, Funcionários, Perfil e Recuperação de Senha

> **Arquivo:** `03-usuarios-funcionarios.md`  
> **Projeto:** PetConnect Web  
> **Stack:** Spring Boot 4.1 + Java 21 + Spring MVC/REST + Next.js 16 + TypeScript + MySQL 8.4 + MongoDB 7  
> **Regra:** preservar a arquitetura MVC/REST e os padrões já consolidados. Nunca editar migrations V1–V4.

## 1. Objetivo

Implementar CRUD administrativo de usuários, funcionários 1:1, perfil próprio, troca e recuperação de senha, invalidação imediata de sessões e integração de funcionários aos agendamentos.

## 2. Análise obrigatória

Antes de alterar código, inspecionar `backend/`, `frontend/`, V1–V4, auth/JWT, `AdminBootstrap`, `Usuario`, `Papel`, `Funcionario`, endereços, `AuditoriaService`, handlers de erro, agendamentos, `apiFetch`, Server Actions, `lib/permissoes.ts`, `proxy.ts` e `AppShell`. Executar baseline de testes, lint, typecheck e builds. O legado PHP é somente referência; não copiar SHA-256 nem sua arquitetura.

MVC adotado:
- **Model:** entidades, domínio, serviços de aplicação e repositórios.
- **View:** Next.js.
- **Controller:** Spring MVC expondo REST.
- Não criar JSP/Thymeleaf.

## 3. Decisões arquiteturais

### DA-01 — Usuário ≠ funcionário
ADMIN/VENDAS/SERVICO podem existir sem funcionário. `funcionario` é perfil operacional opcional 1:1; funcionário sempre possui usuário.

### DA-02 — Atividade do funcionário
Não adicionar `funcionario.ativo`. Funcionário está disponível quando existe e `usuario.ativo=true`.

### DA-03 — Funcionário desativado
Desativar usuário não cancela nem apaga agendamentos. O funcionário deixa de aparecer em novas seleções; agendas existentes preservam o ID e mostram **Funcionário indisponível — reatribuição necessária**.

### DA-04 — Último ADMIN
Nunca permitir desativar/rebaixar o último ADMIN ativo. Retornar 409: `Não é possível remover ou desativar o último administrador ativo do sistema.` Validar transacionalmente com lock apropriado.

### DA-05 — Invalidação imediata
Adicionar `usuario.token_version INT NOT NULL DEFAULT 0`. JWT inclui claim `tv`. Em toda requisição autenticada validar usuário ativo e `tv` igual ao banco. Incrementar ao trocar/redefinir senha, alterar papel, desativar ou reativar usuário.

### DA-06 — Política de senha
Centralizar regra:
- 10–72 caracteres;
- 1 maiúscula, 1 minúscula, 1 número e 1 especial;
- diferente do username;
- diferente do e-mail e da parte antes de `@`;
- BCrypt custo 12.

### DA-07 — Redefinição
Token criptograficamente seguro com >=32 bytes; persistir somente SHA-256; expiração 30 minutos; uso único. Novo token invalida pendentes anteriores.

### DA-08 — E-mail
Criar `EmailService`. Desenvolvimento/Docker usa Mailpit SMTP (`mailpit:1025`, UI `8025`, sem TLS/auth). Produção usa propriedades SMTP/variáveis, sem mudança de código.

### DA-09 — Anti-enumeração
`POST /api/v1/auth/esqueci-senha` sempre retorna 202:
```json
{"message":"Se existir uma conta ativa para o e-mail informado, enviaremos as instruções de redefinição."}
```

### DA-10 — Rate limit
Em memória: máximo 3 pedidos/e-mail/30 min e 10/IP/30 min. Mesmo bloqueado, retornar a mesma resposta 202. Estruturar para futura troca por Redis.

## 4. Escopo

Entram: CRUD administrativo de usuários; ativação/desativação; papel; funcionário/endereço; perfil próprio; troca de senha; recuperação por e-mail; token_version; Mailpit; integração funcionário-agendamento; auditoria; frontend e testes.

## 5. Fora de escopo

Múltiplos papéis, permissões customizadas, refresh token, OAuth, MFA, painel de sessões, hard delete, folha, comissão, escala, férias, ponto, redefinição administrativa direta, PDV, estoque, compras, financeiro e migração do legado.

## 6. Banco — V5

Criar `V5__seguranca_usuarios_funcionarios.sql`:

```sql
ALTER TABLE usuario
    ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE INDEX idx_usuario_ativo_papel ON usuario (ativo, papel_id);
CREATE INDEX idx_funcionario_nome ON funcionario (nome);
CREATE INDEX idx_token_redefinicao_usuario_expira
    ON token_redefinicao_senha (usuario_id, expira_em);
CREATE INDEX idx_token_redefinicao_expira_usado
    ON token_redefinicao_senha (expira_em, usado_em);
```

Criar somente índices ausentes após conferir V1–V4.

## 7. API — Usuários

Base `/api/v1/usuarios`, somente ADMIN.

### Listar
`GET /api/v1/usuarios?page=0&size=20&q=maria&ativo=true&papel=VENDAS`

Busca por username, e-mail ou nome do funcionário. `page>=0`, `1<=size<=100`, `q<=100`, papel ADMIN/VENDAS/SERVICO. Retornar `PageResponse`.

### Detalhar
`GET /api/v1/usuarios/{id}` → 404 inexistente. Nunca devolver `senha_hash` ou `token_version`.

### Criar
`POST /api/v1/usuarios`
```json
{
  "username":"maria",
  "email":"maria@empresa.com.br",
  "senha":"Pet@2026Seguro",
  "papel":"VENDAS"
}
```
Username 3–50, `^[a-zA-Z0-9._-]+$`; e-mail válido/único; senha conforme política; papel existente; nasce ativo. Duplicidade → 409. Auditar `USUARIO_CRIADO`.

### Editar
`PUT /api/v1/usuarios/{id}`
```json
{"username":"maria.silva","email":"maria.silva@empresa.com.br"}
```
Não aceitar senha/papel/ativo. Auditar `USUARIO_ATUALIZADO`.

### Alterar papel
`PATCH /api/v1/usuarios/{id}/papel`
```json
{"papel":"SERVICO"}
```
Proteger último ADMIN. Se mudou, incrementar `token_version`. Auditar `USUARIO_PAPEL_ALTERADO` com papel anterior/novo.

### Desativar
`PATCH /api/v1/usuarios/{id}/desativar`. Proteger último ADMIN; incrementar versão; preservar funcionário/agendas. Auditar `USUARIO_DESATIVADO`.

### Ativar
`PATCH /api/v1/usuarios/{id}/ativar`. Incrementar versão. Auditar `USUARIO_ATIVADO`.

## 8. API — Funcionários

### Listar ativos
`GET /api/v1/funcionarios?ativos=true&q=ana` — ADMIN/VENDAS/SERVICO.
```json
[{"id":7,"usuarioId":10,"nome":"Ana Souza"}]
```
Somente usuários ativos; ordenar por nome.

### Detalhar
`GET /api/v1/funcionarios/{id}` — ADMIN.

### Criar
`POST /api/v1/funcionarios` — ADMIN.
```json
{
  "usuarioId":10,
  "nome":"Ana Souza",
  "cpf":"12345678909",
  "dataNascimento":"1995-04-20",
  "sexo":"FEMININO",
  "telefone":"19999999999",
  "endereco":{
    "cep":"13610000","logradouro":"Rua Exemplo","numero":"100",
    "complemento":null,"bairro":"Centro","cidade":"Leme","uf":"SP"
  },
  "observacoes":null
}
```
Usuário existente e sem funcionário; nome 2–150; CPF 11 dígitos com verificadores e único; nascimento não futuro; sexo MASCULINO/FEMININO/OUTRO; telefone 10/11 dígitos e único; reutilizar validações de endereço; observações <=2000. Conflitos → 409. Auditar `FUNCIONARIO_CRIADO`.

### Atualizar
`PUT /api/v1/funcionarios/{id}` — ADMIN. Não permitir trocar `usuarioId`. Auditar `FUNCIONARIO_ATUALIZADO`.

## 9. API — Perfil

Base `/api/v1/me`, qualquer autenticado. Identidade vem do SecurityContext.

### Consultar
`GET /api/v1/me` retorna id, username, e-mail, papel e funcionário/endereço se houver.

### Editar conta
`PUT /api/v1/me`
```json
{"username":"ana.souza","email":"ana.souza@empresa.com.br"}
```
Não aceitar papel/ativo/senha. Auditar `PERFIL_ATUALIZADO`.

### Editar próprio funcionário
`PUT /api/v1/me/funcionario`. Permitir nome, nascimento, sexo, telefone, endereço e observações. Não permitir CPF/usuarioId. Sem funcionário → 404. Auditar `PERFIL_FUNCIONARIO_ATUALIZADO`.

## 10. Troca de senha

`POST /api/v1/me/trocar-senha`
```json
{
  "senhaAtual":"Senha@Atual2026",
  "novaSenha":"Nova@Senha2026",
  "confirmacaoNovaSenha":"Nova@Senha2026"
}
```
Validar senha atual BCrypt, política, confirmação e nova diferente da atual. Sucesso: novo BCrypt, `token_version++`, invalidar tokens de redefinição pendentes, 204. Senha atual errada → 400 `errors.senhaAtual`. Frontend remove `pc_session` e redireciona ao login. Auditar `SENHA_ALTERADA`.

## 11. Esqueci a senha

`POST /api/v1/auth/esqueci-senha` público.
```json
{"email":"ana@empresa.com.br"}
```
Sempre 202 neutro. Para usuário ativo e dentro do limite: invalidar tokens pendentes, gerar token, persistir SHA-256, expirar em 30 min, enviar link `{frontendBaseUrl}/redefinir-senha?token={token}`. Auditar `REDEFINICAO_SENHA_SOLICITADA` sem e-mail/token nos detalhes.

## 12. Redefinir senha

`POST /api/v1/auth/redefinir-senha` público.
```json
{
  "token":"token-url-safe",
  "novaSenha":"Nova@Senha2026",
  "confirmacaoNovaSenha":"Nova@Senha2026"
}
```
Hash do token → localizar → validar não usado/expirado e usuário ativo → política → BCrypt → marcar usado → invalidar pendentes → `token_version++`. Token inválido/expirado/usado → 400 com `errors.token = "O link de redefinição é inválido ou expirou."`. Sucesso 204. Auditar `SENHA_REDEFINIDA`.

## 13. JWT

JWT passa a conter `tv`. Filtro valida assinatura, expiração, `uid`, existência, ativo e versão. A autorização efetiva deve refletir o papel atual do banco. Divergência → 401.

## 14. Integração com agendamentos

Formulário deixa de enviar sempre `funcionarioId:null`. Carregar `GET /api/v1/funcionarios?ativos=true`. Funcionário continua opcional com opção **Sem funcionário definido**. Backend valida funcionário existente + usuário ativo. Inativo → 409: `O funcionário selecionado não está disponível para novos agendamentos.` Preservar conflito de agenda já implementado.

## 15. Autorização

| Operação | ADMIN | VENDAS | SERVICO |
|---|---:|---:|---:|
| Listar/criar/editar usuários | Sim | Não | Não |
| Alterar papel/ativar/desativar | Sim | Não | Não |
| Criar/editar funcionário administrativo | Sim | Não | Não |
| Listar funcionários para agenda | Sim | Sim | Sim |
| Ver/editar próprio perfil | Sim | Sim | Sim |
| Trocar própria senha | Sim | Sim | Sim |
| Recuperar/redefinir senha | Público | Público | Público |

## 16. Auditoria

Eventos:
- `USUARIO_CRIADO`
- `USUARIO_ATUALIZADO`
- `USUARIO_PAPEL_ALTERADO`
- `USUARIO_DESATIVADO`
- `USUARIO_ATIVADO`
- `FUNCIONARIO_CRIADO`
- `FUNCIONARIO_ATUALIZADO`
- `PERFIL_ATUALIZADO`
- `PERFIL_FUNCIONARIO_ATUALIZADO`
- `SENHA_ALTERADA`
- `REDEFINICAO_SENHA_SOLICITADA`
- `SENHA_REDEFINIDA`

Nunca registrar senha, BCrypt, token, hash do token, JWT, cookie ou credencial SMTP.

## 17. Frontend

Criar:
```text
/usuarios
/usuarios/novo
/usuarios/[id]
/perfil
/esqueci-senha
/redefinir-senha?token=
```

`/usuarios*` somente ADMIN. Item **Usuários** no AppShell somente ADMIN. Liberar apenas esqueci/redefinir no `proxy.ts`.

### `/usuarios`
Busca por usuário/e-mail/funcionário; filtros papel e ativo; tabela em md+ e cards mobile.

### `/usuarios/novo`
Username, e-mail, senha, confirmação e papel. Mostrar requisitos. Após criar → `/usuarios/{id}`.

### `/usuarios/[id]`
Seções Conta, Funcionário e Segurança. Confirmar mudança de papel/desativação. Se sem funcionário, CTA **Cadastrar funcionário**.

### `/perfil`
Conta + funcionário, editar dados permitidos e alterar senha. Sem funcionário: `Esta conta não possui um perfil de funcionário vinculado.`

### `/esqueci-senha`
Campo e-mail. Sempre mostrar mensagem neutra.

### `/redefinir-senha`
Nova senha + confirmação. Não mostrar token. Token ausente → link inválido/incompleto. Sucesso → CTA login.

Acessibilidade: labels visíveis, erros associados, `autocomplete` adequado, estados de processamento, confirmação de ações destrutivas e status textual.

## 18. Regras numeradas

1. Usuário nunca é excluído fisicamente.
2. Funcionário não é excluído nesta etapa.
3. Usuário possui no máximo um funcionário.
4. Funcionário possui exatamente um usuário.
5. ADMIN pode existir sem funcionário.
6. Só ADMIN gerencia usuários/vínculos.
7. Deve existir ao menos um ADMIN ativo.
8. Último ADMIN não pode ser desativado/rebaixado.
9. Usuário inativo não autentica.
10. Funcionário de usuário inativo não aparece em novas agendas.
11. Desativação preserva agendas existentes.
12. Senha segue política central.
13. Senha não pode ser username/e-mail/local-part.
14. BCrypt custo 12.
15. Mudança/redefinição de senha incrementa versão.
16. Mudança de papel incrementa versão.
17. Ativação/desativação incrementa versão.
18. JWT com versão divergente é inválido.
19. Token de redefinição expira em 30 min.
20. Token é uso único.
21. Só SHA-256 do token é persistido.
22. Novo pedido invalida pendentes anteriores.
23. Recuperação não revela existência do e-mail.
24. Rate limit não altera resposta pública.
25. CPF é válido e único.
26. Telefone informado é único.
27. Nascimento não pode ser futuro.
28. Usuário edita somente próprio perfil permitido.
29. Usuário não altera próprio papel.
30. CPF não é alterável pelo próprio perfil.
31. Funcionário atribuído a novo agendamento deve ter usuário ativo.

## 19. Testes unitários

### PoliticaSenhaTest
`deveAceitarSenhaValida`, `deveRejeitarSenhaComMenosDeDezCaracteres`, `deveRejeitarSenhaSemMaiuscula`, `deveRejeitarSenhaSemMinuscula`, `deveRejeitarSenhaSemNumero`, `deveRejeitarSenhaSemEspecial`, `deveRejeitarSenhaIgualAoUsername`, `deveRejeitarSenhaIgualAoEmail`, `deveRejeitarSenhaIgualAParteLocalDoEmail`, `deveRejeitarSenhaMaiorQueSetentaEDoisCaracteres`.

### UsuarioServiceTest
`deveCriarUsuario`, `deveFalharComUsernameDuplicado`, `deveFalharComEmailDuplicado`, `deveAlterarPapelEIncrementarTokenVersion`, `deveDesativarUsuarioEIncrementarTokenVersion`, `deveAtivarUsuarioEIncrementarTokenVersion`, `deveImpedirDesativacaoDoUltimoAdminAtivo`, `deveImpedirRebaixamentoDoUltimoAdminAtivo`, `devePermitirRebaixarAdminQuandoExisteOutroAdminAtivo`.

### FuncionarioServiceTest
`deveCriarFuncionarioVinculadoAUsuario`, `deveImpedirSegundoFuncionarioParaMesmoUsuario`, `deveRejeitarCpfInvalido`, `deveRejeitarCpfDuplicado`, `deveRejeitarTelefoneDuplicado`, `deveRejeitarNascimentoFuturo`, `deveListarSomenteFuncionariosComUsuarioAtivo`.

### TrocaSenhaServiceTest
`deveTrocarSenhaComSenhaAtualCorreta`, `deveRejeitarSenhaAtualIncorreta`, `deveRejeitarNovaSenhaIgualAAtual`, `deveIncrementarTokenVersionAoTrocarSenha`, `deveInvalidarTokensDeRedefinicaoPendentes`.

### RedefinicaoSenhaServiceTest
`deveGerarTokenComHashSha256`, `deveDefinirExpiracaoDeTrintaMinutos`, `deveRedefinirSenhaComTokenValido`, `deveRejeitarTokenExpirado`, `deveRejeitarTokenJaUsado`, `deveRejeitarTokenInexistente`, `deveInvalidarTokensAnterioresAoGerarNovo`, `deveIncrementarTokenVersionAoRedefinir`, `deveRetornarRespostaNeutraParaEmailInexistente`, `deveRespeitarRateLimitPorEmail`, `deveRespeitarRateLimitPorIp`.

### JwtAuthenticationFilterTest
`deveAceitarTokenComVersaoAtual`, `deveRejeitarTokenComVersaoAntiga`, `deveRejeitarTokenDeUsuarioInativo`, `deveUsarPapelAtualDoUsuario`.

## 20. Testes de integração

### UsuarioControllerIT
`adminDeveListarUsuarios`, `vendasNaoDeveListarUsuarios`, `servicoNaoDeveCriarUsuario`, `adminDeveCriarUsuario`, `deveRetornar409ParaUsernameDuplicado`, `deveRetornar409ParaEmailDuplicado`, `adminDeveAlterarPapel`, `deveImpedirRebaixamentoDoUltimoAdmin`, `deveImpedirDesativacaoDoUltimoAdmin`, `usuarioDesativadoNaoDeveConseguirLogin`, `tokenAnteriorDeveFalharAposMudancaDePapel`, `tokenAnteriorDeveFalharAposDesativacao`.

### FuncionarioControllerIT
`adminDeveCriarFuncionario`, `vendasNaoDeveCriarFuncionario`, `deveRetornar409ParaUsuarioJaVinculado`, `deveRetornar400ParaCpfInvalido`, `deveListarFuncionariosAtivosParaAgendamento`, `naoDeveListarFuncionarioDeUsuarioInativoComoDisponivel`.

### MeuPerfilControllerIT
`usuarioDeveConsultarProprioPerfil`, `usuarioDeveEditarUsernameEEmail`, `usuarioNaoDeveAlterarProprioPapel`, `funcionarioDeveEditarPropriosDadosPermitidos`, `usuarioSemFuncionarioDeveReceber404AoEditarFuncionario`, `deveTrocarSenha`, `loginComSenhaAntigaDeveFalharAposTroca`, `loginComSenhaNovaDeveFuncionar`, `jwtAnteriorDeveFalharAposTrocaDeSenha`.

### RecuperacaoSenhaControllerIT
`deveResponder202ParaEmailExistente`, `deveResponder202ParaEmailInexistente`, `deveResponder202ParaUsuarioInativo`, `deveCriarTokenHashParaUsuarioAtivo`, `deveCompletarFluxoDeRedefinicao`, `deveRejeitarTokenExpirado`, `deveRejeitarTokenUsado`, `loginComNovaSenhaDeveFuncionarAposRedefinicao`, `loginComSenhaAntigaDeveFalharAposRedefinicao`.

### AgendamentoFuncionarioIT
`deveCriarAgendamentoComFuncionarioAtivo`, `deveRejeitarFuncionarioComUsuarioInativo`, `deveManterFuncionarioEmAgendamentoExistenteAposDesativacao`, `deveDetectarConflitoDeAgendaDoFuncionarioAtivo`.

## 21. Testes frontend

Criar `usuario-schema.test.ts`, `funcionario-schema.test.ts`, `trocar-senha-schema.test.ts`, `usuarios-list.test.tsx`, `usuario-detalhes.test.tsx`, `perfil.test.tsx`, `esqueci-senha.test.tsx`, `redefinir-senha.test.tsx`, `agendamento-funcionario.test.tsx`.

Cobrir validações, estados vazio/ativo/inativo, proteção visual por papel, confirmação de desativação, perfil sem funcionário, mensagem neutra de recuperação, token ausente/inválido, sucesso de redefinição e seleção de funcionário ativo.

## 22. Critérios de aceite

- [ ] V1–V4 intactas; V5 aplica em banco limpo e upgrade.
- [ ] `token_version` implementado e validado.
- [ ] Último ADMIN protegido.
- [ ] CRUD administrativo funciona sem hard delete.
- [ ] `senha_hash`/`token_version` nunca retornados.
- [ ] Funcionário 1:1 e CPF válido/único.
- [ ] Usuário inativo remove funcionário de novas seleções.
- [ ] Agendas existentes preservam funcionário.
- [ ] Troca de papel/desativação/senha invalida JWT anterior.
- [ ] `/perfil` funciona.
- [ ] Recuperação é neutra contra enumeração.
- [ ] Token expira em 30 min, é uso único e só hash é persistido.
- [ ] Rate limit por e-mail/IP funciona.
- [ ] Mailpit funciona no Docker.
- [ ] SMTP real é configurável por ambiente.
- [ ] Funcionário ativo aparece em agendamento e conflito continua válido.
- [ ] Auditoria não contém segredos.
- [ ] `/usuarios*` somente ADMIN.
- [ ] rotas de recuperação públicas no proxy.
- [ ] frontend mobile-first e acessível.
- [ ] testes unitários, IT e frontend verdes.
- [ ] backend/frontend build, lint e typecheck PASS.

## 23. Riscos

1. Consulta ao banco em cada JWT: aceitar custo para invalidação imediata; não criar cache prematuramente.
2. Rate limit em memória não é distribuído; preparar abstração para Redis futuro.
3. SMTP real depende da infraestrutura; Mailpit garante desenvolvimento reproduzível.
4. Funcionário desativado com agenda futura exige reatribuição manual, sem cancelamento automático.
5. Concorrência no último ADMIN: usar transação + `PESSIMISTIC_WRITE` ou mecanismo equivalente.

## 24. Próximas etapas

PDV/vendas, estoque/compras, financeiro, folha/comissões, escala, MFA, refresh token, painel de sessões e migração do legado. Usuários migrados nunca reutilizam SHA-256 como senha válida: devem redefinir para BCrypt.

## 25. Ordem de implementação

1. Inspecionar arquitetura e baseline.
2. Analisar V1–V4 e criar V5.
3. Centralizar política de senha.
4. Implementar token_version/JWT/filtro.
5. Proteger último ADMIN.
6. CRUD usuários.
7. Funcionários/endereço.
8. Perfil e troca de senha.
9. Token de redefinição/rate limit.
10. EmailService + Mailpit.
11. Recuperação/redefinição.
12. Auditoria.
13. Integração agendamentos.
14. Testes backend.
15. Rotas frontend e AppShell/proxy.
16. Testes frontend.
17. Testes completos, lint, typecheck e builds.
18. Revisar `git diff`.

## 26. Restrições

NÃO editar V1–V4; NÃO alterar legado; NÃO migrar SHA-256; NÃO armazenar senha/token puro; NÃO logar/auditar segredos; NÃO retornar hash; NÃO reduzir BCrypt abaixo de 12; NÃO hard-delete; NÃO deixar o sistema sem ADMIN; NÃO permitir autoalteração de papel; NÃO confiar apenas no papel de JWT antigo; NÃO proteger admin apenas no frontend; NÃO usar `any` para contornar TypeScript; NÃO desabilitar testes/lint; NÃO adicionar Redis/serviço externo obrigatório; NÃO implementar PDV/estoque/financeiro; NÃO commit/push automaticamente.

## 27. Relatório final obrigatório

```text
## Implementação concluída

### Banco
- migration V5
- colunas/índices

### Segurança
- política de senha
- token_version/JWT
- invalidação de sessão
- proteção último ADMIN

### Backend — Usuários/Funcionários
- arquivos/endpoints

### Recuperação
- token/hash/expiração
- rate limit
- Mailpit/SMTP

### Perfil
- endpoints/campos

### Agendamentos
- integração funcionário

### Frontend
- rotas/componentes/AppShell/proxy

### Auditoria
- eventos

### Testes
- unitários: X/Y
- integração: X/Y
- frontend: X/Y

### Validações
- backend build: PASS/FAIL
- frontend build: PASS/FAIL
- lint: PASS/FAIL
- typecheck: PASS/FAIL

### Pendências
- somente pendências reais
```

## 28. Definição de pronto

A etapa está pronta quando funcionarem de ponta a ponta:

```text
ADMIN → Usuários → Criar conta → Papel → Funcionário opcional → funcionário disponível na agenda

Login → Perfil → Editar dados / Alterar senha → token_version++ → sessão antiga inválida → novo login

Esqueci senha → resposta neutra → token seguro → SHA-256 no banco → Mailpit/SMTP
→ redefinição → BCrypt 12 → token usado → token_version++ → login

Alterar papel/desativar/reativar → proteger último ADMIN → token_version++ → JWT anterior inválido

Novo agendamento → funcionário ativo opcional → validar disponibilidade/conflito → salvar funcionario_id
```

Somente considerar concluído com autorização backend, auditoria sem segredos, testes e builds verdes.
