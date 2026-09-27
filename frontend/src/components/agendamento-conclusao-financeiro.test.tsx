// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Agendamento } from "@/lib/agendamentos";

const { alterarStatus } = vi.hoisted(() => ({
  alterarStatus: vi.fn(async () => ({ erros: { formaPagamento: "Selecione a forma de pagamento do serviço." } })),
}));
vi.mock("@/app/actions/agendamentos", () => ({ alterarStatus }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { AcoesStatus } from "./acoes-status";

const emAndamento = {
  id: 7,
  status: "EM_ANDAMENTO",
  dataHora: "2026-09-26T10:00:00",
  dataHoraFim: "2026-09-26T10:45:00",
  totalEstimado: 55,
  servico: { id: 1, nome: "Banho", vacinacao: false },
} as unknown as Agendamento;

afterEach(() => {
  vi.restoreAllMocks();
  alterarStatus.mockClear();
});

describe("conclusão do agendamento com financeiro", () => {
  it("sem caixa aberto, bloqueia a conclusão e leva ao caixa", () => {
    render(<AcoesStatus agendamento={emAndamento} papel="SERVICO" horarioAtingido caixaAberto={false} />);
    expect(screen.getByText(/Abra o caixa para concluir o atendimento/)).toBeDefined();
    expect(screen.getByRole("link", { name: "Abrir caixa" }).getAttribute("href")).toBe("/caixa");
    expect(screen.queryByRole("button", { name: "Concluir atendimento" })).toBeNull();
  });

  it("com caixa aberto exige a forma de pagamento", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<AcoesStatus agendamento={emAndamento} papel="SERVICO" horarioAtingido caixaAberto />);
    const opcoes = Array.from((screen.getByLabelText(/Forma de pagamento/) as HTMLSelectElement).options).map((o) => o.textContent);
    expect(opcoes).toEqual(["Selecione…", "Dinheiro", "Pix", "Cartão de débito", "Cartão de crédito", "Transferência"]);

    await user.click(screen.getByRole("button", { name: "Concluir atendimento" }));
    expect(await screen.findByText("Selecione a forma de pagamento do serviço.")).toBeDefined();
  });

  it("envia a forma escolhida na conclusão", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<AcoesStatus agendamento={emAndamento} papel="SERVICO" horarioAtingido caixaAberto />);
    await user.selectOptions(screen.getByLabelText(/Forma de pagamento/), "PIX");
    await user.click(screen.getByRole("button", { name: "Concluir atendimento" }));

    const formData = (alterarStatus.mock.calls[0] as unknown[]).at(-1) as FormData;
    expect(formData.get("status")).toBe("CONCLUIDO");
    expect(formData.get("formaPagamento")).toBe("PIX");
  });
});
