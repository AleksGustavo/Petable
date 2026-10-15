#!/usr/bin/env bash
# Teste de aceite da etapa 08 entre DUAS instalações já no ar (ver docs/operacao/08_GUIA_NOVO_CLIENTE.md):
#   - isolamento de volumes, redes, segredos e backups;
#   - módulos diferentes respeitados (o que A não contratou responde 403 em A e 200 em B);
#   - token de A não vale em B;
#   - modificar A não afeta B (e vice-versa);
#   - backup, teste de restore e restore real de A, sem tocar em B; backup de A recusado em B.
# Uso: scripts/homologacao.sh <idA> <idB>        (A deve ter o módulo produtos; B, produtos e clientes)
# Destrutivo SÓ nas instalações informadas (cria produtos de teste e restaura o backup de A). Use em homologação.
set -euo pipefail
export MSYS_NO_PATHCONV=1
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && { pwd -W 2>/dev/null || pwd; })"
A="${1:?informe a instalação A}"; B="${2:?informe a instalação B}"
CLI="$AQUI/cliente.sh"
CLIENTES_DIR="${CLIENTES_DIR:-$AQUI/../clientes}"

falhas=0
log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
ok() { log "PASS $*"; }
ruim() { log "FAIL $*"; falhas=$((falhas + 1)); }
confere() { if [[ "$2" == "$3" ]]; then ok "$1"; else ruim "$1 (esperado '$3', obtido '$2')"; fi; }

valor() { grep -E "^$2=" "$CLIENTES_DIR/$1/.env.prod" | head -n 1 | cut -d= -f2- | tr -d '\r'; }
# api <id> <MÉTODO> <caminho> [token] [json] → "<http>|<corpo>" (chamada interna à API da instalação, sem passar pelo proxy)
api() {
  local id="$1" metodo="$2" caminho="$3" token="${4:-}" corpo="${5:-}"
  local saida
  saida="$(printf '%s' "$corpo" | PROJETO="petconnect-$id" docker compose -f "$AQUI/../compose.prod.yaml" \
    --env-file "$CLIENTES_DIR/$id/.env.prod" -p "petconnect-$id" exec -T -e "T=$token" -e "M=$metodo" -e "P=$caminho" api sh -c '
      if [ -n "$T" ]; then H="Authorization: Bearer $T"; else H="X-Sem: 1"; fi
      curl -sS -w "|%{http_code}" -X "$M" -H "Content-Type: application/json" -H "$H" --data-binary @- "http://localhost:8080$P"')"
  printf '%s' "$saida"
}
http() { local s="$1"; printf '%s' "${s##*|}"; }
corpo() { local s="$1"; printf '%s' "${s%|*}"; }
login() { # id → token
  local r; r="$(api "$1" POST /api/v1/auth/login "" "$(printf '{"login":"%s","senha":"%s"}' "$(valor "$1" ADMIN_USERNAME)" "$(valor "$1" ADMIN_PASSWORD)")")"
  [[ "$(http "$r")" == 200 ]] || { ruim "login do ADMIN de '$1'"; return 1; }
  corpo "$r" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}
criar_categoria() { # id token → id da categoria existente
  corpo "$(api "$1" GET /api/v1/categorias-produto "$2")" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -n 1
}
criar_produto() { # id token nome
  local cat; cat="$(criar_categoria "$1" "$2")"
  http "$(api "$1" POST /api/v1/produtos "$2" "$(printf '{"nome":"%s","categoriaId":%s,"precoVenda":10.00,"estoqueMinimo":0}' "$3" "$cat")")"
}
contar() { # id token texto → quantos produtos com este nome a instalação enxerga
  corpo "$(api "$1" GET "/api/v1/produtos?busca=$3&size=50" "$2")" | grep -o "\"nome\":\"$3\"" | wc -l | tr -d ' '
}

log "== 1. Isolamento de volumes, redes, segredos e backups"
"$CLI" isolamento >/dev/null 2>&1 && ok "cliente.sh isolamento" || { ruim "cliente.sh isolamento"; "$CLI" isolamento 2>&1 | grep FAIL || true; }

TA="$(login "$A")"; TB="$(login "$B")"

log "== 2. Módulos diferentes respeitados"
modA="$(valor "$A" PETCONNECT_MODULOS)"; modB="$(valor "$B" PETCONNECT_MODULOS)"
log "A=$modA | B=$modB"
confere "A: /clientes (não contratado) → 403" "$(http "$(api "$A" GET /api/v1/clientes "$TA")")" 403
r="$(api "$A" GET /api/v1/clientes "$TA")"; [[ "$(corpo "$r")" == *MODULO_INDISPONIVEL* ]] && ok "A: corpo uniforme MODULO_INDISPONIVEL" || ruim "A: corpo do 403"
confere "B: /clientes (contratado) → 200" "$(http "$(api "$B" GET /api/v1/clientes "$TB")")" 200
confere "A: /produtos (contratado) → 200" "$(http "$(api "$A" GET /api/v1/produtos "$TA")")" 200

log "== 3. Token de A não vale em B"
confere "token de A em B → 401" "$(http "$(api "$B" GET /api/v1/produtos "$TA")")" 401
confere "token de B em A → 401" "$(http "$(api "$A" GET /api/v1/produtos "$TB")")" 401

