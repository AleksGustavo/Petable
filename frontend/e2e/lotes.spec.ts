import { randomBytes } from "node:crypto";
import { expect, test, type Page } from "@playwright/test";
import { chamar, tokenAdmin } from "./api";

/**
 * Controle de lote e validade (etapa 05): o fluxo completo do balcão e da gestão, no navegador e em série.
 *  1. produto controlado: ligar o controle, receber compra com dois lotes, consultar estoque e alerta de validade
 *  2. vender no PDV (FEFO), conferir a baixa, cancelar devolvendo ao lote, conferir auditoria e caixa
 *  3. inventário (ajuste por lote)
 *  4. produto SEM lote continua como sempre
 *  5. produto com estoque LEGADO a regularizar
 *  6. reconciliação do estoque sem divergência nos produtos do teste
 * Exige a stack com PETCONNECT_ESTOQUE_LOTES=true (o workflow de E2E liga a flag).
 */
test.describe.configure({ mode: "serial" });

const FUSO = "America/Sao_Paulo";
const sufixo = Date.now().toString(36);
const senha = `E2e-${randomBytes(9).toString("base64url")}9a`;
const username = `e2e.lote.${sufixo}`;

const dia = (aFrente: number) => new Date(Date.now() + aFrente * 86_400_000).toLocaleDateString("sv-SE", { timeZone: FUSO });

type Produto = { id: number; nome: string; codigo: string };
let controlado: Produto;
let semLote: Produto;
let legado: Produto;
let compraId = 0;
let vendaId = 0;
let tokenUsuario = "";

function cnpjAleatorio(): string {
  const d = Array.from({ length: 8 }, () => Math.floor(Math.random() * 10)).concat([0, 0, 0, 1]);
  for (const t of [12, 13]) {
    let soma = 0;
    let peso = t - 7;
    for (let i = 0; i < t; i++) {
      soma += d[i] * peso;
      peso = peso === 2 ? 9 : peso - 1;
    }
    const resto = soma % 11;
    d.push(resto < 2 ? 0 : 11 - resto);
  }
  return d.join("");
}

async function criarProduto(token: string, rotulo: string, categoriaId: number): Promise<Produto> {
  const codigo = `789${(Date.now() + Math.floor(Math.random() * 1000)).toString().slice(-10)}`;
  const nome = `${rotulo} ${sufixo}`;
  const p = await chamar<{ id: number }>("POST", "/api/v1/produtos", token, {
    nome,
    categoriaId,
    codigoBarras: codigo,
    precoCusto: "20.00",
    precoVenda: "49.90",
    estoqueMinimo: 1,
  });
  return { id: p.id, nome, codigo };
}

test.beforeAll(async () => {
  const admin = await tokenAdmin();
  await chamar("POST", "/api/v1/usuarios", admin, { username, email: `${username}@petconnect.test`, senha, papel: "ADMIN" });
  tokenUsuario = (await chamar<{ accessToken: string }>("POST", "/api/v1/auth/login", undefined, { login: username, senha })).accessToken;

  const categorias = await chamar<{ id: number }[]>("GET", "/api/v1/categorias-produto", admin);
  controlado = await criarProduto(admin, "Ração Lote E2E", categorias[0].id);
  semLote = await criarProduto(admin, "Brinquedo Sem Lote E2E", categorias[0].id);
  legado = await criarProduto(admin, "Vacina Legado E2E", categorias[0].id);
  await chamar("POST", `/api/v1/produtos/${semLote.id}/ajustes-estoque`, admin, { quantidade: 5, motivo: "Saldo do teste E2E" });
  await chamar("POST", `/api/v1/produtos/${legado.id}/ajustes-estoque`, admin, { quantidade: 6, motivo: "Saldo legado do teste E2E" });

  const fornecedor = await chamar<{ id: number }>("POST", "/api/v1/fornecedores", admin, {
    nomeFantasia: `Fornecedor Lote ${sufixo}`,
    razaoSocial: `Fornecedor Lote ${sufixo} Ltda`,
    cnpj: cnpjAleatorio(),
    telefone: null,
    email: "contato@fornecedor.com.br",
    contato: "Carlos",
    endereco: { cep: "13610000", logradouro: "Rua Exemplo", numero: "500", bairro: "Centro", cidade: "Leme", uf: "SP" },
  });
  const compra = await chamar<{ id: number }>("POST", "/api/v1/compras", admin, {
    fornecedorId: fornecedor.id,
    dataCompra: dia(0),
    formaPagamento: "BOLETO",
    observacoes: "E2E de lotes",
    itens: [{ produtoId: controlado.id, quantidade: 10, precoCustoUnitario: 20 }],
  });
  compraId = compra.id;
});

