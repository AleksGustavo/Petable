// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { VacinaCatalogo } from "@/lib/pets";
import type { VacinaEstoque } from "@/lib/lotes";

const { vincularVacina, desvincularVacina } = vi.hoisted(() => ({
  vincularVacina: vi.fn(async () => ({})),
  desvincularVacina: vi.fn(async () => ({})),
}));
vi.mock("@/app/actions/estoque", () => ({ vincularVacina, desvincularVacina }));

import { LoteDaVacina } from "./lote-da-vacina";
import { ProdutoVacinas } from "./produto-vacinas";

const vacinas: VacinaCatalogo[] = [
  { id: 1, nome: "Raiva (Antirrábica Canina)", doencaProtecao: "Raiva", validadePadraoMeses: 12, baixaEstoque: true },
  { id: 2, nome: "V8 Polivalente", doencaProtecao: null, validadePadraoMeses: 12, baixaEstoque: false },
];

/** O select da vacina vive no formulário da tela; aqui um igual, só para o campo de lote acompanhar. */
function Tela() {
  return (
    <>
      <select id="campo-vacina" defaultValue="" aria-label="Vacina">
        <option value="">Selecione…</option>
        {vacinas.map((v) => (
          <option key={v.id} value={v.id}>{v.nome}</option>
        ))}
      </select>
      <LoteDaVacina vacinas={vacinas} selectId="campo-vacina" />
    </>
  );
}

afterEach(() => {
  vi.restoreAllMocks();
  vincularVacina.mockClear();
  desvincularVacina.mockClear();
});

describe("campo de lote na aplicação de vacina", () => {
  it("vacina que baixa estoque: o campo some e entra o aviso; as outras mantêm o campo", async () => {
    const user = userEvent.setup();
    render(<Tela />);
    expect(screen.getByLabelText(/Lote/)).toBeDefined();

    await user.selectOptions(screen.getByLabelText("Vacina"), "1");
    expect(screen.queryByLabelText(/^Lote/)).toBeNull();
    expect(screen.getByRole("note").textContent).toMatch(/O lote sai do estoque automaticamente/);

    await user.selectOptions(screen.getByLabelText("Vacina"), "2");
    expect(screen.getByLabelText(/Lote/)).toBeDefined();
    expect(screen.queryByRole("note")).toBeNull();

    await user.selectOptions(screen.getByLabelText("Vacina"), "");
    expect(screen.getByLabelText(/Lote/)).toBeDefined();
  });

  it("sem o campo, o formulário não envia lote nenhum", async () => {
    const user = userEvent.setup();
    const { container } = render(<form><Tela /></form>);
    await user.selectOptions(screen.getByLabelText("Vacina"), "1");
    expect(container.querySelector('input[name="lote"]')).toBeNull();
  });
});

const raiva: VacinaEstoque = { id: 1, nome: "Raiva (Antirrábica Canina)", especie: "Cão", produto: { id: 10, nome: "Vacina Raiva 1 dose" } };
const v8: VacinaEstoque = { id: 2, nome: "V8 Polivalente", especie: "Cão", produto: null };
const v10: VacinaEstoque = { id: 3, nome: "V10 Polivalente", especie: "Cão", produto: { id: 99, nome: "Outro produto" } };

describe("vacinas do produto", () => {
  it("ADMIN vê as ligadas, pode desvincular com confirmação e vincular outra", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<ProdutoVacinas produtoId={10} vinculadas={[raiva]} catalogo={[raiva, v8, v10]} podeGerenciar />);

    expect(screen.getByRole("list", { name: "Vacinas ligadas ao produto" }).textContent).toMatch(/Raiva/);
    await user.click(screen.getByRole("button", { name: "Desvincular" }));
    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("voltam a pedir o lote digitado"));
    expect(desvincularVacina).not.toHaveBeenCalled();

    const opcoes = screen.getAllByRole("option").map((o) => o.textContent);
    expect(opcoes.some((t) => t?.includes("V8 Polivalente"))).toBe(true);
    expect(opcoes.some((t) => t?.includes("V10 Polivalente") && t.includes("hoje: Outro produto"))).toBe(true);
    // A que já está ligada a este produto não aparece para vincular de novo.
    expect(opcoes.some((t) => t?.includes("Raiva"))).toBe(false);
  });

  it("quem não é ADMIN só lê a lista", () => {
    render(<ProdutoVacinas produtoId={10} vinculadas={[raiva]} catalogo={[]} podeGerenciar={false} />);
    expect(screen.getByText(/Raiva/)).toBeDefined();
    expect(screen.queryByRole("button", { name: "Desvincular" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Vincular" })).toBeNull();
  });

  it("sem vacina ligada, explica", () => {
    render(<ProdutoVacinas produtoId={10} vinculadas={[]} catalogo={[v8]} podeGerenciar />);
    expect(screen.getByText("Nenhuma vacina ligada a este produto.")).toBeDefined();
    expect(screen.getByRole("button", { name: "Vincular" })).toBeDefined();
  });
});
