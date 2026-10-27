#!/usr/bin/env python3
"""Popula um ambiente de DESENVOLVIMENTO/TESTE do Petable com dados FICTÍCIOS em várias situações.

Usa a própria API (não o SQL direto), então estoque, lotes, caixa, financeiro e auditoria ficam consistentes,
e o script não depende da versão do schema. Cada tabela de negócio recebe 5 ou mais registros em situações
diferentes (ativo/inativo, pedido/recebido/cancelado, caixa aberto/fechado com diferença, vendas canceladas etc.).

SEGURANÇA
- Só roda contra localhost/127.0.0.1/::1/*.localhost/*.test, salvo a opção --permitir-host-remoto (nunca em produção).
- Nenhum dado é real: nomes, e-mails (@seed.petable.test), CPFs e CNPJs são gerados por algoritmo.
- As senhas dos usuários de teste NÃO são impressas: ficam no arquivo local .seed-credenciais.txt (fora do git).
- Roda uma vez por banco. Para repetir, apague o banco (ex.: docker compose down -v) e suba de novo.

USO
  ADMIN_PASSWORD='...' python scripts/seed-dados-ficticios.py --api http://127.0.0.1:8080 --admin admin
Veja docs/operacao/seed-dados-ficticios.md.
"""
import argparse
import json
import os
import random
import secrets
import string
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlparse

DOMINIO = "seed.petable.test"
rnd = random.Random(20261026)  # determinístico: mesmas escolhas a cada execução

PRIMEIROS = ["Ana", "Bruno", "Carla", "Diego", "Elisa", "Fábio", "Gabriela", "Hugo", "Isabela", "João", "Karen",
              "Lucas", "Marina", "Nelson", "Olívia", "Paulo", "Rita", "Sérgio", "Tânia", "Vitor"]
SOBRENOMES = ["Almeida", "Barros", "Cardoso", "Dias", "Esteves", "Farias", "Gomes", "Henrique", "Ito", "Jesus",
              "Klein", "Lima", "Moreira", "Nogueira", "Oliveira", "Pires", "Queiroz", "Ramos", "Silva", "Teixeira"]
NOMES_PET = ["Thor", "Mel", "Bidu", "Luna", "Pipoca", "Nina", "Simba", "Fred", "Mia", "Toby", "Nala", "Rex",
             "Bolinha", "Amora", "Zeca", "Pérola"]
CIDADES = [("São Paulo", "SP", "01310100"), ("Campinas", "SP", "13010100"), ("Curitiba", "PR", "80010000"),
           ("Belo Horizonte", "MG", "30110000"), ("Recife", "PE", "50010000"), ("Porto Alegre", "RS", "90010000")]


# --------------------------------------------------------------------------- utilidades


class ErroApi(Exception):
    def __init__(self, status, corpo):
        super().__init__(f"HTTP {status}: {corpo}")
        self.status = status
        self.corpo = corpo


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def req(self, metodo, caminho, corpo=None, token=None, extra=None):
        dados = None if corpo is None else json.dumps(corpo).encode("utf-8")
        cab = {"Content-Type": "application/json", "Accept": "application/json"}
        if token:
            cab["Authorization"] = f"Bearer {token}"
        if extra:
            cab.update(extra)
        r = urllib.request.Request(self.base + caminho, data=dados, method=metodo, headers=cab)
        try:
            with urllib.request.urlopen(r, timeout=60) as resp:
                txt = resp.read().decode("utf-8")
                return json.loads(txt) if txt else None
        except urllib.error.HTTPError as e:
            raise ErroApi(e.code, e.read().decode("utf-8", "replace")[:300])

    def login(self, usuario, senha):
        return self.req("POST", "/api/v1/auth/login", {"login": usuario, "senha": senha})["accessToken"]


def digitos_cpf(base9):
    d = [int(c) for c in base9]
    for n in (9, 10):
        s = sum(d[i] * (n + 1 - i) for i in range(n))
        d.append((s * 10 % 11) % 10)
    return "".join(map(str, d))


def cpf(i):
    while True:
        base = f"{(i * 7919 + 100000001) % 900000000 + 100000000:09d}"
        if len(set(base)) > 1:
            return digitos_cpf(base)
        i += 1


def cnpj(i):
    base = f"{(i * 104729 + 12345678) % 90000000 + 10000000:08d}0001"
    d = [int(c) for c in base]
    for pesos in ([5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2], [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]):
        s = sum(a * b for a, b in zip(d, pesos))
        r = s % 11
        d.append(0 if r < 2 else 11 - r)
    return "".join(map(str, d))


