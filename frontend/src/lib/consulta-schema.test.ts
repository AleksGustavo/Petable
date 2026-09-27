import { describe, expect, it } from "vitest";
import { consultaSchema } from "./pets";

describe("consultaSchema", () => {
  it("aceita consulta válida", () => {
    expect(consultaSchema.parse({ dataConsulta: "2025-01-10T14:30", veterinario: " Dr. Paulo ", motivo: "" })).toMatchObject({
      veterinario: "Dr. Paulo",
      motivo: null,
    });
  });

  it("recusa veterinário vazio", () => {
    const r = consultaSchema.safeParse({ dataConsulta: "2025-01-10T14:30", veterinario: " " });
    expect(r.error!.issues.map((i) => i.path[0])).toContain("veterinario");
  });

  it("recusa consulta futura", () => {
    const r = consultaSchema.safeParse({ dataConsulta: "2999-01-01T08:00", veterinario: "Dr. X" });
    expect(r.error!.issues.map((i) => i.path[0])).toContain("dataConsulta");
  });
});
