# ETAPA 12 — Fechar incidente de exposição e proteger a `main` do Petable

**Data:** 20/10/2026 · **Tipo:** Especificação operacional para Claude Code, NÃO EXECUTADA · **Repositório:** `AleksGustavo/Petable` (antes `PetConnect-Web`) · **Base:** `main`, SHA relatado `7887debf83` (**revalidar**) · **Issues:** #2 e #3 · **PRs:** #48 (relatada mesclada) e #15–#27 (Dependabot; revalidar).

**Premissas (não confirmar por inferência):** 1. Estado informado como verificado em 19/10/2026, não revalidado aqui. 2. Dono `AleksGustavo` seria único administrador. 3. Haveria 41 refs residuais e 31 refs reescritas. 4. Backup original cifrado teria senha guardada em texto plano. 5. Colaboradores informados incluem `marcos22-s`, `Wanderson` e possível Everton — identidade real e acesso exigem confirmação. 6. Nenhum segredo de produção foi confirmado, mas credenciais do legado precisam ser investigadas. 7. Verificações legais cabem exclusivamente ao jurídico/DPO. 8. Funcionalidades do GitHub dependem do plano e de confirmação atual.

## 1. Missão e resultado esperado

Concluir de forma verificável a contenção de exposição de dados pessoais que estavam em scripts SQL do legado e impedir recorrência. As issues só podem ser encerradas com evidências objetivas ou com decisões explícitas e documentadas sobre risco residual. **Não confundir “arquivo removido”, “repositório privado” ou “ticket aberto” com eliminação integral de dados**. Não prometer apagar clones externos, caches desconhecidos ou réplicas fora do controle do projeto.

## 2. Quem faz o quê

| Tarefa | Claude Code | Dono | Terceiro / jurídico |
|---|---|---|---|
| T1 | Auditar estado por Git/gh; evidências sanitizadas | Desbloquear acesso se necessário | — |
| T2 | Identificar SHAs/refs e preparar ticket | Abrir e acompanhar ticket | GitHub Support avalia remoção |
| T3 | Listar contas, preparar aviso/checklist | Avisar e cobrar confirmações | Colaboradores fazem clone novo |
| T4 | Preparar guia e verificar metadados | Guardar/testar senha e definir retenção | Jurídico apoia retenção |
| T5 | Inventariar somente clones autorizados | Autorizar cada limpeza | Colaboradores cuidam de seus clones |
| T6 | Configurar/testar proteção após decisão | Decidir plano, visibilidade e exceções | GitHub aplica recursos disponíveis |
| T7 | Auditar alertas, acessos, security settings | Verificar/ativar no painel se preciso | GitHub fornece serviços |
| T8 | Inventariar consumidores/roteiro de rotação | Confirmar uso e autorizar rotações | Provedores confirmam revogação |
| T9 | Preparar dossiê sanitizado | Enviar por canal restrito | Jurídico/DPO decide efeitos da LGPD |
| T10 | Auditar imagens GHCR e Dependabot | Autorizar mudanças específicas | Dependabot |
| T11 | Atualizar docs e abrir PR; preparar comentários | Aprovar merge/fechamento | — |

## 3. Autoridade e limites

**Pode sem perguntar novamente:** leitura de repositório/`gh`, inspeção de estado Git, scanners com resultados redigidos, verificação em clone novo, relatórios sanitizados, criação de branches e PRs de documentação/CI, edição dos documentos existentes e elaboração de rascunhos. **Após decisão formal registrada** pode configurar controles da `main`, ambientes e permissões exatamente no escopo autorizado.

**Exigem autorização específica com alvo, efeito, risco e reversão:** reescrever histórico novamente; apagar branches/tags; mudar visibilidade; contratar plano; revogar ou rotacionar credenciais; comunicar Support/colaboradores/jurídico/titulares; mesclar PR fora das etapas 01–09 (inclusive Etapa 12 e Dependabot); apagar clones/worktrees/backup; rodar `git reflog expire` ou `git gc --prune` sobre histórico antigo; mudanças de permissões não aprovadas; testes destrutivos. O dono abre ticket, envia mensagens, opera gerenciador de senhas e consulta jurídico.

**Invariantes:** jamais inserir em conversa, PR, issue, log, artifact ou Git conteúdo de dados pessoais, dumps, hashes de senha, senhas, tokens, chaves ou credenciais (nem mesmo “para provar”); citar no máximo caminhos, contagens e SHAs quando necessários. Não desativar checks para passar. Não alegar limpeza completa sem prova. Classificar **PASS / FAIL / NÃO EXECUTADO / BLOQUEADO / NÃO VERIFICADO**. Não interpretar `null`, `403` ou `404` isolados como evidência. Se houver reintrodução do histórico antigo, **parar e conter**.

## 4. Estado conhecido em 19/10/2026 — obrigatoriamente REVALIDAR

| ID | Relato histórico recebido | O que confirmar |
|---|---|---|
| E1 | SQL legado com dados pessoais declarados reais: ~80 padrões de CPF, ~45 e-mails, ~89 telefones e hashes por arquivo | Arquivos, categorias, contagens, SHAs; nunca valores |
| E2 | Repositório público tornado privado em 19/10 | Visibilidade atual e histórico de mudanças disponível |
| E3 | PR #48 mesclada, commit `7887debf83`; remoção na árvore e prevenção via gitleaks v8.30.1, `.gitleaks.toml`, `.gitignore`, barreira CPF | PR, commit, checks, regras e hashes de executáveis |
| E4 | Cinco caminhos removidos por `git filter-repo`; 31 refs reescritas; main antiga `12bbf0d26e`, depois `b1641ff0c8` | Refs atuais, clone fresco, caminhos eliminados |
| E5 | Clone novo com `git fsck` OK; gitleaks 3 falsos positivos sem segredos reais confirmados | Reexecutar com `--redact`, registrar somente resumo |
| E6 | 41 `refs/pull/*` e `refs/github-services/*` ainda alcançavam conteúdo antigo | Recontar e consultar Support |
| E7 | Espelho original cifrado com senha ainda em texto plano no perfil do dono | Teste de recuperação e remoção segura do texto plano |
| E8 | Clone principal `feat/producao-operacao` com alterações não commitadas; worktrees `C:\wt-lotes`, `C:\wt-petable`, `C:\wt-seg` | Metadados e plano de preservação, sem tocar em árvore suja |
| E9 | API de branch protection deu `403` em repo privado no plano anterior | Plano, permissão, documentação atual e status real |
| E10 | `security_and_analysis: null` após privatização | NÃO VERIFICADO até painel/APIs específicas |
| E11 | Push de `v0.1.0` republicou GHCR `petconnect-web-api`/`petconnect-web-web` tags `main`, `0.1.0`, `sha-*`, sem deploy relatado | Digests, imagens, consumidores, data e SBOM |
| E12 | 13 PRs Dependabot #15–#27 podem ter branches reescritas | Estado atual, checks e conflitos |
| E13 | “Everton” só em branch local | Confirmar identidade e worktree |
| E14 | Rotações não executadas; uso de senhas legadas desconhecido | Decisão por serviço |
| E15 | Avaliação LGPD pendente | Dossiê e recebimento pelo jurídico/DPO |
| E16 | Todos os 13 checks verdes em main/PR #48 | Validar quais ainda são emitidos em novos PRs |

