# 02 — Agendamentos: relatório de execução

Especificação: [02-agendamentos.md](02-agendamentos.md). Branch `feat/agendamentos`, **sem commit** (seção 40). A branch foi criada a partir de `feat/pets`, então a etapa 01, também sem commit, está junto na mesma cópia de trabalho.

## Implementação concluída

### Banco
- `V4__indices_agendamentos.sql` com 5 índices: `idx_agendamento_pet_data_status`, `idx_agendamento_funcionario_data_status`, `idx_agendamento_status_data`, `idx_agendamento_servico_data` e `idx_servico_realizado_pet_data`.
- `idx_servico_ativo_nome` **não** foi criado: o catálogo tem poucas linhas e já tem `UNIQUE (nome)`.
- Nenhuma tabela de itens foi criada. V1–V3 e `legacy/` estão intactos.
- O Flyway foi validado em banco limpo (Testcontainers) e no upgrade V3 → V4 (banco do compose).

### Arquitetura MVC
- **Model (domínio):** `Servico`, `PrecoServicoPorte`, `OrigemPreco`/`PrecoEstimado`, `Agendamento`, `StatusAgendamento` (máquina de estados), `ServicoRealizado` e `Funcionario` (somente leitura).
- **Controllers REST:** `ServicoController` e `AgendamentoController`. O Spring não renderiza HTML.
- **Serviços de aplicação:** `ServicoService` e `AgendamentoService`, com as regras e a orquestração transacional.
- **Views (Next.js):**
  - `/agendamentos`: dia/semana, navegação, filtros e "Gerenciar serviços" para ADMIN;
  - `/agendamentos/novo` (`?petId=`);
  - `/agendamentos/[id]`: ações de status e edição;
  - `/servicos`, `/servicos/novo` e `/servicos/[id]`.

### Backend
- Endpoints:
  - `GET/POST/PUT/DELETE /api/v1/servicos`;
  - `GET /api/v1/agendamentos?inicio&fim&status&petId&clienteId&servicoId&q&page&size`;
  - `GET /api/v1/agendamentos/preco`;
  - `GET/POST/PUT /api/v1/agendamentos[/{id}]`;
  - `POST /api/v1/agendamentos/{id}/status`;
  - `GET /api/v1/pets/{petId}/agendamentos/proximos?limit`.
- **Máquina de estados** em `StatusAgendamento`. Transição inválida → 409 com a mensagem da spec. Estados terminais não reabrem.
- **Conflito:** intervalo `[início, fim)`, por pet sempre e por funcionário quando houver. Janela de busca de até 1440 min, com a sobreposição conferida em Java. A linha do pet fica travada (`PESSIMISTIC_WRITE`) durante a criação ou edição (mitigação do R03).
- **Preço:** porte, senão padrão (pet sem porte nunca vira Pequeno). O valor fica congelado em `total_estimado`, e mudanças no catálogo não o alteram.

### Integrações
- **servico_realizado:** exatamente um por conclusão. Uma segunda conclusão dá 409. O `valorFinal` omitido usa o estimado; informado, não altera o estimado.
- **Carteira de vacinação:** a conclusão do serviço "Vacinação" chama `CarteiraVacinaService.registrar` (a regra e a auditoria do módulo de pets, sem duplicar nada), na mesma transação. Se a dose falhar, o agendamento continua EM_ANDAMENTO e nada é gravado.
- **Ficha do pet:** seção "Próximos agendamentos" (até 5) com o botão "Agendar serviço".

### Frontend
- Componentes novos: `AgendaLista`, `AgendamentoForm`, `AcoesStatus`, `StatusAgendamentoBadge`, `ProximosAgendamentos` e `ServicoForm` (com validação no navegador antes de enviar).
- `TutorPicker` ganhou o callback `onSelecionar`.
- Route handlers: `/api/pets` (pets do tutor) e `/api/agendamentos/preco`.
- Ações de status aparecem conforme o estado e o papel. Cancelar, não compareceu e concluir pedem confirmação, e o cancelamento exige motivo.
- O menu "Agendamentos" foi liberado.

