import { describe, expect, it } from "vitest";
import {
  baixaLoteSchema,
  conferirSoma,
  destinoSchema,
  diasParaVencer,
  linhaVazia,
  lotesDoRecebimento,
  mensagemSoma,
  paraPayload,
  regularizacaoSchema,
  rotuloFaixa,
  situacaoLote,
  somaLinhas,
  textoPrazo,
  validarLinhas,
  type LinhaLote,
} from "./lotes";
import { adicionarAoCarrinho } from "./vendas";

const HOJE = "2026-09-25";
const linha = (codigo: string, validade: string, quantidade: string): LinhaLote => ({ codigo, validade, quantidade });

describe("soma da grade de lotes", () => {
  it("soma só quantidades válidas e confere com o esperado", () => {
    const linhas = [linha("A", "2027-01-01", "4"), linha("B", "2027-02-01", "6"), linha("C", "", "abc")];
    expect(somaLinhas(linhas)).toBe(10);
    expect(conferirSoma(linhas, 10).confere).toBe(true);
    expect(conferirSoma(linhas, 12)).toMatchObject({ soma: 10, diferenca: -2, confere: false });
  });

  it("mensagens dizem faltam, sobram ou confere", () => {
    expect(mensagemSoma(conferirSoma([linha("A", "", "4")], 10))).toBe("Faltam 6 para chegar a 10 (soma atual: 4).");
    expect(mensagemSoma(conferirSoma([linha("A", "", "12")], 10))).toBe("Sobram 2 sobre 10 (soma atual: 12).");
    expect(mensagemSoma(conferirSoma([linha("A", "", "10")], 10))).toBe("Os lotes somam 10: confere.");
  });
});

describe("validação da grade", () => {
  const op = { exigeValidade: true, esperado: 10, recebimento: true };

  it("grade correta não tem erro", () => {
    expect(validarLinhas([linha("A", "2027-01-01", "4"), linha("B", "2027-06-01", "6")], op, HOJE)).toEqual({});
  });

  it("código vazio, repetido (sem diferenciar caixa) e longo demais", () => {
    const erros = validarLinhas([linha("", "2027-01-01", "5"), linha("ab", "2027-01-01", "3"), linha("AB", "2027-01-01", "2")], op, HOJE);
    expect(erros["0.codigo"]).toBe("Informe o código do lote");
    expect(erros["2.codigo"]).toBe("Código repetido neste item");
    expect(validarLinhas([linha("x".repeat(51), "2027-01-01", "10")], op, HOJE)["0.codigo"]).toBe("Use no máximo 50 caracteres");
  });

  it("validade obrigatória só quando o produto exige", () => {
    expect(validarLinhas([linha("A", "", "10")], op, HOJE)["0.validade"]).toBe("Este produto exige validade");
    expect(validarLinhas([linha("A", "", "10")], { ...op, exigeValidade: false }, HOJE)).toEqual({});
  });

  it("recebimento recusa lote vencido; a regularização do saldo antigo aceita", () => {
    expect(validarLinhas([linha("A", "2026-09-24", "10")], op, HOJE)["0.validade"]).toBe("Lote já vencido não pode ser recebido");
    expect(validarLinhas([linha("A", "2026-09-24", "10")], { ...op, recebimento: false }, HOJE)).toEqual({});
    // Vence hoje ainda é válido (vendável até a data, inclusive).
    expect(validarLinhas([linha("A", HOJE, "10")], op, HOJE)).toEqual({});
  });

  it("quantidade fora de 1 a 1.000.000 e soma divergente", () => {
    expect(validarLinhas([linha("A", "2027-01-01", "0")], op, HOJE)["0.quantidade"]).toBe("Quantidade de 1 a 1.000.000");
    expect(validarLinhas([linha("A", "2027-01-01", "1,5")], op, HOJE)["0.quantidade"]).toBe("Quantidade de 1 a 1.000.000");
    const erros = validarLinhas([linha("A", "2027-01-01", "4")], op, HOJE);
    expect(erros._).toBe("Faltam 6 para chegar a 10 (soma atual: 4).");
  });

  it("payload leva código aparado, validade nula quando vazia e quantidade numérica", () => {
    expect(paraPayload([linha(" A1 ", "2027-01-01", "4"), linha("B", "", "6")])).toEqual([
      { codigoLote: "A1", validade: "2027-01-01", quantidade: 4 },
      { codigoLote: "B", validade: null, quantidade: 6 },
    ]);
  });

  it("linhaVazia nasce em branco", () => {
    expect(linhaVazia()).toEqual({ codigo: "", validade: "", quantidade: "" });
  });
});

describe("regularização do saldo legado", () => {
  const dados = (lotes: LinhaLote[], extra: Record<string, string> = {}) => ({ exigirValidade: "on", lotes: JSON.stringify(lotes), ...extra });

  it("soma exata vira payload com permiteSemValidade falso", () => {
    const r = regularizacaoSchema(10).safeParse(dados([linha("A", "2027-01-01", "4"), linha("B", "2027-02-01", "6")]));
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.permiteSemValidade).toBe(false);
      expect(r.data.diasBloqueioValidade).toBeNull();
      expect(r.data.lotes).toHaveLength(2);
    }
  });

  it("soma diferente do saldo é recusada, sem inventar lote", () => {
    const r = regularizacaoSchema(10).safeParse(dados([linha("A", "2027-01-01", "4")]));
    expect(r.success).toBe(false);
  });

  it("sem exigir validade, lote sem data passa e permiteSemValidade vira verdadeiro; margem é opcional", () => {
    const r = regularizacaoSchema(3).safeParse({ lotes: JSON.stringify([linha("A", "", "3")]), diasBloqueioValidade: "30" });
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.permiteSemValidade).toBe(true);
      expect(r.data.diasBloqueioValidade).toBe(30);
    }
    expect(regularizacaoSchema(3).safeParse({ exigirValidade: "on", lotes: JSON.stringify([linha("A", "", "3")]) }).success).toBe(false);
  });

  it("saldo zero aceita lista vazia; margem inválida é recusada; aceita lote vencido do estoque antigo", () => {
    expect(regularizacaoSchema(0).safeParse({ exigirValidade: "on", lotes: "[]" }).success).toBe(true);
    expect(regularizacaoSchema(0).safeParse({ lotes: "[]", diasBloqueioValidade: "-1" }).success).toBe(false);
    expect(regularizacaoSchema(2).safeParse(dados([linha("VELHO", "2020-01-01", "2")])).success).toBe(true);
  });
});

