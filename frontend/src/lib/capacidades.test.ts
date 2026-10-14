import { describe, expect, it } from "vitest";
import { MODULO_DA_ROTA, moduloDaRota, temIntegracao, temModulo, temRelatorio, type Capacidades } from "./capacidades";
import { mensagemDeErro, MENSAGEM_MODULO_INDISPONIVEL } from "./erros";

const caps = (modulos: string[], extra: Partial<Capacidades> = {}): Capacidades => ({ todos: false, modulos, integracoes: [], relatorios: [], versaoCatalogo: 1, ...extra });

describe("capacidades da instalação", () => {
  it("modo todos libera tudo; lista explícita libera só o que foi contratado", () => {
    expect(temModulo(caps([], { todos: true }), "financeiro")).toBe(true);
    expect(temModulo(caps(["produtos", "pdv"]), "pdv")).toBe(true);
    expect(temModulo(caps(["produtos", "pdv"]), "estoque")).toBe(false);
  });

  it("integração e relatório seguem a lista da API", () => {
    expect(temIntegracao(caps(["clinica", "lotes"], { integracoes: ["vacina-estoque"] }), "vacina-estoque")).toBe(true);
    expect(temIntegracao(caps(["clinica"]), "vacina-estoque")).toBe(false);
    expect(temRelatorio(caps(["relatorios"], { relatorios: ["vendas"] }), "contas")).toBe(false);
    expect(temRelatorio(caps(["relatorios"], { relatorios: ["vendas"] }), "vendas")).toBe(true);
    expect(temRelatorio(caps([], { todos: true }), "contas")).toBe(true);
  });

  it("rota → módulo: lotes é subrota de estoque; núcleo não tem dono", () => {
    expect(moduloDaRota("/estoque/lotes")).toBe("lotes");
    expect(moduloDaRota("/vendas/12")).toBe("pdv");
    expect(moduloDaRota("/fornecedores")).toBe("compras");
    expect(moduloDaRota("/financeiro/contas-a-pagar")).toBe("financeiro");
    expect(moduloDaRota("/dashboard")).toBeNull();
    expect(moduloDaRota("/usuarios")).toBeNull();
    expect(moduloDaRota("/perfil")).toBeNull();
  });

  it("todo segmento mapeado aponta para um módulo do catálogo", () => {
    const ids = ["clientes", "produtos", "estoque", "lotes", "caixa", "pdv", "compras", "financeiro", "pets", "clinica", "agenda", "relatorios"];
    for (const m of Object.values(MODULO_DA_ROTA)) expect(ids).toContain(m);
  });

  it("mensagem amigável só para o 403 de módulo desligado", () => {
    expect(mensagemDeErro(403, MENSAGEM_MODULO_INDISPONIVEL)).toBe(MENSAGEM_MODULO_INDISPONIVEL);
    expect(mensagemDeErro(403)).toContain("permissão");
  });
});