### Segurança
- `@PreAuthorize` em todas as escritas: catálogo só para ADMIN; criar e editar agendamento para ADMIN e VENDAS.
- A transição de status tem papel conferido por destino: confirmar e cancelar para ADMIN e VENDAS; iniciar, concluir e não comparecimento também para SERVICO. Papel sem permissão → 403.

### Auditoria
- `SERVICO_CRIADO`, `SERVICO_ATUALIZADO`, `SERVICO_DESATIVADO`.
- `AGENDAMENTO_CRIADO`, `AGENDAMENTO_ATUALIZADO`, `AGENDAMENTO_STATUS_ALTERADO`, `AGENDAMENTO_CANCELADO`, `AGENDAMENTO_NAO_COMPARECEU`, `AGENDAMENTO_CONCLUIDO`, cada transição registrada uma vez, com `statusAnterior` e `statusNovo` (e motivo ou `valorFinal` quando se aplica).
- `VACINA_PET_REGISTRADA` continua vindo do módulo de pets.

### Testes
- Unitários: **115 aprovados / 0 falharam**. Antes eram 55; entraram 60, com todos os casos nominais da seção 33 e alguns extras.
- Integração: **71 aprovados / 0 falharam**. Antes eram 34; entraram 37 em 5 ITs, com todos os casos da seção 34.
- Frontend: **65 aprovados / 0 falharam**. Antes eram 36; entraram 29 em 8 arquivos, com todos os casos da seção 35.

### Validações finais
- Backend build (`./mvnw verify`): PASS
- Frontend build: PASS
- Lint: PASS
- Typecheck: PASS
- Ponta a ponta no `docker compose`, no navegador:
  - pet → "Agendar serviço" → Banho com prévia de R$ 60,00 e 45 min → criado;
  - segundo horário sobreposto → aviso de conflito;
  - Confirmar → Iniciar → Concluir com valor de R$ 55,00 (estimado de R$ 60,00 mantido);
  - ADMIN cadastrou "Vacinação" pela tela → agendou → concluiu com a V8, e a dose apareceu na carteira como EM_DIA;
  - visão semanal agrupada por dia;
  - celular sem rolagem horizontal;
  - auditoria conferida no Mongo.

## Divergências em relação à spec (decisões tomadas)
1. **V4 com 5 índices, não 6.** Ver o item Banco acima.
2. **`servicoId` virou filtro da lista**, porque a seção 26 pede filtro por serviço na agenda.
3. **Respostas enriquecidas:**
   - `PrecoResponse` inclui `duracaoMinutos` (a tela mostra a duração junto com o preço);
   - `AgendamentoResponse` inclui `pet.porte`, `pet.especieId`, `servico.vacinacao` e `realizado` (este só no detalhe de um concluído).
4. **Campos de conclusão fora da conclusão → 400.** `valorFinal`, `observacoesConclusao` ou `vacina` enviados numa transição que não é conclusão são recusados com 400, e não ignorados.
5. **Serviço sem duração → 409.** A regra da seção 9.3 (serviço sem preço → 409) não tem como acontecer, porque `servico.preco` é `NOT NULL` na V1. Protegi o caso que pode acontecer: `duracao_minutos` é nulo na V1, e sem duração também dá 409 ("sem preço ou duração configurados").
6. **"Vacinação" é reconhecida por `trim` + comparação exata, sem diferenciar maiúsculas.** O acento é obrigatório e substring nunca vale.
7. **A visão semanal cobre a data escolhida e os 6 dias seguintes**, e não a semana de segunda a domingo.

## Pendências
- **Duas etapas sem commit na mesma branch:** 01 e 02 estão juntas em `feat/agendamentos`. Decida se prefere um commit por etapa.
- **R01, duração não congelada:** alterar a duração de um serviço muda o fim previsto e a checagem de conflito dos agendamentos ativos já existentes. Está documentado; um snapshot de duração fica para uma migration futura.
- **Auditoria da dose fora da transação:** o registro no Mongo é gravado na hora (Mongo não participa da transação do MySQL). Se a gravação do `servico_realizado` falhasse *depois* da dose, a auditoria mostraria uma dose que foi desfeita. A janela é pequena, mas existe.
- **Ainda não há usuários SERVICO nem funcionários para testar pela interface.** As permissões estão cobertas pelos ITs.
- **"Não compareceu" não foi exercitado no navegador,** porque exige um agendamento no passado. Está coberto por teste unitário e IT.

