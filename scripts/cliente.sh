#!/usr/bin/env bash
# Provisionamento e operação por cliente (etapa 08). Uma instalação = um projeto Compose (petconnect-<id>) com banco,
# fotos, redes, logs, segredos e backups próprios. Sem multi-tenant em banco compartilhado.
#
#   scripts/cliente.sh novo <id> --dominio D --modulos L [--tls EMAIL|internal] [--tag T] [--bind-ip IP]
#                               [--http-port N --https-port N] [--admin-email E] [--smtp-host H]
#   scripts/cliente.sh validar <id>          confere o .env.prod (placeholders, segredos, duplicidade entre clientes)
#   scripts/cliente.sh subir <id>            valida e sobe a instalação (migrations rodam na subida da API)
#   scripts/cliente.sh smoke-usuario <id>    cria o usuário técnico de smoke (VENDAS) usando o ADMIN inicial
#   scripts/cliente.sh smoke|backup|restore-test|restore|monitorar <id> [args]
#   scripts/cliente.sh modulos <id> <lista>  upgrade/downgrade de módulos: backup → valida → reinicia → smoke → (volta se falhar)
#   scripts/cliente.sh rotacionar-jwt <id>   troca o JWT_SECRET (derruba as sessões) e reinicia
#   scripts/cliente.sh status|logs|parar <id>
#   scripts/cliente.sh listar                instalações, domínio, módulos e estado (nunca mostra segredo)
#   scripts/cliente.sh isolamento            prova que as instalações não compartilham banco, volume, rede, segredo ou backup
#
# Diretório das instalações: CLIENTES_DIR (padrão: <raiz>/clientes, fora do git). Cada uma: .env.prod (chmod 600) e backups/.
set -euo pipefail
export MSYS_NO_PATHCONV=1

# pwd -W só existe no Git Bash (Windows): lá o Docker precisa do caminho C:/...
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && { pwd -W 2>/dev/null || pwd; })"
RAIZ="$(cd "$AQUI/.." && { pwd -W 2>/dev/null || pwd; })"
CLIENTES_DIR="${CLIENTES_DIR:-$RAIZ/clientes}"
TEMPLATE="$RAIZ/deploy/cliente.env.template"
CATALOGO="$RAIZ/backend/src/main/resources/modulos/catalogo.json"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
falhar() { log "ERRO: $*" >&2; exit 1; }

id_valido() { [[ "$1" =~ ^[a-z][a-z0-9-]{1,30}$ ]]; }
dir_de() { echo "$CLIENTES_DIR/$1"; }
env_de() { echo "$CLIENTES_DIR/$1/.env.prod"; }

# Lê UMA chave de um .env sem executá-lo (valores podem ter espaços e "<>").
valor() { # arquivo chave
  local linha
  linha="$(grep -E "^$2=" "$1" 2>/dev/null | head -n 1 || true)"
  linha="${linha#*=}"
  printf '%s' "${linha%$'\r'}"
}

