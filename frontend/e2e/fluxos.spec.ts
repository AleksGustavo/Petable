import { expect, test, type Page } from "@playwright/test";
import { lerEstado } from "./api";

/**
 * Fluxos críticos (etapa 09, §15), em série e com o mesmo usuário efêmero:
 * login → cliente + pet → agendamento até a conclusão → venda no PDV → fechamento do caixa → relatório.
 */
test.describe.configure({ mode: "serial" });

const estado = lerEstado();
const cliente = `Cliente E2E ${estado.sufixo}`;
const telefone = `11${String(Date.now()).slice(-9)}`;
let petUrl = "";

async function entrar(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Usuário ou e-mail").fill(estado.usuario.username);
  await page.getByLabel("Senha").fill(estado.usuario.senha);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}

test("1. login: senha errada é recusada; correta entra no painel", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Usuário ou e-mail").fill(estado.usuario.username);
  await page.getByLabel("Senha").fill("senha-errada-123");
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByText(/inválid/i)).toBeVisible();
  await entrar(page);
  const csp = (await page.request.get("/login")).headers()["content-security-policy"];
  expect(csp).toContain("nonce-");
});

test("2. cadastro de cliente e pet", async ({ page }) => {
  await entrar(page);
  await page.goto("/clientes/novo");
  await page.getByLabel("Nome completo").fill(cliente);
  await page.getByLabel("Telefone").fill(telefone);
  await page.getByRole("button", { name: "Cadastrar cliente" }).click();
  await expect(page.getByRole("heading", { name: cliente })).toBeVisible();

  await page.getByRole("link", { name: /novo pet|adicionar pet|cadastrar pet/i }).first().click();
  await page.getByLabel("Nome do pet").fill("Rex E2E");
  await page.getByLabel("Espécie").selectOption({ label: "Cão" });
  await page.getByRole("button", { name: "Cadastrar pet" }).click();
  await expect(page.getByRole("heading", { name: "Rex E2E" })).toBeVisible();
  petUrl = page.url();
});

test("3. agendamento: agendar, confirmar, iniciar e concluir com caixa aberto", async ({ page }) => {
  await entrar(page);
  // O usuário do E2E é novo a cada execução: sempre começa sem caixa.
  await page.goto("/caixa");
  await page.getByLabel("Fundo de troco (R$)").fill("50,00");
  await page.getByRole("button", { name: "Abrir caixa" }).click();
  await expect(page.getByRole("heading", { name: "Fechar caixa" })).toBeVisible();

  const petId = petUrl.match(/\/pets\/(\d+)/)?.[1];
  expect(petId).toBeTruthy();
  await page.goto(`/agendamentos/novo?petId=${petId}`);
  await page.getByLabel("Serviço").selectOption({ label: "Banho" });
  const amanha = new Date(Date.now() + 86_400_000);
  const data = amanha.toLocaleDateString("sv-SE", { timeZone: "America/Sao_Paulo" });
  await page.getByLabel("Data e hora").fill(`${data}T10:${String(amanha.getMinutes()).padStart(2, "0")}`);
  await page.getByRole("button", { name: "Confirmar agendamento" }).click();
  const acoes = page.getByRole("region", { name: "Ações do agendamento" });
  await expect(acoes).toBeVisible();

  // Cada transição recarrega o formulário: espera a confirmação antes da próxima ação.
  await acoes.getByRole("button", { name: "Confirmar", exact: true }).click();
  await expect(page.getByRole("status").filter({ hasText: "Confirmado" })).toBeVisible();
  await acoes.getByRole("button", { name: "Iniciar atendimento" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Em andamento" })).toBeVisible();
  await acoes.getByLabel("Forma de pagamento").selectOption("PIX");
  // A conclusão pede confirmação (window.confirm): aceita e confere a mensagem.
  page.once("dialog", async (d) => {
    expect(d.message()).toContain("Concluir este atendimento");
    await d.accept();
  });
  await acoes.getByRole("button", { name: "Concluir atendimento" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Concluído" })).toBeVisible();
});

test("4. venda no PDV com caixa aberto", async ({ page }) => {
  await entrar(page);
  page.on("dialog", (d) => d.accept()); // "Finalizar a venda de R$ …?"
  await page.goto("/pdv");
  await page.getByLabel("Código de barras ou nome do produto").fill(estado.produto.codigo);
  await page.getByLabel("Código de barras ou nome do produto").press("Enter");
  const carrinho = page.getByRole("list", { name: "Itens do carrinho" });
  await expect(carrinho.getByText(estado.produto.nome)).toBeVisible();
  await page.getByRole("radiogroup", { name: "Forma de pagamento" }).getByRole("radio", { name: /pix/i }).click();
  await page.getByRole("button", { name: "Finalizar venda" }).click();
  await expect(page.getByRole("heading", { name: /Venda #\d+ concluída/ })).toBeVisible();
});

test("5. fechamento do caixa com conferência", async ({ page }) => {
  await entrar(page);
  page.on("dialog", (d) => d.accept());
  await page.goto("/caixa");
  const fechamento = page.getByRole("form", { name: "Fechamento do caixa" });
  const campos = fechamento.locator('input[name^="contado"]');
  await expect(campos).toHaveCount(5); // uma conferência por forma de pagamento
  // Conta exatamente o esperado de cada forma (dinheiro = fundo + vendas; Pix = serviço + venda).
  for (const campo of await campos.all()) {
    const linha = campo.locator("..");
    const esperado = (await linha.getByText(/^esperado/).textContent()) ?? "0";
    await campo.fill(esperado.replace(/[^\d,]/g, ""));
  }
  await expect(page.getByTestId("diferenca-total")).toContainText(/sem diferença|R\$\s?0,00/i);
  await fechamento.getByRole("button", { name: /fechar caixa/i }).click();
  await expect(page.getByText("Caixa fechado.")).toBeVisible();
});

test("6. relatório de vendas do dia mostra a venda", async ({ page }) => {
  await entrar(page);
  const hoje = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Sao_Paulo" });
  await page.goto(`/relatorios/vendas?dataInicio=${hoje}&dataFim=${hoje}`);
  await expect(page.getByRole("heading", { name: "Vendas" })).toBeVisible();
  await expect(page.getByText("Faturamento líquido")).toBeVisible();
  await expect(page.getByText(/R\$\s?\d/).first()).toBeVisible();
});
