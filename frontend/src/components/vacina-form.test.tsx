// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

// Como a action real: devolve os valores para o formulário repopular após o reset do React 19.
const { registrarVacina } = vi.hoisted(() => ({
  registrarVacina: vi.fn(async (_petId: unknown, _prev: unknown, fd: FormData) => ({
    mensagem: "A vacina selecionada não é indicada para a espécie deste pet.",
    valores: Object.fromEntries([...fd.entries()].map(([k, v]) => [k, String(v)])),
  })),
}));
vi.mock("@/app/actions/pets", () => ({ registrarVacina }));

import { VacinaForm } from "./vacina-form";

const vacinas = [
  { id: 4, nome: "Raiva", doencaProtecao: "Raiva", validadePadraoMeses: 12 },
  { id: 5, nome: "V10", doencaProtecao: null, validadePadraoMeses: 12 },
];

describe("VacinaForm", () => {
  it("mantém a vacina escolhida após erro da action", async () => {
    const user = userEvent.setup();
    render(<VacinaForm petId={42} vacinas={vacinas} />);
    await user.selectOptions(screen.getByLabelText(/^Vacina/), "5");
    await user.click(screen.getByRole("button", { name: "Registrar vacina" }));

    expect(await screen.findByText("A vacina selecionada não é indicada para a espécie deste pet.")).toBeDefined();
    expect(registrarVacina).toHaveBeenCalled();
    expect(screen.getByLabelText(/^Vacina/)).toHaveProperty("value", "5");
  });
});
