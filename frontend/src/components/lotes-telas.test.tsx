// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { linhaVazia, type AlertasEstoque, type LinhaLote, type Lote } from "@/lib/lotes";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { DashboardValidade } from "./dashboard-validade";
import { LotesAlertas } from "./lotes-alertas";
import { GradeLotes } from "./lotes-grade";
import { LotesLista } from "./lotes-lista";

const alertas: AlertasEstoque = {
  habilitado: true,
  hoje: "2026-09-25",
  faixas: [
    { deDias: 0, ateDias: 30, lotes: 2, unidades: 12 },
    { deDias: 31, ateDias: 60, lotes: 0, unidades: 0 },
    { deDias: 61, ateDias: 90, lotes: 1, unidades: 4 },
  ],
  vencidos: { lotes: 1, unidades: 3 },
  bloqueados: { lotes: 0, unidades: 0 },
  produtosSemControle: 7,
  produtosAbaixoDoMinimo: 2,
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
  compraId: null,
  fornecedorId: null,
  createdAt: "",
  ...extra,
});

afterEach(() => vi.restoreAllMocks());

describe("alertas de validade", () => {
  it("mostra vencidos, as faixas configuradas, bloqueados, mínimo e sem controle, com ícone e texto", () => {
    render(<LotesAlertas alertas={alertas} />);
    expect(screen.getByText("✕ Lotes vencidos com saldo")).toBeDefined();
    expect(screen.getByText(/Vencem em até 30 dias/)).toBeDefined();
    expect(screen.getByText(/Vencem em 31 a 60 dias/)).toBeDefined();
    expect(screen.getByText(/Vencem em 61 a 90 dias/)).toBeDefined();
    expect(screen.getByText(/Produtos abaixo do mínimo/)).toBeDefined();
    expect(screen.getByText(/Só informativo/)).toBeDefined();
    expect(screen.getByRole("link", { name: /Vencem em até 30 dias/ }).getAttribute("href")).toBe("/estoque/lotes?situacao=30");
    expect(screen.getByRole("link", { name: /Lotes vencidos/ }).getAttribute("href")).toBe("/estoque/lotes?situacao=vencidos");
  });

  it("card do painel resume vencidos e a primeira faixa", () => {
    render(<DashboardValidade alertas={alertas} />);
    expect(screen.getByText("lote(s) vencido(s) com saldo")).toBeDefined();
    expect(screen.getByText(/2 vence\(m\) em até 30 dias/)).toBeDefined();
    expect(screen.getByRole("link").getAttribute("href")).toBe("/estoque/lotes");
  });
});

describe("lista de lotes", () => {
  it("mostra lote, validade DD/MM/AAAA, prazo, saldo, situação e rastreio", () => {
    render(<LotesLista lotes={[lote(), lote({ id: 2, codigoLote: "L-OLD", validade: "2026-09-20", vencido: true, vendavel: false })]} hoje="2026-09-25" />);
    expect(screen.getAllByText("31/12/2026").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/venceu há 5 dia\(s\)/).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Vencido").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: /Rastre/ })[0].getAttribute("href")).toBe("/estoque/lotes/1");
    expect(screen.getAllByRole("link", { name: "Ração Premium" })[0].getAttribute("href")).toBe("/produtos/10?aba=lotes");
  });

  it("sem resultado, explica", () => {
    render(<LotesLista lotes={[]} hoje="2026-09-25" />);
    expect(screen.getByText("Nenhum lote encontrado com estes filtros.")).toBeDefined();
  });
});

describe("grade de lotes", () => {
  function Estado() {
    const [linhas, setLinhas] = useState<LinhaLote[]>([linhaVazia()]);
    return <GradeLotes linhas={linhas} onChange={setLinhas} prefixo="t" esperado={5} rotuloEsperado="teste" exigeValidade />;
  }

  it("adiciona e remove linhas e atualiza a soma em texto", async () => {
    const user = userEvent.setup();
    render(<Estado />);
    expect(screen.getByRole("status").textContent).toMatch(/Faltam 5 para chegar a 5/);
    await user.type(screen.getByLabelText(/^Quantidade/), "5");
    expect(screen.getByRole("status").textContent).toMatch(/confere/);
    await user.click(screen.getByRole("button", { name: "+ Adicionar lote" }));
    expect(screen.getAllByLabelText(/Código do lote/).length).toBe(2);
    await user.click(screen.getByRole("button", { name: "Remover lote 2" }));
    expect(screen.getAllByLabelText(/Código do lote/).length).toBe(1);
  });
});
