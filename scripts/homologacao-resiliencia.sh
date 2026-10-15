#!/usr/bin/env bash
# Resiliência da etapa 09 numa instalação no ar: repetir uma operação e simular indisponibilidade da API NÃO duplica venda, baixa de
# estoque nem lançamento financeiro (a chave de idempotência sobrevive à reinicialização).
# Uso: scripts/homologacao-resiliencia.sh <id>      (precisa de produtos, estoque, caixa e pdv; destrutivo só nessa instalação)
set -euo pipefail
export MSYS_NO_PATHCONV=1
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && { pwd -W 2>/dev/null || pwd; })"
ID="${1:?informe a instalação}"
CLIENTES_DIR="${CLIENTES_DIR:-$AQUI/../clientes}"
ENVF="$CLIENTES_DIR/$ID/.env.prod"

falhas=0
log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
ok() { log "PASS $*"; }
ruim() { log "FAIL $*"; falhas=$((falhas + 1)); }
confere() { if [[ "$2" == "$3" ]]; then ok "$1"; else ruim "$1 (esperado '$3', obtido '$2')"; fi; }
valor() { grep -E "^$1=" "$ENVF" | head -n 1 | cut -d= -f2- | tr -d '\r'; }
dc() { docker compose -f "$AQUI/../compose.prod.yaml" --env-file "$ENVF" -p "petconnect-$ID" "$@"; }

# api <MÉTODO> <caminho> <token> <json> [chave-de-idempotência] → "<corpo>|<http>"
api() {
  printf '%s' "$4" | dc exec -T -e "T=$3" -e "M=$1" -e "P=$2" -e "K=${5:-}" api sh -c '
    if [ -n "$K" ]; then KH="Idempotency-Key: $K"; else KH="X-Sem: 1"; fi
    if [ -n "$T" ]; then AH="Authorization: Bearer $T"; else AH="X-Anonimo: 1"; fi
    curl -sS -w "|%{http_code}" -X "$M" -H "Content-Type: application/json" -H "$AH" -H "$KH" --data-binary @- "http://localhost:8080$P"'
}
http() { printf '%s' "${1##*|}"; }
corpo() { printf '%s' "${1%|*}"; }
idde() { sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -n 1; }
sql() { dc exec -T mysql sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -N petconnect_web -e \"$1\"" | tr -d '\r'; }
aguardar_api() { dc up -d --wait --wait-timeout 300 api >/dev/null 2>&1; }

login() {
  corpo "$(api POST /api/v1/auth/login "" "$(printf '{"login":"%s","senha":"%s"}' "$(valor ADMIN_USERNAME)" "$(valor ADMIN_PASSWORD)")")" \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

T="$(login)"; [[ -n "$T" ]] || { log "FAIL login"; exit 1; }
cat="$(corpo "$(api GET /api/v1/categorias-produto "$T" "")" | idde)"
nome="Resiliencia$(date +%s)"
api POST /api/v1/produtos "$T" "$(printf '{"nome":"%s","categoriaId":%s,"precoVenda":10.00,"estoqueMinimo":0}' "$nome" "$cat")" >/dev/null
produto="$(sql "select id from produto where nome='$nome'")"
[[ -n "$produto" ]] && ok "produto de teste criado (#$produto)" || { ruim "produto"; exit 1; }
confere "saldo inicial por ajuste" "$(http "$(api POST /api/v1/produtos/$produto/ajustes-estoque "$T" '{"quantidade":5,"motivo":"Saldo inicial da resiliência"}')")" 201
api POST /api/v1/caixas/abrir "$T" '{"fundoAbertura":10.00}' "" >/dev/null || true

# A chave de idempotência da API é um UUID.
h="$(openssl rand -hex 16)"
chave="${h:0:8}-${h:8:4}-4${h:13:3}-a${h:17:3}-${h:20:12}"
venda_json="$(printf '{"itens":[{"produtoId":%s,"quantidade":2}],"desconto":0.00,"formaPagamento":"PIX","valorRecebido":null}' "$produto")"

log "== 1. Repetir a mesma venda (mesma chave)"
r1="$(api POST /api/v1/vendas "$T" "$venda_json" "$chave")"
confere "primeira venda → 201" "$(http "$r1")" 201
v1="$(corpo "$r1" | idde)"
r2="$(api POST /api/v1/vendas "$T" "$venda_json" "$chave")"
[[ "$(http "$r2")" == 200 || "$(http "$r2")" == 201 ]] && ok "repetição aceita (HTTP $(http "$r2"))" || ruim "repetição (HTTP $(http "$r2"))"
confere "mesma venda devolvida" "$(corpo "$r2" | idde)" "$v1"

log "== 2. API indisponível no meio e retentativa"
dc stop api >/dev/null 2>&1 && ok "API parada (simula indisponibilidade)" || ruim "parar API"
aguardar_api && ok "API de volta" || ruim "API de volta"
T="$(login)"
r3="$(api POST /api/v1/vendas "$T" "$venda_json" "$chave")"
[[ "$(http "$r3")" == 200 || "$(http "$r3")" == 201 ]] && ok "retentativa depois do reinício aceita (HTTP $(http "$r3"))" || ruim "retentativa (HTTP $(http "$r3"))"
confere "ainda a mesma venda" "$(corpo "$r3" | idde)" "$v1"

log "== 3. Nada duplicado no banco"
v1="$(sql "select v.id from venda v join item_venda i on i.venda_id=v.id where i.produto_id=$produto limit 1")"
confere "uma só venda para o produto" "$(sql "select count(*) from venda v join item_venda i on i.venda_id=v.id where i.produto_id=$produto")" 1
confere "baixa de estoque uma vez só (saldo 3)" "$(sql "select quantidade_estoque from produto where id=$produto")" 3
confere "um só movimento de saída" "$(sql "select count(*) from movimentacao_estoque where produto_id=$produto and tipo='SAIDA_VENDA'")" 1
confere "um só lançamento financeiro da venda" "$(sql "select count(*) from movimentacao_financeira where chave_origem='VENDA:$v1'")" 1

log "== 4. Cancelamento repetido também não duplica"
c1="$(api POST /api/v1/vendas/$v1/cancelar "$T" '{"motivo":"Cancelamento de resiliência"}')"
confere "cancelamento → 200" "$(http "$c1")" 200
confere "segundo cancelamento → 409" "$(http "$(api POST /api/v1/vendas/$v1/cancelar "$T" '{"motivo":"Cancelamento repetido"}')")" 409
confere "saldo recomposto uma vez (5)" "$(sql "select quantidade_estoque from produto where id=$produto")" 5
confere "um só estorno financeiro" "$(sql "select count(*) from movimentacao_financeira where chave_origem='VENDA_ESTORNO:$v1'")" 1

[[ "$falhas" -eq 0 ]] || { log "ERRO: $falhas verificação(ões) falharam"; exit 1; }
log "Resiliência OK em '$ID'"
