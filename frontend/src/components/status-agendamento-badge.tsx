import { STATUS, type StatusAgendamento } from "@/lib/agendamentos";

/** Símbolo + texto: o status não depende só da cor. */
export function StatusAgendamentoBadge({ status }: { status: StatusAgendamento }) {
  const s = STATUS[status];
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium ${s.classe}`}>
      <span aria-hidden="true">{s.simbolo}</span>
      {s.rotulo}
    </span>
  );
}
