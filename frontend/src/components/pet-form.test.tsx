// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

const { salvarPet } = vi.hoisted(() => ({
  salvarPet: vi.fn(async () => ({ erros: { nome: "Já existe um pet com este nome." } })),
}));
vi.mock("@/app/actions/pets", () => ({ salvarPet }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { PetForm } from "./pet-form";

const especies = [
  { id: 1, nome: "Cão", racas: [{ id: 10, nome: "Labrador" }] },
  { id: 2, nome: "Gato", racas: [{ id: 20, nome: "Siamês" }] },
];

function renderizar() {
  return render(<PetForm especies={especies} tutorInicial={{ id: 7, nome: "Ana" }} />);
}

describe("PetForm", () => {
  it("deixa a raça desabilitada sem espécie", () => {
    renderizar();
    expect(screen.getByLabelText("Raça")).toHaveProperty("disabled", true);
  });

  it("limpa a raça ao trocar a espécie e mostra só as raças da nova espécie", async () => {
    const user = userEvent.setup();
    renderizar();
    await user.selectOptions(screen.getByLabelText(/Espécie/), "1");
    await user.selectOptions(screen.getByLabelText("Raça"), "10");
    expect(screen.getByLabelText("Raça")).toHaveProperty("value", "10");

    await user.selectOptions(screen.getByLabelText(/Espécie/), "2");

    const raca = screen.getByLabelText("Raça") as HTMLSelectElement;
    expect(raca.value).toBe("");
    expect([...raca.options].map((o) => o.text)).toEqual(["Não informada", "Siamês"]);
  });

  it("mostra no campo o erro devolvido pela Server Action", async () => {
    const user = userEvent.setup();
    renderizar();
    await user.type(screen.getByLabelText(/Nome do pet/), "Thor");
    await user.selectOptions(screen.getByLabelText(/Espécie/), "1");
    await user.click(screen.getByRole("button", { name: "Cadastrar pet" }));

    expect(await screen.findByText("Já existe um pet com este nome.")).toBeDefined();
    expect(screen.getByLabelText(/Nome do pet/).getAttribute("aria-invalid")).toBe("true");
    expect(salvarPet).toHaveBeenCalled();
  });
});
