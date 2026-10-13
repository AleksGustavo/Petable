// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Caixa } from "@/lib/financeiro";

const { movimentarCaixa } = vi.hoisted(() => ({
  movimentarCaixa: vi.fn(async () => ({ mensagem: "A sangria não pode exceder o saldo esperado em dinheiro." })),
}));
vi.mock("@/app/actions/financeiro", () => ({ movimentarCaixa, abrirCaixa: vi.fn(), fecharCaixa: vi.fn() }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { CaixaOperacao } from "./caixa";

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
  movimentarCaixa.mockClear();
});

describe("CaixaOperacao", () => {
  it("mostra o esperado por forma e os movimentos", () => {
    render(<CaixaOperacao caixa={caixa} />);
    expect(screen.getByTestId("esperado-DINHEIRO").textContent).toMatch(/730,00/);
    expect(screen.getByTestId("esperado-PIX").textContent).toMatch(/980,00/);
    expect(screen.getByText(/Reforço de troco/)).toBeDefined();
    expect(screen.getByText(/Retirada preventiva/)).toBeDefined();
  });

  it("sangria pede confirmação e mostra o 409 de sangria excedente", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<CaixaOperacao caixa={caixa} />);
    const sangria = screen.getByRole("form", { name: "Sangria" });

    await user.type(within(sangria).getByLabelText(/Valor/), "900");
    await user.type(within(sangria).getByLabelText(/Motivo/), "Retirada grande");
    await user.click(within(sangria).getByRole("button", { name: "Registrar sangria" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("Retirar R$ 900 do caixa"));
    expect(await within(sangria).findByText("A sangria não pode exceder o saldo esperado em dinheiro.")).toBeDefined();
  });

  it("suprimento cancelado na confirmação não envia", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<CaixaOperacao caixa={caixa} />);
    const suprimento = screen.getByRole("form", { name: "Suprimento" });
    await user.click(within(suprimento).getByRole("button", { name: "Registrar suprimento" }));
    expect(movimentarCaixa).not.toHaveBeenCalled();
  });
});
