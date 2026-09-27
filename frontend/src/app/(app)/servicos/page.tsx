import type { Metadata } from "next";
import Link from "next/link";
import { moeda, type Servico } from "@/lib/agendamentos";
import { apiFetch } from "@/lib/api";
import { podeAdministrarServicos } from "@/lib/permissoes";
import { rotuloPorte } from "@/lib/pets";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Serviços" };

export default async function ServicosPage() {
  const [sessao, servicos] = await Promise.all([exigirSessao(), apiFetch<Servico[]>("/api/v1/servicos")]);
  const admin = podeAdministrarServicos(sessao.papel);

  return (
    <div className="space-y-6">
      <Link href="/agendamentos" className="text-sm text-muted hover:text-foreground">← Agendamentos</Link>
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Serviços</h1>
          <p className="text-sm text-muted">Catálogo usado nos agendamentos.</p>
        </div>
        {admin && <Link href="/servicos/novo" className="botao-primario">+ Novo serviço</Link>}
      </header>

      <ul className="grid gap-3 sm:grid-cols-2">
        {servicos.map((s) => (
          <li key={s.id}>
            <Link href={`/servicos/${s.id}`} className={`cartao block p-4 transition hover:border-brand ${s.ativo ? "" : "opacity-60"}`}>
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium">{s.nome}</p>
                {!s.ativo && <span className="rounded-full border border-border px-2 py-0.5 text-xs">Inativo</span>}
              </div>
              <p className="mt-1 text-sm text-muted">
                {moeda(s.preco)}
                {s.duracaoMinutos != null && ` · ${s.duracaoMinutos} min`}
              </p>
              {s.precosPorPorte.length > 0 && (
                <p className="mt-1 text-xs text-muted">
                  {s.precosPorPorte.map((p) => `${rotuloPorte(p.porte)} ${moeda(p.preco)}`).join(" · ")}
                </p>
              )}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
