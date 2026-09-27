import { rotuloSituacao, type SituacaoConta, type TipoLancamento } from "@/lib/financeiro";

const base = "inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium";

/** Tipo em texto e sinal (não só cor). */
export function BadgeTipo({ tipo }: { tipo: TipoLancamento }) {
  return tipo === "ENTRADA" ? (
    <span className={`${base} border-brand/40 bg-brand-soft text-brand-strong`}>+ Entrada</span>
  ) : (
    <span className={`${base} border-danger/40 bg-danger-soft text-danger`}>− Saída</span>
  );
}

const ESTILO_SITUACAO: Record<SituacaoConta, string> = {
  VENCIDA: "border-danger/40 bg-danger-soft text-danger",
  VENCE_HOJE: "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300",
  A_VENCER: "border-border text-foreground",
  PAGA: "border-brand/40 bg-brand-soft text-brand-strong",
};

const ICONE_SITUACAO: Record<SituacaoConta, string> = { VENCIDA: "⚠", VENCE_HOJE: "◷", A_VENCER: "○", PAGA: "✓" };

export function BadgeSituacao({ situacao }: { situacao: SituacaoConta }) {
  return (
    <span className={`${base} ${ESTILO_SITUACAO[situacao]}`}>
      <span aria-hidden="true">{ICONE_SITUACAO[situacao]}</span>
      {rotuloSituacao(situacao)}
    </span>
  );
}

export function BadgeCaixa({ aberto }: { aberto: boolean }) {
  return aberto ? (
    <span className={`${base} border-brand/40 bg-brand-soft text-brand-strong`}>● Caixa aberto</span>
  ) : (
    <span className={`${base} border-border text-muted`}>○ Caixa fechado</span>
  );
}

/** Diferença de caixa: sinal e texto explícitos (sobra/falta). */
export function Diferenca({ valor, formatar }: { valor: number; formatar: (v: number) => string }) {
  if (Math.round(valor * 100) === 0) return <span className="tabular-nums text-muted">Sem diferença</span>;
  return valor > 0 ? (
    <span className="font-medium tabular-nums text-brand-strong">Sobra de {formatar(valor)}</span>
  ) : (
    <span className="font-medium tabular-nums text-danger">Falta de {formatar(Math.abs(valor))}</span>
  );
}
