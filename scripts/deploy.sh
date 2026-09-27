#!/usr/bin/env bash
# Deploy por tag (DA-07, DA-21): backup → troca TAG → pull → up --wait → smoke → (rollback se falhar).
# Uso no servidor: scripts/deploy.sh 1.2.3      (chamado pelo workflow deploy.yml via SSH)
source "$(dirname "$0")/_comum.sh"

nova="${1:?informe a tag (ex.: 1.2.3 ou sha-abc1234)}"
[[ "$nova" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || falhar "tag inválida: $nova"
anterior="$(grep '^TAG=' "$ENV_FILE" | cut -d= -f2 | tr -d '\r')"
log "Deploy $anterior → $nova"

trocar_tag() { sed -i "s/^TAG=.*/TAG=$1/" "$ENV_FILE"; export TAG="$1"; }

# R-05: sempre backup antes (a migration do Flyway roda na subida da API).
"$RAIZ/scripts/backup.sh"

trocar_tag "$nova"
if "${COMPOSE[@]}" pull api web && "${COMPOSE[@]}" up -d --wait --wait-timeout 300 && "$RAIZ/scripts/smoke.sh"; then
  echo "$nova" > "$RAIZ/.ultimo-deploy"
  log "Deploy $nova concluído"
  exit 0
fi

# R-06: smoke falhou → volta para a tag anterior. Banco: só por restore (ver runbook de rollback).
log "Deploy $nova FALHOU; voltando para $anterior"
trocar_tag "$anterior"
"${COMPOSE[@]}" up -d --wait --wait-timeout 300 || true
"$RAIZ/scripts/smoke.sh" || log "ATENÇÃO: smoke da versão anterior também falhou — siga docs/runbooks/rollback.md"
exit 1
