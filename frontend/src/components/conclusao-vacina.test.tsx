// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Agendamento } from "@/lib/agendamentos";

const { alterarStatus } = vi.hoisted(() => ({
  alterarStatus: vi.fn(async (...args: unknown[]) => {
    const fd = args.at(-1) as FormData;
    // Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
    return { mensagem: "A vacina selecionada não é indicada para a espécie deste pet.", valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])) };
  }),
}));
vi.mock("@/app/actions/agendamentos", () => ({ alterarStatus }));

import { AcoesStatus } from "./acoes-status";

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

const vacinacao: Agendamento = {
  ...base,
  status: "EM_ANDAMENTO",
  servico: { id: 9, nome: "Vacinação", duracaoMinutos: 15, vacinacao: true },
};
const vacinas = [{ id: 4, nome: "Raiva (Antirrábica Canina)", doencaProtecao: "Raiva", validadePadraoMeses: 12 }];

describe("Conclusão de vacinação", () => {
  it("exibeCamposDeVacinaParaServicoVacinacao", () => {
    render(<AcoesStatus agendamento={vacinacao} papel="SERVICO" horarioAtingido vacinas={vacinas} />);
    expect(screen.getByText("Vacina aplicada")).toBeDefined();
    expect(screen.getByRole("option", { name: "Raiva (Antirrábica Canina)" })).toBeDefined();
    expect(screen.getByLabelText("Data de aplicação")).toBeDefined();
  });

  it("naoExibeCamposDeVacinaParaOutroServico", () => {
    render(<AcoesStatus agendamento={{ ...base, status: "EM_ANDAMENTO" }} papel="SERVICO" horarioAtingido />);
    expect(screen.queryByText("Vacina aplicada")).toBeNull();
    expect(screen.queryByLabelText("Data de aplicação")).toBeNull();
  });

  it("exibeErroDeVacinaDaApi", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<AcoesStatus agendamento={vacinacao} papel="VENDAS" horarioAtingido vacinas={vacinas} />);

    await user.selectOptions(screen.getByLabelText(/^Vacina/), "4");
    await user.click(screen.getByRole("button", { name: "Concluir atendimento" }));

    expect(await screen.findByText("A vacina selecionada não é indicada para a espécie deste pet.")).toBeDefined();
  });

  it("mantemVacinaEFormaDePagamentoAposErro", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<AcoesStatus agendamento={vacinacao} papel="VENDAS" horarioAtingido vacinas={vacinas} />);

    await user.selectOptions(screen.getByLabelText(/^Vacina/), "4");
    await user.selectOptions(screen.getByLabelText(/Forma de pagamento/), "PIX");
    await user.click(screen.getByRole("button", { name: "Concluir atendimento" }));

    expect(await screen.findByText("A vacina selecionada não é indicada para a espécie deste pet.")).toBeDefined();
    expect(screen.getByLabelText(/^Vacina/)).toHaveProperty("value", "4");
    expect(screen.getByLabelText(/Forma de pagamento/)).toHaveProperty("value", "PIX");
  });
});
