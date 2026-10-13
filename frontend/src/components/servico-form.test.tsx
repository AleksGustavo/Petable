// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

const { salvarServico } = vi.hoisted(() => ({ salvarServico: vi.fn(async () => ({})) }));
vi.mock("@/app/actions/agendamentos", () => ({ salvarServico }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ServicoForm } from "./servico-form";

afterEach(() => salvarServico.mockClear());

describe("ServicoForm", () => {
  it("validaNomeObrigatorio", async () => {
    const user = userEvent.setup();
    render(<ServicoForm />);
    await user.click(screen.getByRole("button", { name: "Cadastrar serviço" }));

    expect(await screen.findByText("Informe o nome")).toBeDefined();
    expect(salvarServico).not.toHaveBeenCalled();
  });

  it("validaDuracao", async () => {
    const user = userEvent.setup();
    render(<ServicoForm />);
    await user.type(screen.getByLabelText(/^Nome/), "Tosa Higiênica");
    await user.type(screen.getByLabelText(/Preço padrão/), "50");
    await user.type(screen.getByLabelText(/Duração/), "2");
    await user.click(screen.getByRole("button", { name: "Cadastrar serviço" }));

    expect(await screen.findByText("A duração deve ser de 5 a 1440 minutos")).toBeDefined();
    expect(salvarServico).not.toHaveBeenCalled();
  });

  it("editaPrecosPorPorte", async () => {
    const user = userEvent.setup();
    render(
      <ServicoForm
        servico={{ id: 1, nome: "Banho", descricao: null, preco: 50, duracaoMinutos: 45, ativo: true, vacinacao: false, precosPorPorte: [{ porte: "GRANDE", preco: 60 }] }}
      />,
    );
    expect(screen.getByLabelText("Grande (R$)")).toHaveProperty("value", "60");

    await user.type(screen.getByLabelText("Médio (R$)"), "55,00");
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect(salvarServico).toHaveBeenCalled();
    const formData = (salvarServico.mock.calls[0] as unknown[]).at(-1) as FormData;
    expect(formData.get("precoMEDIO")).toBe("55,00");
    expect(formData.get("precoGRANDE")).toBe("60");
  });
});
