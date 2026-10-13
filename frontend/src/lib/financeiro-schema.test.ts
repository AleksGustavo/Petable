import { describe, expect, it } from "vitest";
import {
  aberturaCaixaSchema,
  diferencasCentavos,
  fechamentoSchema,
  lancamentoManualSchema,
  movimentoCaixaSchema,
  pagamentoContaSchema,
} from "@/lib/financeiro";

const manual = { tipo: "SAIDA", categoria: "DESPESA_OPERACIONAL", descricao: " Material de limpeza ", valor: "89,90", formaPagamento: "DINHEIRO", observacoes: "" };
const esperado = { DINHEIRO: 730, CARTAO_CREDITO: 0, CARTAO_DEBITO: 0, PIX: 980, TRANSFERENCIA: 0 };
const contado = (dinheiro: string, motivo = "") => ({
  contadoDINHEIRO: dinheiro,
  contadoCARTAO_CREDITO: "0",
  contadoCARTAO_DEBITO: "0",
  contadoPIX: "980,00",
  contadoTRANSFERENCIA: "0",
  motivoDiferenca: motivo,
});

describe("schemas do financeiro", () => {
  it("lançamento manual válido vira payload em reais com duas casas", () => {
    expect(lancamentoManualSchema.parse(manual)).toEqual({
      tipo: "SAIDA",
      categoria: "DESPESA_OPERACIONAL",
      descricao: "Material de limpeza",
      valor: 89.9,
      formaPagamento: "DINHEIRO",
      observacoes: null,
    });
  });

  it("lançamento manual rejeita categoria automática, valor zero e valor com três casas", () => {
    const erro = (dados: Record<string, string>, campo: string) =>
      lancamentoManualSchema.safeParse(dados).error?.issues.find((i) => i.path[0] === campo)?.message;
    expect(erro({ ...manual, categoria: "VENDA_PRODUTO" }, "categoria")).toMatch(/Lançamento manual aceita só/);
    expect(erro({ ...manual, valor: "0,00" }, "valor")).toBe("O valor deve ser maior que zero");
    expect(erro({ ...manual, valor: "10,999" }, "valor")).toBe("Informe um valor válido (até duas casas)");
    expect(erro({ ...manual, descricao: "ab" }, "descricao")).toBe("A descrição deve ter entre 3 e 100 caracteres");
  });

  it("abertura aceita fundo vazio (zero) e movimento exige motivo", () => {
    expect(aberturaCaixaSchema.parse({ fundoAbertura: "" })).toEqual({ fundoAbertura: 0 });
    expect(aberturaCaixaSchema.parse({ fundoAbertura: "200,00" })).toEqual({ fundoAbertura: 200 });
    expect(movimentoCaixaSchema.safeParse({ valor: "50", motivo: "abc" }).error?.issues[0]?.message).toBe("O motivo deve ter entre 5 e 500 caracteres");
    expect(movimentoCaixaSchema.parse({ valor: "0,10", motivo: "Reforço de troco" })).toEqual({ valor: 0.1, motivo: "Reforço de troco" });
    expect(pagamentoContaSchema.safeParse({ formaPagamento: "" }).success).toBe(false);
  });

  it("diferença por forma em centavos (contado − esperado)", () => {
    const d = diferencasCentavos(esperado, { DINHEIRO: "725,00", PIX: "980", CARTAO_CREDITO: "0", CARTAO_DEBITO: "0", TRANSFERENCIA: "" });
    expect(d.DINHEIRO).toBe(-500);
    expect(d.PIX).toBe(0);
    expect(d.TRANSFERENCIA).toBeNaN();
  });

  it("fechamento com diferença exige motivo; sem diferença, não", () => {
    const schema = fechamentoSchema(esperado);
    const semMotivo = schema.safeParse(contado("725,00"));
    expect(semMotivo.error?.issues.find((i) => i.path[0] === "motivoDiferenca")?.message).toBe("Explique a diferença (5 a 500 caracteres).");
    expect(schema.parse(contado("725,00", "Faltou uma moeda")).contado.DINHEIRO).toBe(725);
    const exato = schema.parse(contado("730,00"));
    expect(exato.motivoDiferenca).toBeNull();
    expect(exato.contado).toEqual({ DINHEIRO: 730, CARTAO_CREDITO: 0, CARTAO_DEBITO: 0, PIX: 980, TRANSFERENCIA: 0 });
    expect(schema.safeParse({ ...contado("730"), contadoPIX: "" }).success).toBe(false);
  });

  it("aceita líquido negativo nas formas eletrônicas, nunca em dinheiro", () => {
    const schema = fechamentoSchema({ ...esperado, PIX: -10.1 });
    expect(schema.parse({ ...contado("730"), contadoPIX: "-10,10" }).contado.PIX).toBe(-10.1);
    expect(diferencasCentavos({ ...esperado, PIX: -10.1 }, { DINHEIRO: "730", PIX: "−10,10", CARTAO_CREDITO: "0", CARTAO_DEBITO: "0", TRANSFERENCIA: "0" }).PIX).toBe(0);
    expect(schema.safeParse({ ...contado("-5"), contadoPIX: "-10,10" }).success).toBe(false);
  });
});