describe("outros schemas", () => {
  it("baixa de lote exige quantidade positiva e motivo", () => {
    expect(baixaLoteSchema.safeParse({ quantidade: "3", motivo: "Quebra no transporte" }).success).toBe(true);
    expect(baixaLoteSchema.safeParse({ quantidade: "0", motivo: "Quebra no transporte" }).success).toBe(false);
    expect(baixaLoteSchema.safeParse({ quantidade: "3", motivo: "curt" }).success).toBe(false);
  });

  it("destino do estoque não tem valor padrão", () => {
    expect(destinoSchema.safeParse("").success).toBe(false);
    expect(destinoSchema.safeParse(undefined).success).toBe(false);
    expect(destinoSchema.safeParse("RETORNAR").success).toBe(true);
    expect(destinoSchema.safeParse("DESCARTAR").success).toBe(true);
    expect(destinoSchema.safeParse("TALVEZ").success).toBe(false);
  });

  it("lotes do recebimento: JSON inválido vira lista vazia", () => {
    expect(lotesDoRecebimento("não é json")).toEqual([]);
    expect(lotesDoRecebimento(JSON.stringify([{ produtoId: 3, linhas: [{ codigo: "A", validade: "", quantidade: "2" }] }]))).toHaveLength(1);
  });
});

describe("situação e prazo", () => {
  const base = { status: "DISPONIVEL" as const, vencido: false, vendavel: true, saldo: 5 };

  it("rótulo com ícone para cada situação (nunca só cor)", () => {
    expect(situacaoLote(base)).toMatchObject({ rotulo: "Disponível", icone: "✓" });
    expect(situacaoLote({ ...base, vencido: true, vendavel: false })).toMatchObject({ rotulo: "Vencido", tom: "perigo" });
    expect(situacaoLote({ ...base, status: "BLOQUEADO", vendavel: false })).toMatchObject({ rotulo: "Bloqueado" });
    expect(situacaoLote({ ...base, vendavel: false })).toMatchObject({ rotulo: "Perto do vencimento" });
    expect(situacaoLote({ ...base, saldo: 0, vendavel: false })).toMatchObject({ rotulo: "Sem saldo" });
  });

  it("dias para vencer e texto do prazo", () => {
    expect(diasParaVencer("2026-10-05", HOJE)).toBe(10);
    expect(diasParaVencer("2026-09-24", HOJE)).toBe(-1);
    expect(diasParaVencer(null, HOJE)).toBeNull();
    expect(textoPrazo("2026-09-25", HOJE)).toBe("vence hoje");
    expect(textoPrazo("2026-09-30", HOJE)).toBe("vence em 5 dia(s)");
    expect(textoPrazo("2026-09-20", HOJE)).toBe("venceu há 5 dia(s)");
    expect(textoPrazo(null, HOJE)).toBe("sem validade");
  });

  it("rótulo das faixas", () => {
    expect(rotuloFaixa({ deDias: 0, ateDias: 30, lotes: 0, unidades: 0 })).toBe("Vencem em até 30 dias");
    expect(rotuloFaixa({ deDias: 31, ateDias: 60, lotes: 0, unidades: 0 })).toBe("Vencem em 31 a 60 dias");
  });
});

describe("PDV usa o saldo vendável", () => {
  const produto = { id: 7, nome: "Vacina X", codigoBarras: null, precoVenda: 80, quantidadeEstoque: 9, disponivel: true };

  it("produto com lote: o teto é o vendável, não o total da prateleira", () => {
    const r = adicionarAoCarrinho([], { ...produto, quantidadeVendavel: 2, controlaLote: true });
    expect(r.itens[0].disponivel).toBe(2);
    const dois = adicionarAoCarrinho(r.itens, { ...produto, quantidadeVendavel: 2, controlaLote: true });
    const tres = adicionarAoCarrinho(dois.itens, { ...produto, quantidadeVendavel: 2, controlaLote: true });
    expect(tres.erro).toBe("Só há 2 unidade(s) de Vacina X disponível(is) para venda.");
  });

  it("tudo vencido ou bloqueado: avisa que não há lote disponível", () => {
    const r = adicionarAoCarrinho([], { ...produto, quantidadeVendavel: 0, controlaLote: true, disponivel: false });
    expect(r.itens).toHaveLength(0);
    expect(r.erro).toBe("Vacina X não tem lote disponível para venda (vencido ou bloqueado).");
  });

  it("sem lote o comportamento é o de sempre", () => {
    expect(adicionarAoCarrinho([], produto).itens[0].disponivel).toBe(9);
    expect(adicionarAoCarrinho([], { ...produto, quantidadeEstoque: 0 }).erro).toBe("Vacina X está sem estoque.");
  });
});
