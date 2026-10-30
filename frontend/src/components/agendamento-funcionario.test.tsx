// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Servico } from "@/lib/agendamentos";

// Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
const { salvarAgendamento } = vi.hoisted(() => ({
  salvarAgendamento: vi.fn(async (_id: unknown, _prev: unknown, fd: FormData) => ({
    mensagem: "Horário indisponível.",
    valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
  })),
}));
vi.mock("@/app/actions/agendamentos", () => ({ salvarAgendamento }));
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

  it("mantém serviço e profissional escolhidos após erro da action", async () => {
    const user = userEvent.setup();
    render(
      <AgendamentoForm
        servicos={[...servicos, { ...servicos[0], id: 2, nome: "Tosa" }]}
        petInicial={petInicial}
        funcionarios={[
          { id: 7, usuarioId: 3, nome: "Ana Tosadora" },
          { id: 8, usuarioId: 4, nome: "Bia Banhista" },
        ]}
      />,
    );
    await user.selectOptions(screen.getByLabelText(/Serviço/), "2");
    await user.selectOptions(screen.getByLabelText("Profissional"), "8");
    await user.click(screen.getByRole("button", { name: "Confirmar agendamento" }));

    expect(await screen.findByText("Horário indisponível.")).toBeDefined();
    expect(salvarAgendamento).toHaveBeenCalled();
    expect(screen.getByLabelText(/Serviço/)).toHaveProperty("value", "2");
    expect(screen.getByLabelText("Profissional")).toHaveProperty("value", "8");
  });
});
