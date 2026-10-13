# Produção — PetConnect Web

Como colocar e manter o PetConnect Web no ar num petshop. Spec: [next-stage/09-producao-operacao.md](next-stage/09-producao-operacao.md). Procedimentos passo a passo ficam em [runbooks/](runbooks).

## Arquitetura

```
Internet ──80/443──▶ caddy (HTTPS automático, HSTS, gzip/zstd, corpo ≤ 7 MB, X-Request-Id)
                        ▼
                      web (Next.js, BFF: CSP com nonce, cookie de sessão httpOnly/Secure)
                        ▼  http://api:8080 (Bearer do cookie, X-Request-Id, X-Forwarded-For)
                      api (Spring Boot, perfil prod, logs JSON mascarados; health/métricas só na :9090 interna)
                     ▼            ▼
                 mysql          mongo (auditoria)        ← rede "interna", sem saída para a internet
volumes: mysql-data, mongo-data, uploads (fotos), caddy-data (certificados)
```

**Portas e redes**
- Só o Caddy publica portas. Web, API e bancos não são alcançáveis de fora.
- A API sai para a internet apenas pela rede `saida`, para enviar e-mail por SMTP.

**Imagens**
- Publicadas pelo `delivery.yml` no GHCR: `ghcr.io/aleksgustavo/petconnect-web-{api,web}:<tag>`.
- Tags: `1.2.3` para a git tag `v1.2.3`, ou `sha-abc1234`.

## Requisitos do servidor

- VPS Linux (Ubuntu 24.04 LTS ou Debian 12) com **2 vCPU, 4 GB de RAM e 40 GB de SSD**; o `mem_limit` total é cerca de 3 GB.
- Docker Engine + plugin Compose v2, `openssl`, `curl`, `cron` e `git`. Opcional: `rclone`, para cópia off-site dos backups.
- DNS: registro `A`/`AAAA` de `<DOMINIO>` apontando para `<IP_DO_SERVIDOR>`.
- Firewall: liberar só 22 (SSH, de preferência restrito), 80 e 443.
- Se os pacotes do GHCR forem privados, rode `docker login ghcr.io` no servidor com um token de leitura de pacotes.

## Instalação (primeira vez)

```bash
sudo mkdir -p /opt/petconnect && sudo chown "$USER" /opt/petconnect
git clone https://github.com/AleksGustavo/PetConnect-Web.git /opt/petconnect
cd /opt/petconnect
cp .env.prod.example .env.prod && chmod 600 .env.prod    # preencher TODOS os valores (openssl rand -base64 48)
docker compose -f compose.prod.yaml --env-file .env.prod up -d --wait
./scripts/smoke.sh
```

Depois, agende o cron:

```cron
30 2 * * *  /opt/petconnect/scripts/backup.sh       >> /var/log/petconnect-backup.log 2>&1
0 4 * * 1   /opt/petconnect/scripts/restore-test.sh >> /var/log/petconnect-restore-test.log 2>&1
*/5 * * * * /opt/petconnect/scripts/monitorar.sh    >> /var/log/petconnect-monitor.log 2>&1
```

Crie o usuário de smoke (papel VENDAS) com `SMOKE_USERNAME`/`SMOKE_PASSWORD` do `.env.prod` e guarde o `.env.prod` no cofre de senhas.

## Variáveis

A lista completa está em [`.env.prod.example`](../.env.prod.example). Três pontos críticos:
- **`BACKUP_PASSPHRASE`**: sem ela os backups não podem ser restaurados. Guarde no cofre.
- **`JWT_SECRET`**: trocar derruba todas as sessões (ver [rotação](runbooks/rotacao-segredos.md)).
- **`CADDY_TLS`**: e-mail real para o Let's Encrypt. `internal` só em teste local.

## Segurança aplicada

| Controle | Onde |
|---|---|
| HTTPS obrigatório, HTTP → 308, HSTS 1 ano | Caddy |
| CSP com nonce por requisição (`script-src 'self' 'nonce-…' 'strict-dynamic'`, `frame-ancestors 'none'`…) | `frontend/src/proxy.ts` + `lib/seguranca.ts` |
| X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy | `next.config.ts` |
| Cookie de sessão `httpOnly; SameSite=Lax; Secure` | `app/actions/auth.ts` |
| Limite de login: 20 falhas por IP em 5 min → 429 (além do bloqueio por conta) | `LimitadorLogin` |
| Logs JSON (ECS) com CPF, e-mail, telefone e tokens mascarados | `MascaramentoLogs` (perfil prod) |
| Actuator (health/métricas) só na porta 9090, não publicada | `application-prod.yml` |
| Segredos só no servidor; actions do CI fixadas por SHA; Trivy + CodeQL + SBOM | `.env.prod`, `.github/workflows` |

## Observabilidade

- **Logs:** `docker compose -f compose.prod.yaml logs -f api`. Formato JSON; filtre pelo `requestId`, que é o mesmo do cabeçalho `X-Request-Id` da resposta. O log de acesso do Caddy fica no volume `caddy-logs`, com IP truncado e sem a busca.
- **Métricas:** `docker compose -f compose.prod.yaml exec api curl -s localhost:9090/actuator/prometheus`. Para um Prometheus externo, conecte-o à rede `interna`.
- **Alertas:** `monitorar.sh` avisa quando a API ou o Web cai, o disco passa de 85%, o último backup tem mais de 26 h ou há mais de 20 respostas 5xx em 5 min. O aviso vai para `ALERTA_WEBHOOK_URL`.

## Retenção

| Dado | Retenção |
|---|---|
| Logs dos containers | 10 MB × 5 por serviço |
| Log de acesso do Caddy | 10 MB × 5 |
| Backups | 7 diários + 4 semanais + 6 mensais (+ off-site) |
| Auditoria (Mongo) | **sem expiração automática**: prova de operações e acessos, pelo prazo fiscal mínimo de 5 anos. Expurgo anual manual, documentado em [lgpd.md](runbooks/lgpd.md) |

## Metas de recuperação

- **RTO 4 h / RPO 24 h.** O RPO cai para o intervalo do backup, se ele for mais frequente.
- Teste de desastre semestral: [recuperacao-desastre.md](runbooks/recuperacao-desastre.md).

## Validação local da produção

```bash
docker compose build api web
docker tag petconnect-web-api ghcr.io/aleksgustavo/petconnect-web-api:local
docker tag petconnect-web-web ghcr.io/aleksgustavo/petconnect-web-web:local
# .env.prod.local: TAG=local, DOMINIO=localhost, CADDY_TLS=internal, HTTP_PORT=8880, HTTPS_PORT=8443 + segredos de teste
docker compose -f compose.prod.yaml --env-file .env.prod.local -p petconnect-prodlocal up -d --wait
ENV_FILE=.env.prod.local PROJETO=petconnect-prodlocal SMOKE_URL=https://localhost:8443 \
  SMOKE_HTTP_URL=http://localhost:8880 ./scripts/smoke.sh
docker compose -f compose.prod.yaml --env-file .env.prod.local -p petconnect-prodlocal down -v   # só o projeto de teste
```

## E2E

- **Local**, com a stack de dev no ar:
  ```bash
  cd frontend
  E2E_BASE_URL=http://localhost:3000 E2E_API_URL=http://localhost:8081 \
    E2E_ADMIN_USERNAME=... E2E_ADMIN_PASSWORD=... npm run e2e
  ```
- **No CI:** `.github/workflows/e2e.yml`. Os dados são isolados: usuário ADMIN efêmero, desativado no final, e produto próprio.
