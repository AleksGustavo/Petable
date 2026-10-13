import Link from "next/link";
import { StatusAgendamentoBadge } from "@/components/status-agendamento-badge";
import { dataDe, hora, type Agendamento } from "@/lib/agendamentos";
import { formatarData } from "@/lib/format";

export function ProximosAgendamentos({
  petId,
  agendamentos,
  podeAgendar,
}: {
  petId: number;
  agendamentos: Agendamento[];
  podeAgendar: boolean;
}) {
  return (
    <section className="cartao p-4 sm:p-6" aria-labelledby="titulo-proximos">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 id="titulo-proximos" className="text-sm font-semibold text-muted">Próximos agendamentos</h2>
        {podeAgendar && (
          <Link href={`/agendamentos/novo?petId=${petId}`} className="botao-secundario px-3 py-1.5">
            Agendar serviço
          </Link>
        )}
      </div>
      {agendamentos.length === 0 ? (
        <p className="text-sm text-muted">Nenhum agendamento futuro para este pet.</p>
      ) : (
        <ul className="divide-y divide-border">
          {agendamentos.map((a) => (
            <li key={a.id}>
              <Link href={`/agendamentos/${a.id}`} className="flex flex-wrap items-center justify-between gap-2 py-2 hover:text-brand">
                <span className="text-sm">
                  <strong className="tabular-nums">{formatarData(dataDe(a.dataHora))} {hora(a.dataHora)}</strong> · {a.servico.nome}
                </span>
                <StatusAgendamentoBadge status={a.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