def telefone(i):
    return f"119{rnd.randint(10000000, 99999999)}" if i % 3 else f"1133{rnd.randint(100000, 999999)}"


def endereco(i):
    cidade, uf, cep = CIDADES[i % len(CIDADES)]
    return {"cep": cep, "logradouro": f"Rua Fictícia {i + 1}", "numero": str(10 + i * 7), "complemento": None,
            "bairro": "Centro", "cidade": cidade, "uf": uf}


def senha_forte():
    alfabeto = string.ascii_letters + string.digits
    return "Sd-" + "".join(secrets.choice(alfabeto) for _ in range(14)) + "-9z"


class Resultado:
    def __init__(self):
        self.criados = {}
        self.falhas = []

    def ok(self, tabela, n=1):
        self.criados[tabela] = self.criados.get(tabela, 0) + n

    def falha(self, o_que, erro):
        self.falhas.append(f"{o_que}: {erro}")
        print(f"  ! {o_que}: {erro}", file=sys.stderr)


R = Resultado()


def tentar(o_que, fn, tabela=None):
    """Executa uma criação sem derrubar o script inteiro se uma regra de negócio recusar."""
    try:
        v = fn()
        if tabela:
            R.ok(tabela)
        return v
    except ErroApi as e:
        R.falha(o_que, e)
    except Exception as e:  # noqa: BLE001
        R.falha(o_que, repr(e))
    return None


def host_local(url):
    h = (urlparse(url).hostname or "").lower()
    return h in ("localhost", "127.0.0.1", "::1") or h.endswith(".localhost") or h.endswith(".test")


# --------------------------------------------------------------------------- semeadura


