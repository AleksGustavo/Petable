// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Agendamento } from "@/lib/agendamentos";

const { alterarStatus } = vi.hoisted(() => ({ alterarStatus: vi.fn(async () => ({})) }));
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

afterEach(() => {
  vi.restoreAllMocks();
  alterarStatus.mockClear();
});

describe("AcoesStatus", () => {
  it("exibeConfirmarParaAgendadoQuandoVendas", () => {
    render(<AcoesStatus agendamento={{ ...base, status: "AGENDADO" }} papel="VENDAS" horarioAtingido={false} />);
    expect(screen.getByRole("button", { name: "Confirmar" })).toBeDefined();
  });

  it("naoExibeConfirmarParaServico", () => {
    render(<AcoesStatus agendamento={{ ...base, status: "AGENDADO" }} papel="SERVICO" horarioAtingido={false} />);
    expect(screen.queryByRole("button", { name: "Confirmar" })).toBeNull();
  });

  it("exibeIniciarParaServicoQuandoConfirmado", () => {
    render(<AcoesStatus agendamento={base} papel="SERVICO" horarioAtingido={false} />);
    expect(screen.getByRole("button", { name: "Iniciar atendimento" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Cancelar agendamento" })).toBeNull();
  });

  it("solicitaConfirmacaoAoCancelar", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<AcoesStatus agendamento={base} papel="VENDAS" horarioAtingido={false} />);

    await user.type(screen.getByLabelText(/Motivo do cancelamento/), "Tutor pediu");
    await user.click(screen.getByRole("button", { name: "Cancelar agendamento" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("Cancelar este agendamento"));
    expect(alterarStatus).not.toHaveBeenCalled();
  });

  it("solicitaConfirmacaoAoConcluir", async () => {
    const user = userEvent.setup();
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<AcoesStatus agendamento={{ ...base, status: "EM_ANDAMENTO" }} papel="SERVICO" horarioAtingido />);

    expect(screen.getByLabelText(/Valor do serviço/)).toHaveProperty("value", "55");
    await user.click(screen.getByRole("button", { name: "Concluir atendimento" }));

    expect(confirmar).toHaveBeenCalledWith(expect.stringContaining("Concluir este atendimento"));
    expect(alterarStatus).toHaveBeenCalled();
  });
});
