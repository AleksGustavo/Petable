#!/usr/bin/env bash
# Teste de restore (DA-09): restaura o backup num MySQL DESCARTÁVEL e compara com o manifesto.
# Não toca na produção. Uso: scripts/restore-test.sh [pasta-do-backup]   (padrão: o diário mais recente)
# Cron semanal sugerido: 0 4 * * 1 /opt/petconnect/scripts/restore-test.sh >> /var/log/petconnect-restore-test.log 2>&1
source "$(dirname "$0")/_comum.sh"

: "${BACKUP_PASSPHRASE:?defina BACKUP_PASSPHRASE}"
BACKUP_DIR="${BACKUP_DIR:-$RAIZ/backups}"
pasta="${1:-$(ls -1d "$BACKUP_DIR"/diario/*/ 2>/dev/null | sort | tail -n 1)}"
pasta="${pasta%/}"
[[ -f "$pasta/manifesto.txt" ]] || falhar "backup não encontrado: ${pasta:-<nenhum>}"

decifrar() { openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$1"; }

log "Conferindo integridade (sha256) de $pasta"
( cd "$pasta" && grep -E '^[0-9a-f]{64} ' manifesto.txt | sha256sum -c --quiet - ) || falhar "hash não confere"
decifrar "$pasta/mongo.archive.gz.enc" | gzip -t || falhar "arquivo do MongoDB corrompido"
decifrar "$pasta/fotos.tar.gz.enc" | tar tzf - > /dev/null || falhar "arquivo das fotos corrompido"

container="petconnect-restore-teste-$$"
senha="$(openssl rand -hex 16)"
trap 'docker rm -f "$container" > /dev/null 2>&1 || true' EXIT
docker run -d --name "$container" -e MYSQL_ROOT_PASSWORD="$senha" -e MYSQL_DATABASE=petconnect_web mysql:8.4 > /dev/null
log "MySQL descartável iniciado; aguardando..."
for _ in $(seq 1 60); do
  docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "SELECT 1" petconnect_web' > /dev/null 2>&1 && break
  sleep 3
done

decifrar "$pasta/mysql.sql.gz.enc" | gunzip | docker exec -i "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot petconnect_web'
log "Dump importado"

falhas=0
while IFS='=' read -r chave esperado; do
  esperado="${esperado%$'\r'}"
  if [[ "$chave" == tabela.* ]]; then
    tabela="${chave#tabela.}"
    obtido="$(docker exec "$container" sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -N petconnect_web -e 'SELECT COUNT(*) FROM $tabela'" | tr -d '\r')"
  elif [[ "$chave" == flyway ]]; then
    obtido="$(docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N petconnect_web -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1"' | tr -d '\r')"
  else
    continue
  fi
  if [[ "$obtido" == "$esperado" ]]; then
    log "PASS $chave = $obtido"
  else
    log "FAIL $chave: esperado $esperado, restaurado $obtido"
    falhas=$((falhas + 1))
  fi
done < "$pasta/manifesto.txt"

[[ "$falhas" -eq 0 ]] || falhar "$falhas conferência(s) falharam"
log "Restore testado com sucesso: $pasta"
