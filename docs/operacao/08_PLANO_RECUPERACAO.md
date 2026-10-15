# Plano de recuperação por cliente (etapa 08)

Meta herdada do [recuperacao-desastre.md](../runbooks/recuperacao-desastre.md): **RTO 4 h / RPO 24 h**, agora **por instalação**. Cada cliente tem backup, frase de cifragem e restore próprios; recuperar um cliente não toca nos outros.

## O que cada backup contém

`clientes/<id>/backups/{diario,semanal,mensal}/<data>/`: `mysql.sql.gz.enc`, `mongo.archive.gz.enc`, `fotos.tar.gz.enc` (AES-256, PBKDF2, frase do cliente) e `manifesto.txt` (contagens por tabela, versão do Flyway, hashes e `instalacao=<id>`). Retenção: 7 diários, 4 semanais, 6 mensais. Cópia off-site: `BACKUP_REMOTO` **por cliente** (um destino por cliente, com credenciais que não alcancem os outros).

## Rotina

| Quando | O quê | Comando |
|---|---|---|
| Diário 02:30 | backup cifrado | `cliente.sh backup <id>` |
| Semanal | restore num MySQL descartável, comparando contagens e versão do Flyway | `cliente.sh restore-test <id>` |
| A cada 5 min | alerta de API/Web fora, disco, backup atrasado, 5xx | `cliente.sh monitorar <id>` |
| Antes de qualquer upgrade de versão ou de módulos | backup | (o `cliente.sh modulos` já faz) |
| Semestral | ensaio de desastre numa VM temporária | abaixo |

## Restaurar a MESMA instalação (dado corrompido ou apagado)

```bash
scripts/cliente.sh backup <id>                         # guarda o estado atual antes de qualquer coisa
CONFIRMAR=RESTAURAR scripts/cliente.sh restore <id> clientes/<id>/backups/diario/<data>
scripts/cliente.sh smoke <id>
```

O restore substitui MySQL, MongoDB e fotos **desta** instalação, para e sobe só a API e o Web dela. Recusa o backup se o hash não confere ou se o manifesto é de **outra** instalação (`instalacao=` diferente).

## Servidor perdido

1. Servidor novo seguindo o [guia](08_GUIA_NOVO_CLIENTE.md) até o `validar`, **sem** `subir`.
2. `clientes/<id>/.env.prod` e a frase do cofre **do cliente** (sem elas não há restore: por isso as duas ficam fora do servidor).
3. Baixe os backups do cliente (`rclone copy "$BACKUP_REMOTO" clientes/<id>/backups`) e rode `restore-test`.
4. Suba só os bancos: `docker compose -f compose.prod.yaml --env-file clientes/<id>/.env.prod -p petconnect-<id> up -d --wait mysql mongo`, depois `restore` e DNS para o IP novo.
5. `smoke` e registro do que se perdeu (RPO).

## Se um segredo de UM cliente vazar

`rotacionar-jwt <id>`, troca de senhas de banco/SMTP daquele `.env.prod` e [incidente.md](../runbooks/incidente.md). Os outros clientes não compartilham nenhum desses segredos (o `cliente.sh validar` e o `isolamento` garantem), então não precisam de rotação por causa desse evento.

## Evidência

Backup, `restore-test`, restore real e recusa de backup entre clientes foram executados em homologação local: [08_RELATORIO_HOMOLOGACAO.md](08_RELATORIO_HOMOLOGACAO.md). O ensaio em servidor remoto e a cópia off-site **não foram executados** (sem acesso).