**Nomes históricos dos 13 checks — não configurar sem revalidar nome/contexto exato:**

1. `Build + testes (unit + integração)`
2. `Lint + tipos + testes + build`
3. `Playwright (Chromium)`
4. `Playwright por pacote (pdv)`
5. `Playwright por pacote (petshop)`
6. `Playwright por pacote (completo)`
7. `Análise (java-kotlin)`
8. `Análise (javascript-typescript)`
9. `Compose + scripts de operação`
10. `Dependências (Trivy fs)`
11. `Imagem backend (Trivy + SBOM)`
12. `Imagem frontend (Trivy + SBOM)`
13. `Segredos e dados pessoais`

**Arquivos existentes que devem ser lidos e atualizados, sem criar versões conflitantes:** `docs/seguranca/PLANO_EXECUCAO_SEGURANCA_CI_HOMOLOGACAO.md`, `docs/seguranca/RELATORIO_HISTORICO_DADOS_PESSOAIS.md` (seções 6, 7, 9), `docs/runbooks/lgpd.md`, `docs/github.md`, `docs/runbooks/rotacao-segredos.md`, `docs/runbooks/go-live.md`, `.github/CODEOWNERS`. Conferir se já existe runbook de incidentes.

## 5. Tarefas T1–T11

### T1 — Revalidar estado (PRIORIDADE 1, gate obrigatório)

**Objetivo:** detectar divergências e novo vazamento antes de modificar qualquer coisa. **Responsável:** IA. **Dependências:** nenhuma. **Pré-condições:** `gh` autenticado, leitura no repo; ambiente seguro para evidências.

**Passos numerados:**
1. Ler `gh repo view AleksGustavo/Petable --json nameWithOwner,isPrivate,defaultBranchRef,url` e `gh auth status` (não imprimir token), `git remote -v`, `git status --porcelain`; não mudar clone sujo.
2. Conferir `git ls-remote --heads --tags origin`, issues #2/#3 e PR #48 via `gh issue view` / `gh pr view` e PRs atuais.
3. Inventariar somente SHAs de `git ls-remote origin 'refs/pull/*' 'refs/github-services/*'`. Ausência na listagem **não prova** remoção de cache no GitHub.
4. Conferir visibilidade, plano/permissões e endpoints específicos da proteção/segurança via `gh api` com saída filtrada; não despejar JSON de credenciais.
5. Em clone fresco isolado, `git fsck --full`, verificar ausência dos cinco caminhos a partir de lista sanitizada existente e executar versão fixada do scanner gitleaks **com `--redact`**, gerando apenas contagem/classificação, não trechos. Não usar `git show` nos commits sensíveis.
6. Inspecionar workflows e PR atual para conferir os nomes e disponibilidade dos checks, incluindo PR somente de documentação.
7. **Se encontrar reintrodução de blobs do legado em refs públicas/graváveis: interromper trabalho, manter privacidade, classificar incidente ativo e solicitar decisão de contenção**.

**Aceite:** tabela de estado atual com data, SHA real, visibilidade, contagem de refs e resultados de clone/scanner; divergências explicitadas. **Evidência:** inventário sanitizado privado com IDs/SHAs e resumo versionável sem dados pessoais. **Risco:** comandos verbosos podem expor dumps ou alterar checkout sujo. **Reversão:** somente leitura; descartar cópias temporárias sob política de retenção. **Risco de vazamento:** logs de scanner, `git log -p`, `git show`, artifacts, telas.

### T2 — Solicitar retirada das refs/caches ao GitHub Support (URGENTE)

**Objetivo:** tratar referências hospedadas pelo GitHub que não podem ser reescritas via push. **Responsável:** IA inventaria/redige; **dono envia**; Support processa. **Dependências:** T1. **Pré-condições:** inventário de SHAs/refs sem payload sensível.

**Passos:**
1. Consultar páginas oficiais atuais **“Removing sensitive data from a repository”** e **“Contacting GitHub Support”**; confirmar formulário e instruções, sem inventar menus ou prazos.
2. Usando o espelho original somente se autorizado, gerar lista **exclusivamente de SHAs, caminhos e refs afetados**, incluindo eventuais forks/PR IDs. Nunca anexar dump nem valores.
3. Usar a minuta em inglês da seção 8, revisar fatos e encaminhar pelo dono, no canal oficial.
4. Registrar protocolo, data, prazo informado **se houver**, responsável e data de acompanhamento em local privado.
5. Após resposta, repetir contagem de refs/URLs com acesso apropriado; solicitar confirmação explícita do escopo de caches/refs removidos; `404` isolado não demonstra remoção.
6. Se houver demora/recusa, manter repo privado e propor **novo repositório limpo** apenas como alternativa sujeita a autorização; analisar perdas de issues/PRs, links, stars, releases, branches, packages e integrações.

**Aceite:** ticket protocolado (encaminhado), depois confirmação de remoção e verificação pós-ação (resolvido); não confundir estados. **Evidência:** protocolo, SHAs sanitizados, resposta e verificação. **Risco:** perda de referências históricas, exposição continuada. **Reversão:** Support deve orientar eventuais impactos; não há reversão segura universal. **Vazamento:** ticket/anexos/capturas com dados reais.

### T3 — Avisar colaboradores e reconstruir clones

**Objetivo:** evitar que branches locais antigas reintroduzam os blobs. **Responsáveis:** IA prepara, dono comunica, colaboradores executam. **Dependências:** T1; paralelo a T2. **Pré-condições:** contas e pessoas confirmadas, não presumir que apelidos são identidade real.

