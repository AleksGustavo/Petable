import { describe, expect, it } from "vitest";
import { agendamentoSchema } from "./agendamentos";

const valido = { petId: "42", servicoId: "1", dataHora: "2999-09-28T09:00", observacoes: " Shampoo " };

describe("agendamentoSchema", () => {
  it("aceitaAgendamentoValido", () => {
    expect(agendamentoSchema.parse(valido)).toEqual({ petId: 42, servicoId: 1, funcionarioId: null, dataHora: "2999-09-28T09:00", observacoes: "Shampoo" });
  });

  it("rejeitaDataHoraInvalida", () => {
    for (const dataHora of ["amanhã", "2999-09-28", "2020-01-01T09:00"]) {
      const r = agendamentoSchema.safeParse({ ...valido, dataHora });
      expect(r.error!.issues.map((i) => i.path[0])).toContain("dataHora");
    }
  });

  it("rejeitaPetAusente", () => {
    const r = agendamentoSchema.safeParse({ ...valido, petId: "" });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(["petId"]);
  });

  it("rejeitaServicoAusente", () => {
    const r = agendamentoSchema.safeParse({ ...valido, servicoId: undefined });
    expect(r.error!.issues.map((i) => i.path[0])).toEqual(["servicoId"]);
  });
});
