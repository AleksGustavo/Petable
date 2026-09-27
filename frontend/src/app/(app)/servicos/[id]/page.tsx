import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { desativarServico } from "@/app/actions/agendamentos";
import { Aviso } from "@/components/aviso";
import { BotaoConfirmar } from "@/components/botao-confirmar";
import { ServicoForm } from "@/components/servico-form";
import { moeda, type Servico } from "@/lib/agendamentos";
import { ApiError, apiFetch } from "@/lib/api";
import { podeAdministrarServicos } from "@/lib/permissoes";
import { rotuloPorte } from "@/lib/pets";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Serviço" };

async function carregar(id: string): Promise<Servico> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Servico>(`/api/v1/servicos/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

export default async function ServicoPage(props: PageProps<"/servicos/[id]">) {
  const { id } = await props.params;
  const { ok, desativado } = await props.searchParams;
  const [sessao, s] = await Promise.all([exigirSessao(), carregar(id)]);
  const admin = podeAdministrarServicos(sessao.papel);

  return (
    <div className="space-y-6">
      <Link href="/servicos" className="text-sm text-muted hover:text-foreground">← Serviços</Link>
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{s.nome}</h1>
          <p className="text-sm text-muted">
            {s.ativo ? "Ativo" : "Inativo"}
            {s.vacinacao && " · lança a dose na carteira ao concluir"}
          </p>
        </div>
        {admin && s.ativo && (
          <BotaoConfirmar
            action={desativarServico.bind(null, s.id)}
            rotulo="Desativar serviço"
            confirmacao={`Desativar ${s.nome}? Ele deixa de aparecer em novos agendamentos; os existentes continuam valendo.`}
            className="botao-perigo w-full sm:w-auto"
          />
        )}
      </header>

      {ok && <Aviso>Serviço salvo. Agendamentos já criados mantêm o valor estimado original.</Aviso>}
      {desativado && <Aviso>Serviço desativado.</Aviso>}

      {admin ? (
        <ServicoForm key={`${s.id}-${s.nome}-${s.preco}`} servico={s} />
      ) : (
        <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
          <div><dt className="text-xs uppercase text-muted">Preço padrão</dt><dd className="font-medium">{moeda(s.preco)}</dd></div>
          <div><dt className="text-xs uppercase text-muted">Duração</dt><dd className="font-medium">{s.duracaoMinutos ?? "—"} min</dd></div>
          <div>
            <dt className="text-xs uppercase text-muted">Por porte</dt>
            <dd className="font-medium">{s.precosPorPorte.map((p) => `${rotuloPorte(p.porte)} ${moeda(p.preco)}`).join(" · ") || "—"}</dd>
          </div>
        </dl>
      )}
    </div>
  );
}
