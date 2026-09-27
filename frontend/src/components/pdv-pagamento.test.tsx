// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { Papel } from "@/lib/jwt";
import { PAGAMENTO_INICIAL, PdvPagamento, type Pagamento } from "./pdv-pagamento";

function Painel({ papel = "VENDAS", onFinalizar = vi.fn(), pendente = false, subtotal = 36980 }: { papel?: Papel; onFinalizar?: () => void; pendente?: boolean; subtotal?: number }) {
  const [valor, setValor] = useState<Pagamento>(PAGAMENTO_INICIAL);
  return (
    <PdvPagamento papel={papel} subtotalCentavos={subtotal} valor={valor} onChange={setValor} onFinalizar={onFinalizar} pendente={pendente} vazio={false} />
  );
}

describe("PdvPagamento", () => {
  it("alterna desconto entre R$ e % e mostra o total", async () => {
    const user = userEvent.setup();
    render(<Painel />);
    await user.type(screen.getByLabelText("Desconto em reais"), "5,00");
    expect(screen.getByTestId("total-venda").textContent).toMatch(/364,80/);

    await user.click(screen.getByRole("radio", { name: "%" }));
    await user.type(screen.getByLabelText("Desconto em porcentagem"), "10");
    // 10% de 369,80 = 36,98
    expect(screen.getByTestId("total-venda").textContent).toMatch(/332,82/);
  });

  it("mostra a dica e o erro do teto de desconto só para VENDAS", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<Painel />);
    expect(screen.getByText("Seu perfil pode dar no máximo 10% de desconto.")).toBeDefined();
    await user.type(screen.getByLabelText("Desconto em reais"), "40,00");
    expect(screen.getByText("Desconto acima do permitido para o seu perfil (máximo 10%).")).toBeDefined();
    unmount();

    render(<Painel papel="ADMIN" />);
    expect(screen.queryByText(/Seu perfil pode dar no máximo/)).toBeNull();
    await user.type(screen.getByLabelText("Desconto em reais"), "40,00");
    expect(screen.queryByText(/acima do permitido/)).toBeNull();
  });

  it("calcula o troco em dinheiro e bloqueia valor recebido menor que o total", async () => {
    const user = userEvent.setup();
    render(<Painel />);
    await user.click(screen.getByRole("radio", { name: "Dinheiro" }));
    await user.type(screen.getByLabelText(/Valor recebido/), "300,00");
    expect(screen.getByText("O valor recebido não pode ser menor que o total.")).toBeDefined();
    expect(screen.getByRole("button", { name: "Finalizar venda" })).toHaveProperty("disabled", true);

    await user.clear(screen.getByLabelText(/Valor recebido/));
    await user.type(screen.getByLabelText(/Valor recebido/), "400,00");
    expect(screen.getByTestId("troco").textContent).toMatch(/30,20/);
    expect(screen.getByRole("button", { name: "Finalizar venda" })).toHaveProperty("disabled", false);
  });

  it("só finaliza com forma de pagamento escolhida", async () => {
    const user = userEvent.setup();
    const onFinalizar = vi.fn();
    render(<Painel onFinalizar={onFinalizar} />);
    expect(screen.getByRole("button", { name: "Finalizar venda" })).toHaveProperty("disabled", true);

    await user.click(screen.getByRole("radio", { name: "Pix" }));
    await user.click(screen.getByRole("button", { name: "Finalizar venda" }));
    expect(onFinalizar).toHaveBeenCalledTimes(1);
  });

  it("botão desabilitado durante o envio", () => {
    render(<Painel pendente />);
    expect(screen.getByRole("button", { name: "Finalizando…" })).toHaveProperty("disabled", true);
  });
});
