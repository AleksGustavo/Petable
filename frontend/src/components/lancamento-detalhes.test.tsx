// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Lancamento } from "@/lib/financeiro";

const { estornarLancamento } = vi.hoisted(() => ({ estornarLancamento: vi.fn(async () => ({})) }));
vi.mock("@/app/actions/financeiro", () => ({ estornarLancamento }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { LancamentoDetalhes } from "./lancamento-detalhes";

const manual: Lancamento = {
  id: 520,
  tipo: "SAIDA",
  categoria: "DESPESA_OPERACIONAL",
  descricao: "Material de limpeza",
  valor: 89.9,
  dataMovimentacao: "2026-09-26",
  formaPagamento: "DINHEIRO",
  usuarioId: 1,
  usuario: { id: 1, nome: "admin" },
  caixaId: 18,
  origem: { tipo: "MANUAL", id: null },
  estornoDeId: null,
  estornadoPorId: null,
  motivoEstorno: null,
  observacoes: "Higienização",
  estornavel: true,
};

afterEach(() => {
  vi.restoreAllMocks();
  estornarLancamento.mockClear();
});

describe("LancamentoDetalhes", () => {
  it("mostra dados, sinal e valor", () => {
    render(<LancamentoDetalhes lancamento={manual} />);
    expect(screen.getByText("− Saída")).toBeDefined();
    expect(screen.getByText(/89,90/)).toBeDefined();
    expect(screen.getByText("Despesa operacional")).toBeDefined();
    expect(screen.getByRole("link", { name: "#18" }).getAttribute("href")).toBe("/caixa/18");
  });

  it("estorno pede confirmação e não envia se cancelar", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<LancamentoDetalhes lancamento={manual} />);

    await user.type(screen.getByLabelText(/Motivo/), "Lançado em duplicidade");
    await user.click(screen.getByRole("button", { name: "Estornar lançamento" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("Estornar o lançamento #520"));
    expect(estornarLancamento).not.toHaveBeenCalled();
  });

  it("automático não oferece estorno direto", () => {
    render(<LancamentoDetalhes lancamento={{ ...manual, categoria: "VENDA_PRODUTO", tipo: "ENTRADA", origem: { tipo: "VENDA", id: 91 }, estornavel: false }} />);
    expect(screen.queryByRole("button", { name: "Estornar lançamento" })).toBeNull();
    expect(screen.getByText(/corrigido pelo módulo de origem/)).toBeDefined();
    expect(screen.getByText("Venda #91")).toBeDefined();
  });

  it("já estornado mostra o vínculo e nenhuma ação", () => {
    render(<LancamentoDetalhes lancamento={{ ...manual, estornavel: false, estornadoPorId: 521 }} />);
    expect(screen.getByRole("link", { name: "lançamento #521" }).getAttribute("href")).toBe("/financeiro/lancamentos/521");
    expect(screen.queryByRole("button")).toBeNull();
  });
});
