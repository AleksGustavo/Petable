# Recuperação de desastre

Cenário: servidor perdido (disco, provedor, invasão). Meta: **RTO 4 h / RPO 24 h**.

## Pré-requisitos (verificar no go-live e a cada teste)

- Cópia off-site dos backups (`BACKUP_REMOTO`) funcionando.
- `.env.prod` e `BACKUP_PASSPHRASE` no cofre de senhas (fora do servidor).
- Acesso ao DNS do domínio e ao GitHub (imagens no GHCR).

## Procedimento

1. Suba um servidor novo seguindo [../producao.md](../producao.md#instalação-primeira-vez), **sem** o `up`.
2. Recupere o `.env.prod` do cofre e ponha em `/opt/petconnect/.env.prod` (chmod 600).
3. Baixe os backups: `rclone copy "$BACKUP_REMOTO" /opt/petconnect/backups`.
4. Confira o mais recente: `./scripts/restore-test.sh backups/diario/<mais-recente>`.
5. Suba só os bancos: `docker compose -f compose.prod.yaml --env-file .env.prod up -d --wait mysql mongo`.
6. Rode `./scripts/restore.sh backups/diario/<mais-recente>` e digite `RESTAURAR`. Ele sobe api e web ao final.
7. Aponte o DNS para o IP novo e rode `docker compose -f compose.prod.yaml --env-file .env.prod up -d --wait`. O Caddy emite o certificado.
8. Rode `./scripts/smoke.sh` e confira os dados do último dia. Registre o que se perdeu (RPO).

## Teste semestral

Execute o procedimento numa VPS temporária, com um subdomínio de teste. Anote o tempo total e compare com o RTO. Destrua a VPS no final.
