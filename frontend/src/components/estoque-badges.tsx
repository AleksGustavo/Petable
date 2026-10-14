import { rotuloStatusCompra, type StatusCompra } from "@/lib/estoque";
import { situacaoLote, type Lote, type TomLote } from "@/lib/lotes";

const base = "inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium";

/** Situação do saldo em texto (não só cor): saldo <= mínimo é "abaixo do mínimo". */
export function BadgeEstoque({ abaixoDoMinimo }: { abaixoDoMinimo: boolean }) {
  return abaixoDoMinimo ? (
    <span className={`${base} border-danger/40 bg-danger-soft text-danger`}>⚠ Abaixo do mínimo</span>
  ) : (
    <span className={`${base} border-brand/40 bg-brand-soft text-brand-strong`}>✓ Estoque adequado</span>
  );
}

export function BadgeAtivo({ ativo }: { ativo: boolean }) {
  return (
    <span className={`${base} ${ativo ? "border-brand/40 bg-brand-soft text-brand-strong" : "border-border text-muted"}`}>
      <span aria-hidden="true">{ativo ? "●" : "○"}</span>
      {ativo ? "Ativo" : "Inativo"}
    </span>
  );
}

const ESTILO_STATUS: Record<StatusCompra, string> = {
  PEDIDO: "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300",
  RECEBIDO: "border-brand/40 bg-brand-soft text-brand-strong",
  CANCELADO: "border-border text-muted line-through decoration-1",
};

const ICONE_STATUS: Record<StatusCompra, string> = { PEDIDO: "◷", RECEBIDO: "✓", CANCELADO: "✕" };

export function BadgeStatusCompra({ status }: { status: StatusCompra }) {
  return (
    <span className={`${base} ${ESTILO_STATUS[status]}`}>
      <span aria-hidden="true">{ICONE_STATUS[status]}</span>
      {rotuloStatusCompra(status)}
    </span>
  );
}

const ESTILO_LOTE: Record<TomLote, string> = {
  ok: "border-brand/40 bg-brand-soft text-brand-strong",
  atencao: "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300",
  perigo: "border-danger/40 bg-danger-soft text-danger",
  neutro: "border-border text-muted",
};

/** Situação do lote em ícone + texto (nunca só cor): disponível, perto do vencimento, bloqueado, vencido ou sem saldo. */
export function BadgeLote({ lote }: { lote: Pick<Lote, "status" | "vencido" | "vendavel" | "saldo"> }) {
  const s = situacaoLote(lote);
  return (
    <span className={`${base} ${ESTILO_LOTE[s.tom]}`}>
      <span aria-hidden="true">{s.icone}</span>
      {s.rotulo}
    </span>
  );
}
