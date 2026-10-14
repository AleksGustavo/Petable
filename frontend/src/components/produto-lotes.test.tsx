// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Produto } from "@/lib/estoque";
import type { Lote } from "@/lib/lotes";

const { ativarControleLote, acaoSobreLote } = vi.hoisted(() => ({
  ativarControleLote: vi.fn(async () => ({})),
  acaoSobreLote: vi.fn(async () => ({})),
}));
vi.mock("@/app/actions/estoque", () => ({ ativarControleLote, acaoSobreLote }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ProdutoLotes } from "./produto-lotes";

const produto: Produto = {
  id: 10,
  nome: "Ração Premium",
  descricao: null,
  categoria: { id: 1, nome: "Ração" },
  fornecedorPadrao: null,
  codigoBarras: "7891234567890",
  precoCusto: 22,
  precoVenda: 169.9,
  quantidadeEstoque: 10,
  estoqueMinimo: 5,
  abaixoDoMinimo: false,
  ativo: true,
  createdAt: "",
  updatedAt: "",
  controlaLote: false,
  permiteSemValidade: false,
  diasBloqueioValidade: null,
};

const lote = (extra: Partial<Lote> = {}): Lote => ({
  id: 1,
  produtoId: 10,
  produtoNome: "Ração Premium",
  codigoLote: "L-001",
  validade: "2026-12-31",
  saldo: 6,
  status: "DISPONIVEL",
  vencido: false,
  vendavel: true,
  custoUnitario: 22,
  dataRecebimento: "2026-09-01",
  compraId: 50,
  fornecedorId: 4,
  createdAt: "",
  ...extra,
});

/** Texto de todas as regiões de status da tela (a grade e o aviso informativo). */
const somaTexto = () => screen.getAllByRole("status").map((e) => e.textContent).join(" | ");

afterEach(() => {
  vi.restoreAllMocks();
  ativarControleLote.mockClear();
  acaoSobreLote.mockClear();
});

describe("aba Lotes do produto", () => {
  it("com o controle desligado na instalação, só informa e não oferece nada", () => {
    render(<ProdutoLotes produto={produto} lotes={[]} habilitado={false} podeGerenciar hoje="2026-09-25" />);
    expect(screen.getByText(/ainda não foi liberado nesta instalação/)).toBeDefined();
    expect(screen.queryByRole("button", { name: "Ligar controle de lote" })).toBeNull();
  });

  it("quem não é ADMIN não vê o botão de ligar o controle", () => {
    render(<ProdutoLotes produto={produto} lotes={[]} habilitado podeGerenciar={false} hoje="2026-09-25" />);
    expect(screen.getByText(/Só o administrador pode ligar o controle/)).toBeDefined();
    expect(screen.queryByRole("button", { name: "Ligar controle de lote" })).toBeNull();
  });

  it("ligar com saldo pede a distribuição exata e bloqueia soma diferente, sem chamar a ação", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<ProdutoLotes produto={produto} lotes={[]} habilitado podeGerenciar hoje="2026-09-25" />);

    expect(screen.getByText(/já tem/)).toBeDefined();
    expect(screen.getByText(/Nenhum lote ou validade é inventado/)).toBeDefined();
    await user.type(screen.getByLabelText(/Código do lote 1/), "L-001");
    await user.type(screen.getByLabelText(/^Validade/), "2027-01-31");
    await user.type(screen.getByLabelText(/^Quantidade/), "4");
    expect(somaTexto()).toMatch(/Faltam 6 para chegar a 10/);

    await user.click(screen.getByRole("button", { name: "Ligar controle de lote" }));
    expect(confirmar).not.toHaveBeenCalled();
    expect(ativarControleLote).not.toHaveBeenCalled();
    expect((await screen.findAllByText(/Faltam 6 para chegar a 10/)).length).toBeGreaterThan(0);
  });

  it("soma exata libera o envio depois da confirmação, com os lotes no campo oculto", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    const { container } = render(<ProdutoLotes produto={produto} lotes={[]} habilitado podeGerenciar hoje="2026-09-25" />);

    await user.type(screen.getByLabelText(/Código do lote 1/), "L-001");
    await user.type(screen.getByLabelText(/^Validade/), "2027-01-31");
    await user.type(screen.getByLabelText(/^Quantidade/), "10");
    expect(somaTexto()).toMatch(/confere/);
    await user.click(screen.getByRole("button", { name: "Ligar controle de lote" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("10 unidade(s) será distribuído em 1 lote(s)"));
    const oculto = container.querySelector('input[name="lotes"]') as HTMLInputElement;
    expect(JSON.parse(oculto.value)).toEqual([{ codigo: "L-001", validade: "2027-01-31", quantidade: "10" }]);
  });

  it("lote sem validade só passa se o operador desmarcar 'Exigir validade'", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<ProdutoLotes produto={{ ...produto, quantidadeEstoque: 3 }} lotes={[]} habilitado podeGerenciar hoje="2026-09-25" />);

    await user.type(screen.getByLabelText(/Código do lote 1/), "SEM");
    await user.type(screen.getByLabelText(/^Quantidade/), "3");
    await user.click(screen.getByRole("button", { name: "Ligar controle de lote" }));
    expect(await screen.findByText("Este produto exige validade")).toBeDefined();

    await user.click(screen.getByLabelText(/Exigir validade em todos os lotes/));
    await user.click(screen.getByRole("button", { name: "Ligar controle de lote" }));
    expect(screen.queryByText("Este produto exige validade")).toBeNull();
  });

  it("produto sem saldo liga direto, sem grade", () => {
    render(<ProdutoLotes produto={{ ...produto, quantidadeEstoque: 0 }} lotes={[]} habilitado podeGerenciar hoje="2026-09-25" />);
    expect(screen.getByText(/Sem saldo no momento/)).toBeDefined();
    expect(screen.queryByLabelText(/Código do lote 1/)).toBeNull();
  });

  it("produto controlado lista lotes com validade em DD/MM/AAAA, prazo, situação em texto e rastreio", () => {
    const controlado = { ...produto, controlaLote: true };
    render(
      <ProdutoLotes
        produto={controlado}
        lotes={[lote(), lote({ id: 2, codigoLote: "L-OLD", validade: "2026-09-20", vencido: true, vendavel: false, saldo: 2 }), lote({ id: 3, codigoLote: "L-Q", status: "BLOQUEADO", vendavel: false })]}
        habilitado
        podeGerenciar
        hoje="2026-09-25"
      />,
    );
    expect(screen.getAllByText(/Validade 31\/12\/2026/).length).toBeGreaterThan(0);
    expect(screen.getByText(/venceu há 5 dia\(s\)/)).toBeDefined();
    expect(screen.getByText("Disponível")).toBeDefined();
    expect(screen.getByText("Vencido")).toBeDefined();
    expect(screen.getByText("Bloqueado")).toBeDefined();
    expect(screen.getAllByRole("link", { name: /Rastreabilidade/ })[0].getAttribute("href")).toBe("/estoque/lotes/1");
  });

  it("ações do lote só para ADMIN: baixar vencido aparece só em lote vencido com saldo", () => {
    const controlado = { ...produto, controlaLote: true };
    const lotes = [lote(), lote({ id: 2, codigoLote: "L-OLD", vencido: true, vendavel: false, saldo: 2 })];
    const { rerender } = render(<ProdutoLotes produto={controlado} lotes={lotes} habilitado podeGerenciar hoje="2026-09-25" />);
    const ok = screen.getByText("Ações do lote L-001").closest("details") as HTMLElement;
    const velho = screen.getByText("Ações do lote L-OLD").closest("details") as HTMLElement;
    expect(within(ok).getByRole("button", { name: "Bloquear lote" })).toBeDefined();
    expect(within(ok).getByRole("button", { name: "Registrar perda" })).toBeDefined();
    expect(within(ok).queryByRole("button", { name: "Baixar vencido" })).toBeNull();
    expect(within(velho).getByRole("button", { name: "Baixar vencido" })).toBeDefined();

    rerender(<ProdutoLotes produto={controlado} lotes={lotes} habilitado podeGerenciar={false} hoje="2026-09-25" />);
    expect(screen.queryByText(/Ações do lote/)).toBeNull();
    expect(screen.queryByRole("button", { name: "Bloquear lote" })).toBeNull();
  });

  it("perda exige quantidade e motivo e pede confirmação", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<ProdutoLotes produto={{ ...produto, controlaLote: true }} lotes={[lote()]} habilitado podeGerenciar hoje="2026-09-25" />);

    await user.type(screen.getByLabelText(/^Quantidade/), "2");
    await user.type(screen.getAllByLabelText(/Motivo/)[1], "Quebra no transporte");
    await user.click(screen.getByRole("button", { name: "Registrar perda" }));
    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("Registrar perda no lote L-001"));
    expect(acaoSobreLote).not.toHaveBeenCalled();
  });
});