def semear(api, admin_login, admin_senha):
    t_admin = api.login(admin_login, admin_senha)
    A = lambda m, p, b=None, **k: api.req(m, p, b, t_admin, **k)  # noqa: E731

    ja = A("GET", "/api/v1/clientes?busca=(Seed)&size=5")
    if ja and ja.get("content"):
        sys.exit("Este banco já foi populado (existem clientes '(Seed)'). Para repetir, recrie o banco.")

    senha = senha_forte()
    cred = []

    # 1) usuários e funcionários -------------------------------------------------------------
    print("Usuários e funcionários...")
    spec = [("seed.admin2", "ADMIN"), ("seed.vendas1", "VENDAS"), ("seed.vendas2", "VENDAS"),
            ("seed.servico1", "SERVICO"), ("seed.servico2", "SERVICO"), ("seed.inativo", "VENDAS")]
    usuarios = {}
    for i, (login, papel) in enumerate(spec):
        u = tentar(f"usuário {login}", lambda login=login, papel=papel: A("POST", "/api/v1/usuarios", {
            "username": login, "email": f"{login}@{DOMINIO}", "senha": senha, "papel": papel,
            "nomeCompleto": f"{login.split('.')[1].capitalize()} {SOBRENOMES[i]} (Seed)"}), "usuario")
        if u:
            usuarios[login] = u["id"]
            cred.append(f"{login} ({papel})")
    funcionarios = {}
    for i, (login, uid) in enumerate(usuarios.items()):
        nome = f"{PRIMEIROS[i]} {SOBRENOMES[i]} (Seed)"
        f = tentar(f"funcionário {login}", lambda uid=uid, nome=nome, i=i: A("POST", "/api/v1/funcionarios", {
            "usuarioId": uid, "cpf": cpf(100 + i), "nome": nome, "dataNascimento": f"199{i}-0{i + 1}-1{i}",
            "sexo": ["FEMININO", "MASCULINO", "OUTRO"][i % 3], "telefone": telefone(i), "endereco": endereco(i),
            "observacoes": "Funcionário fictício (seed)."}), "funcionario")
        if f:
            funcionarios[login] = f["id"]
    if "seed.inativo" in usuarios:
        tentar("desativar seed.inativo", lambda: A("PATCH", f"/api/v1/usuarios/{usuarios['seed.inativo']}/desativar"))

    # 2) clientes ----------------------------------------------------------------------------
    print("Clientes...")
    clientes = []
    for i in range(10):
        nome = f"{PRIMEIROS[(i + 3) % 20]} {SOBRENOMES[(i + 7) % 20]} (Seed)"
        corpo = {"nome": nome, "telefone": telefone(i), "observacoes": "Cliente fictício (seed)."}
        if i % 4 != 3:
            corpo["cpf"] = cpf(200 + i)
        if i % 3 != 2:
            corpo["email"] = f"cliente{i + 1}@{DOMINIO}"
        if i % 5 != 4:
            corpo["endereco"] = endereco(i)
        if i % 2 == 0:
            corpo["dataNascimento"] = f"19{70 + i}-0{(i % 9) + 1}-15"
        c = tentar(f"cliente {i + 1}", lambda corpo=corpo: A("POST", "/api/v1/clientes", corpo), "cliente")
        if c:
            clientes.append(c["id"])
    for cid in clientes[-2:]:  # situação: cliente inativo
        tentar(f"inativar cliente {cid}", lambda cid=cid: A("DELETE", f"/api/v1/clientes/{cid}"))
    clientes_ativos = clientes[:-2]

    # 3) pets, vacinas e consultas -----------------------------------------------------------
    print("Pets, carteira de vacinas e consultas...")
    especies = A("GET", "/api/v1/especies")
    por_nome = {e["nome"]: e["id"] for e in especies}
    pets = []
    portes = ["PEQUENO", "MEDIO", "GRANDE"]
    plano_pets = [("Cão", 0), ("Cão", 1), ("Cão", 2), ("Gato", 0), ("Gato", 1), ("Ave", 0), ("Roedor", 0),
                  ("Cão", 0), ("Gato", 2), ("Réptil", 0), ("Cão", 1), ("Peixe", 0)]
    for i, (esp, p) in enumerate(plano_pets):
        eid = por_nome.get(esp)
        if not eid or not clientes_ativos:
            continue
        racas = A("GET", f"/api/v1/especies/{eid}/racas")
        corpo = {"clienteId": clientes_ativos[i % len(clientes_ativos)], "nome": f"{NOMES_PET[i]} (Seed)",
                 "especieId": eid, "racaId": racas[i % len(racas)]["id"] if racas and i % 4 else None,
                 "dataNascimento": (date.today() - timedelta(days=200 + i * 150)).isoformat(),
                 "peso": str(Decimal(2 + i * 3) + Decimal("0.5")), "cor": ["Caramelo", "Preto", "Branco", "Mesclado"][i % 4],
                 "porte": portes[p] if esp in ("Cão", "Gato") else None, "castrado": bool(i % 2),
                 "observacoes": "Pet fictício (seed)."}
        pet = tentar(f"pet {i + 1}", lambda corpo=corpo: A("POST", "/api/v1/pets", corpo), "pet")
        if pet:
            pets.append({"id": pet["id"], "especie": esp, "especieId": eid, "porte": corpo["porte"]})
    caes = [p for p in pets if p["especie"] == "Cão"]
    for i, p in enumerate(pets[:7]):  # carteira de vacinação
        vac = tentar(f"vacinas do pet {p['id']}", lambda p=p: A("GET", f"/api/v1/especies/{p['especieId']}/vacinas"))
        if vac:
            hoje = date.today()
            # situações: 2 vencidas, 2 a vencer em até 30 dias, o restante em dia
            aplic_dias, prox_dias = [(400, -35), (380, -15), (340, 25), (350, 15), (30, 335), (60, 305), (10, 355)][i]
            tentar(f"vacina do pet {p['id']}", lambda p=p, vac=vac, i=i, ad=aplic_dias, pd=prox_dias: A("POST", f"/api/v1/pets/{p['id']}/vacinas", {
                "vacinaId": vac[i % len(vac)]["id"], "dataAplicacao": (hoje - timedelta(days=ad)).isoformat(),
                "dataProxima": (hoje + timedelta(days=pd)).isoformat(),
                "veterinario": "Dra. Fictícia (seed)", "lote": f"SEEDVAC{i + 1}",
                "observacoes": "Vacina fictícia (seed)."}), "carteira_vacina")
    motivos = ["Check-up anual", "Coceira persistente", "Vômito", "Vacinação", "Claudicação", "Perda de apetite"]
    for i, p in enumerate(pets[:6]):
        tentar(f"consulta pet {p['id']}", lambda p=p, i=i: A("POST", f"/api/v1/pets/{p['id']}/consultas", {
            "dataConsulta": (datetime.now() - timedelta(days=10 * (i + 1))).replace(microsecond=0).isoformat(),
            "veterinario": "Dr. Fictício (seed)", "motivo": motivos[i], "diagnostico": "Diagnóstico fictício (seed).",
            "tratamento": "Tratamento fictício (seed).", "observacoes": None}), "historico_consulta")

    # 4) fornecedores ------------------------------------------------------------------------
    print("Fornecedores...")
    fornecedores = []
    for i in range(6):
        f = tentar(f"fornecedor {i + 1}", lambda i=i: A("POST", "/api/v1/fornecedores", {
            "nomeFantasia": f"Fornecedor Fictício {i + 1}", "razaoSocial": f"Fornecedor Fictício {i + 1} Ltda (Seed)",
            "cnpj": cnpj(i + 1), "telefone": telefone(i), "email": f"fornecedor{i + 1}@{DOMINIO}",
            "contato": f"Contato {i + 1}", "endereco": endereco(i + 2), "observacoes": "Fornecedor fictício (seed)."}),
            "fornecedor")
        if f:
            fornecedores.append(f["id"])

    # 5) serviços (complementa os de referência) ---------------------------------------------
    print("Serviços...")
    servicos = A("GET", "/api/v1/servicos?ativo=true") or []
    extras = [("Hidratação (Seed)", "35.00", 30), ("Corte de unhas (Seed)", "20.00", 15),
              ("Escovação de dentes (Seed)", "25.00", 20), ("Spa completo (Seed)", "120.00", 120),
              ("Banho terapêutico (Seed)", "80.00", 60)]
    for nome, preco, dur in extras:
        s = tentar(f"serviço {nome}", lambda nome=nome, preco=preco, dur=dur: A("POST", "/api/v1/servicos", {
            "nome": nome, "descricao": "Serviço fictício (seed).", "preco": preco, "duracaoMinutos": dur,
            "precosPorPorte": [{"porte": "PEQUENO", "preco": preco}, {"porte": "MEDIO", "preco": str(Decimal(preco) + 10)},
                               {"porte": "GRANDE", "preco": str(Decimal(preco) + 25)}]}), "servico")
        if s:
            servicos.append(s)
    serv_ids = [s["id"] for s in servicos]

    # 6) produtos, lotes e compras -----------------------------------------------------------
    print("Produtos...")
    cats = A("GET", "/api/v1/categorias-produto")
    cfg = tentar("configuração de lotes", lambda: A("GET", "/api/v1/estoque/configuracao")) or {}
    lotes_ativos = bool(cfg.get("lotesHabilitado"))
    catalogo = [  # nome, custo, venda, mínimo, controla lote?
        ("Ração Premium Cães 15kg", "120.00", "189.90", 5, True), ("Ração Gatos Castrados 3kg", "40.00", "69.90", 8, True),
        ("Antipulgas Cães P", "35.00", "69.00", 5, True), ("Vermífugo Gatos", "12.00", "24.90", 6, True),
        ("Shampoo Neutro 500ml", "9.00", "22.90", 5, False), ("Coleira Ajustável M", "11.00", "29.90", 4, False),
        ("Brinquedo Bolinha", "3.00", "12.90", 10, False), ("Areia Higiênica 4kg", "8.00", "17.90", 6, False),
        ("Petisco Bifinho 65g", "3.50", "9.90", 12, True), ("Casinha Plástica P", "60.00", "119.90", 2, False),
        ("Comedouro Inox", "10.00", "26.90", 4, False), ("Produto Descontinuado (Seed)", "5.00", "9.90", 0, False)]
    produtos = []
    for i, (nome, custo, venda, minimo, lote) in enumerate(catalogo):
        p = tentar(f"produto {nome}", lambda i=i, nome=nome, custo=custo, venda=venda, minimo=minimo: A(
            "POST", "/api/v1/produtos", {"nome": nome + " (Seed)", "descricao": "Produto fictício (seed).",
                                         "categoriaId": cats[i % len(cats)]["id"],
                                         "fornecedorPadraoId": fornecedores[i % len(fornecedores)] if fornecedores else None,
                                         "codigoBarras": f"78990{i + 1:08d}", "precoCusto": custo, "precoVenda": venda,
                                         "estoqueMinimo": minimo}), "produto")
        if p:
            produtos.append({"id": p["id"], "lote": lote, "venda": Decimal(venda), "custo": Decimal(custo)})
    if len(fornecedores) >= 6:  # situação: fornecedor inativo (depois de cadastrar os produtos que o usam)
        tentar("desativar fornecedor", lambda: A("PATCH", f"/api/v1/fornecedores/{fornecedores[-1]}/desativar"))
    if lotes_ativos:
        for p in produtos:
            if p["lote"]:
                tentar(f"controle de lote do produto {p['id']}", lambda p=p: A(
                    "POST", f"/api/v1/produtos/{p['id']}/controle-lote",
                    {"permiteSemValidade": False, "diasBloqueioValidade": 0, "lotes": []}))

    print("Compras, recebimentos e contas a pagar...")
    hoje = date.today()
    compras = []
    # situações: 3 recebidas (com contas a pagar de vencimentos diferentes), 1 pedido em aberto, 1 cancelada
    # (situação, produtos, dias da entrega a partir de hoje, dias do vencimento a partir de hoje)
    cenarios = [("RECEBIDA", [0, 1, 2], 0, 30), ("RECEBIDA", [3, 4, 5], 0, 7), ("RECEBIDA", [6, 7, 8, 9], -20, -5),
                ("PEDIDO", [10, 11], 0, 15), ("CANCELADA", [4, 5], 0, 10),
                ("RECEBIDA", [1, 2], 0, 45), ("RECEBIDA", [0, 3], 0, 3)]
    for k, (situacao, idxs, entrega, venc) in enumerate(cenarios):
        itens = [{"produtoId": produtos[j]["id"], "quantidade": 30 + 10 * j,
                  "precoCustoUnitario": str(produtos[j]["custo"])} for j in idxs if j < len(produtos)]
        if not itens or not fornecedores:
            continue
        c = tentar(f"compra {k + 1}", lambda k=k, itens=itens: A("POST", "/api/v1/compras", {
            "fornecedorId": fornecedores[k % max(len(fornecedores) - 1, 1)], "funcionarioId": None,
            "dataCompra": (hoje - timedelta(days=25 + 3 * k)).isoformat(), "formaPagamento": "TRANSFERENCIA",
            "observacoes": "Compra fictícia (seed).", "itens": itens}), "compra")
        if not c:
            continue
        compras.append(c["id"])
        if situacao == "RECEBIDA":
            lotes = []
            for it in itens:
                prod = next(p for p in produtos if p["id"] == it["produtoId"])
                if lotes_ativos and prod["lote"]:
                    lotes.append({"produtoId": it["produtoId"], "lotes": [
                        {"codigoLote": f"SEED-{k + 1}-{it['produtoId']}A", "validade": (hoje + timedelta(days=20 + 40 * k)).isoformat(),
                         "quantidade": it["quantidade"] // 2},
                        {"codigoLote": f"SEED-{k + 1}-{it['produtoId']}B", "validade": (hoje + timedelta(days=200 + 30 * k)).isoformat(),
                         "quantidade": it["quantidade"] - it["quantidade"] // 2}]})
            tentar(f"receber compra {c['id']}", lambda c=c, venc=venc, entrega=entrega, lotes=lotes: A(
                "POST", f"/api/v1/compras/{c['id']}/receber",
                {"dataEntrega": (hoje + timedelta(days=entrega)).isoformat(),
                 "dataVencimento": (hoje + timedelta(days=venc)).isoformat(),
                 "lotes": lotes or None}))
        elif situacao == "CANCELADA":
            tentar(f"cancelar compra {c['id']}", lambda c=c: A(
                "POST", f"/api/v1/compras/{c['id']}/cancelar", {"motivo": "Cancelada por divergência (seed)."}))
    if len(produtos) >= 12:
        tentar("desativar produto descontinuado", lambda: A("PATCH", f"/api/v1/produtos/{produtos[11]['id']}/desativar"))

    # 7) ajustes de estoque e perdas ---------------------------------------------------------
    print("Ajustes de estoque...")
    if produtos:
        sem_lote = [p for p in produtos if not p["lote"]]
        for i, p in enumerate(sem_lote[:3]):
            tentar(f"ajuste +{p['id']}", lambda p=p, i=i: A(
                "POST", f"/api/v1/produtos/{p['id']}/ajustes-estoque", {"quantidade": 5 + i, "motivo": "Inventário: sobra (seed)"}))
        for p in sem_lote[3:5]:
            tentar(f"ajuste -{p['id']}", lambda p=p: A(
                "POST", f"/api/v1/produtos/{p['id']}/ajustes-estoque", {"quantidade": -2, "motivo": "Avaria no transporte (seed)"}))
        if lotes_ativos:
            for p in [x for x in produtos if x["lote"]][:3]:
                lotes = tentar(f"lotes do produto {p['id']}", lambda p=p: A("GET", f"/api/v1/produtos/{p['id']}/lotes")) or []
                if lotes:
                    tentar("perda de lote", lambda p=p, l=lotes[0]: A(
                        "POST", f"/api/v1/produtos/{p['id']}/lotes/{l['id']}/perdas", {"quantidade": 1, "motivo": "Embalagem rompida (seed)"}))
                if len(lotes) > 1:
                    tentar("bloqueio de lote", lambda p=p, l=lotes[1]: A(
                        "POST", f"/api/v1/produtos/{p['id']}/lotes/{l['id']}/bloquear", {"motivo": "Recolhimento preventivo (seed)"}))

    # 8) caixa, vendas, financeiro e agendamentos --------------------------------------------
    print("Caixas e vendas...")
    tokens = {"seed.admin2": api.login("seed.admin2", senha) if "seed.admin2" in usuarios else None,
              "seed.vendas1": api.login("seed.vendas1", senha) if "seed.vendas1" in usuarios else None,
              "seed.vendas2": api.login("seed.vendas2", senha) if "seed.vendas2" in usuarios else None}
    ativos = [p for p in produtos if p["id"] != (produtos[11]["id"] if len(produtos) > 11 else -1)]

    def vender(tok, itens_idx, forma, cliente=None, desconto="0.00", cancelar=None):
        itens = [{"produtoId": ativos[j]["id"], "quantidade": q} for j, q in itens_idx if j < len(ativos)]
        total = sum(ativos[j]["venda"] * q for j, q in itens_idx if j < len(ativos)) - Decimal(desconto)
        corpo = {"clienteId": cliente, "itens": itens, "desconto": desconto, "formaPagamento": forma,
                 "valorRecebido": str((total + 10).quantize(Decimal("0.01"))) if forma == "DINHEIRO" else None,
                 "observacoes": "Venda fictícia (seed)."}
        v = tentar("venda", lambda: api.req("POST", "/api/v1/vendas", corpo, tok, {"Idempotency-Key": str(uuid.uuid4())}), "venda")
        if v and cancelar:
            tentar("cancelar venda", lambda: api.req("POST", f"/api/v1/vendas/{v['id']}/cancelar", {
                "motivo": "Cliente desistiu (seed).", "destinoEstoque": cancelar}, tok))
        return v

    def caixa(tok, fundo, acoes, fechar=None):
        tentar("abrir caixa", lambda: api.req("POST", "/api/v1/caixas/abrir", {"fundoAbertura": fundo}, tok), "caixa")
        acoes()
        if fechar is not None:
            prev = tentar("prévia de fechamento", lambda: api.req("GET", "/api/v1/caixas/atual/previa-fechamento", None, tok)) or {}
            esperado = prev.get("esperado") or prev.get("valoresEsperados") or {}
            contado = {k: str(Decimal(str(v)).quantize(Decimal("0.01"))) for k, v in esperado.items()} \
                if isinstance(esperado, dict) else {}
            if not contado:
                contado = {"DINHEIRO": str(Decimal(fundo))}
            # a diferença (falta/sobra) fica sempre no dinheiro da gaveta
            contado["DINHEIRO"] = str((Decimal(contado["DINHEIRO"]) + fechar).quantize(Decimal("0.01")))
            tentar("fechar caixa", lambda: api.req("POST", "/api/v1/caixas/atual/fechar", {
                "contado": contado, "motivoDiferenca": "Diferença de troco (seed)." if fechar != 0 else None}, tok))

    t1, t2, t3 = tokens["seed.admin2"], tokens["seed.vendas1"], tokens["seed.vendas2"]
    cli = (clientes_ativos + [None] * 5)

    def mov(tok, tipo, valor, motivo):
        tentar(f"caixa {tipo}", lambda: api.req("POST", f"/api/v1/caixas/atual/{tipo}", {"valor": valor, "motivo": motivo}, tok))

    def acoes_admin1():
        for t, mov in (("suprimentos", ("40.00", "Reforço de troco (seed)")), ("sangrias", ("30.00", "Sangria ao cofre (seed)"))):
            tentar(f"caixa {t}", lambda t=t, mov=mov: api.req("POST", f"/api/v1/caixas/atual/{t}", {"valor": mov[0], "motivo": mov[1]}, t_admin))
        vender(t_admin, [(0, 1), (4, 2)], "DINHEIRO", cli[0])
        vender(t_admin, [(1, 1)], "PIX", cli[1], "5.00")
        vender(t_admin, [(6, 3), (7, 1)], "CARTAO_DEBITO")
        vender(t_admin, [(2, 1)], "CARTAO_CREDITO", cli[2], cancelar="RETORNAR")
        vender(t_admin, [(8, 4)], "DINHEIRO", cancelar="DESCARTAR")

    caixa(t_admin, "200.00", acoes_admin1, Decimal("0"))  # fecha sem diferença
    if t2:
        caixa(t2, "100.00", lambda: (mov(t2, "sangrias", "20.00", "Sangria parcial (seed)"),
                                     vender(t2, [(3, 2), (5, 1)], "PIX", cli[3]), vender(t2, [(9, 1)], "TRANSFERENCIA")),
              Decimal("-3.50"))  # fecha com falta
    if t1:
        caixa(t1, "80.00", lambda: (mov(t1, "suprimentos", "25.00", "Troco extra (seed)"),
                                    vender(t1, [(4, 1), (6, 2)], "DINHEIRO", cli[4])), Decimal("2.00"))  # sobra
    caixa(t_admin, "150.00", lambda: vender(t_admin, [(3, 1)], "CARTAO_CREDITO"), Decimal("0"))
    if t3:  # fica ABERTO de propósito, com vendas, para testar PDV/fechamento
        caixa(t3, "120.00", lambda: (mov(t3, "suprimentos", "60.00", "Reforço de fundo (seed)"),
                                     mov(t3, "sangrias", "10.00", "Pagamento de entregador (seed)"),
                                     vender(t3, [(0, 1), (7, 2)], "DINHEIRO", cli[5])))

    print("Financeiro e agendamentos...")
    # contas a pagar: paga duas, deixa as demais em aberto/vencida. Pagar e lançar exigem caixa aberto.
    def financeiro():
        contas = (tentar("contas a pagar", lambda: A("GET", "/api/v1/financeiro/contas-a-pagar?size=50")) or {}).get("content", [])
        for c in contas[:2]:
            tentar(f"pagar conta {c['id']}", lambda c=c: A(
                "POST", f"/api/v1/financeiro/contas-a-pagar/{c['id']}/pagar", {"formaPagamento": "PIX"}))
        for cat, tipo, val in (("DESPESA_OPERACIONAL", "SAIDA", "250.00"), ("DESPESA_FUNCIONARIO", "SAIDA", "900.00"),
                               ("OUTROS", "ENTRADA", "75.00"), ("OUTROS", "SAIDA", "18.90"), ("DESPESA_OPERACIONAL", "SAIDA", "42.00")):
            tentar(f"lançamento {cat}", lambda cat=cat, tipo=tipo, val=val: A(
                "POST", "/api/v1/financeiro/lancamentos",
                {"tipo": tipo, "categoria": cat, "descricao": f"{cat} fictício (seed)", "valor": val,
                 "formaPagamento": "PIX", "observacoes": "Lançamento fictício (seed)."},
                extra={"Idempotency-Key": str(uuid.uuid4())}), "movimentacao_financeira")
        lanc = (tentar("lançamentos", lambda: A("GET", "/api/v1/financeiro/lancamentos?size=50")) or {}).get("content", [])
        manual = [x for x in lanc if x.get("categoria") in ("OUTROS", "DESPESA_OPERACIONAL")]
        if manual:
            tentar("estorno de lançamento", lambda: A(
                "POST", f"/api/v1/financeiro/lancamentos/{manual[0]['id']}/estornar", {"motivo": "Lançado em duplicidade (seed)."}))

    caixa(t_admin, "50.00", financeiro, Decimal("0"))

    # Agendamentos: a API só aceita data FUTURA na criação. Os "concluídos" e "em andamento" são criados daqui a
    # poucas horas e avançam de status; o "não compareceu" exige o horário já ter passado, então espera um minuto.
    agora = datetime.now().replace(minute=0, second=0, microsecond=0)
    nc = []
    func_ativos = [v for k, v in funcionarios.items() if k != "seed.inativo"]  # o inativo não aceita agendamentos

    def agendar(i, dh, status_final=None, **kw):
        if not pets or not serv_ids:
            return None
        ag = tentar(f"agendamento {i}", lambda: A("POST", "/api/v1/agendamentos", {
            "petId": pets[i % len(pets)]["id"], "servicoId": serv_ids[i % len(serv_ids)],
            "funcionarioId": func_ativos[(i + 3) % len(func_ativos)] if func_ativos else None,
            "dataHora": dh.isoformat(), "observacoes": "Agendamento fictício (seed)."}), "agendamento")
        if ag and status_final:
            for st in status_final:
                corpo = {"status": st}
                corpo.update(kw.get(st, {}))
                if st == "NAO_COMPARECEU":
                    nc.append((ag["id"], dh, corpo))
                    continue
                tentar(f"status {st}", lambda corpo=corpo: A("POST", f"/api/v1/agendamentos/{ag['id']}/status", corpo))
        return ag

    def agenda():
        agendar(0, agora + timedelta(days=1, hours=1))                                          # AGENDADO
        agendar(1, agora + timedelta(days=2, hours=2), ["CONFIRMADO"])                          # CONFIRMADO
        agendar(2, agora + timedelta(hours=3), ["CONFIRMADO", "EM_ANDAMENTO"])                  # EM_ANDAMENTO
        agendar(3, agora + timedelta(hours=4), ["CONFIRMADO", "EM_ANDAMENTO", "CONCLUIDO"],
                CONCLUIDO={"valorFinal": "70.00", "formaPagamento": "PIX", "observacoesConclusao": "Sem intercorrências (seed)."})
        agendar(4, agora + timedelta(hours=5), ["CONFIRMADO", "EM_ANDAMENTO", "CONCLUIDO"],
                CONCLUIDO={"valorFinal": "45.00", "formaPagamento": "DINHEIRO", "observacoesConclusao": "Cliente satisfeito (seed)."})
        agendar(5, agora + timedelta(hours=6), ["CONFIRMADO", "EM_ANDAMENTO", "CONCLUIDO"],
                CONCLUIDO={"valorFinal": "120.00", "formaPagamento": "CARTAO_CREDITO"})
        agendar(6, agora + timedelta(days=3), ["CANCELADO"], CANCELADO={"motivo": "Tutor viajou (seed)."})      # CANCELADO
        agendar(10, agora + timedelta(hours=7), ["CONFIRMADO", "EM_ANDAMENTO", "CONCLUIDO"],
                CONCLUIDO={"valorFinal": "35.00", "formaPagamento": "CARTAO_DEBITO", "observacoesConclusao": "Retorno em 30 dias (seed)."})
        agendar(11, agora + timedelta(hours=8), ["CONFIRMADO", "EM_ANDAMENTO", "CONCLUIDO"],
                CONCLUIDO={"valorFinal": "90.00", "formaPagamento": "DINHEIRO"})
        agendar(8, agora + timedelta(days=5, hours=3), ["CONFIRMADO"])
        agendar(9, agora + timedelta(days=6))
        em_breve = datetime.now().replace(second=0, microsecond=0) + timedelta(minutes=2)
        agendar(7, em_breve, ["NAO_COMPARECEU"], NAO_COMPARECEU={"motivo": "Sem aviso (seed)."})  # NÃO COMPARECEU (depois)

    caixa(t_admin, "50.00", agenda, Decimal("0"))
    if nc:
        import time
        espera = (max(dh for _, dh, _ in nc) - datetime.now()).total_seconds() + 5
        if espera > 0:
            print(f"Aguardando {int(espera)}s para o horário do 'não compareceu' passar...")
            time.sleep(espera)
        for ag_id, _, corpo in nc:
            tentar("status NAO_COMPARECEU", lambda ag_id=ag_id, corpo=corpo: A("POST", f"/api/v1/agendamentos/{ag_id}/status", corpo))

    # 9) tokens de redefinição de senha (gera registros em token_redefinicao_senha) ----------
    print("Redefinições de senha...")
    for login in list(usuarios)[:5]:
        tentar(f"esqueci-senha {login}", lambda login=login: api.req(
            "POST", "/api/v1/auth/esqueci-senha", {"email": f"{login}@{DOMINIO}"}), "token_redefinicao_senha")

    return senha, cred


def principal():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--api", default=os.environ.get("SEED_API", "http://127.0.0.1:8080"))
    ap.add_argument("--admin", default=os.environ.get("ADMIN_USERNAME", "admin"))
    ap.add_argument("--permitir-host-remoto", action="store_true",
                    help="Permite um host que não é local. NUNCA use em produção.")
    ap.add_argument("--credenciais", default=".seed-credenciais.txt")
    a = ap.parse_args()
    senha_admin = os.environ.get("ADMIN_PASSWORD")
    if not senha_admin:
        sys.exit("Defina ADMIN_PASSWORD no ambiente (senha do ADMIN deste ambiente de teste).")
    if not host_local(a.api) and not a.permitir_host_remoto:
        sys.exit(f"Recusado: {a.api} não é um host local. Este script só deve rodar em desenvolvimento/teste.")
    senha, cred = semear(Api(a.api), a.admin, senha_admin)
    Path(a.credenciais).write_text(
        "Usuários de teste (todos com a MESMA senha abaixo). Arquivo local, fora do git.\n"
        f"Senha: {senha}\n" + "\n".join(f"- {c}" for c in cred) + "\n", encoding="utf-8")
    print("\nResumo (linhas criadas pela API):")
    for k, v in sorted(R.criados.items()):
        print(f"  {k:28s} {v}")
    print(f"\nFalhas: {len(R.falhas)}")
    print(f"Credenciais dos usuários de teste gravadas em {a.credenciais} (não impressas aqui).")


if __name__ == "__main__":
    principal()
