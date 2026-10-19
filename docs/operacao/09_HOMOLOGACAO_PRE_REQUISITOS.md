# Pré-requisitos da homologação remota (manifesto sanitizado)

Preencher **antes** de qualquer comando mutante. Não escreva segredos aqui: só nomes, responsáveis e decisões. Estado em **2026-10-19**: nada abaixo foi fornecido, então a homologação remota (#38, #39, #40) está **BLOQUEADA, NÃO EXECUTADA**.

| # | Item | Responsável | Valor / decisão | Situação |
|---|---|---|---|---|
| 1 | Ambiente (deve ser **homologação**, nunca produção) | dono | | PENDENTE |
| 2 | Servidor dedicado e descartável (provedor, região, 2 vCPU/4 GB/40 GB) e **limite de custo** | dono | | PENDENTE |
| 3 | Subdomínio de teste autorizado e acesso ao DNS (hostname ≠ IP de bind) | dono | | PENDENTE |
| 4 | Imagem imutável já publicada (tag `sha-…` ou `vX.Y.Z`) e **digest** registrado; sem criar release/tag | dono / Claude | | PENDENTE (há `sha-…` do `main` no GHCR; escolher uma) |
| 5 | Pacote/módulos da instalação de teste (ex.: `petshop`, e `completo` na 2ª instalação) | Claude | | definido no roteiro |
| 6 | Acesso SSH do operador (chave pública cadastrada, login por senha desativado) | dono | | PENDENTE |
| 7 | **Host key do servidor** obtida por canal confiável (console do provedor) e comparada por fingerprint; vai para `DEPLOY_KNOWN_HOSTS`. Não usar o primeiro `ssh-keyscan` às cegas | dono | | PENDENTE |
| 8 | Token do GHCR só de leitura (`read:packages`), para o servidor | dono | | PENDENTE |
| 9 | SMTP de teste (host, usuário, senha só em `.env.prod`) e caixa de recebimento | dono | | PENDENTE |
| 10 | Destino de backup off-site com credencial restrita à instalação e política de retenção | dono | | PENDENTE |
| 11 | Canal de alerta de teste (`ALERTA_WEBHOOK_URL`), **não** o de produção | dono | | PENDENTE |
| 12 | 2ª instalação fictícia para o teste de isolamento (outro subdomínio ou porta) | dono | | PENDENTE |
| 13 | Environment do GitHub (`producao`) com revisor, ligado ao `deploy.yml`; secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` (valores só pelo dono). Para homologação, preferir environment **separado** (`homologacao`) a reutilizar o de produção | dono | | PENDENTE |
| 14 | Ações de encerramento: destruir servidor, remover DNS, revogar token GHCR e credencial off-site; quem executa e quando | dono | | PENDENTE |

## Limites desta etapa

- Sem os itens 1 a 3, 6, 8 e 9 não há como provar pull do GHCR, DNS, TLS público e SMTP (#38). Docker local, certificado interno e Mailpit **não** contam.
- Backup só local não atende a #40: é preciso o item 10 e a restauração **a partir do off-site** em ambiente novo, com RTO (meta 4 h) e RPO (meta 24 h) medidos, incluindo agendamento, alertas e retenção.
- O deploy por cliente (#39) **não existe**: `deploy.yml` atende uma instalação (`/opt/petconnect`). Implementar só depois do item 1/13, com seleção de instalação validada por lista fechada, environment correto, concorrência por instalação e rollback observável; nunca expor shell livre por parâmetro.
- Migrations: voltar a imagem não desfaz migration. Rollback testado só com imagem anterior conhecida, backup verificável e migrations compatíveis.
- A flag de lotes (`PETCONNECT_ESTOQUE_LOTES`) só é ligada em homologação, com registro de quando e da massa usada; nunca em instalação real como efeito colateral.

Roteiro: [09_ROTEIRO_VPS_HOMOLOGACAO.md](09_ROTEIRO_VPS_HOMOLOGACAO.md).
