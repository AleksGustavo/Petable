"use client";

import dynamic from "next/dynamic";
import type { Papel } from "@/lib/jwt";

/** O PDV lê o carrinho do sessionStorage: renderiza só no navegador (sem divergência de hidratação). */
const Pdv = dynamic(() => import("@/components/pdv").then((m) => m.Pdv), {
  ssr: false,
  loading: () => (
    <div className="grid animate-pulse gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]" aria-busy="true" aria-label="Carregando PDV">
      <div className="h-64 rounded-xl border border-border bg-border/20" />
      <div className="h-96 rounded-xl border border-border bg-border/20" />
    </div>
  ),
});

export function PdvCliente({ papel, caixaAberto }: { papel: Papel; caixaAberto: boolean }) {
  return <Pdv papel={papel} caixaAberto={caixaAberto} />;
}
