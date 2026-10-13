import { describe, expect, it } from "vitest";
import { petSchema } from "./pets";

const valido = { clienteId: "7", nome: " Thor ", especieId: "1", racaId: "10", dataNascimento: "2023-05-20", peso: "32,5", porte: "GRANDE", castrado: "on" };

describe("petSchema", () => {
  it("aceita pet válido e normaliza tipos", () => {
    expect(petSchema.parse(valido)).toMatchObject({ clienteId: 7, nome: "Thor", especieId: 1, racaId: 10, peso: 32.5, porte: "GRANDE", castrado: true });
  });

  it("recusa nome vazio", () => {
    const r = petSchema.safeParse({ ...valido, nome: "  " });
    expect(r.success).toBe(false);
    expect(r.error!.issues[0].path).toEqual(["nome"]);
  });

  it("recusa nascimento futuro", () => {
    const r = petSchema.safeParse({ ...valido, dataNascimento: "2999-01-01" });
    expect(r.error!.issues.map((i) => i.path[0])).toContain("dataNascimento");
  });

  it("recusa peso zero", () => {
    const r = petSchema.safeParse({ ...valido, peso: "0" });
    expect(r.error!.issues.map((i) => i.path[0])).toContain("peso");
  });

  it("recusa porte inválido", () => {
    const r = petSchema.safeParse({ ...valido, porte: "ENORME" });
    expect(r.error!.issues.map((i) => i.path[0])).toContain("porte");
  });

  it("raça, peso e porte são opcionais", () => {
    expect(petSchema.parse({ clienteId: "7", nome: "Mia", especieId: "2" })).toMatchObject({ racaId: null, peso: null, porte: null, castrado: false });
  });
});
