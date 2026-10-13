// @vitest-environment jsdom
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Servico } from "@/lib/agendamentos";

const { salvarAgendamento } = vi.hoisted(() => ({
  salvarAgendamento: vi.fn(async () => ({ mensagem: "O pet já possui um agendamento que se sobrepõe a este horário." })),
}));
vi.mock("@/app/actions/agendamentos", () => ({ salvarAgendamento }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { AgendamentoForm } from "./agendamento-form";

const servicos = [
  { id: 1, nome: "Banho", preco: 50, duracaoMinutos: 60, ativo: true, vacinacao: false, descricao: null, precosPorPorte: [] },
] as Servico[];

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const json = (body: unknown) => ({ ok: true, json: async () => body });
      if (url.startsWith("/api/clientes")) return json([{ id: 7, nome: "Ana Souza" }]);
      if (url.startsWith("/api/pets")) return json([{ id: 3, nome: "Thor" }, { id: 4, nome: "Luna" }]);
      if (url.startsWith("/api/agendamentos/preco")) return json({ totalEstimado: 55, duracaoMinutos: 60, origemPreco: "PORTE" });
      return { ok: false, json: async () => null };
    }),
  );
});
afterEach(() => vi.unstubAllGlobals());

const petInicial = { id: 42, nome: "Mel", tutor: { id: 10, nome: "Maria Silva" } };

describe("AgendamentoForm", () => {
  it("carregaPetsAposSelecionarTutor", async () => {
    const user = userEvent.setup();
    render(<AgendamentoForm servicos={servicos} />);
    expect(screen.getByLabelText(/^Pet/)).toHaveProperty("disabled", true);

    await user.type(screen.getByLabelText(/Tutor/), "Ana");
    await user.click(await screen.findByRole("button", { name: /Ana Souza/ }, { timeout: 2000 }));

    await waitFor(() => expect(screen.getByRole("option", { name: "Thor" })).toBeDefined());
    expect(screen.getByLabelText(/^Pet/)).toHaveProperty("disabled", false);
    expect(fetch).toHaveBeenCalledWith("/api/pets?clienteId=7");
  });

  it("preSelecionaPetRecebidoNaUrl", () => {
    render(<AgendamentoForm servicos={servicos} petInicial={petInicial} />);
    expect(screen.getByLabelText(/^Pet/)).toHaveProperty("value", "42");
    expect(screen.getByText("Maria Silva")).toBeDefined();
  });

  it("exibePrecoAposSelecionarPetEServico", async () => {
    const user = userEvent.setup();
    render(<AgendamentoForm servicos={servicos} petInicial={petInicial} />);
    await user.selectOptions(screen.getByLabelText(/Serviço/), "1");

    expect(await screen.findByText(/R\$\s55,00/)).toBeDefined();
    expect(fetch).toHaveBeenCalledWith("/api/agendamentos/preco?petId=42&servicoId=1");
  });

  it("exibeDuracaoPrevista", async () => {
    const user = userEvent.setup();
    render(<AgendamentoForm servicos={servicos} petInicial={petInicial} />);
    await user.selectOptions(screen.getByLabelText(/Serviço/), "1");

    expect(await screen.findByText("Duração prevista: 60 minutos")).toBeDefined();
  });

  it("exibeErroDeConflito", async () => {
    const user = userEvent.setup();
    render(<AgendamentoForm servicos={servicos} petInicial={petInicial} />);
    await user.selectOptions(screen.getByLabelText(/Serviço/), "1");
    await user.click(screen.getByRole("button", { name: "Confirmar agendamento" }));

    expect(await screen.findByText("O pet já possui um agendamento que se sobrepõe a este horário.")).toBeDefined();
    expect(salvarAgendamento).toHaveBeenCalled();
  });
});
