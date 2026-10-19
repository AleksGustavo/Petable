# Plano de execução — segurança, CI e homologação

Controle sanitizado (sem valores sensíveis). Atualizado em **2026-10-19**. Base: `main` em `12bbf0d` (merge da PR #47).
Estados: NÃO INICIADO · EM EXECUÇÃO · CONCLUÍDO · BLOQUEADO · NÃO APLICÁVEL. Testes: PASS · FAIL · NÃO EXECUTADO.

## Situação encontrada (verificada em 2026-10-19)

| Fato | Evidência |
|---|---|
| Repositório canônico `AleksGustavo/Petable`, visibilidade **PÚBLICA** (a issue #2 diz que fora tornado privado como mitigação: **já não vale**) | `gh repo view` |
| O script de inserção com dados pessoais **continua na árvore atual da `main`** em `docs/legacy/documentacao/Banco de Dados/Scripts do Banco/` e em 4 blobs do histórico | [relatório](RELATORIO_HISTORICO_DADOS_PESSOAIS.md) |
| `main` sem proteção clássica e sem rulesets; nenhum environment criado; permissão padrão do `GITHUB_TOKEN` = leitura | `gh api` (rulesets `[]`, proteção 404, environments 0) |
| Secret scanning e push protection **ativos**; Dependabot alerts estavam **desligados** (ativados nesta execução) | `security_and_analysis` |
| 0 forks, 13 PRs abertos (todos do Dependabot), 39 refs `refs/pull/*` | `gh api`, clone espelho |

## Controle por item

| ID | Issue | Objetivo | Status | Evidência | Dependência / próxima ação |
|---|---|---|---|---|---|
| S-01 | #2 | Inventário do histórico (caminhos, refs, categorias) | CONCLUÍDO | [relatório](RELATORIO_HISTORICO_DADOS_PESSOAIS.md) | Dono confirmar quais dados são reais (a issue afirma que são) |
| S-02 | #2 | Remover o script da árvore atual | CONCLUÍDO (em PR) | PR desta branch; barreira `verificar-dados-sensiveis.sh` testada antes/depois | Merge da PR |
| S-03 | #2 | Prevenção: gitleaks + barreira de CPF + `.gitignore` | CONCLUÍDO (em PR) | workflow `Segredos`; varredura local: histórico e árvore sem achados | Check real só aparece quando a PR abrir |
| S-04 | #2 | Ensaio da limpeza de histórico (sem push) | CONCLUÍDO | clone espelho descartável; `main` perde só 1 arquivo (349 linhas); `fsck` ok; gitleaks e padrão de CPF limpos | — |
| S-05 | #2 | Publicar a reescrita do histórico | **BLOQUEADO: exige autorização** | procedimento e refs em [relatório](RELATORIO_HISTORICO_DADOS_PESSOAIS.md#5-procedimento-de-publicação-após-autorização) | Autorização específica do dono + janela de congelamento |
| S-06 | #2 | Tornar o repositório privado enquanto isso | **BLOQUEADO: exige autorização** | repo público com dados pessoais na `main` | Decisão do dono (ver efeitos no relatório) |
| S-07 | #2 | Credenciais: confirmar e rotacionar | EM EXECUÇÃO | 0 segredos reais encontrados pelo gitleaks (3 falsos positivos conferidos); `admin123` aparece só em doc/teste do legado | Dono confirmar que nenhuma instalação usa senhas do legado |
| S-08 | #2 | Cópias residuais (refs de PR, caches) | BLOQUEADO | `refs/pull/*` não são reescrevíveis por push | Pedido ao GitHub Support depois da reescrita (texto no relatório) |
| P-01 | #3 | Checks sem deadlock por caminho | CONCLUÍDO (em PR) | `backend.yml` e `frontend.yml` agora emitem o job em todo PR | Validar nas PRs reais (docs-only, front, back) |
| P-02 | #3 | Proteção da `main` (PR + checks + sem force-push) | NÃO INICIADO: depende de S-05 e P-01 | repo público: o recurso está disponível sem plano pago | Aplicar depois do merge de P-01; se S-05 for autorizado, antes dele (force-push seria bloqueado) |
| P-03 | #3 | Dependabot alerts / secret scanning / push protection | CONCLUÍDO | alerts ativados; secret scanning e push protection já ativos | — |
| P-04 | #3 | Environment `producao` ligado ao `deploy.yml` | NÃO INICIADO | `deploy.yml` já usa `environment: producao`; o environment não existe | Criar com revisor e secrets (nomes em `docs/runbooks/go-live.md`); valores só pelo dono |
| P-05 | #3 | Host keys SSH por canal confiável | NÃO INICIADO | — | Depende de ter servidor (H-01) |
| B-01 | PR #15 | Causa da falha do Backend | CONCLUÍDO | [abaixo](#pr-15-falha-do-backend) | Merge da PR #15 é decisão do dono |
| H-01 | #38 | Servidor de homologação (GHCR pull, DNS, TLS, SMTP) | BLOQUEADO | sem VPS/domínio/SMTP autorizados | Manifesto de pré-requisitos a preencher pelo dono |
| H-02 | #39 | Deploy por cliente | NÃO INICIADO | — | Depende de H-01 |
| H-03 | #40 | Backup off-site + restore + RTO/RPO | NÃO INICIADO | — | Depende de H-01 e de destino off-site |
| H-04 | #37/#10/#41/#46 | Canário, migração real, jurídico, desempenho | NÃO APLICÁVEL a este escopo | exigem dados/pessoas reais | Não encerrar por homologação sintética |

## PR 15: falha do Backend

- **Execução falha:** run `38775913063`, SHA `e95e2ef`, job `Build + testes (unit + integração)`, 2026-10-15.
- **Causa:** o runner não conseguiu baixar `testcontainers/ryuk:0.14.0` do Docker Hub (`registry-1.docker.io: context deadline exceeded`), então nenhum contexto de teste de integração subiu (≈90 erros `ContainerFetchException`/Flyway em cascata, todos do mesmo motivo). O step `Relatórios de teste e cobertura` falhou depois, por `getaddrinfo EAI_AGAIN results-receiver.actions.githubusercontent.com` (DNS do runner). Nenhum dos dois tem relação com a atualização do `upload-artifact` (a PR só altera `.github/workflows/*`, 4 arquivos, a partir da base `12bbf0d`).
- **Ação:** um único rerun dos jobs falhos, justificado por falha de rede externa. Resultado: `Build + testes (unit + integração)` **success** no mesmo SHA.
- **Observação de robustez:** o ryuk e as imagens dos testes vêm do Docker Hub sem cache; uma falha pontual do Hub derruba o job inteiro. Mitigação possível (não aplicada): cache de imagens ou espelho; avaliar se a recorrência justificar.
- **Merge:** não feito (a autorização permanente cobre PRs das etapas 01–09, não atualizações do Dependabot).

## Validações executadas nesta etapa

| Validação | Resultado |
|---|---|
| `verificar-dados-sensiveis.sh` com o arquivo presente | **falha** (esperado) |
| `verificar-dados-sensiveis.sh` sem o arquivo | **passa** |
| gitleaks 8.30.1: histórico de todas as refs locais | 0 achados (3 falsos positivos em allowlist por caminho) |
| gitleaks: árvore atual | 0 achados |
| Ensaio de reescrita: gitleaks, padrões de CPF, `git fsck` | limpo |
| `mvn verify`, lint/typecheck/testes do frontend | NÃO EXECUTADO: a PR só altera workflows, scripts e docs; os checks reais da PR cobrem o que cabe |
| Checks reais do GitHub no SHA final da PR | NÃO EXECUTADO até a PR ser aberta |

## Quem faz o quê

Ver o handoff no fim de [RELATORIO_HISTORICO_DADOS_PESSOAIS.md](RELATORIO_HISTORICO_DADOS_PESSOAIS.md) e em `docs/operacao/09_ROTEIRO_VPS_HOMOLOGACAO.md` para a homologação remota.