**Passos:**
1. Revisar `gh api repos/AleksGustavo/Petable/collaborators --paginate`, convites e branches, somente metadados; confirmar `marcos22-s`, `Wanderson`, Everton e outros.
2. Dono envia texto da seção 8 e acompanha cada resposta.
3. Cada colaborador **não deve fazer merge/pull/cherry-pick cego** de refs antigas; preservar trabalhos não enviados como arquivos de código revisados/patch limpo (sem `.git`, dumps, histórico sensível).
4. Clonar do remoto limpo, abrir branch nova a partir da `main`, reaplicar mudanças selecionadas e rodar scanner/CI antes do push.
5. Registrar confirmação de recebimento, recuperação, clone novo e compromisso de não reenviar commits históricos. Se não houver resposta, dono decide restrição temporária de escrita **se disponível**, com reversão documentada.
6. Verificar refs do remoto após novos pushes.

**Aceite:** 100% dos colaboradores conhecidos confirmados ou pendências explícitas com mitigação; branches novas limpas. **Evidência:** checklist individual sanitizado. **Risco:** perda de trabalho não enviado, push de histórico antigo. **Reversão:** preservar código revisado até validação; restaurar permissão somente após confirmação. **Vazamento:** patches/copias com dumps.

### T4 — Senha do backup cifrado e retenção

**Objetivo:** guardar senha em gerenciador e retirar arquivo temporário sem perder recuperabilidade. **Responsável:** dono; IA fornece checklist. **Dependências:** T1; antecede qualquer limpeza T5. **Pré-condições:** acesso ao backup cifrado e gerenciador seguro.

**Passos:**
1. Conferir existência/metadados do backup e arquivo de senha sem imprimir conteúdo.
2. Dono cria entrada no gerenciador de senhas com MFA, custodiante e procedimento de recuperação; transfere o valor diretamente na interface, sem chat/log.
3. Testar a decifragem de cópia controlada com o valor **obtido do gerenciador**, sem senha em linha de comando ou shell history, com saída temporária protegida; confirmar integridade e limpeza posterior.
4. Só após teste, apagar o arquivo de texto e esvaziar lixeira; confirmar ausência no caminho original e analisar se houve sincronização/backups. Em SSD, exclusão/overwrite **não garante** apagamento físico; considerar criptografia de disco e descarte criptográfico conforme risco.
5. Definir responsável, armazenamento restrito, cópias autorizadas, data de revisão, prazo **mínimo justificável** de retenção e data de destruição, considerando Support/jurídico.
6. Destruição do espelho cifrado **apenas com autorização específica** e após avaliação de preservação de evidência.

**Aceite:** dono declara teste de recuperação bem-sucedido com gerenciador, arquivo em texto ausente, retenção e proprietário documentados. **Evidência:** datas e status, nunca senha. **Risco:** perder único backup ou mantê-lo demais. **Reversão:** se teste falha, não excluir arquivo; corrigir antes. **Vazamento:** terminal com echo/debug, lixeira, clipboard, cloud sync.

### T5 — Clones/worktrees: preservar primeiro, limpar depois

**Objetivo:** controlar cópias locais antigas sem destruir trabalho. **Responsáveis:** IA inventaria, dono autoriza limpeza por destino. **Dependências:** T1 e T4 completos. **Pré-condições:** backup testado e diretórios autorizados.

**Passos:**
1. Conferir clone principal com branch `feat/producao-operacao` e alterações não commitadas; worktrees informadas `C:\wt-lotes`, `C:\wt-petable`, `C:\wt-seg`; identificar eventuais clones adicionais **somente em diretórios autorizados**.
2. Por clone, coletar apenas metadados com `git status --porcelain`, `git worktree list --porcelain`, `git branch --no-merged`, `git stash list` e refs. Não imprimir diffs/patches sensíveis.
3. Gerar matriz: clone, branch, status sujo/limpo, trabalho a preservar, risco, plano de substituição, autorização.
4. **Não tocar no checkout principal sujo:** explicar alternativas de commit/cópia de fonte ou patch revisado; aguardar instrução específica.
5. Para clone expressamente autorizado e depois de cópia nova validada, realizar substituição e, se aprovado, `git reflog expire --expire=now --all` + `git gc --prune=now` apenas naquele clone. Não executar sobre único backup ou trabalho pendente.
6. Entregar checklist humano de lixeira, downloads, dumps, anexos de chat, sincronizações e caches; não vasculhar arquivos pessoais.

**Aceite:** inventário e plano por clone; nenhuma perda de trabalho; limpeza só com aprovações por alvo. **Evidência:** metadados e aceite. **Risco:** remoção irreversível de objetos necessários. **Reversão:** eventual restauração a partir de backup somente se autorizado. **Vazamento:** zip de `.git`, patch contendo dados.

### T6 — Decidir plano e proteger `main` (PRIORIDADE ALTA)

**Objetivo:** implantar proteção efetiva, verificável, sem tornar o código comercial público por acidente. **Responsáveis:** dono decide; IA configura após decisão registrada. **Dependências:** T1; para tornar público, T2 resolvido ou risco residual excepcional explicitamente aceito pelo dono; autorização para configurações. **Pré-condições:** verificar plano, propriedade, escopo da autorização, contexts dos checks.

**Matriz de decisão — cada elegibilidade/custo deve ser conferida na documentação oficial atual:**

| Aspecto | A — manter privado + plano elegível (Pro/Team) | B — voltar a público |
|---|---|---|
| Código comercial | Acesso controlado | Código visível ao público |
| Cópias residuais | Privacidade reduz exposição casual, mas não apaga refs | Pode expor refs antigas enquanto Support não tratar |
| Proteção de branches/rulesets | Documentação oficial indica disponibilidade em privados com Pro/Team, confirmar titularidade/plano | Documentação indica disponibilidade em públicos Free; confirmar |
| Preço e quotas Actions/storage | **Conferir preços/limites atuais na documentação oficial** | **Conferir preços/limites atuais** |
| Secret scanning, push protection, CodeQL e segurança avançada | Cada recurso tem regras próprias e possíveis licenças; **conferir atual** | Conferir disponibilidade específica atual |
| Environments/revisores | Atenção: acesso a environments e **required reviewers** são recursos distintos; em privados alguns planos não dão revisores obrigatórios | Conferir documentação atual |
| Recomendação | **A**, para ERP comercial | Só após tratamento de refs e decisão consciente |

