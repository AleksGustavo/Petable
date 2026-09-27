import { describe, expect, it } from "vitest";
import { vacinaSchema } from "./pets";

const valido = { vacinaId: "4", dataAplicacao: "2025-09-20", dataProxima: "2026-09-20", veterinario: "Dra. Ana" };

describe("vacinaSchema", () => {
  it("aceita lançamento válido", () => {
    expect(vacinaSchema.parse(valido)).toMatchObject({ vacinaId: 4, dataProxima: "2026-09-20", lote: null });
  });

  it("aceita próxima dose nula", () => {
    expect(vacinaSchema.parse({ ...valido, dataProxima: "" }).dataProxima).toBeNull();
  });

  it("recusa aplicação futura", () => {
    const r = vacinaSchema.safeParse({ ...valido, dataAplicacao: "2999-01-01", dataProxima: "" });
    expect(r.error!.issues.map((i) => i.path[0])).toContain("dataAplicacao");
  });

  it("recusa próxima dose anterior à aplicação", () => {
    const r = vacinaSchema.safeParse({ ...valido, dataProxima: "2025-09-19" });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(["dataProxima"]);
  });
});
