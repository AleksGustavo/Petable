# Execução da etapa 12 — incidente de exposição e proteção da `main`

Registro sanitizado (sem dados pessoais, segredos ou nomes de terceiros). Especificação: [docs/next-stage/12_FECHAR_INCIDENTE_EXPOSICAO_E_PROTEGER_MAIN.md](../next-stage/12_FECHAR_INCIDENTE_EXPOSICAO_E_PROTEGER_MAIN.md). Execução iniciada em **2026-10-20**, base `main` = `7887debf83`.
Estados: PASS · FAIL · NÃO EXECUTADO · BLOQUEADO · NÃO VERIFICADO.

## Descoberta que muda o escopo

O histórico do Petable foi importado de um **repositório de origem** (projeto acadêmico, criado em 2025-08-31) que estava **público** e tinha **os mesmos 4 arquivos de dados** no histórico, além de **3 forks públicos** de antigos colaboradores. A contenção de 2026-10-19 só cobriu o Petable.

| Ação em 2026-10-20 | Resultado |
|---|---|
| Repositório de origem tornado **privado** (autorizado pelo dono) | PASS (`gh repo view`: PRIVATE) |
| Forks públicos depois da mudança | **continuam públicos** (2 confirmados; 1 deixou de resolver): só os donos ou o GitHub Support podem tratá-los |
| Operações Git no repositório de origem | o GitHub responde "Your repository is disabled" ao `git`, embora a API diga `disabled: false` (causa não esclarecida; o dono precisa abrir a página do repositório) |
| Ensaio de limpeza no repositório de origem (a partir do clone local, sem publicar) | blobs removidos, `fsck` ok, sem e-mails restantes em arquivos de dados, gitleaks 0; 3 refs mudam (`main`, `Aleksander` e uma branch `claude/*`); **publicação NÃO EXECUTADA** |
| `PetConnect-API` (público): histórico completo | sem padrão de CPF em arquivos de dados; gitleaks 0 (PASS) |

## Matriz T1–T11

