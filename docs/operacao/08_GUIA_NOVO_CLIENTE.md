# Guia — nova instalação de cliente (etapa 08)

Modelo: **uma instalação por cliente**, sem multi-tenant em banco compartilhado. Cada cliente tem o seu projeto Compose (`petconnect-<id>`) com MySQL, MongoDB, fotos, redes, logs, segredos e backups próprios. Sem cobrança automática nem licença online: contrato e faturamento ficam fora do ERP. O que cada módulo faz está no [catálogo](../implementacao/06_CATALOGO_MODULOS.md); o enforcement, na [etapa 07](../implementacao/07_AUTORIZACAO_MODULAR.md).

> **Nome da variável de módulos:** `PETCONNECT_MODULOS` (decidida nas etapas 06/07; o `MODULOS_HABILITADOS` do exemplo do plano é o mesmo conceito). Os ids são os do catálogo (`clientes`, `produtos`, `estoque`, `caixa`, `pdv`...), não `cadastros`.

## Estado do deploy real (verificado nesta etapa)

| Item | Situação |
|---|---|
| `compose.prod.yaml`, `deploy/Caddyfile`, `scripts/{deploy,backup,restore,restore-test,smoke,monitorar}.sh`, workflows `delivery.yml`/`deploy.yml` | **existem no repositório** |
| Imagens no GHCR e servidor de produção em operação | **NÃO VERIFICADO** (depende de acesso ao GHCR e ao servidor, que esta etapa não tem). O `go-live.md` continua sendo o checklist de primeira entrada |
| Duas instalações no mesmo host, com Docker real | **EXECUTADO** em homologação local (ver [08_RELATORIO_HOMOLOGACAO.md](08_RELATORIO_HOMOLOGACAO.md)) |

## O que cada instalação tem (e não divide)

| Recurso | Isolamento | Como |
|---|---|---|
| Identificador | `INSTALACAO_ID` (não sensível, `[a-z][a-z0-9-]{1,30}`) | nome do projeto Compose e rótulo `br.com.petconnect.instalacao` em todo container |
| Domínio / TLS | um domínio por cliente, certificado próprio no Caddy da instalação | `DOMINIO`, `CADDY_TLS` |
| Banco relacional | MySQL próprio, volume próprio, usuário e senhas próprios | `mysql-data` do projeto |
| Auditoria | MongoDB próprio | `mongo-data` do projeto |
| Arquivos (fotos) | volume `uploads` próprio; URLs de anexo passam pela API da instalação | `uploads` do projeto |
| Rede | `interna` (sem saída para a internet) e `borda` próprias por projeto; o Compose não cria rota entre projetos | redes do projeto |
| Segredos | `clientes/<id>/.env.prod`, `chmod 600`, fora do git. `JWT_SECRET`, senhas de banco, frase de backup e senha do ADMIN **geradas por instalação e nunca repetidas** | `cliente.sh novo` / `validar` |
| Backups | `clientes/<id>/backups`, cifrados com a frase **do cliente**; manifesto registra a instalação e o restore **recusa** backup de outra | `backup.sh`, `restore.sh` |
| Logs | `json-file` do container (10 MB × 5) e log do Caddy no volume da instalação, IP truncado | `x-logs` do compose |
| Jobs agendados | rodam dentro da API da instalação, sobre o banco dela; os crons de backup/monitor são por instalação (`cliente.sh backup|monitorar <id>`) | — |
| Recursos | `mem_limit` e `cpus` por serviço, ajustáveis no `.env.prod` (`API_CPUS`, ...) | `compose.prod.yaml` |

**Token de um cliente não vale em outro:** com `JWT_SECRET` distinto a assinatura não confere (401). Por isso o `validar` recusa segredo repetido.

## Vários clientes no mesmo servidor

- **Recomendado:** um servidor (ou VM) por cliente. É o isolamento mais simples de explicar e de restaurar.
- **Mesmo host:** cada cliente com um **IP próprio** (`BIND_IP`) mantendo 80/443 e seu DNS apontando para esse IP. Sem IP extra, use portas distintas (`--http-port/--https-port`) apenas em homologação: cookies não isolam por porta, então use **domínios distintos** (`a.localhost`, `b.localhost`) mesmo assim.
- Some os limites: cada instalação reserva até ~3 GB (`mem_limit`) e ~5,5 vCPU de teto. O host precisa comportar a soma, ou reduza os limites.
- Monitoramento: `cliente.sh monitorar <id>` por instalação (cron), cada uma com seu `ALERTA_WEBHOOK_URL`.

## Passo a passo (operador)

Pré-requisitos: Docker + Compose v2, `openssl`, `curl`, o repositório em `/opt/petconnect` na tag da release, DNS do cliente apontando para o IP (ou `BIND_IP`), e as imagens acessíveis (`docker login ghcr.io` se privadas).

