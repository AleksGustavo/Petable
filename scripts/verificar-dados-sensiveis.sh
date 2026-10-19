#!/usr/bin/env bash
# Barreira preventiva contra dados pessoais em dumps/SQL versionados.
#
# Falha se um arquivo de dados (.sql, .dump, .csv, .bak) rastreado pelo git trouxer padrões de CPF formatado
# (000.000.000-00), fora das fixtures sintéticas listadas em PERMITIDOS. Não imprime o conteúdo achado: só o
# caminho e a contagem, para o log do CI não virar uma nova exposição.
#
# NÃO substitui a revisão humana: o script reconhece só o padrão de CPF formatado. Nomes, telefones, e-mails e
# endereços em dumps exigem conferência de quem conhece a origem dos dados (ver docs/seguranca/).
#
# Uso: scripts/verificar-dados-sensiveis.sh            # confere todos os arquivos rastreados
#      scripts/verificar-dados-sensiveis.sh <refspec>   # só os arquivos alterados em <refspec> (ex.: origin/main...HEAD)
set -euo pipefail
export LC_ALL=C

# Fixtures sintéticas aprovadas (expressão regular sobre o caminho). Inclua aqui só o que for comprovadamente fictício.
PERMITIDOS='^(backend/src/test/resources/legacy/|backend/src/main/resources/db/migration/|docker/migracao/vazio\.sql$)'

cd "$(git rev-parse --show-toplevel)"

if [ "$#" -ge 1 ]; then
  listar() { git diff -z --name-only --diff-filter=AMR "$1"; }
else
  listar() { git ls-files -z; }
fi

achados=0
# -z: caminhos com acento (ex.: "Inserção") não podem ser escapados pelo git, ou o arquivo não seria encontrado.
while IFS= read -r -d '' f; do
  [ -z "$f" ] && continue
  case "$f" in
    *.sql|*.dump|*.csv|*.bak) ;;
    *) continue ;;
  esac
  [ -f "$f" ] || continue
  if printf '%s\n' "$f" | grep -Eq "$PERMITIDOS"; then continue; fi
  n=$(grep -Eoc '[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}' "$f" || true)
  if [ "${n:-0}" -gt 0 ]; then
    echo "::error file=$f::possível dado pessoal (padrão de CPF em $n linha(s)); não versione dumps reais"
    achados=$((achados + 1))
  fi
done < <(listar "$@")

if [ "$achados" -gt 0 ]; then
  echo "Falha: $achados arquivo(s) com padrão de CPF fora das fixtures permitidas." >&2
  exit 1
fi
echo "Nenhum padrão de CPF em arquivos de dados fora das fixtures permitidas."
