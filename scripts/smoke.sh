#!/usr/bin/env bash
# Smoke test pós-deploy (etapa 09): página de login pelo proxy, cabeçalhos de segurança,
# readiness da API (porta interna) e login do usuário técnico SMOKE_USERNAME.
source "$(dirname "$0")/_comum.sh"
set +e   # cada verificação é contada; o resultado final decide o código de saída

URL="${SMOKE_URL:-https://$DOMINIO}"
CURL=(curl -sS --max-time 15)
[[ "${CADDY_TLS:-}" == "internal" ]] && CURL+=(-k)   # teste local com certificado interno
falhas=0
resultado() { if [[ "$2" == 0 ]]; then log "PASS $1"; else log "FAIL $1"; falhas=$((falhas + 1)); fi; }

cabecalhos="$("${CURL[@]}" -D - -o "$NULO" "$URL/login" | tr -d '\r' || true)"
[[ "$cabecalhos" == HTTP/*" 200"* ]]; resultado "página de login responde 200" $?
grep -qi '^content-security-policy:.*nonce-' <<< "$cabecalhos"; resultado "CSP com nonce" $?
grep -qi '^strict-transport-security: max-age=' <<< "$cabecalhos"; resultado "HSTS" $?
grep -qi '^x-request-id: ' <<< "$cabecalhos"; resultado "X-Request-Id" $?

if [[ "$URL" == https://* ]]; then
  http_url="${SMOKE_HTTP_URL:-http://${URL#https://}}"
  codigo="$(curl -s -o "$NULO" -w '%{http_code}' --max-time 15 "$http_url/login" || true)"
  [[ "$codigo" == 308 ]]; resultado "HTTP redireciona para HTTPS (308)" $?
fi

"${COMPOSE[@]}" exec -T api curl -fsS http://localhost:9090/actuator/health/readiness > /dev/null
resultado "API readiness (porta interna 9090)" $?

if [[ -n "${SMOKE_USERNAME:-}" && -n "${SMOKE_PASSWORD:-}" ]]; then
  # Senha pela entrada padrão (não aparece na lista de processos). Não use aspas nem \ na senha de smoke.
  printf '{"login":"%s","senha":"%s"}' "$SMOKE_USERNAME" "$SMOKE_PASSWORD"     | "${COMPOSE[@]}" exec -T api curl -fsS -o /dev/null -X POST -H "Content-Type: application/json"         --data-binary @- http://localhost:8080/api/v1/auth/login
  resultado "login do usuário de smoke" $?
else
  log "AVISO: SMOKE_USERNAME/SMOKE_PASSWORD não definidos; login não testado"
fi

[[ "$falhas" -eq 0 ]] || falhar "smoke test: $falhas verificação(ões) falharam"
log "Smoke test OK"
