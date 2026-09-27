#!/usr/bin/env bash
# RESTORE REAL (substitui os dados atuais). Só em incidente/desastre — ver docs/runbooks/backup-restore.md.
# Uso: scripts/restore.sh <pasta-do-backup>
source "$(dirname "$0")/_comum.sh"

: "${BACKUP_PASSPHRASE:?defina BACKUP_PASSPHRASE}"
pasta="${1:?informe a pasta do backup}"
pasta="${pasta%/}"
[[ -f "$pasta/manifesto.txt" ]] || falhar "backup não encontrado: $pasta"
( cd "$pasta" && grep -E '^[0-9a-f]{64} ' manifesto.txt | sha256sum -c --quiet - ) || falhar "hash não confere"

echo "ATENÇÃO: os dados ATUAIS do MySQL, do MongoDB e das fotos serão SUBSTITUÍDOS por $pasta."
if [[ "${CONFIRMAR:-}" != "RESTAURAR" ]]; then
  read -r -p "Digite RESTAURAR para continuar: " resposta
  [[ "$resposta" == "RESTAURAR" ]] || falhar "cancelado"
fi

decifrar() { openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$1"; }

log "Parando web e api"
"${COMPOSE[@]}" stop web api

log "Restaurando MySQL"
"${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS petconnect_web;
  CREATE DATABASE petconnect_web CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  GRANT ALL ON petconnect_web.* TO \"$MYSQL_USER\"@\"%\";"'
decifrar "$pasta/mysql.sql.gz.enc" | gunzip | "${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot petconnect_web'

log "Restaurando MongoDB"
decifrar "$pasta/mongo.archive.gz.enc" | "${COMPOSE[@]}" exec -T mongo sh -c 'if [ -n "${MONGO_INITDB_ROOT_USERNAME:-}" ]; then
    set -- -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin; fi
    mongorestore --quiet --archive --gzip --drop "$@"'

log "Restaurando fotos"
"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint sh api -c 'rm -rf /data/uploads/* && mkdir -p /data/uploads' < /dev/null
decifrar "$pasta/fotos.tar.gz.enc" | "${COMPOSE[@]}" run --rm --no-deps -T --entrypoint tar api xzf - -C /data/uploads

log "Subindo api e web"
"${COMPOSE[@]}" up -d --wait api web
log "Restore concluído. Rode scripts/smoke.sh e confira o sistema."
