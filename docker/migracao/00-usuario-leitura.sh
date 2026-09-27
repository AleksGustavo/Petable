#!/bin/bash
# Roda uma vez, na criação do volume do mysql-legado: cria o usuário SÓ COM SELECT
# que a migração usa. A senha vem do .env (LEGADO_DB_PASSWORD) e não é impressa.
# Usa o cliente mysql pelo socket do servidor temporário do entrypoint, então funciona
# tanto se o arquivo for executado quanto se for "sourced".
set -euo pipefail
USUARIO="${LEGADO_DB_USERNAME:-legado_read}"
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS '${USUARIO}'@'%' IDENTIFIED BY '${LEGADO_DB_PASSWORD}';
GRANT SELECT ON \`${MYSQL_DATABASE}\`.* TO '${USUARIO}'@'%';
FLUSH PRIVILEGES;
SQL