async function entrar(page: Page) {
  page.on("dialog", (d) => d.accept());
  await page.goto("/login");
  await page.getByLabel("Usuário ou e-mail").fill(username);
  await page.getByLabel("Senha").fill(senha);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}

async function preencherLote(page: Page, indice: number, codigo: string, validade: string, quantidade: string) {
  await page.getByLabel(`Código do lote ${indice + 1}`).fill(codigo);
  await page.getByLabel(/^Validade/).nth(indice).fill(validade);
  await page.getByLabel(/^Quantidade/).nth(indice).fill(quantidade);
}

test("1. produto controlado: liga o controle, recebe a compra com dois lotes e consulta o estoque", async ({ page }) => {
  await entrar(page);
  // O usuário é novo: abre o caixa (necessário para vender e cancelar mais adiante).
  await page.goto("/caixa");
  await page.getByLabel("Fundo de troco (R$)").fill("50,00");
  await page.getByRole("button", { name: "Abrir caixa" }).click();
  await expect(page.getByRole("heading", { name: "Fechar caixa" })).toBeVisible();

  // Liga o controle de lote (produto sem saldo liga direto).
  await page.goto(`/produtos/${controlado.id}?aba=lotes`);
  await expect(page.getByRole("heading", { name: "Controlar por lote" })).toBeVisible();
  await expect(page.getByLabel(/Exigir validade em todos os lotes/)).toBeChecked();
  await page.getByRole("button", { name: "Ligar controle de lote" }).click();
  await expect(page.getByText("Controle de lote ligado")).toBeVisible();

  // Recebe a compra de 10 unidades em dois lotes. Soma errada bloqueia; soma certa confere.
  await page.goto(`/compras/${compraId}`);
  await preencherLote(page, 0, "L-A", dia(20), "4");
  await page.getByRole("button", { name: "+ Adicionar lote" }).click();
  await preencherLote(page, 1, "L-B", dia(90), "5");
  await expect(page.getByRole("status").filter({ hasText: /Faltam 1 para chegar a 10/ })).toBeVisible();
  await page.getByRole("button", { name: "Receber compra" }).click();
  await expect(page.getByText(/Faltam 1 para chegar a 10/).first()).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/compras/${compraId}$`));

  await page.getByLabel(/^Quantidade/).nth(1).fill("6");
  await expect(page.getByRole("status").filter({ hasText: /confere/ })).toBeVisible();
  await page.getByRole("button", { name: "Receber compra" }).click();
  await expect(page.getByText("Recebimento registrado")).toBeVisible();
  await expect(page.getByText(/L-A \(val\. \d{2}\/\d{2}\/\d{4}\) × 4/).filter({ visible: true }).first()).toBeVisible();

  // Consulta de estoque por código do lote e alerta de validade (L-A vence em 20 dias).
  await page.goto(`/estoque/lotes?busca=${encodeURIComponent("L-A")}`);
  const linha = page.getByRole("row").filter({ hasText: controlado.nome });
  await expect(linha).toContainText("L-A");
  await expect(linha).toContainText("Disponível");
  const alertas = page.getByRole("region", { name: "Alertas de validade e estoque" });
  await expect(alertas.getByRole("link", { name: /Vencem em até 30 dias/ })).toBeVisible();
  await page.goto("/dashboard");
  await expect(page.getByText("Validade dos lotes")).toBeVisible();
});

test("2. vende no PDV (FEFO), confere a baixa, cancela devolvendo ao lote e confere auditoria e caixa", async ({ page }) => {
  await entrar(page);
  await page.goto("/pdv");
  const busca = page.getByLabel("Código de barras ou nome do produto");
  await busca.fill(controlado.codigo);
  await busca.press("Enter");
  await expect(page.getByRole("list", { name: "Itens do carrinho" }).getByText(controlado.nome)).toBeVisible();
  await page.getByRole("spinbutton", { name: `Quantidade de ${controlado.nome}` }).fill("5");
  await page.getByRole("radiogroup", { name: "Forma de pagamento" }).getByRole("radio", { name: /pix/i }).click();
  await page.getByRole("button", { name: "Finalizar venda" }).click();
  const titulo = page.getByRole("heading", { name: /Venda #\d+ concluída/ });
  await expect(titulo).toBeVisible();
  vendaId = Number((await titulo.textContent())?.match(/#(\d+)/)?.[1]);
  expect(vendaId).toBeGreaterThan(0);

  // FEFO: 4 do lote que vence antes (L-A) e 1 do outro (L-B).
  await page.goto(`/vendas/${vendaId}`);
  await expect(page.getByText(/L-A \(val\. \d{2}\/\d{2}\/\d{4}\) × 4 · L-B \(val\. \d{2}\/\d{2}\/\d{4}\) × 1/)).toBeVisible();
  await page.goto(`/produtos/${controlado.id}?aba=lotes`);
  await expect(page.getByLabel("Saldo do lote L-A")).toHaveText("0");
  await expect(page.getByLabel("Saldo do lote L-B")).toHaveText("5");

  // Cancelar exige escolher o destino; devolve ao lote de origem.
  await page.goto(`/vendas/${vendaId}`);
  await page.getByLabel("Motivo").fill("Cliente desistiu da compra");
  await page.getByRole("button", { name: "Cancelar venda" }).click();
  await expect(page.getByText("Escolha o que fazer com o produto devolvido.")).toBeVisible();
  await page.getByRole("radio", { name: /Voltar ao estoque/ }).check();
  await page.getByRole("button", { name: "Cancelar venda" }).click();
  await expect(page.getByText(/Cancelada em/)).toBeVisible();
  await page.goto(`/produtos/${controlado.id}?aba=lotes`);
  await expect(page.getByLabel("Saldo do lote L-A")).toHaveText("4");
  await expect(page.getByLabel("Saldo do lote L-B")).toHaveText("6");

  // Auditoria e caixa pela API (o mesmo usuário): eventos registrados e lançamento financeiro da venda.
  const auditoria = await chamar<{ content: { acao: string }[] }>("GET", `/api/v1/auditoria?entidade=venda&entidadeId=${vendaId}`, tokenUsuario);
  expect(auditoria.content.map((e) => e.acao)).toEqual(expect.arrayContaining(["VENDA_REALIZADA", "VENDA_CANCELADA"]));
  const venda = await chamar<{ lancamentoFinanceiroId: number | null; status: string }>("GET", `/api/v1/vendas/${vendaId}`, tokenUsuario);
  expect(venda.status).toBe("CANCELADA");
  expect(venda.lancamentoFinanceiroId).not.toBeNull();
  const caixa = await chamar<{ aberto: boolean }>("GET", "/api/v1/caixas/resumo-atual", tokenUsuario);
  expect(caixa.aberto).toBe(true);
});

test("3. inventário: ajuste por lote com confirmação", async ({ page }) => {
  await entrar(page);
  await page.goto(`/produtos/${controlado.id}?aba=estoque`);
  await page.getByLabel(/^Lote/).selectOption({ label: "L-B · saldo 6" });
  await page.getByLabel(/^Quantidade/).fill("-2");
  await page.getByLabel(/^Motivo/).fill("Contagem física do inventário");
  await page.getByRole("button", { name: "Ajustar estoque" }).click();
  await expect(page.getByText("Ajuste de estoque registrado no histórico.")).toBeVisible();
  await page.goto(`/produtos/${controlado.id}?aba=lotes`);
  await expect(page.getByLabel("Saldo do lote L-B")).toHaveText("4");
});

test("4. produto sem lote continua como sempre", async ({ page }) => {
  await entrar(page);
  await page.goto("/pdv");
  const busca = page.getByLabel("Código de barras ou nome do produto");
  await busca.fill(semLote.codigo);
  await busca.press("Enter");
  await expect(page.getByRole("list", { name: "Itens do carrinho" }).getByText(semLote.nome)).toBeVisible();
  await page.getByRole("radiogroup", { name: "Forma de pagamento" }).getByRole("radio", { name: /pix/i }).click();
  await page.getByRole("button", { name: "Finalizar venda" }).click();
  await expect(page.getByRole("heading", { name: /Venda #\d+ concluída/ })).toBeVisible();

  await page.goto(`/produtos/${semLote.id}?aba=estoque`);
  await expect(page.getByText("Saída por venda").filter({ visible: true }).first()).toBeVisible();
  // Sem controle: a aba Lotes só oferece ligar; nenhum lote existe.
  await page.goto(`/produtos/${semLote.id}?aba=lotes`);
  await expect(page.getByRole("heading", { name: "Controlar por lote" })).toBeVisible();
  const produto = await chamar<{ quantidadeEstoque: number; controlaLote: boolean }>("GET", `/api/v1/produtos/${semLote.id}`, tokenUsuario);
  expect(produto).toMatchObject({ quantidadeEstoque: 4, controlaLote: false });
});

test("5. estoque legado: regulariza o saldo em lotes reais, sem inventar lote", async ({ page }) => {
  await entrar(page);
  await page.goto(`/produtos/${legado.id}?aba=lotes`);
  await expect(page.getByText(/já tem/)).toBeVisible();
  await preencherLote(page, 0, "LEG-1", dia(60), "2");
  await expect(page.getByRole("status").filter({ hasText: /Faltam 4 para chegar a 6/ })).toBeVisible();
  await page.getByRole("button", { name: "Ligar controle de lote" }).click();
  await expect(page.getByText(/Faltam 4 para chegar a 6/).first()).toBeVisible();
  expect((await chamar<{ controlaLote: boolean }>("GET", `/api/v1/produtos/${legado.id}`, tokenUsuario)).controlaLote).toBe(false);

  await page.getByRole("button", { name: "+ Adicionar lote" }).click();
  await preencherLote(page, 1, "LEG-2", dia(120), "4");
  await expect(page.getByRole("status").filter({ hasText: /confere/ })).toBeVisible();
  await page.getByRole("button", { name: "Ligar controle de lote" }).click();
  await expect(page.getByText("Controle de lote ligado")).toBeVisible();
  await expect(page.getByLabel("Saldo do lote LEG-1")).toHaveText("2");
  await expect(page.getByLabel("Saldo do lote LEG-2")).toHaveText("4");
  const produto = await chamar<{ quantidadeEstoque: number }>("GET", `/api/v1/produtos/${legado.id}`, tokenUsuario);
  expect(produto.quantidadeEstoque).toBe(6); // o total não mudou: a regularização só distribui
});

test("6. reconciliação do estoque sem divergência nos produtos do teste", async () => {
  const r = await chamar<{ divergencias: { produtoId: number | null; regra: string }[] }>("GET", "/api/v1/estoque/reconciliacao", tokenUsuario);
  const meus = [controlado.id, semLote.id, legado.id];
  expect(r.divergencias.filter((d) => d.produtoId !== null && meus.includes(d.produtoId))).toEqual([]);
});
