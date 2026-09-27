#!/usr/bin/env bash
# Funções e variáveis comuns dos scripts de operação (etapa 09). Não execute direto.
#
#   ENV_FILE      arquivo de ambiente (padrão: .env.prod na raiz)
#   COMPOSE_FILE  compose a usar      (padrão: compose.prod.yaml)
#   PROJETO       nome do projeto Compose (opcional; usado na validação local)
# Variáveis já definidas no ambiente têm prioridade sobre o arquivo.

set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash (Windows): não converter caminhos como /data/uploads

# pwd -W só existe no Git Bash (Windows): lá o Docker precisa do caminho C:/...
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && { pwd -W 2>/dev/null || pwd; })"
ENV_FILE="${ENV_FILE:-$RAIZ/.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-$RAIZ/compose.prod.yaml}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Arquivo de ambiente não encontrado: $ENV_FILE" >&2
  exit 1
fi

# Leitura segura do .env (não é bash: valores podem ter espaços e "<>").
while IFS= read -r linha || [[ -n "$linha" ]]; do
  [[ "$linha" =~ ^[[:space:]]*# || "$linha" != *=* ]] && continue
  chave="${linha%%=*}"
  valor="${linha#*=}"
  valor="${valor%$'\r'}"
  [[ "$chave" =~ ^[A-Z_][A-Z0-9_]*$ ]] || continue
  [[ -z "${!chave+x}" ]] && export "$chave=$valor"
done < "$ENV_FILE"

# curl.exe do Windows não conhece /dev/null.
if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then NULO=NUL; else NULO=/dev/null; fi
export NULO

COMPOSE=(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE")
[[ -n "${PROJETO:-}" ]] && COMPOSE+=(-p "$PROJETO")

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
falhar() { log "ERRO: $*" >&2; exit 1; }
