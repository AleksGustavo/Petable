// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

// Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
const { criarLancamento } = vi.hoisted(() => ({
  criarLancamento: vi.fn(async (_prev: unknown, fd: FormData) => ({
    mensagem: "Falha temporária.",
    valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
  })),
}));
vi.mock("@/app/actions/financeiro", () => ({ criarLancamento }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { LancamentoForm } from "./lancamento-form";

afterEach(() => criarLancamento.mockClear());

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

async function preencher(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByLabelText(/Tipo/), "SAIDA");
  await user.selectOptions(screen.getByLabelText(/Categoria/), "DESPESA_OPERACIONAL");
  await user.type(screen.getByLabelText(/Descrição/), "Material de limpeza");
  await user.type(screen.getByLabelText(/Valor/), "89,90");
  await user.selectOptions(screen.getByLabelText(/Forma de pagamento/), "DINHEIRO");
}

describe("LancamentoForm", () => {
  it("valida no navegador antes de enviar", async () => {
    const user = userEvent.setup();
    render(<LancamentoForm caixaAberto />);
    await user.click(screen.getByRole("button", { name: "Registrar lançamento" }));
    expect(await screen.findByText("A descrição deve ter entre 3 e 100 caracteres")).toBeDefined();
    expect(criarLancamento).not.toHaveBeenCalled();
  });

  it("só oferece categorias manuais", () => {
    render(<LancamentoForm caixaAberto />);
    const opcoes = Array.from((screen.getByLabelText(/Categoria/) as HTMLSelectElement).options).map((o) => o.value);
    expect(opcoes).toEqual(["", "DESPESA_FUNCIONARIO", "DESPESA_OPERACIONAL", "OUTROS"]);
  });

  it("envia a mesma Idempotency-Key num novo envio após falha", async () => {
    const user = userEvent.setup();
    render(<LancamentoForm caixaAberto />);
    await preencher(user);
    await user.click(screen.getByRole("button", { name: "Registrar lançamento" }));
    expect(await screen.findByText("Falha temporária.")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Registrar lançamento" }));

    const chaves = criarLancamento.mock.calls.map((c) => ((c as unknown[]).at(-1) as FormData).get("chave"));
    expect(chaves).toHaveLength(2);
    expect(String(chaves[0])).toMatch(UUID);
    expect(chaves[1]).toBe(chaves[0]);
  });

  it("sem caixa aberto avisa e bloqueia o envio", () => {
    render(<LancamentoForm caixaAberto={false} />);
    expect(screen.getByText(/Não há caixa aberto/)).toBeDefined();
    expect(screen.getByRole("link", { name: "Abrir caixa" }).getAttribute("href")).toBe("/caixa");
    expect(screen.getByRole("button", { name: "Registrar lançamento" })).toHaveProperty("disabled", true);
  });
});
