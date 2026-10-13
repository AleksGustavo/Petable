// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ContaPagar } from "@/lib/financeiro";

const { pagarConta } = vi.hoisted(() => ({ pagarConta: vi.fn(async () => ({ mensagem: "Esta conta já foi paga." })) }));
vi.mock("@/app/actions/financeiro", () => ({ pagarConta }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ContaDetalhes } from "./contas-pagar";

const conta: ContaPagar = {
  id: 40,
  compraId: 12,
  fornecedor: { id: 4, nome: "Pet Distribuidora" },
  descricao: "Compra #12",
  valorOriginal: 1250,
  saldo: 1250,
  dataVencimento: "2026-10-10",
  status: "ABERTA",
  vencida: false,
  situacao: "A_VENCER",
  pagaEm: null,
  formaPagamento: null,
  usuarioPagamento: null,
  lancamentoId: null,
};

afterEach(() => {
  vi.restoreAllMocks();
  pagarConta.mockClear();
});

describe("ContaDetalhes", () => {
  it("pagamento integral pede confirmação com valor e forma", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<ContaDetalhes conta={conta} caixaAberto />);

    await user.selectOptions(screen.getByLabelText(/Forma de pagamento/), "PIX");
    await user.click(screen.getByRole("button", { name: "Pagar conta" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringMatching(/Pagar R\$\s1\.250,00 \(valor integral\) em Pix/));
    expect(pagarConta).not.toHaveBeenCalled();
  });

  it("mostra o 409 da API (conta já paga)", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<ContaDetalhes conta={conta} caixaAberto />);
    await user.selectOptions(screen.getByLabelText(/Forma de pagamento/), "PIX");
    await user.click(screen.getByRole("button", { name: "Pagar conta" }));
    expect(await screen.findByText("Esta conta já foi paga.")).toBeDefined();
  });

  it("sem caixa aberto bloqueia o pagamento", () => {
    render(<ContaDetalhes conta={conta} caixaAberto={false} />);
    expect(screen.getByText(/Não há caixa aberto/)).toBeDefined();
    expect(screen.getByRole("button", { name: "Pagar conta" })).toHaveProperty("disabled", true);
  });

  it("conta paga mostra os dados do pagamento e nenhuma ação", () => {
    render(
      <ContaDetalhes
        conta={{ ...conta, status: "PAGA", situacao: "PAGA", saldo: 0, pagaEm: "2026-09-26T13:40:00", formaPagamento: "PIX", usuarioPagamento: { id: 1, nome: "admin" }, lancamentoId: 530 }}
        caixaAberto
      />,
    );
    expect(screen.getByText("Paga")).toBeDefined();
    expect(screen.getByRole("link", { name: "#530" }).getAttribute("href")).toBe("/financeiro/lancamentos/530");
    expect(screen.queryByRole("button")).toBeNull();
  });
});