**Passos:**
1. Consultar GitHub Docs atuais: **“GitHub's plans”**, **“Available rules for rulesets”**, **“Managing a branch protection rule”**, **“Reviewing deployments”**, **“Billing and licensing for GitHub Actions”**; confirmar preços, features e limites vigentes, sem inventar.
2. Dono registra a decisão A/B e, se A, trata contratação por conta própria. **Não executar mudança de visibilidade sem autorização separada**.
3. Preferir **ruleset** direcionado exatamente a `refs/heads/main`, se disponível, por clareza/auditoria; branch protection equivalente é alternativa se necessário. Documentar escolha.
4. Configurar **PR obrigatório**, **0 aprovações exigidas enquanto houver único mantenedor** se permitido, migrando para ≥1 aprovação/Code Owners quando houver revisor distinto. `CODEOWNERS * @AleksGustavo` não serve como aprovação do próprio autor; evitar bloqueio.
5. Requerer os 13 checks efetivamente reportados, associados a **GitHub Actions** como origem apropriada; validar contextos exatos, jobs matrizados, PRs só docs e Dependabot. **Nomes duplicados** podem tornar status ambíguos. Jobs agregadores que usam `always()` devem falhar quando dependências obrigatórias falharem/cancelarem/estiverem ausentes — não declarar sucesso incondicional.
6. Exigir atualização da branch antes do merge **ou** merge queue, conforme suporte real e compatibilidade; resolução de conversas; proibir força/exclusão de `main`; histórico linear opcional conforme método de merge.
7. Preferir **sem bypass permanente**. Caso indispensável, lista mínima para mantenedor único com auditoria e uso limitado; não dar bypass generalizado que torne testes falsos.
8. Criar regra separada para `refs/tags/v*` proibindo exclusão/reescrita não autorizada. Não publicar tag para testar.
9. Auditar Actions: `GITHUB_TOKEN` leitura por padrão, `write` por job quando indispensável, SHA pinning, controles de PRs de forks e **não executar código do PR em `pull_request_target` privilegiado**.
10. Inspecionar `deploy.yml`: environment já referido **`producao`**, sem criar `production` duplicado; apenas conferir **nomes** `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`; manter `StrictHostKeyChecking=yes`. O dono gerencia valores. **Se reviewers obrigatórios não estiverem disponíveis em repo privado/plano atual, manter deploy automático indisponível**, registrar alternativa de aprovação humana externa; não anunciar proteção inexistente.
11. Abrir PR de teste **somente de docs**, verificar emissão dos checks e bloqueio até todos passarem.
12. Testar **push direto real** usando clone descartável e commit inofensivo de docs contra `main`; se a política falhar, interromper antes de alterar a branch, registrando como FAIL e restaurando estado apenas por PR autorizado. **Não usar `--dry-run` como única evidência**, pois não comprova rejeição do servidor.
13. Verificar force-push bloqueado por ruleset/API e teste negativo real **somente com procedimento que não substitua commits válidos caso a proteção falhe**. Se não for possível demonstrar uma tentativa real com segurança, registrar **BLOQUEADO**, pedir autorização para ensaio isolado planejado; nunca forçar histórico de produção como teste.
14. Remover recursos de teste somente se a remoção estiver autorizada e não destruir evidências.

**Aceite:** plano/visibilidade decididos; regra ativa; PR com checks reais; push direto efetivamente negado; force-push bloqueado por configuração e ensaio seguro ou teste pendente marcado explicitamente; exclusão vedada. **Evidência:** ID da regra, PR, checks, respostas sanitizadas, decisão do dono, teste negativo. **Risco:** lockout, custos, exposição em modo público, bypass amplo. **Reversão:** exportar regra prévia, rollback administrativo mínimo registrado; nunca desabilitar proteção para passar CI. **Vazamento:** payload de API de secrets, logs de Git, PR com código antigo.

### T7 — Confirmar proteções do GitHub após privatização

**Objetivo:** eliminar o estado “não verificado” após a resposta `security_and_analysis:null`. **Responsáveis:** IA coleta; dono confere interface quando API não responder. **Dependências:** T1, revisar novamente após T6. **Pré-condições:** acesso admin somente leitura.

**Passos:**
1. Para cada recurso, registrar PASS/FAIL/NÃO VERIFICADO, URL da documentação atual e evidência: **secret scanning**, **push protection**, **Dependabot alerts**, **security updates**, **code scanning/CodeQL**, **private vulnerability reporting**, arquivo **`SECURITY.md`**.
2. Conferir colaboradores e permissões; convites pendentes; deploy keys, GitHub Apps, webhooks, tokens pessoais conhecidos, variáveis e secrets de Actions/environments **somente seus nomes**; logs de auditoria, quando disponíveis.
3. Onde API retornar `null`, `403` ou omitir campos, conferir o painel com o dono; nunca traduzir “não visível” por “inativo”.
4. Identificar recursos não suportados pelo plano e propor mitigação de CI (gitleaks, Trivy, verificações de dependência), sem alegar equivalência com controles nativos.
5. Atualizar/criar `SECURITY.md` com canal privado de denúncia de vulnerabilidades; orientar a nunca abrir issues públicas com dados sensíveis.

**Aceite:** inventário completo de recursos/permissões com data e evidência; cada não verificado tem responsável/ação. **Evidência:** metadados sanitizados e capturas sem segredos. **Risco:** falsa sensação de segurança e privilégios excessivos. **Reversão:** restaurar configuração aprovada após autorização; não revogar chaves sem autorização. **Vazamento:** logs, nomes de variáveis sensíveis e payloads.

### T8 — Credenciais antigas: responder antes de rotacionar

**Objetivo:** resolver se credenciais de exemplo/legado foram usadas de verdade. **Responsáveis:** dono confirma e autoriza; IA elabora plano por serviço. **Dependências:** T1; pode operar em paralelo. **Pré-condições:** inventário de ambientes e serviços sem seus segredos.

**Perguntas ao dono:** PHP antigo operou em produção? Algum serviço, VPS, banco, e-mail, painel, deploy ou conta Petable utilizou senhas ou padrões legados? Houve conta administrativa padrão em ambiente real? Algum usuário reutilizou senha em sistemas externos? Hashes representavam usuários reais? Para cada serviço responder **SIM/NÃO/DESCONHECIDO** com data.

