// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

// Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
const { salvarCliente } = vi.hoisted(() => ({
  salvarCliente: vi.fn(async (_id: unknown, _prev: unknown, fd: FormData) => ({
    mensagem: "Já existe um cliente com este CPF.",
    valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
  })),
}));
vi.mock("@/app/actions/clientes", () => ({ salvarCliente }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { ClienteForm } from "./cliente-form";

describe("ClienteForm", () => {
  it("mantém a UF escolhida após erro da action", async () => {
    const user = userEvent.setup();
    render(<ClienteForm />);
    await user.type(screen.getByLabelText(/Nome completo/), "Maria Silva");
    await user.type(screen.getByLabelText(/Telefone/), "19999999999");
    await user.selectOptions(screen.getByLabelText("UF"), "MG");
    await user.click(screen.getByRole("button", { name: "Cadastrar cliente" }));

    expect(await screen.findByText("Já existe um cliente com este CPF.")).toBeDefined();
    expect(salvarCliente).toHaveBeenCalled();
    expect(screen.getByLabelText("UF")).toHaveProperty("value", "MG");
    expect(screen.getByLabelText(/Nome completo/)).toHaveProperty("value", "Maria Silva");
  });
});
