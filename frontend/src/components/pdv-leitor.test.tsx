// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ProdutoRapido } from "@/lib/estoque";
import { PdvLeitor } from "./pdv-leitor";

const racao: ProdutoRapido = { id: 12, nome: "Ração Premium", codigoBarras: "7891234567890", precoVenda: 169.9, quantidadeEstoque: 3, disponivel: true };
const coleira: ProdutoRapido = { id: 10, nome: "Coleira", codigoBarras: "7890000000001", precoVenda: 30, quantidadeEstoque: 0, disponivel: false };
const buscar = vi.fn(async () => [racao, coleira]);

afterEach(() => buscar.mockClear());

describe("PdvLeitor", () => {
  it("Enter com código exato adiciona direto e limpa o campo", async () => {
    const user = userEvent.setup();
    const onAdicionar = vi.fn();
    render(<PdvLeitor onAdicionar={onAdicionar} buscar={buscar} />);
    const campo = screen.getByLabelText("Código de barras ou nome do produto");
    expect(document.activeElement).toBe(campo);

    await user.type(campo, "7891234567890{Enter}");

    expect(onAdicionar).toHaveBeenCalledWith(racao);
    expect(campo).toHaveProperty("value", "");
  });

  it("Tab (enviado por alguns leitores) também adiciona", async () => {
    const user = userEvent.setup();
    const onAdicionar = vi.fn();
    render(<PdvLeitor onAdicionar={onAdicionar} buscar={buscar} />);
    await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "7891234567890");
    await user.keyboard("{Tab}");
    expect(onAdicionar).toHaveBeenCalledWith(racao);
  });

  it("texto mostra a lista de resultados", async () => {
    const user = userEvent.setup();
    const onAdicionar = vi.fn();
    render(<PdvLeitor onAdicionar={onAdicionar} buscar={buscar} />);

    await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "ra");
    await user.click(await screen.findByRole("button", { name: "Adicionar Ração Premium" }));

    expect(buscar).toHaveBeenCalledWith("ra");
    expect(onAdicionar).toHaveBeenCalledWith(racao);
  });

  it("produto sem estoque aparece desabilitado e o código dele não adiciona", async () => {
    const user = userEvent.setup();
    const onAdicionar = vi.fn();
    render(<PdvLeitor onAdicionar={onAdicionar} buscar={buscar} />);

    await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "co");
    expect(await screen.findByRole("button", { name: "Coleira sem estoque" })).toHaveProperty("disabled", true);

    await user.clear(screen.getByLabelText("Código de barras ou nome do produto"));
    await user.type(screen.getByLabelText("Código de barras ou nome do produto"), "7890000000001{Enter}");
    expect(await screen.findByText("Coleira está sem estoque.")).toBeDefined();
    expect(onAdicionar).not.toHaveBeenCalled();
  });
});
