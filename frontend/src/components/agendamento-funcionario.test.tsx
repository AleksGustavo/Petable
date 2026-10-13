// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Servico } from "@/lib/agendamentos";

vi.mock("@/app/actions/agendamentos", () => ({ salvarAgendamento: vi.fn(async () => ({})) }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { AgendamentoForm } from "./agendamento-form";

const servicos = [{ id: 1, nome: "Banho", preco: 50, duracaoMinutos: 60, ativo: true, vacinacao: false, descricao: null, precosPorPorte: [] }] as Servico[];
const petInicial = { id: 42, nome: "Mel", tutor: { id: 10, nome: "Maria Silva" } };

describe("Funcionário no agendamento", () => {
  it("lista só os funcionários ativos recebidos e permite não definir", () => {
    render(<AgendamentoForm servicos={servicos} petInicial={petInicial} funcionarios={[{ id: 7, usuarioId: 3, nome: "Ana Tosadora" }]} />);
    const select = screen.getByLabelText("Profissional") as HTMLSelectElement;
    expect([...select.options].map((o) => o.text)).toEqual(["Sem funcionário definido", "Ana Tosadora"]);
    expect(select.value).toBe("");
  });

  it("não mostra o campo quando não há funcionários", () => {
    render(<AgendamentoForm servicos={servicos} petInicial={petInicial} funcionarios={[]} />);
    expect(screen.queryByLabelText("Profissional")).toBeNull();
  });
});
