# Rotação de segredos

Faça a rotação uma vez por ano, e **imediatamente** se o segredo vazar ou alguém com acesso sair. Gere valores com `openssl rand -base64 48`.

| Segredo | Como trocar | Efeito |
|---|---|---|
| `JWT_SECRET` | editar `.env.prod` → `docker compose -f compose.prod.yaml --env-file .env.prod up -d api` | todas as sessões caem; todos fazem login de novo |
| `MYSQL_PASSWORD` | `docker compose -f compose.prod.yaml --env-file .env.prod exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "ALTER USER \"petconnect\"@\"%\" IDENTIFIED BY \"<nova>\""'` → editar `.env.prod` → `up -d api` | API reconecta |
| `MYSQL_ROOT_PASSWORD` | `ALTER USER 'root'@'%' ...` e `'root'@'localhost'` → editar `.env.prod` | afeta os scripts de backup |
| `MONGO_PASSWORD` | `docker compose -f compose.prod.yaml --env-file .env.prod exec mongo mongosh -u petconnect -p --authenticationDatabase admin --eval 'db.getSiblingDB("admin").changeUserPassword("petconnect", "<nova>")'` → `.env.prod` → `up -d api` | API reconecta |
| `SMTP_PASSWORD` | trocar no provedor → `.env.prod` → `up -d api` | — |
| `BACKUP_PASSPHRASE` | editar `.env.prod` | backups **novos** usam a nova; **mantenha a antiga no cofre** enquanto houver backups antigos (até 6 meses) |
| `SMOKE_PASSWORD` | trocar a senha do usuário no sistema → `.env.prod` | — |
| Chave SSH do deploy | nova chave → `authorized_keys` no servidor → secret `DEPLOY_SSH_KEY` no environment `producao` → remover a antiga | — |

Depois de qualquer troca: `./scripts/smoke.sh` e atualizar o cofre.
