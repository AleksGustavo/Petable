import { describe, expect, it } from "vitest";
import { ajusteEstoqueSchema } from "@/lib/estoque";

const erro = (dados: Record<string, string>) => ajusteEstoqueSchema.safeParse(dados).error?.issues[0]?.message;

describe("ajusteEstoqueSchema", () => {
  it("aceita ajuste positivo", () => {
    expect(ajusteEstoqueSchema.parse({ quantidade: "+5", motivo: " Contagem física " })).toEqual({ quantidade: 5, motivo: "Contagem física" });
  });

  it("aceita ajuste negativo", () => {
    expect(ajusteEstoqueSchema.parse({ quantidade: "-3", motivo: "Avaria no transporte" }).quantidade).toBe(-3);
  });

  it("rejeita ajuste zero e não inteiro", () => {
    expect(erro({ quantidade: "0", motivo: "Motivo válido" })).toBe("O ajuste não pode ser zero");
    expect(erro({ quantidade: "1,5", motivo: "Motivo válido" })).toBe("Informe um número inteiro (use − para saída)");
    expect(erro({ quantidade: "1000001", motivo: "Motivo válido" })).toBeDefined();
  });

  it("rejeita motivo curto", () => {
    expect(erro({ quantidade: "2", motivo: " abc " })).toBe("O motivo deve ter entre 5 e 500 caracteres");
  });
});
