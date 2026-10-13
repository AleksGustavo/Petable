import Link from "next/link";
import { StatusAgendamentoBadge } from "@/components/status-agendamento-badge";
import { dataDe, hora, moeda, type Agendamento } from "@/lib/agendamentos";
import { formatarData } from "@/lib/format";

const DIA_SEMANA = new Intl.DateTimeFormat("pt-BR", { weekday: "long", timeZone: "UTC" });

function tituloDoDia(iso: string) {
  const [a, m, d] = iso.split("-").map(Number);
  const nome = DIA_SEMANA.format(new Date(Date.UTC(a, m - 1, d)));
  return `${nome.charAt(0).toUpperCase()}${nome.slice(1)}, ${formatarData(iso)}`;
}

function Item({ a }: { a: Agendamento }) {
  return (
    <li>
      <Link
        href={`/agendamentos/${a.id}`}
        className="cartao grid gap-1 p-4 transition hover:border-brand md:grid-cols-[7rem_1fr_12rem_7rem_9rem] md:items-center md:gap-4"
      >
        <span className="font-semibold tabular-nums">
          {hora(a.dataHora)}–{hora(a.fimPrevisto)}
        </span>
        <span className="min-w-0">
          <span className="block truncate font-medium">
            {a.pet.nome} • {a.tutor.nome}
          </span>
        </span>
        <span className="truncate text-sm text-muted md:text-foreground">
          {a.servico.nome}
          {a.funcionario && (
            <span className={`block truncate text-xs ${a.funcionario.disponivel ? "text-muted" : "text-danger"}`}>
              {a.funcionario.disponivel ? a.funcionario.nome : `${a.funcionario.nome} — reatribuição necessária`}
            </span>
          )}
        </span>
        <span className="text-sm tabular-nums">{moeda(a.totalEstimado)}</span>
        <span className="md:text-right">
          <StatusAgendamentoBadge status={a.status} />
        </span>
      </Link>
    </li>
  );
}

/**
 * Agenda em ordem cronológica. Na visão semanal agrupa por dia. Uma lista só,
 * que vira colunas no md+ (sem tabela duplicada para o celular).
 */
export function AgendaLista({
  agendamentos,
  agruparPorDia = false,
  filtrado = false,
}: {
  agendamentos: Agendamento[];
  agruparPorDia?: boolean;
  filtrado?: boolean;
}) {
  if (agendamentos.length === 0) {
    return (
      <p className="cartao p-10 text-center text-sm text-muted">
        {filtrado ? "Nenhum agendamento encontrado com os filtros selecionados." : "Nenhum agendamento para esta data."}
      </p>
    );
  }
  const ordenados = [...agendamentos].sort((x, y) => x.dataHora.localeCompare(y.dataHora) || x.id - y.id);

  if (!agruparPorDia) {
    return (
      <ol className="space-y-2" aria-label="Agendamentos do dia">
        {ordenados.map((a) => (
          <Item key={a.id} a={a} />
        ))}
      </ol>
    );
  }

  const dias = [...new Set(ordenados.map((a) => dataDe(a.dataHora)))];
  return (
    <div className="space-y-6">
      {dias.map((dia) => (
        <section key={dia} aria-label={tituloDoDia(dia)}>
          <h2 className="mb-2 text-sm font-semibold text-muted">{tituloDoDia(dia)}</h2>
          <ol className="space-y-2">
            {ordenados
              .filter((a) => dataDe(a.dataHora) === dia)
              .map((a) => (
                <Item key={a.id} a={a} />
              ))}
          </ol>
        </section>
      ))}
    </div>
  );
}
