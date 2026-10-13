"""Gera docs/dicionario-de-dados.md a partir do MySQL de desenvolvimento (docker compose up -d mysql).
Uso, na raiz do repositório:  python scripts/gerar-dicionario.py"""
import collections
import pathlib
import subprocess

REPO = str(pathlib.Path(__file__).resolve().parent.parent)


def mysql(sql):
    r = subprocess.run(["docker", "compose", "exec", "-T", "mysql", "sh", "-c",
                        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B petconnect_web'],
                       input=sql, cwd=REPO, capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0:
        raise SystemExit(r.stderr)
    return [l.split("\t") for l in r.stdout.splitlines() if l]


cols = mysql("""
SELECT c.table_name, c.ordinal_position, c.column_name, c.column_type, c.is_nullable, c.column_key,
       IFNULL(c.column_default,''), c.extra, IFNULL(c.column_comment,'')
  FROM information_schema.columns c JOIN information_schema.tables t USING (table_schema, table_name)
 WHERE c.table_schema='petconnect_web' AND t.table_type='BASE TABLE' AND c.table_name <> 'flyway_schema_history'
 ORDER BY c.table_name, c.ordinal_position;""")
fks = mysql("""
SELECT table_name, column_name, referenced_table_name, referenced_column_name FROM information_schema.key_column_usage
 WHERE table_schema='petconnect_web' AND referenced_table_name IS NOT NULL ORDER BY table_name, column_name;""")
checks = mysql("""
SELECT tc.table_name, cc.constraint_name, cc.check_clause FROM information_schema.check_constraints cc
  JOIN information_schema.table_constraints tc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name
 WHERE cc.constraint_schema='petconnect_web' ORDER BY tc.table_name, cc.constraint_name;""")
uniques = mysql("""
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
 WHERE table_schema='petconnect_web' AND non_unique=0 AND index_name<>'PRIMARY' GROUP BY table_name, index_name ORDER BY table_name, index_name;""")
versoes = mysql("SELECT version, description FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank")

por_tabela = collections.OrderedDict()
for t, pos, nome, tipo, nulo, chave, padrao, extra, comentario in cols:
    por_tabela.setdefault(t, []).append((nome, tipo, nulo, chave, padrao, extra, comentario))
fk_de = collections.defaultdict(dict)
for t, c, rt, rc in fks:
    fk_de[t][c] = f"{rt}.{rc}"
ck_de = collections.defaultdict(list)
for t, n, clausula in checks:
    ck_de[t].append((n, clausula))
uk_de = collections.defaultdict(list)
for t, n, c in uniques:
    uk_de[t].append((n, c))

GRUPOS = [
    ("Acesso", ["papel", "usuario", "token_redefinicao_senha"]),
    ("Pessoas e endereço", ["endereco", "cliente", "funcionario", "fornecedor"]),
    ("Pets", ["especie", "raca", "pet", "vacina", "carteira_vacina", "historico_consulta"]),
    ("Serviços e agenda", ["servico", "preco_servico_porte", "agendamento", "servico_realizado"]),
    ("Produtos, estoque e compras", ["categoria_produto", "produto", "movimentacao_estoque", "compra", "item_compra"]),
    ("Vendas", ["venda", "item_venda"]),
    ("Financeiro e caixa", ["caixa", "caixa_movimento", "conta_pagar", "movimentacao_financeira"]),
    ("Migração do legado", ["migracao_legado_execucao", "migracao_legado_mapa"]),
]
listadas = {t for _, ts in GRUPOS for t in ts}
assert listadas == set(por_tabela), (set(por_tabela) ^ listadas)


def esc(s):
    return s.replace("|", "\\|").replace("\n", " ")


out = ["# Dicionário de dados — PetConnect Web (MySQL)\n",
       "> **Gerado a partir do banco real** (`information_schema`) com as migrations Flyway aplicadas até a "
       f"V{versoes[-1][0]}. A fonte da verdade continua sendo [`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration). "
       "Para regenerar: `python scripts/gerar-dicionario.py` com o MySQL de desenvolvimento no ar.\n",
       "Convenções: dinheiro em `DECIMAL(10,2)`; datas e horas em horário local de São Paulo; enums como `VARCHAR` + `CHECK`; "
       "sem exclusão física (campo `ativo`). Veja o diagrama em [banco-de-dados.md](banco-de-dados.md).\n",
       "## Migrations aplicadas\n", "| Versão | Descrição |", "|---|---|"]
out += [f"| V{v} | {esc(d)} |" for v, d in versoes]
out.append("")
total = len(por_tabela)
out.append(f"**{total} tabelas** em {len(GRUPOS)} grupos.\n")
for grupo, tabelas in GRUPOS:
    out.append(f"## {grupo}\n")
    for t in tabelas:
        out.append(f"### `{t}`\n")
        out.append("| Coluna | Tipo | Nulo | Chave | Padrão | Referência / observação |")
        out.append("|---|---|---|---|---|---|")
        for nome, tipo, nulo, chave, padrao, extra, comentario in por_tabela[t]:
            ch = {"PRI": "PK", "UNI": "UK", "MUL": "índice"}.get(chave, "")
            ref = fk_de[t].get(nome)
            obs = []
            if ref:
                obs.append("FK → `" + ref + "`")
            if "GENERATED" in extra.upper():
                obs.append("coluna gerada")
            if "auto_increment" in extra:
                obs.append("auto incremento")
            if comentario:
                obs.append(comentario)
            out.append(f"| `{nome}` | `{tipo}` | {'sim' if nulo == 'YES' else 'não'} | {ch} | {esc(padrao) or ''} | {esc('; '.join(obs))} |")
        extras = []
        for n, c in uk_de[t]:
            extras.append(f"- **Único** `{n}`: ({c})")
        for n, clausula in ck_de[t]:
            extras.append(f"- **Regra** `{n}`: `{esc(clausula)}`")
        if extras:
            out.append("")
            out += extras
        out.append("")

open(pathlib.Path(REPO) / "docs" / "dicionario-de-dados.md", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print(total, "tabelas;", sum(len(v) for v in por_tabela.values()), "colunas;", len(checks), "regras CHECK;", len(versoes), "migrations")
