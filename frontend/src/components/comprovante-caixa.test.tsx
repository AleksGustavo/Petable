// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Caixa } from "@/lib/financeiro";

vi.mock("@/app/actions/financeiro", () => ({ abrirCaixa: vi.fn(), fecharCaixa: vi.fn(), movimentarCaixa: vi.fn() }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ComprovanteCaixa } from "./caixa";

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

const fechado: Caixa = {
  ...caixa,
  status: "FECHADO",
  fechadoEm: "2026-09-26T18:05:00",
  contado: { DINHEIRO: 725, CARTAO_CREDITO: 1200, CARTAO_DEBITO: 0, PIX: 980, TRANSFERENCIA: 0 },
  diferenca: { DINHEIRO: -5, CARTAO_CREDITO: 0, CARTAO_DEBITO: 0, PIX: 0, TRANSFERENCIA: 0 },
  diferencaTotal: -5,
  motivoDiferenca: "Faltou uma moeda",
};

afterEach(() => vi.restoreAllMocks());

describe("ComprovanteCaixa", () => {
  it("mostra operador, horários, conferência, diferença e movimentos", () => {
    render(<ComprovanteCaixa caixa={fechado} />);
    expect(screen.getByText(/documento não fiscal/)).toBeDefined();
    expect(screen.getByText(/Caixa nº 18 · joana/)).toBeDefined();
    expect(screen.getByText(/Fechamento: 26\/09\/2026 18:05/)).toBeDefined();
    expect(screen.getByText(/Diferença total: -?R\$\s-?5,00|Diferença total: -R\$/)).toBeDefined();
    expect(screen.getByText("Motivo: Faltou uma moeda")).toBeDefined();
    expect(screen.getByText(/Suprimento: Reforço de troco/)).toBeDefined();
    expect(screen.getByText(/Sangria: Retirada preventiva/)).toBeDefined();
  });

  it("imprime pelo botão", async () => {
    const imprimir = vi.spyOn(window, "print").mockImplementation(() => {});
    render(<ComprovanteCaixa caixa={fechado} />);
    await userEvent.setup().click(screen.getByRole("button", { name: "Imprimir" }));
    expect(imprimir).toHaveBeenCalled();
  });
});
