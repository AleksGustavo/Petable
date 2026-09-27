import { describe, expect, it } from "vitest";
import type { ProdutoRapido } from "@/lib/estoque";
import {
  adicionarAoCarrinho,
  alterarQuantidade,
  aplicarPrevia,
  carregarCarrinho,
  CHAVE_STORAGE,
  removerDoCarrinho,
  salvarCarrinho,
  subtotalCarrinhoCentavos,
  type ItemCarrinho,
} from "@/lib/vendas";

const racao: ProdutoRapido = { id: 12, nome: "Ração", codigoBarras: "7891234567890", precoVenda: 169.9, quantidadeEstoque: 3, disponivel: true };
const petisco: ProdutoRapido = { id: 10, nome: "Petisco", codigoBarras: null, precoVenda: 0.1, quantidadeEstoque: 50, disponivel: true };

function memoria() {
  const dados = new Map<string, string>();
  return {
    getItem: (k: string) => dados.get(k) ?? null,
    setItem: (k: string, v: string) => void dados.set(k, v),
    removeItem: (k: string) => void dados.delete(k),
    dados,
  };
}

describe("carrinho", () => {
  it("adiciona e soma ao repetir", () => {
    let itens: ItemCarrinho[] = adicionarAoCarrinho([], racao).itens;
    itens = adicionarAoCarrinho(itens, racao).itens;
    expect(itens).toHaveLength(1);
    expect(itens[0]).toMatchObject({ produtoId: 12, quantidade: 2, precoCentavos: 16990 });
  });

  it("limita pelo saldo conhecido", () => {
    let itens = adicionarAoCarrinho([], racao).itens;
    itens = alterarQuantidade(itens, 12, 99);
    expect(itens[0].quantidade).toBe(3);
    const r = adicionarAoCarrinho(itens, racao);
    expect(r.erro).toBe("Só há 3 unidade(s) de Ração em estoque.");
    expect(alterarQuantidade(itens, 12, 0)[0].quantidade).toBe(1);
    expect(adicionarAoCarrinho([], { ...racao, quantidadeEstoque: 0, disponivel: false }).erro).toBe("Ração está sem estoque.");
  });

  it("remove item", () => {
    const itens = adicionarAoCarrinho(adicionarAoCarrinho([], racao).itens, petisco).itens;
    expect(removerDoCarrinho(itens, 12).map((i) => i.produtoId)).toEqual([10]);
  });

  it("soma o total em centavos sem erro de float", () => {
    // 3 × 0,10 = 0,30 exatos (em ponto flutuante: 0.30000000000000004).
    const itens = alterarQuantidade(adicionarAoCarrinho([], petisco).itens, 10, 3);
    expect(subtotalCarrinhoCentavos(itens)).toBe(30);
    const dois = [...itens, ...adicionarAoCarrinho([], racao).itens];
    expect(subtotalCarrinhoCentavos(dois)).toBe(30 + 16990);
  });

  it("persiste e restaura do sessionStorage, descartando lixo", () => {
    const storage = memoria();
    const itens = adicionarAoCarrinho([], racao).itens;
    const chave = "0b8f9a52-5f0e-4d0e-9d1c-6e7b2a1c3d4e";
    salvarCarrinho(storage, { itens, chave });
    expect(storage.dados.get(CHAVE_STORAGE)).not.toMatch(/token|Bearer/i);
    expect(carregarCarrinho(storage)).toEqual({ itens, chave });

    storage.setItem(CHAVE_STORAGE, "{nao é json");
    expect(carregarCarrinho(storage)).toBeNull();
    storage.setItem(CHAVE_STORAGE, JSON.stringify({ itens, chave: "sem-uuid" }));
    expect(carregarCarrinho(storage)).toBeNull();
    expect(carregarCarrinho(undefined)).toBeNull();
  });

  it("aplica preços e saldos da prévia e avisa se o preço mudou", () => {
    const itens = adicionarAoCarrinho([], racao).itens;
    const previa = {
      itens: [{ produtoId: 12, nome: "Ração", precoUnitario: 175, quantidade: 1, subtotal: 175, disponivel: 2, suficiente: true }],
      subtotal: 175,
      desconto: 0,
      total: 175,
      troco: null,
      podeFinalizar: true,
    };
    const r = aplicarPrevia(itens, previa);
    expect(r.precosMudaram).toBe(true);
    expect(r.itens[0]).toMatchObject({ precoCentavos: 17500, disponivel: 2 });
    expect(aplicarPrevia(r.itens, previa).precosMudaram).toBe(false);
  });
});
