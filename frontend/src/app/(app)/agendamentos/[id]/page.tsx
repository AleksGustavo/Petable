import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AcoesStatus } from "@/components/acoes-status";
import { resumoCaixaAtual } from "@/lib/financeiro-server";
import { AgendamentoForm } from "@/components/agendamento-form";
import { Aviso } from "@/components/aviso";
import { StatusAgendamentoBadge } from "@/components/status-agendamento-badge";
import { dataDe, editavel, hora, moeda, STATUS, type Agendamento, type Servico, type StatusAgendamento } from "@/lib/agendamentos";
import { ApiError, apiFetch } from "@/lib/api";
import { agoraLocalISO, formatarDataHora } from "@/lib/datas";
import { formatarData } from "@/lib/format";
import { podeAgendar } from "@/lib/permissoes";
import { rotuloPorte, type VacinaCatalogo } from "@/lib/pets";
import type { FuncionarioResumo } from "@/lib/usuarios";
import { exigirSessao } from "@/lib/session";
import { moduloAtivo } from "@/lib/modulos";

export const metadata: Metadata = { title: "Agendamento" };

async function carregar(id: string): Promise<Agendamento> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Agendamento>(`/api/v1/agendamentos/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

function Info({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-muted">{rotulo}</dt>
      <dd className="mt-0.5 text-sm font-medium">{children}</dd>
    </div>
  );
}

export default async function AgendamentoPage(props: PageProps<"/agendamentos/[id]">) {
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const [sessao, a] = await Promise.all([exigirSessao(), carregar(id)]);
  const podeEditar = podeAgendar(sessao.papel) && editavel(a.status);
  const concluindoVacinacao = a.status === "EM_ANDAMENTO" && a.servico.vacinacao && (await moduloAtivo("clinica"));

  const [servicos, vacinas, funcionarios] = await Promise.all([
    podeEditar ? apiFetch<Servico[]>("/api/v1/servicos?ativo=true") : Promise.resolve([]),
    concluindoVacinacao ? apiFetch<VacinaCatalogo[]>(`/api/v1/especies/${a.pet.especieId}/vacinas`) : Promise.resolve([]),
    podeEditar ? apiFetch<FuncionarioResumo[]>("/api/v1/funcionarios") : Promise.resolve([]),
  ]);
  // Etapa 06: só consulta o caixa quando o atendimento pode ser concluído.
  const caixaAberto = a.status === "EM_ANDAMENTO" ? (await resumoCaixaAtual()).aberto : true;
  const aviso =
    typeof ok === "string"
      ? ok === "criado"
        ? "Agendamento criado."
        : ok === "salvo"
          ? "Alterações salvas."
          : ok in STATUS
            ? `Status alterado para ${STATUS[ok as StatusAgendamento].rotulo}.`
            : null
      : null;

  return (
    <div className="space-y-6">
      <Link href={`/agendamentos?data=${dataDe(a.dataHora)}`} className="text-sm text-muted hover:text-foreground">← Agenda</Link>

      <header className="cartao space-y-4 p-4 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold">{a.servico.nome}</h1>
          <StatusAgendamentoBadge status={a.status} />
        </div>
        <dl className="grid gap-4 sm:grid-cols-3">
          <Info rotulo="Data">{formatarData(dataDe(a.dataHora))}</Info>
          <Info rotulo="Horário">
            {hora(a.dataHora)}–{hora(a.fimPrevisto)}
            {a.servico.duracaoMinutos != null && <span className="text-muted"> ({a.servico.duracaoMinutos} min)</span>}
          </Info>
          <Info rotulo="Valor estimado">{moeda(a.totalEstimado)}</Info>
          <Info rotulo="Pet">
            <Link href={`/pets/${a.pet.id}`} className="text-brand hover:underline">{a.pet.nome}</Link>
            {a.pet.porte && <span className="text-muted"> · porte {rotuloPorte(a.pet.porte).toLowerCase()}</span>}
          </Info>
          <Info rotulo="Tutor">
            <Link href={`/clientes/${a.tutor.id}`} className="text-brand hover:underline">{a.tutor.nome}</Link>
          </Info>
          {a.funcionario && (
            <Info rotulo="Profissional">
              {a.funcionario.nome}
              {!a.funcionario.disponivel && (
                <span className="mt-1 block text-xs font-medium text-danger">Funcionário indisponível — reatribuição necessária</span>
              )}
            </Info>
          )}
          {a.observacoes && (
            <div className="sm:col-span-3">
              <Info rotulo="Observações">
                <span className="whitespace-pre-line font-normal">{a.observacoes}</span>
              </Info>
            </div>
          )}
          {a.realizado && (
            <div className="rounded-lg bg-brand-soft/50 p-3 sm:col-span-3">
              <Info rotulo="Serviço realizado">
                {formatarDataHora(a.realizado.dataServico)} · valor do serviço {moeda(a.realizado.valor)}
                {a.realizado.observacoes && <span className="block font-normal">{a.realizado.observacoes}</span>}
              </Info>
            </div>
          )}
        </dl>
      </header>

      {aviso && <Aviso>{aviso}</Aviso>}

      <AcoesStatus agendamento={a} papel={sessao.papel} horarioAtingido={a.dataHora.slice(0, 16) <= agoraLocalISO()} vacinas={vacinas} caixaAberto={caixaAberto} />

      {podeEditar && (
        <details className="cartao p-4 sm:p-6">
          <summary className="cursor-pointer font-medium">Editar agendamento</summary>
          <div className="mt-4">
            <AgendamentoForm
              servicos={servicos}
              agendamento={a}
              funcionarios={funcionarios}
              petInicial={{ id: a.pet.id, nome: a.pet.nome, tutor: a.tutor }}
            />
          </div>
        </details>
      )}
    </div>
  );
}