---

## Handoff para a etapa 03: prompt para o ChatGPT

Recomendo que a etapa 03 seja **Usuários, funcionários e redefinição de senha**, antes do PDV. Hoje ela destrava três coisas:
- o papel SERVICO na prática;
- o funcionário no agendamento (e o conflito por funcionário, que já está pronto);
- a migração do legado, cujas senhas SHA-256 sem sal precisam de redefinição.

Se preferir ir direto para PDV/estoque, peça outro prompt.

````text
Você é o arquiteto de software do projeto PetConnect Web. Gere UM arquivo Markdown chamado `03-usuarios-funcionarios.md`, que será executado por outro agente de IA (Claude Code) direto no repositório. Seja específico e verificável: nada de "a definir". Responda somente com o conteúdo do arquivo, dentro de um único bloco ```markdown. Mantenha o formato das etapas anteriores: objetivo, análise obrigatória, decisões arquiteturais justificadas, escopo, fora de escopo, banco, API endpoint a endpoint com JSON, regras numeradas, autorização em tabela, auditoria, frontend, testes com nomes de casos, critérios de aceite, riscos, ordem de implementação, restrições e relatório final.

## Contexto do projeto (estado atual)
- Monorepo: `backend/` (Spring Boot 4.1, Java 21, monólito modular `br.com.tecnodreamer.petconnect.<modulo>.{domain,application,infrastructure,web}`; MVC com Spring só expondo REST), `frontend/` (Next.js 16 App Router, Server Actions, Zod, Tailwind 4; padrão BFF com o JWT em cookie httpOnly `pc_session`), `legacy/php/` (só referência).
- MySQL 8.4 com Flyway (aplicadas V1–V4; nunca editar migration aplicada). MongoDB só para a auditoria (`AuditoriaService.registrar(acao, entidade, id, detalhes)`, sem senha/token/binário).
- Padrões: entidades sem setters públicos; exclusão lógica; `NotFoundException` → 404; `ConflictException` → 409; `CampoInvalidoException(campo, msg)` → 400 com `errors`; `PageResponse<T>`; `Clock` com fuso America/Sao_Paulo; `@PreAuthorize`; testes `*Test` (Mockito) e `*IT` (Testcontainers; bases `IntegrationTest` e `PetIntegrationTest` com `criarCliente`/`criarPet`/`comPapel`; o ADMIN inicial vem de `app.bootstrap-admin.*`).
- Segurança atual: `POST /api/v1/auth/login` (login por username ou e-mail, BCrypt custo 12, bloqueio de 5 falhas por 15 min em memória, resposta igual para usuário inexistente e senha errada) emite um JWT HS256 com claims `sub`, `uid`, `roles` (TTL 8 h, sem refresh token). `GET /api/v1/auth/me`. Papéis: ADMIN, VENDAS, SERVICO (tabela `papel`). O primeiro ADMIN é criado por variável de ambiente (`AdminBootstrap`). Não existe CRUD de usuários, troca de senha nem "esqueci a senha".
- Frontend: `AppShell` (menu), `Campo`, `Aviso`, `BotaoConfirmar`, `TutorPicker`; `lib/permissoes.ts` espelha os papéis; `proxy.ts` redireciona sem sessão.
- Módulos prontos: clientes + endereço, pets (+ carteira, consultas, foto), serviços (catálogo + preço por porte) e agendamentos (máquina de estados, conflito por pet e por funcionário, conclusão + vacinação). `agendamento.funcionario_id` e `servico_realizado.funcionario_id` hoje são sempre nulos, e existe uma entidade `Funcionario` só leitura (id, nome), sem CRUD.

## Tabelas já existentes (V1)
- `usuario(id, username UNIQUE, email UNIQUE, senha_hash, papel_id FK, ativo, created_at, updated_at)`.
- `papel(id, nome UNIQUE, descricao)`.
- `token_redefinicao_senha(id, usuario_id FK CASCADE, token_hash CHAR(64) UNIQUE, expira_em, usado_em, created_at)` — pensada para guardar só o SHA-256 do token.
- `funcionario(id, usuario_id FK UNIQUE NOT NULL, nome, cpf CHAR(11) UNIQUE NOT NULL, data_nascimento, sexo CHECK MASCULINO|FEMININO|OUTRO, telefone UNIQUE, endereco_id FK UNIQUE NULL → endereco, observacoes, created_at, updated_at)`.
- `endereco` (compartilhada; o módulo de clientes já tem `EnderecoRequest`/`EnderecoResponse` e as validações de CEP/UF).

## Legado (PHP) relevante
- `registrar.php`, `registrar_autenticar_adm.php`, `registrar_processar_login_adm.php`: o cadastro de usuário exigia autenticar um admin.
- `esqueci_senha.php`, `redefinir_senha.php`: token de redefinição em colunas soltas do usuário.
- `perfil_form_dados.php`, `perfil_processar_dados.php`, `editar_perfil_processar.php`, `configuracoes.php`: perfil do próprio usuário.
- As senhas legadas são SHA-256 sem sal e NÃO podem ser migradas: usuários migrados precisarão redefinir a senha.

## O que a especificação `03-usuarios-funcionarios.md` deve decidir e cobrir
1. Escopo: CRUD de usuários (só ADMIN: criar, listar/buscar, editar papel, ativar/desativar; nunca excluir fisicamente); cadastro de funcionário vinculado a usuário (1:1), com dados pessoais e endereço; perfil do próprio usuário (ver/editar dados permitidos e trocar a senha informando a atual); "esqueci a senha" com token de uso único e expiração.
2. Decisões justificadas: se todo usuário é funcionário ou se ADMIN pode existir sem funcionário; o que acontece com os agendamentos futuros de um funcionário desativado; como impedir que o último ADMIN ativo seja rebaixado ou desativado; se a troca de papel/desativação invalida sessões já emitidas (o JWT é stateless com TTL de 8 h: proponha algo concreto, por exemplo versão de credencial/`token_version` numa coluna nova da V5 checada no filtro, ou justificar aceitar a janela); política de senha (tamanho mínimo, bloqueio de senhas iguais ao username/e-mail).
3. E-mail do "esqueci a senha": não há provedor configurado. Defina uma interface de envio com implementação de desenvolvimento (log/arquivo, ou MailHog/Mailpit no docker compose) e como configurar SMTP real depois, sem vazar se o e-mail existe (resposta sempre igual) e com rate limit.
4. Banco: V5 só com o necessário (ex.: coluna de versão de credencial, índices), sem editar V1–V4.
5. API endpoint a endpoint (usuários, funcionários, perfil `/api/v1/me`, troca de senha, `POST /auth/esqueci-senha`, `POST /auth/redefinir-senha`), com JSON, validações, papéis e códigos de erro. Nunca devolver `senha_hash`.
6. Integração com agendamentos: seleção de funcionário no agendamento passa a aparecer quando houver funcionários ativos (a regra de conflito por funcionário já existe); o frontend hoje sempre envia `funcionarioId: null`.
7. Frontend: `/usuarios` (lista/busca, só ADMIN, item no menu só para ADMIN), `/usuarios/novo`, `/usuarios/[id]`, `/perfil` (qualquer autenticado), `/esqueci-senha` e `/redefinir-senha?token=` (públicas, liberadas no `proxy.ts`), mobile-first e acessível.
8. Auditoria: eventos por ação (criação, papel alterado com anterior/novo, ativação/desativação, troca e redefinição de senha, pedido de redefinição), sem dados sensíveis.
9. Testes nominais: unitários (política de senha, último ADMIN, token expirado/usado, invalidação de sessão), IT (permissões, fluxo completo de redefinição, login após troca de senha, usuário desativado não loga) e frontend.
10. Critérios de aceite, riscos e o que fica para depois: PDV/vendas, estoque/compras, financeiro, migração de dados do legado (que dependerá do fluxo de redefinição criado aqui).
````
