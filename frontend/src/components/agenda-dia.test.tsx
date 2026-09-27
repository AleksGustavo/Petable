// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Agendamento } from "@/lib/agendamentos";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { AgendaLista } from "./agenda-lista";

const base: Agendamento = {
  id: 100,
  dataHora: "2026-09-25T09:00:00",
  fimPrevisto: "2026-09-25T10:00:00",
  status: "CONFIRMADO",
  statusDescricao: "Confirmado",
  pet: { id: 42, nome: "Mel", porte: "MEDIO", especieId: 1 },
  tutor: { id: 10, nome: "Maria Silva" },
  servico: { id: 1, nome: "Banho", duracaoMinutos: 60, vacinacao: false },
  funcionario: null,
  totalEstimado: 55,
  observacoes: null,
  realizado: null,
};

describe("AgendaLista", () => {
  it("renderizaEstadoVazio", () => {
    const { rerender } = render(<AgendaLista agendamentos={[]} />);
    expect(screen.getByText("Nenhum agendamento para esta data.")).toBeDefined();
    rerender(<AgendaLista agendamentos={[]} filtrado />);
    expect(screen.getByText("Nenhum agendamento encontrado com os filtros selecionados.")).toBeDefined();
  });

  it("renderizaAgendamentosEmOrdemCronologica", () => {
    const tarde = { ...base, id: 101, dataHora: "2026-09-25T14:00:00", fimPrevisto: "2026-09-25T15:00:00", pet: { ...base.pet, nome: "Thor" } };
    render(<AgendaLista agendamentos={[tarde, base]} />);
    const links = screen.getAllByRole("link");
    expect(links.map((l) => l.getAttribute("href"))).toEqual(["/agendamentos/100", "/agendamentos/101"]);
  });

  it("renderizaCardsNoModeloDeDadosEsperado", () => {
    render(<AgendaLista agendamentos={[base]} />);
    expect(screen.getByText("09:00–10:00")).toBeDefined();
    expect(screen.getByText("Mel • Maria Silva")).toBeDefined();
    expect(screen.getByText("Banho")).toBeDefined();
    expect(screen.getByText(/R\$\s55,00/)).toBeDefined();
  });

  it("renderizaStatusComTexto", () => {
    render(<AgendaLista agendamentos={[base, { ...base, id: 2, status: "CANCELADO", dataHora: "2026-09-25T11:00:00" }]} />);
    expect(screen.getByText("Confirmado")).toBeDefined();
    expect(screen.getByText("Cancelado")).toBeDefined();
  });

  it("agrupaPorDiaNaSemana", () => {
    render(<AgendaLista agruparPorDia agendamentos={[base, { ...base, id: 3, dataHora: "2026-09-26T09:00:00" }]} />);
    expect(screen.getByRole("heading", { name: /25\/09\/2026/ })).toBeDefined();
    expect(screen.getByRole("heading", { name: /26\/09\/2026/ })).toBeDefined();
  });
});
