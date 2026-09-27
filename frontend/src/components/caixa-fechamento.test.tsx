// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Caixa } from "@/lib/financeiro";

const { fecharCaixa } = vi.hoisted(() => ({ fecharCaixa: vi.fn(async () => ({})) }));
vi.mock("@/app/actions/financeiro", () => ({ fecharCaixa, abrirCaixa: vi.fn(), movimentarCaixa: vi.fn() }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { CaixaFechamento } from "./caixa";

const caixa: Caixa = {
  id: 18,
  usuario: { id: 5, nome: "joana" },
  status: "ABERTO",
  abertoEm: "2026-09-26T08:02:14",
  fundoAbertura: 200,
  fechadoEm: null,
  esperado: { DINHEIRO: 730, CARTAO_CREDITO: 1200, CARTAO_DEBITO: 0, PIX: 980, TRANSFERENCIA: 0 },
  contado: null,
  diferenca: null,
  totalEsperado: 2910,
  diferencaTotal: null,
  motivoDiferenca: null,
  movimentos: [
    { id: 1, tipo: "SUPRIMENTO", valor: 100, motivo: "Reforço de troco", usuario: { id: 5, nome: "joana" }, createdAt: "2026-09-26T12:00:00Z" },
    { id: 2, tipo: "SANGRIA", valor: 200, motivo: "Retirada preventiva", usuario: { id: 5, nome: "joana" }, createdAt: "2026-09-26T13:00:00Z" },
  ],
};

afterEach(() => {
  vi.restoreAllMocks();
  fecharCaixa.mockClear();
});

async function contar(user: ReturnType<typeof userEvent.setup>, dinheiro: string) {
  await user.type(screen.getByLabelText("Dinheiro"), dinheiro);
  await user.type(screen.getByLabelText("Cartão de crédito"), "1200");
  await user.type(screen.getByLabelText("Cartão de débito"), "0");
  await user.type(screen.getByLabelText("Pix"), "980");
  await user.type(screen.getByLabelText("Transferência"), "0");
}

describe("CaixaFechamento", () => {
  it("só habilita o fechamento com todos os valores contados", async () => {
    const user = userEvent.setup();
    render(<CaixaFechamento caixa={caixa} />);
    expect(screen.getByRole("button", { name: "Fechar caixa" })).toHaveProperty("disabled", true);
    await contar(user, "730");
    expect(screen.getByRole("button", { name: "Fechar caixa" })).toHaveProperty("disabled", false);
    expect(screen.getByTestId("diferenca-total").textContent).toMatch(/Sem diferença/);
    expect(screen.queryByLabelText(/Motivo da diferença/)).toBeNull();
  });

  it("mostra a diferença enquanto digita e exige motivo", async () => {
    const user = userEvent.setup();
    render(<CaixaFechamento caixa={caixa} />);
    await contar(user, "725");

    expect(screen.getByTestId("diferenca-DINHEIRO").textContent).toMatch(/Falta de R\$\s5,00/);
    expect(screen.getByTestId("diferenca-total").textContent).toMatch(/Falta de R\$\s5,00/);
    expect(screen.getByLabelText(/Motivo da diferença/)).toBeDefined();
  });

  it("confirma com a diferença antes de fechar e não envia se cancelar", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<CaixaFechamento caixa={caixa} />);
    await contar(user, "735");
    await user.click(screen.getByRole("button", { name: "Fechar caixa" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringMatching(/diferença de R\$\s5,00.*definitivo/));
    expect(fecharCaixa).not.toHaveBeenCalled();
  });

  it("envia os valores contados e o esperado da prévia", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<CaixaFechamento caixa={caixa} />);
    await contar(user, "730");
    await user.click(screen.getByRole("button", { name: "Fechar caixa" }));

    const formData = (fecharCaixa.mock.calls[0] as unknown[]).at(-1) as FormData;
    expect(formData.get("contadoDINHEIRO")).toBe("730");
    expect(JSON.parse(String(formData.get("esperado"))).DINHEIRO).toBe(730);
  });
});