**Passos:**
1. Registrar respostas e nível de confiança por serviço, sem inserir valor/trecho de senha.
2. **SIM:** gerar credencial nova de privilégio mínimo → atualizar secret store e consumidores → testar funcionalidade → revogar antiga → confirmar revogação com provedor → checar sessões/tokens e logs de autenticação. Toda rotação/revogação requer autorização específica.
3. **NÃO:** registrar declaração do dono com data e base factual; scanner sem achados **não é prova suficiente** de não uso.
4. **DESCONHECIDO:** manter pendência e avaliar rotação preventiva, sem executar sem aprovação.
5. Se hashes forem de usuários reais, informar ao jurídico para análise de risco de reutilização; **não concluir obrigação legal de aviso**.

**Aceite:** decisão por serviço; credenciais usadas rotacionadas e revogadas com validação; casos não usados confirmados. **Evidência:** nome do serviço, horário, teste e resposta sanitizada, jamais segredo. **Risco:** queda de serviços, credencial exposta ainda válida. **Reversão:** procedimento de rollback com credencial **nova**, nunca reativar antiga exposta. **Vazamento:** shell history, tickets, PR, prints e secret logs.

### T9 — Dossiê LGPD para responsável jurídico/DPO

**Objetivo:** fornecer base factual para análise jurídica, sem produzir conclusão jurídica. **Responsáveis:** IA prepara; dono encaminha; jurídico/DPO decide. **Dependências:** T1, atualizações de T2 e T8; pode começar em paralelo. **Pré-condições:** linha do tempo e inventário sanitizado.

**Passos:**
1. Gerar dossiê **fora do Git**, acesso restrito, com categorias e contagens **sem valores**, origem legado PHP, classificação dos dados como reais declarada pelo dono e eventuais incertezas.
2. Levantar data dos commits, momento em que o repo ficou público e privado (confirmado privado em 19/10 no relato), período potencial de acesso, e distinguir **acesso possível** de **acesso comprovado**.
3. Reportar forks conhecidos (0 no relato, revalidar), desconhecimento de clones externos, refs e caches ainda pendentes, scanner, reescrita, PR #48 e ações de proteção.
4. Incluir efeitos e pendências das senhas do legado, cronologia de comunicações e plano de retenção.
5. Formular perguntas ao jurídico: dados pertencem a titulares reais? Há menores de idade ou dados sensíveis? Qual possibilidade de identificação, dano ou risco? Há obrigação de comunicação à ANPD/titulares/contratantes e em qual prazo? Há obrigação de preservação de evidência? Quem decidirá retenção e destruição?
6. Dono envia em canal seguro, registra recebimento, responsável e retorno sem publicar o parecer integral no repo.

**Aceite:** dossiê completo, sanitizado e entregue com recibo **ou** pendente explicitamente como BLOQUEADO. **Evidência:** data, versão do dossiê, responsável e confirmação. **Risco:** avaliação incorreta e vazamento do próprio relatório. **Reversão:** emitir adendo factual versionado em canal restrito. **Vazamento:** anexos do legado, envio para endereço incorreto.

### T10 — Efeito colateral GHCR e PRs Dependabot

**Objetivo:** conferir se imagens republicadas e PRs históricos mantêm riscos. **Responsáveis:** IA verifica; dono autoriza merges/comandos de alteração. **Dependências:** T1. **Pré-condições:** leitura em GitHub Packages, Actions e PRs.

**Passos:**
1. Levantar packages `petconnect-web-api` e `petconnect-web-web`, tags `main`, `0.1.0`, `sha-*`, timestamps, digests, proveniência e SBOM quando disponíveis; comparar com artefatos esperados.
2. Verificar se algum deploy/consumidor usa as tags; o relato é **nenhum**, mas revalidar. Recomendar consumo por digest imutável quando aplicável.
3. Revisar individualmente PRs #15–#27: se aberta, base, head, rebase depois da reescrita, conflitos, status checks, risco de reintroduzir commits e ação recomendada.
4. Escolher **por PR** entre manter/rebasear, pedir `@dependabot recreate`, fechar para recriação ou mesclar atualizações avaliadas. **Não comentar para recriar, fechar nem mesclar sem autorização**, já que essas ações não estão cobertas por autorização permanente.

**Aceite:** matriz por imagem/digest/consumidor e por PR/ação proposta, sem efeito colateral novo. **Evidência:** IDs de packages, metadados e checks redigidos. **Risco:** retag afetar consumidor oculto e branch de bot antiga trazer conteúdo eliminado. **Reversão:** usar digest anterior aprovado, se mudança futura permitida; nunca deletar package sem autorização. **Vazamento:** SBOM/logs internos.

### T11 — Consolidar documentos e encerrar apenas por evidência

**Objetivo:** consolidar aprendizado, runbooks e critérios de fechamento. **Responsáveis:** IA edita/abre PR; dono aprova merge/encerramento se aplicável. **Dependências:** T1–T10 conforme disponibilidade. **Pré-condições:** inventário de documentos existente e matriz de evidências.

**Passos:**
1. Atualizar **os documentos existentes**, evitando duplicatas: plano de segurança, relatório do histórico (seções 6, 7 e 9), `lgpd.md`, `github.md`, `go-live.md`, `rotacao-segredos.md`, `.github/CODEOWNERS`; criar runbook de incidentes **apenas se não existir**.
2. Atualizar estado de cada achado/ref/cópia residual, responsável, próxima ação e evidência. Nenhum dado sensível em Git.
3. Revisar `SECURITY.md`; propor conferência recorrente de visibilidade, alerta quando houver mudança (onde tecnicamente viável), varredura trimestral do histórico inteiro, revisão de colaboradores e scanners no CI.
4. Registrar causas raiz **com suporte factual**: dump legado com dados declarados reais no versionamento; visibilidade pública contrariando a decisão relatada; falta de proteção de `main`.
5. Abrir PR em branch nova, revisar diff e checks. **Não fazer merge automático de PR desta etapa.**
6. Redigir comentários sanitizados das issues #2/#3 e classificá-las **ENCERRÁVEL / PARCIAL / ABERTA ou BLOQUEADA**. Se faltar resposta do Support/jurídico, explicitar exatamente a pendência.
7. Preparar handoff conforme seção 12; se contexto acabar, gerar documento 13 de continuidade autossuficiente.

**Aceite:** documentação sem conflito, PR de CI validado, matriz de evidências atualizada, decisão verificável por issue. **Evidência:** PR/commits, status, comentários ou minuta. **Risco:** encerrar prematuramente, exposição por documento. **Reversão:** novo PR de correção, nunca force-push. **Vazamento:** SHAs/URLs de conteúdo ainda alcançável usados sem necessidade, dumps em PR.

## 6. Caminho crítico e paralelismo

