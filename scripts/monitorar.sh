#!/usr/bin/env bash
# Alertas mínimos (DA-13). Cron a cada 5 min:
#   */5 * * * * /opt/petconnect/scripts/monitorar.sh >> /var/log/petconnect-monitor.log 2>&1
# Envia para ALERTA_WEBHOOK_URL (Discord: "content"; Slack: "text"). Mensagens sem dado pessoal.
source "$(dirname "$0")/_comum.sh"

BACKUP_DIR="${BACKUP_DIR:-$RAIZ/backups}"
alertas=()

saude() {
  local id
  id="$("${COMPOSE[@]}" ps -q "$1" 2>/dev/null)"
  if [[ -z "$id" ]]; then echo "parado"; return; fi
  docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null || echo "desconhecido"
}
[[ "$(saude api)" == healthy ]] || alertas+=("API_FORA: api está $(saude api)")
[[ "$(saude web)" == healthy ]] || alertas+=("WEB_FORA: web está $(saude web)")

uso="$(df -P "$BACKUP_DIR" 2>/dev/null | awk 'NR==2 {gsub("%", "", $5); print $5}')"
[[ -n "$uso" && "$uso" -gt 85 ]] && alertas+=("DISCO_85: disco em ${uso}%")

if [[ ! -f "$BACKUP_DIR/ultimo-ok" ]] || (( $(date +%s) - $(cat "$BACKUP_DIR/ultimo-ok") > 26 * 3600 )); then
  alertas+=("BACKUP_ATRASADO: nenhum backup concluído nas últimas 26 h")
fi

erros="$("${COMPOSE[@]}" exec -T caddy sh -c 'tail -n 5000 /var/log/caddy/acesso.log 2>/dev/null' \
  | awk -v agora="$(date +%s)" 'match($0, /"ts":[0-9.]+/) { ts = substr($0, RSTART + 5, RLENGTH - 5) }
      /"status":5[0-9][0-9]/ && ts > agora - 300 { n++ } END { print n + 0 }' || echo 0)"
[[ "$erros" -gt 20 ]] && alertas+=("ERROS_5XX: $erros respostas 5xx nos últimos 5 min")

if [[ ${#alertas[@]} -eq 0 ]]; then
  log "OK"
  exit 0
fi
mensagem="PetConnect ($(hostname)): $(printf '%s; ' "${alertas[@]}")"
mensagem="${mensagem%; }"
log "ALERTA $mensagem"
if [[ -n "${ALERTA_WEBHOOK_URL:-}" ]]; then
  corpo="$(printf '{"content":"%s","text":"%s"}' "$mensagem" "$mensagem")"
  curl -fsS -m 10 -H 'Content-Type: application/json' -d "$corpo" "$ALERTA_WEBHOOK_URL" > /dev/null || log "falha ao enviar alerta"
fi
exit 1
