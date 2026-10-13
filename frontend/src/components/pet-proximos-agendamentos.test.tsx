// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Agendamento } from "@/lib/agendamentos";

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ProximosAgendamentos } from "./proximos-agendamentos";

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

describe("ProximosAgendamentos", () => {
  it("renderizaProximosAgendamentos", () => {
    render(<ProximosAgendamentos petId={42} agendamentos={[base]} podeAgendar={false} />);
    expect(screen.getByText(/25\/09\/2026 09:00/)).toBeDefined();
    expect(screen.getByText(/Banho/)).toBeDefined();
    expect(screen.getByRole("link", { name: /Banho/ }).getAttribute("href")).toBe("/agendamentos/100");
    expect(screen.queryByText("Agendar serviço")).toBeNull();
  });

  it("renderizaEstadoVazio", () => {
    render(<ProximosAgendamentos petId={42} agendamentos={[]} podeAgendar={false} />);
    expect(screen.getByText("Nenhum agendamento futuro para este pet.")).toBeDefined();
  });

  it("renderizaLinkParaNovoAgendamentoComPet", () => {
    render(<ProximosAgendamentos petId={42} agendamentos={[]} podeAgendar />);
    expect(screen.getByRole("link", { name: "Agendar serviço" }).getAttribute("href")).toBe("/agendamentos/novo?petId=42");
  });
});