```text
T1 Revalidar --------------------> se reintrodução: PARAR E CONTER
 |
 +--> T2 Support ---------------------------+--> Verificação de refs/caches
 +--> T3 Colaboradores ---------------------|
 +--> T4 Senha/backup --> T5 Clones --------|
 +--> T8 Credenciais ----> T9 Jurídico -----|
 +--> T7 Controles do GitHub ---------------|
 +--> T10 GHCR / Dependabot ----------------|
 +--> Decisão explícita do dono -> T6 Proteção da main
       (público só com T2 resolvida ou aceitação específica do risco)
                                            |
                        T11 Documentação e decisão de encerramento
```

**Prioridade:** T1 primeiro; T2 e T3 sem demora; T4 antes de T5; T6 após escolha de plano; T9 assim que houver fatos suficientes; T11 consolida progresso mesmo quando terceiros estiverem pendentes, **sem chamar pendência de conclusão**.

## 7. Decisões pendentes do dono

| Pergunta | Opções | Recomendação | Consequências |
|---|---|---|---|
| D1. Manter privado ou público? | Privado com plano elegível / Público | **Privado**, ERP comercial | Custo a confirmar vs exposição do código/resíduos |
| D2. Abrir Support agora? | Sim / Não | **Sim**, após T1 | Sem protocolo, refs internas permanecem sem ação coordenada |
| D3. Quem são os colaboradores? | Confirmar nomes/contas e acessos | Confirmar todos | Clones antigos podem reintroduzir histórico |
| D4. Onde guardar senha do backup? | Gerenciador com MFA | Gerenciador, testar antes de apagar | Evita arquivo em texto, reduz risco de perda |
| D5. Quando descartar espelho cifrado? | Prazo mínimo aprovado / retenção excepcional | Consultar jurídico e Support | Descarte precoce perde evidência; tardio prolonga risco |
| D6. Quais clones limpar? | Autorizar um a um | Depois de preservar trabalho | Limpeza destrutiva |
| D7. Senhas legadas foram usadas? | Sim / Não / Desconhecido por serviço | Declarar formalmente | Rotação e risco residual dependem disso |
| D8. Limitar acesso de não respondentes? | Sim/Não e escopo | Se necessário/possível | Pode bloquear trabalho legítimo |
| D9. Aprovar ruleset e testes? | Sim/Não, regras propostas | Sim após verificar plano | Pode travar único mantenedor |
| D10. Dependabot/GHCR? | Ações individuais aprovadas | Após T10 | Merges, recriações e tags têm impactos próprios |
| D11. Quem será jurídico/DPO? | Indicar canal e responsável | Definir antes de go-live | Análise regulatória fica sem responsável |
| D12. Autorizar merge da PR documental? | Sim/Não após revisão e CI | Revisar e aprovar se segura | Documento não integra main sem merge |

## 8. Textos prontos — enviar somente por quem tem autoridade

### (a) Pedido em inglês ao GitHub Support

**Subject: Request for removal of residual pull request references and cached objects after sensitive data history rewrite**

Hello GitHub Support,

I am the owner of `AleksGustavo/Petable`, formerly `PetConnect-Web`. We discovered legacy SQL files containing personal information that had been committed while this repository was publicly accessible. On October 19, 2026, we made the repository private. We removed the affected files from the current tree, rewrote writable branch/tag history to remove the identified file paths, and validated a fresh clone using integrity and redacted security checks.

However, our subsequent inspection indicated that GitHub-managed pull-request and service references might still point to previous commits. These references cannot be rewritten using ordinary Git pushes. We request your guidance and assistance in removing the affected GitHub-managed references and any cached views or hosted objects that remain accessible, according to your current sensitive-data removal process.

We can provide a sanitized inventory of the old commit SHAs, impacted refs and pull request identifiers, affected file paths, and the rewrite validation details **without disclosing any personal information, credential values, or file contents**. Please specify the identifiers you require in this private support channel, the scope of the remediation you can perform, and how you recommend we verify completion.

We understand that GitHub cannot guarantee removal of independently maintained third-party copies. Please also indicate any limitations or remaining actions for us.

Thank you.

**Checklist do dono antes de enviar:** revalidar estado/data via T1; completar inventário de SHAs e refs **somente identificadores**; consultar docs oficiais atuais; não anexar dumps, dados ou logs completos; registrar protocolo e acompanhamento em espaço restrito.

### (b) Aviso aos colaboradores (português)

**Assunto: Petable — atualização obrigatória dos clones Git após reescrita do histórico**

Olá! O histórico do repositório Petable foi reescrito após a identificação de arquivos legados que não deveriam constar do versionamento. **Não faça push, merge, pull automático ou cherry-pick direto do seu histórico antigo** para o repositório atualizado.

Caso você possua código ainda não enviado, preserve apenas as alterações legítimas de código, revisadas e sem dumps, arquivos sensíveis ou pasta `.git`. Faça então um **clone novo da `main` atual**, crie uma branch nova e reaplique essas alterações manualmente. Execute os testes e verificações de segurança antes de qualquer push.

Por favor, confirme que recebeu este aviso, se tem trabalho pendente, que realizou o clone novo e que não reenviará commits antigos. Se precisar de ajuda para recuperar mudanças, entre em contato antes de mexer no clone. Não envie arquivos sensíveis por mensagem.

### (c) Comentário proposto de encerramento/pendência — issue #2

**Atualização da resposta à exposição histórica — [DATA]**

- Repositório/visibilidade atual: [estado verificado e evidência].
- Arquivos removidos da árvore e refs regraváveis: [PASS/FAIL e PR/commit verificados].
- Refs/caches hospedadas pelo GitHub: [removidas e testadas / protocolo Support e pendência], evidência [referência sanitizada].
- Colaboradores: [quantidade identificada, avisada e confirmada]; pendências [quantidade].
- Clones locais e backup cifrado: [estado e controles sem caminhos de senha].
- Credenciais: [não utilizadas confirmadas / rotacionadas / avaliação pendente].
- Avaliação jurídica/DPO: [encaminhada e registrada / pendente] — sem conclusão legal nesta issue.
- Prevenção e CI: [controles validados].

**Decisão:** [ENCERRÁVEL / MANTER ABERTA]. **Motivo:** [critérios cumpridos ou pendência específica]. Não incluir dados pessoais, segredos nem links diretos para conteúdo sensível.

### (d) Comentário proposto — issue #3

**Proteção da `main` — [DATA]**