```bash
cd /opt/petconnect

# 1) Cria clientes/acme/.env.prod com TODOS os segredos gerados (não imprime nenhum)
scripts/cliente.sh novo acme --dominio sistema.acme.com.br --modulos produtos,estoque,caixa,pdv \
    --tls ti@acme.com.br --tag 1.2.3 --admin-email dono@acme.com.br --smtp-host smtp.provedor.com

# 2) Complete o que o script não conhece (SMTP_USERNAME/SMTP_PASSWORD) e guarde o arquivo no cofre
$EDITOR clientes/acme/.env.prod
scripts/cliente.sh validar acme

# 3) Sobe (as migrations do Flyway rodam na subida da API) e cria o usuário técnico de smoke
scripts/cliente.sh subir acme
scripts/cliente.sh smoke-usuario acme

# 4) Smoke test e primeiro backup com restore de teste
scripts/cliente.sh smoke acme
scripts/cliente.sh backup acme && scripts/cliente.sh restore-test acme

# 5) Cron do cliente (como na instalação única, uma linha por instalação)
#    30 2 * * *  /opt/petconnect/scripts/cliente.sh backup acme       >> /var/log/petconnect-acme-backup.log 2>&1
#    0 4 * * 1   /opt/petconnect/scripts/cliente.sh restore-test acme >> /var/log/petconnect-acme-restore.log 2>&1
#    */5 * * * * /opt/petconnect/scripts/cliente.sh monitorar acme    >> /var/log/petconnect-acme-monitor.log 2>&1
```

Depois: o ADMIN inicial entra, **troca a senha**, e você remove `ADMIN_PASSWORD` do `.env.prod`. Confira `scripts/cliente.sh listar` e, com mais de uma instalação, `scripts/cliente.sh isolamento`.

`cliente.sh novo` **nunca sobrescreve**: se a instalação existe, nada é alterado. Ele recusa domínio ou `BIND_IP:porta` já usados por outro cliente e lista de módulos inválida (módulo desconhecido ou dependência faltando).

## Módulos contratados

Definidos em `PETCONNECT_MODULOS` (ver [runbook](../runbooks/modulos-por-cliente.md)). A estrutura do banco é a mesma para todos os clientes: **ativar módulo não exige migration**.

```bash
# Upgrade (ex.: passa a ter clientes): backup → valida → reinicia api/web → smoke → volta sozinho se falhar
scripts/cliente.sh modulos acme produtos,estoque,caixa,pdv,clientes
```

- A API valida no boot e a mudança fica no `modulo_instalacao_historico` e na auditoria `MODULOS_ALTERADOS`.
- **Downgrade nunca apaga dado.** A API recusa a partida se houver pendência (caixa aberto, venda cancelável recente, conta a pagar aberta, pedido de compra, agendamento ativo, produto com controle de lote, vacinação ativa); o script então **volta sozinho** para a lista anterior e mostra o motivo. Resolva a pendência e repita.
- Depois de mudar: teste a API (`GET /api/v1/modulos`) e a interface (menu e uma tela do módulo novo).

## Rotação de chaves

| Segredo | Como | Efeito |
|---|---|---|
| `JWT_SECRET` | `scripts/cliente.sh rotacionar-jwt acme` | derruba as sessões **só deste cliente** |
| Senhas de MySQL/Mongo, SMTP, chave SSH de deploy | [rotacao-segredos.md](../runbooks/rotacao-segredos.md), usando `ENV_FILE=clientes/acme/.env.prod` e `-p petconnect-acme` | — |
| `BACKUP_PASSPHRASE` | trocar no `.env.prod`; **guarde a antiga enquanto houver backup cifrado com ela** | backups novos usam a nova |

## Upgrade de versão (todas as instalações)

`TAG` é por instalação. Atualize uma por vez (a migration roda na subida e o backup é feito antes): troque `TAG` no `.env.prod`, `scripts/cliente.sh backup acme`, `docker compose -f compose.prod.yaml --env-file clientes/acme/.env.prod -p petconnect-acme pull && ... up -d --wait`, `scripts/cliente.sh smoke acme`. Rollback: [rollback.md](../runbooks/rollback.md). `scripts/deploy.sh` continua válido para a instalação única (projeto `petconnect`); o deploy automático **por cliente** (GitHub Actions) **não** foi criado nesta etapa.

## Recuperação, privacidade e segurança

- [Plano de recuperação por cliente](08_PLANO_RECUPERACAO.md)
- [Checklist de privacidade e segurança](08_CHECKLIST_SEGURANCA_PRIVACIDADE.md)
- Smoke test: `scripts/cliente.sh smoke <id>` (login, CSP, HSTS, X-Request-Id, redirecionamento HTTPS, readiness, login do usuário de smoke)

## Retirada de um cliente (fim de contrato)

Não está automatizada de propósito: apagar volume é destrutivo. Procedimento manual, **com autorização do dono**: backup final entregue ao cliente, `scripts/cliente.sh parar <id>`, prazo de retenção definido em contrato, e só então remover volumes/diretório e os backups, conforme o [lgpd.md](../runbooks/lgpd.md).
