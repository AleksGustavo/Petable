// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Funcionario } from "@/lib/usuarios";

vi.mock("@/app/actions/usuarios", () => ({ atualizarMeuFuncionario: vi.fn(async () => ({})) }));

import { PerfilFuncionario } from "./perfil-funcionario";

const funcionario: Funcionario = {
  id: 7, usuarioId: 10, username: "ana", nome: "Ana Souza", cpf: "52998224725", dataNascimento: "1995-04-20",
  sexo: "FEMININO", telefone: "19999990000", endereco: null, observacoes: null, disponivel: true,
};

describe("Perfil", () => {
  it("avisa quando a conta não tem funcionário", () => {
    render(<PerfilFuncionario funcionario={null} />);
    expect(screen.getByText("Esta conta não possui um perfil de funcionário vinculado.")).toBeDefined();
  });

  it("permite editar dados do funcionário, mas mostra o CPF só para leitura", () => {
    render(<PerfilFuncionario funcionario={funcionario} />);
    expect(screen.getByLabelText(/Nome completo/)).toHaveProperty("value", "Ana Souza");
    expect(screen.getByText("529.982.247-25")).toBeDefined();
    expect(screen.queryByLabelText("CPF")).toBeNull();
    expect(screen.getByLabelText("Telefone")).toHaveProperty("value", "(19) 99999-0000");
  });
});