Decisão registrada: [plano e visibilidade]. Mecanismo aplicado: [ruleset/branch protection, ID]. Exigências efetivas: [PR obrigatório, checks, resolução de conversas, force-push/exclusão e bypass]. Testes: PR de documentação [PASS/FAIL], checks obrigatórios [PASS/FAIL], push direto rejeitado [PASS/FAIL], force-push impedido [PASS/FAIL/BLOQUEADO com justificativa] e análise de segurança de Actions [PASS/FAIL].

**Decisão:** [ENCERRÁVEL / MANTER ABERTA], pois [evidência ou impedimento exato].

### (e) Mensagem ao jurídico/DPO

**Assunto: Petable — análise restrita de possível exposição histórica de dados pessoais**

Encaminho para avaliação jurídica o dossiê técnico sanitizado referente a arquivos de legado anteriormente incluídos no histórico de um repositório de desenvolvimento. O documento descreve somente categorias/contagens aproximadas, cronologia da visibilidade, medidas de contenção e remoção, referências residuais em avaliação, situação de cópias e dúvidas sobre credenciais. **Não contém valores de dados pessoais ou segredos.**

Solicito avaliação sobre a natureza e identificabilidade dos dados e titulares, eventual presença de menores ou dados sensíveis, risco potencial, obrigações de comunicação à ANPD/titulares/contratantes e prazos, além da política apropriada de retenção/destruição do backup e preservação de evidências. A equipe técnica não concluiu pela existência ou ausência de obrigação legal. Favor indicar o responsável, o próximo encaminhamento e os registros necessários por canal restrito.

### (f) Checklist de confirmação individual de colaborador

```text
Conta GitHub confirmada: [IDENTIFICADOR]
Vínculo com pessoa confirmado pelo dono? [SIM/NÃO]
Aviso recebido em: [DATA]
Existia trabalho local não enviado? [SIM/NÃO]
Trabalho preservado sem .git/dump ou conteúdo sensível? [SIM/NÃO/NÃO SE APLICA]
Clone fresco da main criado em: [DATA]
Branch nova de recuperação: [NOME]
Scanner/CI executado sem reintrodução de histórico: [PASS/FAIL]
Compromisso de não reenviar refs/commits antigos: [CONFIRMADO/PENDENTE]
Tratamento do clone antigo: [CONCLUÍDO/PENDENTE]
Observação sem segredos: [TEXTO]
```

## 9. Matriz de evidências — registro obrigatório

> Preencher resultado **obtido** somente depois de verificar. Evidências contendo metadados sensíveis ficam fora do Git. Nunca publicar screenshot de dados pessoais. Data e responsáveis obrigatórios.

| Requisito | Como verificar | Comando/tela | Resultado esperado | Obtido | Data | Evidência sanitizada |
|---|---|---|---|---|---|---|
| EV01/T1 | Identidade, SHA, privacidade | `gh repo view`, `git ls-remote` | Estado atual descrito | NÃO EXECUTADO | — | — |
| EV02/T1 | Integridade clone novo | `git fsck`, gitleaks redigido | Sem material removido alcançável pelas refs examinadas | NÃO EXECUTADO | — | — |
| EV03/T2 | Ticket de Support | Canal privado | Protocolo registrado | NÃO EXECUTADO | — | — |
| EV04/T2 | Resíduo GitHub tratado | Recontagem, confirmação Support | Escopo confirmado e testes pós-ação | NÃO EXECUTADO | — | — |
| EV05/T3 | Colaboradores avisados | Confirmações individuais | Todos confirmados ou risco formal | NÃO EXECUTADO | — | — |
| EV06/T4 | Senha no gerenciador | Teste humano local | Recuperação OK sem imprimir segredo | NÃO EXECUTADO | — | — |
| EV07/T4 | Texto plano removido | Conferência de arquivo local | Arquivo temporário ausente | NÃO EXECUTADO | — | — |
| EV08/T5 | Clones inventariados | `git worktree list`, `git status` | Nenhum trabalho perdido e plano por clone | NÃO EXECUTADO | — | — |
| EV09/T6 | Decisão do dono | Registro restrito | Plano/visibilidade definidos | NÃO EXECUTADO | — | — |
| EV10/T6 | Regras da main | API/painel | PR/checks/força/exclusão configurados | NÃO EXECUTADO | — | — |
| EV11/T6 | CI da PR docs | PR de teste | Checks reais emitidos e passando | NÃO EXECUTADO | — | — |
| EV12/T6 | Push direto bloqueado | Teste real seguro | Rejeição do servidor | NÃO EXECUTADO | — | — |
| EV13/T6 | Force-push impedido | Regra + teste seguro | Rejeição comprovada | NÃO EXECUTADO | — | — |
| EV14/T7 | Alertas e scanning | Painel/APIs específicas | Matriz por recurso | NÃO EXECUTADO | — | — |
| EV15/T8 | Dúvida credenciais | Confirmação/rotação | Decisão por serviço | NÃO EXECUTADO | — | — |
| EV16/T9 | Jurídico recebeu dossiê | Canal restrito | Recebimento confirmado | NÃO EXECUTADO | — | — |
| EV17/T10 | GHCR e consumidores | Packages/digest/SBOM | Artefatos conhecidos | NÃO EXECUTADO | — | — |
| EV18/T10 | Dependabot | PRs #15–#27 | Ação proposta por PR | NÃO EXECUTADO | — | — |
| EV19/T11 | Documentação consistente | PR/CI e revisão | Todos os runbooks atualizados | NÃO EXECUTADO | — | — |
| EV20/T11 | Decisão issues | Comentários/estados | Fechar só quando comprovado | NÃO EXECUTADO | — | — |

## 10. Riscos e mitigação

| Risco | Mitigação | Reação se ocorrer |
|---|---|---|
| Support demora ou recusa remover refs | Ticket preciso, follow-up, revalidação | Manter privado; avaliar migração a repositório limpo com autorização |
| Colaborador reenviar histórico contaminado | Avisos, clone novo, CI e restrições mínimas | Parar operação, conter e planejar resposta autorizada |
| `main` protegida bloqueia único mantenedor | 0 aprovações enquanto único mantenedor se permitido; testar contexts | Procedimento administrativo com exceção mínima e auditada |
| Plano pago indisponível | Conferir planos e permissões reais | Documentar impossibilidade, manter privado, sem encerrar #3 |
| Voltar a público antes do Support | Proibir por padrão até remoção/risco aceito | Reverter somente sob autorização e investigar acesso |
| Actions privados sem minutos/armazenamento suficiente | Medir consumo e orçamento | Otimizar jobs sem eliminar controles obrigatórios |
| Senha perdida antes de testar gerenciador | Testar recuperação antes de apagar arquivo | Preservar original até validação |
| Backup retido sem prazo | Retenção mínima com responsável e revisão | Consultar jurídico e fazer destruição aprovada |
| Credencial legada ainda ativa | Roteiro de rotação por serviço e prova de revogação | Contenção por provedor com autorização |
| `security_and_analysis:null` interpretado incorretamente | Marcar NÃO VERIFICADO e inspecionar painel | Corrigir relatório e habilitar controle elegível |
| GHCR mutável afeta consumidor oculto | Mapear digests/consumidores | Fixar digest conhecido com autorização |
| Logs/relatórios reproduzem dados | Redação estrita e revisão de saída | Conter novo incidente e retirar material com autorização |