| Tarefa | Estado | Resultado / evidência |
|---|---|---|
| **T1** Revalidar | **PASS** | Petable PRIVADO; `main` = `7887debf83`; clone fresco: `git fsck --full` ok, 5 caminhos inalcançáveis, blobs antigos ausentes, gitleaks 0 achados (controle positivo sem allowlist: 5 achados, todos falsos positivos conferidos: senha de teste, JWT sintético, `localhost:<porta>` e 2 placeholders de `.env.example`); nenhuma reintrodução |
| **T2** Support | **PREPARADO, aguarda o dono** | 42 refs residuais (`refs/pull/*`, `refs/github-services/*`); **13 alcançam os caminhos removidos**, todas `head` de PRs antigos já mesclados (#1, #13, #14, #28–#36, #47). Inventário só de SHAs em arquivo privado fora do Git. Escopo do pedido deve incluir o repositório de origem e os forks |
| **T3** Colaboradores | **PREPARADO, aguarda o dono** | No Petable só o dono tem acesso (nenhum convite pendente). Os antigos colaboradores têm **forks públicos do repositório de origem** com o histórico antigo: o aviso precisa pedir que apaguem ou tornem privados os forks |
| **T4** Senha do backup | **NÃO EXECUTADO (dono)** | backup cifrado presente (50.847.696 bytes); arquivo de senha em texto ainda existe (4 linhas); retenção não definida |
| **T5** Clones locais | **INVENTARIADO** | 4 worktrees do Petable: o checkout principal está sujo (21 alterações, **não tocado**); `wt-lotes`, `wt-petable`, `wt-seg` limpos, 0 stashes. Branches locais sem upstream: `Everton`, `Marquinhos`, `Wanderson`, `feat/petable-etapa-11`. O clone do projeto acadêmico também guarda o histórico antigo. Nenhuma limpeza feita |
| **T6** Proteção da `main` | **BLOQUEADO: decisão do dono** | repo privado: API de rulesets responde `403 Upgrade to GitHub Pro or make this repository public`. Configuração-alvo pronta em [`rulesets/`](rulesets/) (não aplicada). Sem `pull_request_target` nos workflows; `GITHUB_TOKEN` somente leitura; 0 environments; 0 secrets; 0 webhooks; 0 deploy keys |
| **T7** Controles do GitHub | **PARCIAL** | secret scanning: **desativado** (FAIL); code scanning nativo: **não habilitado** (FAIL; o CodeQL roda como workflow); Dependabot alerts: **ativo, 9 abertos** (4 altos: `braces`, `next`, `sharp`, `source-map-js`; 4 médios e 1 baixo em `next`); push protection e private vulnerability reporting: NÃO VERIFICADO (API 404/sem campo); `SECURITY.md` existe, mas depende de relato privado que pode não estar habilitado |
| **T8** Credenciais | **BLOQUEADO (dono)** | perguntas D7 pendentes; nada rotacionado |
| **T9** Dossiê jurídico | **PREPARADO (v1)** | fora do Git, com linha do tempo, acesso comprovável (Petable: 565 clones/52 únicos em 14 dias, incluindo CI; origem: 9 únicos), lacuna de visibilidade entre 2026-09-25 e 2026-10-16 e perguntas ao jurídico. **Não enviado** |
| **T10** GHCR e Dependabot | **PARCIAL** | Entrega da tag `v0.1.0` (2026-10-19) moveu as tags `0.1.0`, **`latest`** e `sha-01cc9a3` das duas imagens; digests nos logs do run `39271511660` (api `sha256:dc3e4659…`, web `sha256:221dbf8c…`); `main`: `sha-b1641ff`. Sem consumidor (nenhum deploy). Listagem direta do pacote **NÃO VERIFICADO**: o token do `gh` não tem `read:packages`. PRs #15–#27: todas abertas e mergeáveis; #15 e #19 com 13/13 checks; #16, #17 e #20–#27 sem checks (rebase do bot depois da reescrita); **#18 com Backend FAIL** |
| **T11** Documentação | **EM EXECUÇÃO** | este arquivo, a especificação e os rulesets; PR **não mesclada** (fora da autorização permanente) |

## Falha da PR #18 (não relacionada ao incidente)

`ApplicationIT.migrationsAplicadasComDadosDeReferencia` espera 6 linhas em `especie` e encontra 7: o resultado depende de outro teste ter inserido dados antes (dependência de ordem de execução). A PR está na base atual da `main`, que passa; é um teste frágil, não um defeito da atualização do `setup-java`. Ação proposta (não executada): corrigir o isolamento do teste em PR própria e então reexecutar.

## Alertas Dependabot abertos

Nove alertas em dependências do frontend e do build (`next`, `braces`, `sharp`, `source-map-js`). As PRs #22 (`next`/`react`) e #26 (`vitest`) são candidatas; mesclar atualizações do Dependabot **não** está coberto pela autorização permanente. Avaliar antes de qualquer cliente real.

## Critérios de encerramento

- **#2: ABERTA.** Faltam: Support (13 refs de PR, repositório de origem e forks), colaboradores, senha do backup, credenciais, jurídico e a publicação da limpeza do repositório de origem.
- **#3: ABERTA / BLOQUEADA** pela decisão Pro × público. Depois dela: aplicar [`rulesets/`](rulesets/), abrir PR só de docs para provar os checks e testar push direto e force-push.
- O que não basta: tornar privado, protocolo do Support sem verificação posterior, gitleaks sem achados, `gh api` com `403`.

## Veredito (limitado às evidências)

Desenvolvimento e homologação local: **apto**. Cliente real: **não apto** enquanto houver dados pessoais acessíveis em cópias públicas (forks), controles nativos do GitHub desativados e proteção da `main` ausente.
