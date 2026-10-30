// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { FormState } from "@/lib/clientes";
import { FuncionarioForm } from "./funcionario-form";

describe("FuncionarioForm", () => {
  it("mantém o sexo escolhido após erro da action", async () => {
    // Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
    const action = vi.fn(async (_prev: FormState, fd: FormData): Promise<FormState> => ({
      mensagem: "Já existe um funcionário com este CPF.",
      valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
    }));
    const user = userEvent.setup();
    render(<FuncionarioForm action={action} modo="admin" />);

    await user.type(screen.getByLabelText(/Nome completo/), "Ana Souza");
    await user.selectOptions(screen.getByLabelText("Sexo"), "FEMININO");
    await user.click(screen.getByRole("button", { name: "Cadastrar funcionário" }));

    expect(await screen.findByText("Já existe um funcionário com este CPF.")).toBeDefined();
    expect(action).toHaveBeenCalled();
    expect(screen.getByLabelText("Sexo")).toHaveProperty("value", "FEMININO");
    expect(screen.getByLabelText(/Nome completo/)).toHaveProperty("value", "Ana Souza");
  });
});