## 11. Critérios formais de encerramento

**Issue #2 encerrável:** saneamento das refs graváveis validado; pedido Support tem **resposta conclusiva e validação** das cópias sob GitHub **ou** exceção formal de risco residual aceita pelo dono após avaliação adequada; colaboradores confirmados ou pendências mitigadas formalmente; backup/senha controlados; clones locais tratados ou com plano aceito; credenciais investigadas e rotacionadas onde necessário; dossiê entregue ao jurídico/DPO e sua decisão ou pendência registrada; evidências sanitizadas.

**Issue #3 encerrável:** decisão de plano/visibilidade registrada; regra de proteção **ativa** sobre a `main`; PR obrigatório, checks existentes e requeridos, bloqueio comprovado de push direto e force-push com evidências seguras; exclusão protegida; bypass mínimo; settings de Actions e environments compatíveis com plano, testados; CI de PR docs validado. Se o teste real de force-push não puder ser feito de modo seguro, registrar bloqueio e critério pendente, não declarar plenamente testado.

**O que não basta:** apagar arquivos apenas do último commit; tornar repo privado; receber protocolo Support sem validação posterior; gitleaks sem achados; criar documentação; `gh api` retornar 403; deixar branch protection no papel; usar `--dry-run` como prova exclusiva; checks da PR #48 serem verdes; 0 forks conhecidos; “não vi o dado” num clone sem verificar outras refs.

**Prontidão honesta:** desenvolvimento com CI não equivale a homologação; homologação não equivale a produção. **Cliente real só com avaliação separada de segurança, LGPD, operação, backup, monitoramento e testes de aplicação**, sem pendências impeditivas.

## 12. Handoff final exigido

O Claude Code deve encerrar respondendo com um relatório curto e rastreável: (a) tarefas T1–T11 com PASS/FAIL/NÃO EXECUTADO/BLOQUEADO/NÃO VERIFICADO; (b) PRs e commits criados, sem inventar merge; (c) checagens locais/GitHub e evidências sanitizadas; (d) issues #2/#3 encerráveis ou abertas e por quê; (e) ações específicas do dono, Support, colaboradores, jurídico; (f) riscos residuais e decisões; (g) veredito limitado a **desenvolvimento / homologação / cliente real** com justificativa. Se o contexto terminar, criar `docs/next-stage/13_CONTINUIDADE_INCIDENTE_PETABLE.md` autossuficiente e sanitizado (sem segredos), contendo progresso, identificadores, dependências e próximos passos. **Nunca declarar conclusão sem evidência.**

## 13. Documentação oficial a consultar durante a execução

- GitHub Docs: [Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).
- GitHub Docs: [Contacting GitHub Support](https://docs.github.com/en/support/contacting-github-support).
- GitHub Docs: [Available rules for rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets).
- GitHub Docs: [Managing a branch protection rule](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule).
- GitHub Docs: [Reviewing deployments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/review-deployments).
- Títulos para conferir na documentação oficial atual: **“GitHub's plans”**, **“Managing environments for deployment”**, **“Billing and licensing for GitHub Actions”**, **“Managing security and analysis settings for your repository”**, **“About secret scanning”**, **“Configuring Dependabot alerts”**, **“About code scanning”** e **“About private vulnerability reporting”**. Confirmar elegibilidade, menus, preços e prazos no dia da execução; não inventar.

## 14. Prompt final para o Claude Code (copiar e colar)

```text
Execute a ETAPA 12 do Petable seguindo o arquivo
12_FECHAR_INCIDENTE_EXPOSICAO_E_PROTEGER_MAIN.md integralmente.

Seu papel é realizar a auditoria e as mudanças técnicas autorizadas, sem
assumir tarefas humanas ou jurídicas. COMEÇE POR T1: revalide o estado
reportado em 19/10/2026, antes de modificar qualquer coisa. Sequencie
T1–T11 conforme as dependências, aproveitando tarefas independentes.

Você está autorizado, sem nova confirmação, a consultar Git/GitHub via gh,
executar verificações seguras/redigidas, revisar documentos, criar branches
e PRs de documentação/CI e preparar textos. Quando existir decisão formal
registrada sobre plano/visibilidade, configure proteções apenas no escopo
aprovado. NÃO peça confirmação repetida de ações já autorizadas.

PARE E SOLICITE AUTORIZAÇÃO ESPECÍFICA com alvo, efeito e reversão antes de:
reescrever histórico; apagar refs, clones ou backups; mudar visibilidade ou
plano; rotacionar/revogar credenciais; enviar mensagens/chamados; fazer merge
de PRs desta etapa ou do Dependabot; alterar permissões não autorizadas;
executar limpeza GC/reflog de clones antigos; testar destrutivamente a main.
O dono abre Support, avisa colaboradores, gerencia senha e aciona jurídico.

Nunca exiba dados pessoais, conteúdo SQL sensível, hashes de senhas, tokens,
senhas, chaves e credenciais em chat, PRs, issues, logs ou artifacts. Nunca
use falsa evidência ou desative checks para passar. Registre PASS / FAIL /
NÃO EXECUTADO / BLOQUEADO / NÃO VERIFICADO com evidências sanitizadas.
Não encerre #2 nem #3 sem o aceite comprovado; não conclua deveres LGPD.

Entregue o handoff especificado na seção 12. Se o contexto acabar, gere um
.md de continuidade autossuficiente e sanitizado com o que foi feito,
o que falta e as decisões pendentes.
```

---

**Estado do documento:** especificação concluída, **execução NÃO INICIADA**. Esta etapa não substitui avaliação jurídica nem garante eliminação de cópias que estejam fora do controle do projeto.
