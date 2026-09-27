#!/usr/bin/env bash
# Backup cifrado do PetConnect (DA-08): MySQL + MongoDB + fotos, com manifesto de contagens.
# Uso (cron diário, 02:30):  30 2 * * * /opt/petconnect/scripts/backup.sh >> /var/log/petconnect-backup.log 2>&1
# Sai com código ≠ 0 em qualquer falha (o monitorar.sh alerta se não houver backup em 26 h).
source "$(dirname "$0")/_comum.sh"

: "${BACKUP_PASSPHRASE:?defina BACKUP_PASSPHRASE (sem ela não há restore)}"
BACKUP_DIR="${BACKUP_DIR:-$RAIZ/backups}"
umask 077
carimbo="$(date +%Y%m%d-%H%M%S)"
destino="$BACKUP_DIR/diario/$carimbo"
mkdir -p "$destino"
# Backup incompleto não pode parecer válido para o restore-test nem para a retenção.
trap 'rm -rf -- "$destino"; log "ERRO: backup falhou; pasta parcial removida" >&2' ERR

cifrar() { openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_PASSPHRASE -out "$1"; }

log "Backup $carimbo → $destino"

"${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction \
    --routines --triggers --default-character-set=utf8mb4 petconnect_web' | gzip -9 | cifrar "$destino/mysql.sql.gz.enc"
log "MySQL ok"

"${COMPOSE[@]}" exec -T mongo sh -c 'if [ -n "${MONGO_INITDB_ROOT_USERNAME:-}" ]; then
    set -- -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin; fi
    mongodump --quiet --archive --gzip --db petconnect_web "$@"' | cifrar "$destino/mongo.archive.gz.enc"
log "MongoDB ok"

"${COMPOSE[@]}" exec -T api tar czf - -C /data/uploads . | cifrar "$destino/fotos.tar.gz.enc"
log "Fotos ok"

# Manifesto (sem dado pessoal): contagens usadas pelo restore-test.sh.
"${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N petconnect_web -e "
  SELECT CONCAT(\"tabela.\", t, \"=\", n) FROM (
    SELECT \"usuario\" t, COUNT(*) n FROM usuario UNION ALL SELECT \"cliente\", COUNT(*) FROM cliente
    UNION ALL SELECT \"pet\", COUNT(*) FROM pet UNION ALL SELECT \"produto\", COUNT(*) FROM produto
    UNION ALL SELECT \"venda\", COUNT(*) FROM venda UNION ALL SELECT \"item_venda\", COUNT(*) FROM item_venda
    UNION ALL SELECT \"agendamento\", COUNT(*) FROM agendamento
    UNION ALL SELECT \"movimentacao_financeira\", COUNT(*) FROM movimentacao_financeira) x;
  SELECT CONCAT(\"flyway=\", MAX(CAST(version AS UNSIGNED))) FROM flyway_schema_history WHERE success = 1;"' \
  | tr -d '\r' > "$destino/manifesto.txt"
( cd "$destino" && sha256sum ./*.enc >> manifesto.txt )
log "Manifesto: $(grep -c '^tabela\.' "$destino/manifesto.txt") tabelas conferíveis"

# Retenção: 7 diários, 4 semanais (domingo), 6 mensais (dia 1).
if [[ "$(date +%u)" == "7" ]]; then mkdir -p "$BACKUP_DIR/semanal" && cp -r "$destino" "$BACKUP_DIR/semanal/"; fi
if [[ "$(date +%d)" == "01" ]]; then mkdir -p "$BACKUP_DIR/mensal" && cp -r "$destino" "$BACKUP_DIR/mensal/"; fi
podar() {
  local pasta="$1" manter="$2"
  [[ -d "$pasta" ]] || return 0
  ls -1d "$pasta"/*/ 2>/dev/null | sort | head -n "-$manter" | while read -r antigo; do rm -rf -- "$antigo"; done
}
podar "$BACKUP_DIR/diario" 7
podar "$BACKUP_DIR/semanal" 4
podar "$BACKUP_DIR/mensal" 6

if [[ -n "${BACKUP_REMOTO:-}" ]]; then
  rclone copy "$BACKUP_DIR" "$BACKUP_REMOTO" --quiet && log "Cópia off-site ok"
fi

trap - ERR
date +%s > "$BACKUP_DIR/ultimo-ok"
log "Backup concluído: $destino"
