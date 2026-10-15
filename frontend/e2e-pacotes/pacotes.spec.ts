import { expect, test, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { chamar, tokenAdmin } from "../e2e/api";
import { ARQUIVO_PACOTE, type EstadoPacote } from "./global-setup";

/**
 * Interface por pacote (issue #42). A API foi iniciada com os módulos do pacote (E2E_PACOTE); o que o usuário vê depende do módulo
 * E do papel. As expectativas abaixo são LITERAIS e independentes do código do menu: se alguém mudar uma regra, o teste acusa.
 * A API continua sendo a barreira real (403 MODULO_INDISPONIVEL, coberto nos ITs); aqui se verifica que a tela é coerente com ela.
 */
type Papel = "ADMIN" | "VENDAS" | "SERVICO";

const PACOTES: Record<string, string[]> = {
  pdv: ["produtos", "estoque", "caixa", "pdv", "relatorios"],
  petshop: ["clientes", "produtos", "estoque", "lotes", "caixa", "pdv", "compras", "financeiro", "pets", "agenda", "relatorios"],
  completo: ["clientes", "produtos", "estoque", "lotes", "caixa", "pdv", "compras", "financeiro", "pets", "clinica", "agenda", "relatorios"],
};

const pacote = process.env.E2E_PACOTE ?? "";
const modulos = PACOTES[pacote];
const estado = (() => {
  try {
    return JSON.parse(readFileSync(ARQUIVO_PACOTE, "utf-8")) as EstadoPacote;
  } catch {
    return undefined;
  }
})();

// rótulo do menu → módulo dono (null = núcleo) e papéis que o veem (as regras de papel não mudaram com a modularização)
const MENU: { rotulo: string; modulo: string | null; papeis: Papel[] }[] = [
  { rotulo: "Início", modulo: null, papeis: ["ADMIN", "VENDAS", "SERVICO"] },
  { rotulo: "Clientes", modulo: "clientes", papeis: ["ADMIN", "VENDAS", "SERVICO"] },
  { rotulo: "Pets", modulo: "pets", papeis: ["ADMIN", "VENDAS", "SERVICO"] },
  { rotulo: "Agendamentos", modulo: "agenda", papeis: ["ADMIN", "VENDAS", "SERVICO"] },
  { rotulo: "Produtos", modulo: "produtos", papeis: ["ADMIN", "VENDAS", "SERVICO"] },
  { rotulo: "Lotes e validade", modulo: "lotes", papeis: ["ADMIN", "VENDAS"] },
  { rotulo: "Fornecedores", modulo: "compras", papeis: ["ADMIN", "VENDAS"] },
  { rotulo: "Compras", modulo: "compras", papeis: ["ADMIN", "VENDAS"] },
  { rotulo: "Vendas (PDV)", modulo: "pdv", papeis: ["ADMIN", "VENDAS"] },
  { rotulo: "Vendas", modulo: "pdv", papeis: ["ADMIN", "VENDAS"] },
  { rotulo: "Caixa", modulo: "caixa", papeis: ["ADMIN", "VENDAS", "SERVICO"] },
  { rotulo: "Financeiro", modulo: "financeiro", papeis: ["ADMIN"] },
  { rotulo: "Contas a pagar", modulo: "financeiro", papeis: ["ADMIN"] },
  { rotulo: "Relatórios", modulo: "relatorios", papeis: ["ADMIN", "VENDAS"] },
  { rotulo: "Usuários", modulo: null, papeis: ["ADMIN"] },
];

const ROTA: Record<string, string> = {
  Clientes: "/clientes",
  Pets: "/pets",
  Agendamentos: "/agendamentos",
  Produtos: "/produtos",
  "Lotes e validade": "/estoque/lotes",
  Fornecedores: "/fornecedores",
  Compras: "/compras",
  "Vendas (PDV)": "/pdv",
  Vendas: "/vendas",
  Caixa: "/caixa",
  Financeiro: "/financeiro",
  "Contas a pagar": "/financeiro/contas-a-pagar",
  Relatórios: "/relatorios",
};

const tem = (modulo: string | null) => modulo === null || modulos.includes(modulo);
const esperado = (papel: Papel) => MENU.filter((i) => tem(i.modulo) && i.papeis.includes(papel)).map((i) => i.rotulo).sort();

test.skip(!modulos || !estado, "defina E2E_PACOTE (pdv | petshop | completo) e rode com playwright.pacotes.config.ts");

async function entrar(page: Page, papel: Papel) {
  const u = estado!.usuarios[papel];
  await page.goto("/login");
  await page.getByLabel("Usuário ou e-mail").fill(u.username);
  await page.getByLabel("Senha").fill(u.senha);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}

for (const papel of ["ADMIN", "VENDAS", "SERVICO"] as const) {
  test(`menu (${papel}): só os itens do módulo contratado E do papel`, async ({ page }) => {
    await entrar(page, papel);
    const itens = await page.getByRole("navigation", { name: "Principal" }).first().getByRole("link").allInnerTexts();
    expect(itens.map((t) => t.trim()).sort()).toEqual(esperado(papel));
  });
}

test("URL direta: rota de módulo não contratado mostra 'Recurso indisponível'; a contratada abre normalmente", async ({ page }) => {
  await entrar(page, "ADMIN");
  for (const [rotulo, rota] of Object.entries(ROTA)) {
    const item = MENU.find((i) => i.rotulo === rotulo)!;
    await page.goto(rota);
    const indisponivel = page.getByRole("heading", { name: "Recurso indisponível" });
    if (tem(item.modulo)) {
      await expect(indisponivel, `${rota} deveria estar disponível`).toHaveCount(0);
    } else {
      await expect(indisponivel, `${rota} deveria estar indisponível`).toBeVisible();
      // Sem citar módulo e sem dado do domínio.
      await expect(page.getByText(/módulo|contrat/i)).toHaveCount(0);
      await expect(page).toHaveURL(new RegExp(`${rota}$`));
    }
  }
});

test("painel: cartões só dos módulos contratados", async ({ page }) => {
  await entrar(page, "ADMIN");
  const clientes = page.getByText("Clientes ativos");
  const atalho = page.getByText("Cadastrar novo cliente");
  if (tem("clientes")) {
    await expect(clientes).toBeVisible();
    await expect(atalho).toBeVisible();
  } else {
    await expect(clientes).toHaveCount(0);
    await expect(atalho).toHaveCount(0);
  }
  // O painel em si (núcleo) sempre abre, sem erro de API de módulo desligado.
  await expect(page.getByRole("heading", { name: "Painel do petshop" })).toBeVisible();
});

test("relatórios: só os de origem contratada aparecem no hub", async ({ page }) => {
  await entrar(page, "ADMIN");
  await page.goto("/relatorios");
  const principal = page.getByRole("main");
  const por: [string, string | null][] = [
    ["Vendas", "pdv"],
    ["Estoque: valor, giro e cobertura", "estoque"],
    ["Caixas", "caixa"],
    ["Contas a pagar", "financeiro"],
    ["Despesas", "financeiro"],
    ["Serviços", "agenda"],
  ];
  for (const [titulo, modulo] of por) {
    const alvo = principal.getByRole("link", { name: titulo, exact: false }).filter({ hasText: titulo });
    if (tem(modulo)) await expect(alvo.first(), `relatório ${titulo}`).toBeVisible();
    else await expect(alvo, `relatório ${titulo} não deveria aparecer`).toHaveCount(0);
  }
});

test("ficha do pet: carteira e consultas só com a clínica contratada", async ({ page }) => {
  test.skip(!tem("pets"), "pacote sem pets");
  const token = await tokenAdmin();
  const tel = `11${String(Date.now()).slice(-9)}`;
  const cliente = await chamar<{ id: number }>("POST", "/api/v1/clientes", token, { nome: `Tutor Pacote ${estado!.sufixo}`, telefone: tel });
  const especies = await chamar<{ id: number; nome: string }[]>("GET", "/api/v1/especies", token);
  const pet = await chamar<{ id: number }>("POST", "/api/v1/pets", token, {
    clienteId: cliente.id,
    nome: "Pet Pacote",
    especieId: especies[0].id,
    racaId: null,
    dataNascimento: "2023-05-20",
    peso: 10.5,
    cor: "Caramelo",
    porte: "MEDIO",
    castrado: true,
  });
  await entrar(page, "ADMIN");
  await page.goto(`/pets/${pet.id}`);
  await expect(page.getByRole("heading", { name: "Pet Pacote" })).toBeVisible();
  const abas = page.getByRole("navigation", { name: "Seções do pet" }).getByRole("link");
  if (tem("clinica")) {
    await expect(abas).toHaveText(["Dados", "Vacinas", "Consultas"]);
  } else {
    await expect(abas).toHaveText(["Dados"]);
  }
  // Próximos agendamentos aparecem só com a agenda.
  const proximos = page.getByText(/Próximos agendamentos/i);
  if (tem("agenda")) await expect(proximos.first()).toBeVisible();
  else await expect(proximos).toHaveCount(0);
});

test("produto: aba de lotes só com o módulo de lotes", async ({ page }) => {
  const token = await tokenAdmin();
  const categorias = await chamar<{ id: number }[]>("GET", "/api/v1/categorias-produto", token);
  const nome = `Produto Pacote ${estado!.sufixo}`;
  const p = await chamar<{ id: number }>("POST", "/api/v1/produtos", token, {
    nome,
    categoriaId: categorias[0].id,
    codigoBarras: null,
    precoCusto: "10.00",
    precoVenda: "20.00",
    estoqueMinimo: 0,
  });
  await entrar(page, "ADMIN");
  await page.goto(`/produtos/${p.id}`);
  await expect(page.getByRole("heading", { name: nome })).toBeVisible();
  const abas = page.getByRole("navigation", { name: "Seções do produto" }).getByRole("link");
  await expect(abas).toHaveText(tem("lotes") ? ["Dados", "Estoque", "Lotes"] : ["Dados", "Estoque"]);
});