instalacoes() { # ids existentes
  [[ -d "$CLIENTES_DIR" ]] || return 0
  local d
  for d in "$CLIENTES_DIR"/*/; do
    [[ -f "${d}.env.prod" ]] && basename "$d"
  done
  return 0
}

segredo() { openssl rand -hex "${1:-24}"; }
# Política de senha da API: 10–72, maiúscula, minúscula, número e especial.
senha_politica() { printf 'Aa1-%s' "$(openssl rand -hex 12)"; }

python_bin() { command -v python3 || command -v python || true; }

# Valida a lista de módulos contra o catálogo (ids conhecidos + dependências). Sem Python, o boot da API recusa de qualquer forma.
validar_modulos() { # lista
  local lista="$1" py
  [[ -n "$lista" ]] || { echo "PETCONNECT_MODULOS vazio"; return 1; }
  [[ "$lista" == "todos" ]] && return 0
  py="$(python_bin)"
  if [[ -z "$py" || ! -f "$CATALOGO" ]]; then
    log "AVISO: sem python/catálogo; a lista de módulos será validada pela API no boot (que recusa lista inválida)"
    return 0
  fi
  PYTHONIOENCODING=utf-8 "$py" -I - "$(cygpath -m "$CATALOGO" 2>/dev/null || echo "$CATALOGO")" "$lista" <<'PY'
import json, re, sys
cat = json.load(open(sys.argv[1], encoding="utf-8"))
ids = {m["id"]: m for m in cat["modulos"]}
pedidos = [x for x in re.split(r"[,;\s]+", sys.argv[2]) if x]
erros = []
for x in pedidos:
    if x not in ids:
        erros.append(f"módulo desconhecido: {x}")
for x in pedidos:
    for dep in ids.get(x, {}).get("requer", []):
        if dep not in pedidos:
            erros.append(f"'{x}' exige '{dep}'")
if erros:
    print("; ".join(erros)); sys.exit(1)
PY
}

# ------------------------------------------------------------------ novo
cmd_novo() {
  local id="${1:-}"; shift || true
  id_valido "$id" || falhar "identificador inválido (minúsculas, números e hífen; 2–31 caracteres): '$id'"
  local dominio="" modulos="" tls="internal" tag="" bind="0.0.0.0" http=80 https=443 admin_email="" smtp="smtp.invalid"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dominio) dominio="$2"; shift 2 ;;
      --modulos) modulos="$2"; shift 2 ;;
      --tls) tls="$2"; shift 2 ;;
      --tag) tag="$2"; shift 2 ;;
      --bind-ip) bind="$2"; shift 2 ;;
      --http-port) http="$2"; shift 2 ;;
      --https-port) https="$2"; shift 2 ;;
      --admin-email) admin_email="$2"; shift 2 ;;
      --smtp-host) smtp="$2"; shift 2 ;;
      *) falhar "opção desconhecida: $1" ;;
    esac
  done
  [[ -n "$dominio" ]] || falhar "informe --dominio"
  [[ "$dominio" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] || falhar "domínio inválido: $dominio"
  [[ -n "$modulos" ]] || falhar "informe --modulos (todos ou lista; ex.: produtos,estoque,caixa,pdv)"
  [[ "$http" =~ ^[0-9]+$ && "$https" =~ ^[0-9]+$ ]] || falhar "portas devem ser numéricas"
  local erro; erro="$(validar_modulos "$modulos" 2>&1)" || falhar "módulos: $erro"
  [[ -n "$tag" ]] || tag="$(cat "$RAIZ/.ultimo-deploy" 2>/dev/null || echo local)"
  [[ "$tag" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || falhar "tag inválida: $tag"
  [[ -n "$admin_email" ]] || admin_email="admin@$dominio"

  local d; d="$(dir_de "$id")"
  [[ ! -e "$d" ]] || falhar "a instalação '$id' já existe em $d (nada foi alterado)"
  # Domínio e portas não podem colidir com outra instalação.
  local outro
  for outro in $(instalacoes); do
    [[ "$(valor "$(env_de "$outro")" DOMINIO)" != "$dominio" ]] || falhar "domínio $dominio já é da instalação '$outro'"
    if [[ "$(valor "$(env_de "$outro")" BIND_IP)" == "$bind" && "$(valor "$(env_de "$outro")" HTTPS_PORT)" == "$https" ]]; then
      falhar "$bind:$https já é usado pela instalação '$outro' (use outro --bind-ip ou --https-port)"
    fi
  done

  umask 077
  mkdir -p "$d/backups"
  sed -e "s|@ID@|$id|g" -e "s|@DOMINIO@|$dominio|g" -e "s|@TLS@|$tls|g" -e "s|@BIND_IP@|$bind|g" \
      -e "s|@HTTP_PORT@|$http|g" -e "s|@HTTPS_PORT@|$https|g" -e "s|@TAG@|$tag|g" -e "s|@MODULOS@|$modulos|g" \
      -e "s|@ADMIN_USUARIO@|admin|g" -e "s|@ADMIN_EMAIL@|$admin_email|g" -e "s|@SMTP_HOST@|$smtp|g" \
      "$TEMPLATE" > "$d/.env.prod.tmp"
  # Cada @GERAR@ vira um valor próprio (linha a linha), nunca repetido.
  : > "$d/.env.prod"
  local linha
  while IFS= read -r linha || [[ -n "$linha" ]]; do
    case "$linha" in
      *@GERAR_SENHA@*) linha="${linha//@GERAR_SENHA@/$(senha_politica)}" ;;
      *@GERAR@*) linha="${linha//@GERAR@/$(segredo 24)}" ;;
    esac
    printf '%s\n' "$linha" >> "$d/.env.prod"
  done < "$d/.env.prod.tmp"
  rm -f -- "$d/.env.prod.tmp"
  chmod 600 "$d/.env.prod" 2>/dev/null || true
  chmod 700 "$d" 2>/dev/null || true
  log "Instalação '$id' criada em $d (segredos gerados só neste arquivo; guarde uma cópia no cofre)"
  cmd_validar "$id"
  log "Próximo passo: scripts/cliente.sh subir $id && scripts/cliente.sh smoke-usuario $id && scripts/cliente.sh smoke $id"
}

# ------------------------------------------------------------------ validar
cmd_validar() {
  local id="${1:?informe o id}"; id_valido "$id" || falhar "id inválido"
  local f; f="$(env_de "$id")"; [[ -f "$f" ]] || falhar "instalação '$id' não existe"
  local falhas=0
  erro() { log "FAIL $*"; falhas=$((falhas + 1)); }
  local k v
  for k in INSTALACAO_ID DOMINIO CADDY_TLS TAG PETCONNECT_MODULOS MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD MONGO_USER \
           MONGO_PASSWORD JWT_SECRET ADMIN_USERNAME ADMIN_EMAIL SMTP_HOST EMAIL_REMETENTE BACKUP_PASSPHRASE; do
    v="$(valor "$f" "$k")"
    if [[ -z "$v" ]]; then erro "$k vazio"; elif [[ "$v" =~ @[A-Z_]+@ || "$v" =~ \<[A-Z_]+\> ]]; then erro "$k ainda é um marcador"; fi
  done
  [[ "$(valor "$f" INSTALACAO_ID)" == "$id" ]] || erro "INSTALACAO_ID difere do diretório"
  [[ "$(valor "$f" TAG)" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || erro "TAG inválida"
  local jwt; jwt="$(valor "$f" JWT_SECRET)"; [[ ${#jwt} -ge 32 ]] || erro "JWT_SECRET com menos de 32 caracteres"
  local pass; pass="$(valor "$f" BACKUP_PASSPHRASE)"; [[ ${#pass} -ge 24 ]] || erro "BACKUP_PASSPHRASE com menos de 24 caracteres"
  local modulos; modulos="$(valor "$f" PETCONNECT_MODULOS)"
  local msg; msg="$(validar_modulos "$modulos" 2>&1)" || erro "módulos: $msg"
  if [[ "$(uname -s)" == Linux ]]; then
    local modo; modo="$(stat -c %a "$f")"; [[ "$modo" == 600 ]] || erro ".env.prod com permissão $modo (esperado 600)"
  fi
  # Nada pode ser igual ao de outra instalação.
  local outro
  for outro in $(instalacoes); do
    [[ "$outro" == "$id" ]] && continue
    local g; g="$(env_de "$outro")"
    for k in JWT_SECRET MYSQL_PASSWORD MYSQL_ROOT_PASSWORD MONGO_PASSWORD BACKUP_PASSPHRASE ADMIN_PASSWORD SMOKE_PASSWORD DOMINIO; do
      [[ -n "$(valor "$f" "$k")" && "$(valor "$f" "$k")" == "$(valor "$g" "$k")" ]] && erro "$k repetido na instalação '$outro'"
    done
    if [[ "$(valor "$f" BIND_IP)" == "$(valor "$g" BIND_IP)" && "$(valor "$f" HTTPS_PORT)" == "$(valor "$g" HTTPS_PORT)" ]]; then
      erro "mesmo endereço $(valor "$f" BIND_IP):$(valor "$f" HTTPS_PORT) da instalação '$outro'"
    fi
  done
  [[ "$falhas" -eq 0 ]] || falhar "$falhas problema(s) em $f"
  log "PASS validação de '$id' ($modulos)"
}

# ------------------------------------------------------------------ execução com o ambiente da instalação
usar() { # id → exporta variáveis para os scripts existentes
  local id="${1:?informe o id}"; id_valido "$id" || falhar "id inválido"
  [[ -f "$(env_de "$id")" ]] || falhar "instalação '$id' não existe"
  export ENV_FILE="$(env_de "$id")" PROJETO="petconnect-$id" BACKUP_DIR="$(dir_de "$id")/backups"
  # Windows (Git Bash): o Docker precisa de caminho C:/...
  if pwd -W >/dev/null 2>&1; then
    ENV_FILE="$(cd "$(dir_de "$id")" && pwd -W)/.env.prod"; BACKUP_DIR="$(cd "$(dir_de "$id")" && pwd -W)/backups"
  fi
}

executar() { # script id args...
  local script="$1" id="$2"; shift 2
  usar "$id"
  "$AQUI/$script" "$@"
}

compor() { # id args...
  local id="$1"; shift
  usar "$id"
  docker compose -f "$RAIZ/compose.prod.yaml" --env-file "$ENV_FILE" -p "$PROJETO" "$@"
}

cmd_subir() {
  local id="${1:?informe o id}"
  cmd_validar "$id"
  compor "$id" up -d --wait --wait-timeout 300
  log "Instalação '$id' no ar. Rode: scripts/cliente.sh smoke-usuario $id && scripts/cliente.sh smoke $id"
}

cmd_smoke_usuario() {
  local id="${1:?informe o id}" f; f="$(env_de "$id")"
  local au ap su sp
  au="$(valor "$f" ADMIN_USERNAME)"; ap="$(valor "$f" ADMIN_PASSWORD)"; su="$(valor "$f" SMOKE_USERNAME)"; sp="$(valor "$f" SMOKE_PASSWORD)"
  [[ -n "$ap" ]] || falhar "ADMIN_PASSWORD ausente do .env.prod (já foi removido após o primeiro acesso?)"
  local token
  token="$(printf '{"login":"%s","senha":"%s"}' "$au" "$ap" | compor "$id" exec -T api sh -c \
    'curl -fsS -X POST -H "Content-Type: application/json" --data-binary @- http://localhost:8080/api/v1/auth/login' \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  [[ -n "$token" ]] || falhar "login do ADMIN falhou"
  local corpo codigo
  corpo="$(printf '{"username":"%s","email":"%s@%s","senha":"%s","papel":"VENDAS"}' "$su" "$su" "$(valor "$f" DOMINIO)" "$sp")"
  codigo="$(printf '%s' "$corpo" | compor "$id" exec -T -e "T=$token" api sh -c \
    'curl -sS -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $T" --data-binary @- http://localhost:8080/api/v1/usuarios')"
  case "$codigo" in
    201) log "Usuário de smoke '$su' criado (papel VENDAS)" ;;
    409|400) log "Usuário de smoke já existia (ou política recusou): HTTP $codigo" ;;
    *) falhar "criação do usuário de smoke retornou HTTP $codigo" ;;
  esac
}

cmd_modulos() {
  local id="${1:?informe o id}" nova="${2:?informe a lista de módulos}"
  local f; f="$(env_de "$id")"; [[ -f "$f" ]] || falhar "instalação '$id' não existe"
  local atual; atual="$(valor "$f" PETCONNECT_MODULOS)"
  [[ "$atual" != "$nova" ]] || { log "Já está com '$nova'; nada a fazer."; return 0; }
  local erro; erro="$(validar_modulos "$nova" 2>&1)" || falhar "módulos: $erro (nada foi alterado)"
  log "Módulos de '$id': $atual → $nova"
  executar backup.sh "$id"
  trocar() { sed -i "s|^PETCONNECT_MODULOS=.*|PETCONNECT_MODULOS=$1|" "$f"; }
  trocar "$nova"
  # A API valida no boot: lista inválida ou remoção com pendência recusa a partida e registra a causa nos logs.
  if compor "$id" up -d --wait --wait-timeout 300 api web && executar smoke.sh "$id"; then
    log "Módulos de '$id' atualizados para '$nova' (auditoria MODULOS_ALTERADOS registrada pela API)"
    return 0
  fi
  log "FALHOU; motivo da API:"
  compor "$id" logs --no-color --tail 300 api 2>&1 | grep -o 'Configuração de módulos recusada[^"\]*' | tail -n 1 || true
  log "Voltando para '$atual'"
  trocar "$atual"
  compor "$id" up -d --wait --wait-timeout 300 api web || true
  falhar "mudança de módulos revertida; nenhum dado foi alterado"
}

cmd_rotacionar_jwt() {
  local id="${1:?informe o id}" f; f="$(env_de "$id")"; [[ -f "$f" ]] || falhar "instalação '$id' não existe"
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(segredo 32)|" "$f"
  compor "$id" up -d --wait --wait-timeout 300 api web
  log "JWT_SECRET de '$id' trocado: todas as sessões dessa instalação caíram (as outras não foram tocadas)"
}

cmd_listar() {
  local id f
  printf '%-14s %-32s %-8s %-10s %s\n' ID DOMINIO TAG ESTADO MODULOS
  for id in $(instalacoes); do
    f="$(env_de "$id")"
    local estado; estado="$(docker ps --filter "label=com.docker.compose.project=petconnect-$id" --filter status=running -q 2>/dev/null | wc -l | tr -d ' ')"
    printf '%-14s %-32s %-8s %-10s %s\n' "$id" "$(valor "$f" DOMINIO)" "$(valor "$f" TAG)" "${estado}/5 no ar" "$(valor "$f" PETCONNECT_MODULOS)"
  done
}

# ------------------------------------------------------------------ isolamento
cmd_isolamento() {
  local ids; ids=($(instalacoes))
  [[ ${#ids[@]} -ge 2 ]] || falhar "são necessárias ao menos 2 instalações para comparar"
  local falhas=0
  ok() { log "PASS $*"; }
  ruim() { log "FAIL $*"; falhas=$((falhas + 1)); }
  local a b
  for a in "${ids[@]}"; do cmd_validar "$a" >/dev/null 2>&1 && ok "validação de '$a'" || ruim "validação de '$a'"; done

  # Volumes, redes e containers por projeto: nenhum compartilhado.
  declare -A vol rede
  for a in "${ids[@]}"; do
    vol[$a]="$(docker volume ls -q --filter "label=com.docker.compose.project=petconnect-$a" | sort | tr '\n' ' ')"
    rede[$a]="$(docker network ls -q --filter "label=com.docker.compose.project=petconnect-$a" | sort | tr '\n' ' ')"
    [[ -n "${vol[$a]}" ]] && ok "'$a' tem volumes próprios: $(docker volume ls --filter "label=com.docker.compose.project=petconnect-$a" --format '{{.Name}}' | tr '\n' ' ')" \
      || ruim "'$a' sem volumes (está no ar?)"
  done
  for a in "${ids[@]}"; do for b in "${ids[@]}"; do
    [[ "$a" < "$b" ]] || continue
    local comum=""
    for v in ${vol[$a]}; do [[ " ${vol[$b]} " == *" $v "* ]] && comum="$comum $v"; done
    [[ -z "$comum" ]] && ok "'$a' e '$b' não compartilham volume" || ruim "'$a' e '$b' compartilham volume:$comum"
    comum=""
    for v in ${rede[$a]}; do [[ " ${rede[$b]} " == *" $v "* ]] && comum="$comum $v"; done
    [[ -z "$comum" ]] && ok "'$a' e '$b' não compartilham rede" || ruim "'$a' e '$b' compartilham rede:$comum"
    [[ "$(dir_de "$a")/backups" != "$(dir_de "$b")/backups" ]] && ok "backups em pastas distintas" || ruim "backups na mesma pasta"
  done; done

  # Alcance de rede: o MySQL/Mongo de A não resolve nem responde a partir do B.
  for a in "${ids[@]}"; do for b in "${ids[@]}"; do
    [[ "$a" != "$b" ]] || continue
    local cid; cid="$(docker ps -q --filter "label=com.docker.compose.project=petconnect-$a" --filter "label=com.docker.compose.service=api" | head -n 1)"
    [[ -n "$cid" ]] || continue
    local ip
    for svc in mysql mongo; do
      ip="$(docker ps -q --filter "label=com.docker.compose.project=petconnect-$b" --filter "label=com.docker.compose.service=$svc" | head -n 1 \
        | xargs -r docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' 2>/dev/null | awk '{print $1}')"
      [[ -n "$ip" ]] || continue
      if docker exec "$cid" bash -c "timeout 3 bash -c '</dev/tcp/$ip/$([[ $svc == mysql ]] && echo 3306 || echo 27017)'" >/dev/null 2>&1; then
        ruim "API de '$a' alcança o $svc de '$b' ($ip)"
      else
        ok "API de '$a' NÃO alcança o $svc de '$b'"
      fi
    done
  done; done

  [[ "$falhas" -eq 0 ]] || falhar "isolamento: $falhas verificação(ões) falharam"
  log "Isolamento OK entre: ${ids[*]}"
}

# ------------------------------------------------------------------ despacho
comando="${1:-}"; shift || true
case "$comando" in
  novo) cmd_novo "$@" ;;
  validar) cmd_validar "$@" ;;
  subir) cmd_subir "$@" ;;
  smoke-usuario) cmd_smoke_usuario "$@" ;;
  smoke) id="${1:?informe o id}"; shift; executar smoke.sh "$id" "$@" ;;
  backup) id="${1:?informe o id}"; shift; executar backup.sh "$id" "$@" ;;
  restore-test) id="${1:?informe o id}"; shift; executar restore-test.sh "$id" "$@" ;;
  restore) id="${1:?informe o id}"; shift; executar restore.sh "$id" "$@" ;;
  monitorar) id="${1:?informe o id}"; shift; executar monitorar.sh "$id" "$@" ;;
  modulos) cmd_modulos "$@" ;;
  rotacionar-jwt) cmd_rotacionar_jwt "$@" ;;
  status) id="${1:?informe o id}"; compor "$id" ps ;;
  logs) id="${1:?informe o id}"; shift; compor "$id" logs --no-color --tail 100 "$@" ;;
  parar) id="${1:?informe o id}"; compor "$id" stop ;;
  listar) cmd_listar ;;
  isolamento) cmd_isolamento ;;
  *) sed -n '2,19p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
