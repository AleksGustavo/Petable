"use client";

import { useEffect, useState } from "react";
import { Campo } from "@/components/campo";
import type { VacinaCatalogo } from "@/lib/pets";

/**
 * Campo "Lote" da aplicação de vacina. Para vacina ligada ao estoque (baixaEstoque) o lote não é digitado: o sistema usa o
 * que vence primeiro e o grava na carteira, então o campo some e entra o aviso. Acompanha o select da vacina pelo id, sem
 * depender de como o formulário guarda o valor.
 */
export function LoteDaVacina({ vacinas, selectId, valorInicial, erro }: { vacinas: VacinaCatalogo[]; selectId: string; valorInicial?: string; erro?: string }) {
  const [baixa, setBaixa] = useState(false);

  useEffect(() => {
    const select = document.getElementById(selectId) as HTMLSelectElement | null;
    if (!select) return;
    const ler = () => setBaixa(vacinas.find((v) => String(v.id) === select.value)?.baixaEstoque === true);
    ler();
    select.addEventListener("change", ler);
    return () => select.removeEventListener("change", ler);
  }, [vacinas, selectId]);

  if (baixa) {
    return (
      <p role="note" className="rounded-lg border border-brand/30 bg-brand-soft px-3 py-2 text-sm text-brand-strong">
        <span aria-hidden="true">✓ </span>
        O lote sai do estoque automaticamente (o que vence primeiro) e fica registrado na carteira. Uma dose é baixada.
      </p>
    );
  }
  return <Campo rotulo="Lote" name="lote" maxLength={50} defaultValue={valorInicial} erro={erro} />;
}
