import type { StatusVacina } from "@/lib/pets";

/** O texto (com símbolo) carrega o significado; a cor só reforça. */
const ESTILOS: Record<StatusVacina, { simbolo: string; texto: string; classe: string }> = {
  EM_DIA: { simbolo: "✓", texto: "Em dia", classe: "border-brand/40 bg-brand-soft text-brand-strong" },
  VENCE_EM_30_DIAS: {
    simbolo: "⚠",
    texto: "Vence em até 30 dias",
    classe: "border-accent/60 bg-accent/15 text-foreground",
  },
  VENCIDA: { simbolo: "!", texto: "Vencida", classe: "border-danger/40 bg-danger-soft text-danger" },
  SEM_PROXIMA_DOSE: { simbolo: "—", texto: "Sem próxima dose", classe: "border-border bg-background text-muted" },
};

export function StatusVacinaBadge({ status }: { status: StatusVacina }) {
  const e = ESTILOS[status];
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium ${e.classe}`}>
      <span aria-hidden="true">{e.simbolo}</span>
      {e.texto}
    </span>
  );
}