log "== 4. Modificar A não afeta B"
sufixo="$(date +%s)"; PA="ProdutoA$sufixo"; PB="ProdutoB$sufixo"
confere "cria $PA em A" "$(criar_produto "$A" "$TA" "$PA")" 201
confere "A enxerga $PA" "$(contar "$A" "$TA" "$PA")" 1
confere "B NÃO enxerga $PA" "$(contar "$B" "$TB" "$PA")" 0
confere "cria $PB em B" "$(criar_produto "$B" "$TB" "$PB")" 201
confere "B enxerga $PB" "$(contar "$B" "$TB" "$PB")" 1
confere "A NÃO enxerga $PB" "$(contar "$A" "$TA" "$PB")" 0

log "== 5. Backup e restore de A (B intocado)"
"$CLI" backup "$A" >/dev/null 2>&1 && ok "backup de A" || ruim "backup de A"
pasta="$(ls -1d "$CLIENTES_DIR/$A"/backups/diario/*/ | sort | tail -n 1)"; pasta="${pasta%/}"
grep -q "^instalacao=$A$" "$pasta/manifesto.txt" && ok "manifesto registra a instalação '$A'" || ruim "manifesto sem a instalação"
"$CLI" restore-test "$A" >/dev/null 2>&1 && ok "restore-test de A (MySQL descartável, contagens do manifesto)" || ruim "restore-test de A"
# Backup de A não entra em B.
if CONFIRMAR=RESTAURAR "$CLI" restore "$B" "$pasta" >/dev/null 2>&1; then ruim "restore do backup de A em B foi aceito"; else ok "restore do backup de A em B recusado"; fi
confere "B continua com $PB depois da tentativa" "$(contar "$B" "$TB" "$PB")" 1

PA2="ProdutoA2$sufixo"
confere "depois do backup, cria $PA2 em A" "$(criar_produto "$A" "$TA" "$PA2")" 201
CONFIRMAR=RESTAURAR "$CLI" restore "$A" "$pasta" >/dev/null 2>&1 && ok "restore real de A" || ruim "restore real de A"
TA="$(login "$A")"
confere "A voltou a ter $PA" "$(contar "$A" "$TA" "$PA")" 1
confere "A não tem mais $PA2 (posterior ao backup)" "$(contar "$A" "$TA" "$PA2")" 0
TB="$(login "$B")"
confere "B segue com $PB" "$(contar "$B" "$TB" "$PB")" 1
confere "B segue sem $PA" "$(contar "$B" "$TB" "$PA")" 0
"$CLI" smoke "$A" >/dev/null 2>&1 && ok "smoke de A após o restore" || ruim "smoke de A após o restore"
"$CLI" smoke "$B" >/dev/null 2>&1 && ok "smoke de B" || ruim "smoke de B"

log "== 6. Upgrade e downgrade de módulos em A (B intocado)"
base="$(valor "$A" PETCONNECT_MODULOS)"
mais="$base,clientes"
"$CLI" modulos "$A" "$mais" >/dev/null 2>&1 && ok "upgrade de A para '$mais'" || ruim "upgrade de A"
TA="$(login "$A")"
confere "A: /clientes passou a responder 200" "$(http "$(api "$A" GET /api/v1/clientes "$TA")")" 200
confere "env de A registra o upgrade" "$(valor "$A" PETCONNECT_MODULOS)" "$mais"
# Lista inválida (pdv sem caixa) é recusada ANTES de reiniciar qualquer coisa.
if "$CLI" modulos "$A" "produtos,pdv" >/dev/null 2>&1; then ruim "lista inválida aceita"; else ok "lista inválida recusada pelo validador"; fi
confere "env de A inalterado após lista inválida" "$(valor "$A" PETCONNECT_MODULOS)" "$mais"
# Downgrade com caixa aberto: a API recusa a partida, o script volta para a lista anterior e nenhum dado é tocado.
api "$A" POST /api/v1/caixas/abrir "$TA" '{"fundoAbertura":50.00}' >/dev/null || true
if "$CLI" modulos "$A" "produtos,estoque,clientes" >/dev/null 2>&1; then ruim "downgrade com caixa aberto foi aceito"; else ok "downgrade com caixa aberto recusado e revertido"; fi
confere "env de A voltou à lista anterior" "$(valor "$A" PETCONNECT_MODULOS)" "$mais"
TA="$(login "$A")"
confere "A segue servindo o PDV/caixa" "$(http "$(api "$A" GET /api/v1/caixas/atual "$TA")")" 200
confere "A segue com $PA (dados preservados)" "$(contar "$A" "$TA" "$PA")" 1
TB="$(login "$B")"
confere "B não foi tocado" "$(contar "$B" "$TB" "$PB")" 1
trilha="$(PROJETO="petconnect-$A" docker compose -f "$AQUI/../compose.prod.yaml" --env-file "$CLIENTES_DIR/$A/.env.prod" -p "petconnect-$A" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N petconnect_web -e "select count(*) from modulo_instalacao_historico"' | tr -d '')"
[[ "${trilha:-0}" -ge 2 ]] && ok "histórico de módulos registrado ($trilha linhas)" || ruim "histórico de módulos ($trilha)"

[[ "$falhas" -eq 0 ]] || { log "ERRO: $falhas verificação(ões) falharam"; exit 1; }
log "Aceite OK: $A e $B isolados, módulos respeitados, backup/restore demonstrado"
