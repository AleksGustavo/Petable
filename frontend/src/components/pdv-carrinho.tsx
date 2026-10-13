"use client";

import { moedaCentavos } from "@/lib/estoque";
import { subtotalItemCentavos, type ItemCarrinho } from "@/lib/vendas";

/** Carrinho do PDV: quantidade com − e + (até o saldo conhecido), remover e limpar. Valores só visuais. */
export function PdvCarrinho({
  itens,
  onQuantidade,
  onRemover,
  onLimpar,
  destaque,
}: {
  itens: ItemCarrinho[];
  onQuantidade: (produtoId: number, quantidade: number) => void;
  onRemover: (produtoId: number) => void;
  onLimpar: () => void;
  /** Produto sem estoque suficiente apontado pela API (409). */
  destaque?: number | null;
}) {
  if (itens.length === 0) {
    return <p className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted">Carrinho vazio. Passe o leitor ou busque um produto.</p>;
  }
  return (
    <div className="space-y-3">
      <ul className="space-y-2" aria-label="Itens do carrinho">
        {itens.map((i) => {
          const alerta = destaque === i.produtoId;
          return (
            <li
              key={i.produtoId}
              data-testid={`linha-${i.produtoId}`}
              data-alerta={alerta || undefined}
              className={`space-y-2 rounded-lg border p-3 ${alerta ? "border-danger bg-danger-soft" : "border-border"}`}
            >
              {/* Duas faixas (dados | quantidade e subtotal): cabe na coluna estreita do PDV em qualquer largura. */}
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium">{i.nome}</p>
                  <p className="text-xs text-muted tabular-nums">
                    {moedaCentavos(i.precoCentavos)} un. · saldo {i.disponivel}
                    {alerta && <span className="font-medium text-danger"> · estoque insuficiente</span>}
                  </p>
                </div>
                <button type="button" className="shrink-0 text-sm font-medium text-danger hover:underline" onClick={() => onRemover(i.produtoId)} aria-label={`Remover ${i.nome}`}>
                  Remover
                </button>
              </div>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-1" role="group" aria-label={`Quantidade de ${i.nome}`}>
                  <button
                    type="button"
                    className="botao-secundario size-9 p-0"
                    onClick={() => onQuantidade(i.produtoId, i.quantidade - 1)}
                    disabled={i.quantidade <= 1}
                    aria-label={`Diminuir ${i.nome}`}
                  >
                    −
                  </button>
                  <input
                    type="number"
                    inputMode="numeric"
                    min={1}
                    max={i.disponivel}
                    className="campo w-16 px-2 text-center tabular-nums"
                    value={i.quantidade}
                    onChange={(e) => onQuantidade(i.produtoId, Number(e.target.value))}
                    aria-label={`Quantidade de ${i.nome}`}
                  />
                  <button
                    type="button"
                    className="botao-secundario size-9 p-0"
                    onClick={() => onQuantidade(i.produtoId, i.quantidade + 1)}
                    disabled={i.quantidade >= i.disponivel}
                    aria-label={`Aumentar ${i.nome}`}
                  >
                    +
                  </button>
                </div>
                <p className="text-right font-semibold tabular-nums" data-testid={`subtotal-${i.produtoId}`}>
                  {moedaCentavos(subtotalItemCentavos(i))}
                </p>
              </div>
            </li>
          );
        })}
      </ul>
      <div className="flex justify-end">
        <button
          type="button"
          className="text-sm text-muted hover:text-danger"
          onClick={() => {
            if (window.confirm("Limpar o carrinho? Todos os itens serão removidos.")) onLimpar();
          }}
        >
          Limpar carrinho
        </button>
      </div>
    </div>
  );
}
