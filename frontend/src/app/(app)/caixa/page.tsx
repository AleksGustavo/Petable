import type { Metadata } from "next";
import Link from "next/link";
import { Aviso } from "@/components/aviso";
import { CaixaAbertura, CaixaFechamento, CaixaOperacao } from "@/components/caixa";
import { BadgeCaixa } from "@/components/financeiro-badges";
import { moeda } from "@/lib/agendamentos";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { formatarDataHora } from "@/lib/datas";
import type { Caixa } from "@/lib/financeiro";
import { resumoCaixaAtual } from "@/lib/financeiro-server";
import { podeAdministrarFinanceiro } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Caixa" };

const AVISOS: Record<string, string> = {
  aberto: "Caixa aberto. Vendas, serviços e pagamentos já podem ser registrados.",
  sangria: "Sangria registrada.",
  suprimento: "Suprimento registrado.",
};

export default async function CaixaPage(props: PageProps<"/caixa">) {
  const sessao = await exigirSessao();
  const { ok } = await props.searchParams;
  const admin = podeAdministrarFinanceiro(sessao.papel);
  const resumo = await resumoCaixaAtual();
  const [atual, recentes] = await Promise.all([
    resumo.aberto ? apiFetch<Caixa>("/api/v1/caixas/atual") : Promise.resolve(null),
    admin ? apiFetch<Pagina<Caixa>>("/api/v1/caixas?size=10") : Promise.resolve(null),
  ]);
  const aviso = typeof ok === "string" ? AVISOS[ok] : undefined;

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Meu caixa</h1>
          <p className="text-sm text-muted">Cada operador abre, opera e fecha o próprio caixa.</p>
        </div>
        <BadgeCaixa aberto={resumo.aberto} />
      </header>
      {aviso && <Aviso>{aviso}</Aviso>}

      {atual ? (
        <>
          <CaixaOperacao caixa={atual} />
          <CaixaFechamento key={JSON.stringify(atual.esperado)} caixa={atual} />
        </>
      ) : (
        <CaixaAbertura />
      )}

      {recentes && (
        <section className="space-y-2" aria-labelledby="sec-caixas">
          <h2 id="sec-caixas" className="text-sm font-semibold uppercase tracking-wide text-muted">Caixas recentes (todos os operadores)</h2>
          {recentes.content.length === 0 ? (
            <p className="cartao p-6 text-center text-sm text-muted">Nenhum caixa registrado.</p>
          ) : (
            <ul className="cartao divide-y divide-border">
              {recentes.content.map((c) => (
                <li key={c.id}>
                  <Link href={`/caixa/${c.id}`} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm hover:bg-brand-soft/40">
                    <span>
                      <strong>#{c.id}</strong> · {c.usuario.nome} · {formatarDataHora(c.abertoEm)}
                    </span>
                    <span className="flex items-center gap-2">
                      {c.status === "FECHADO" && c.diferencaTotal !== null && (
                        <span className={`tabular-nums ${c.diferencaTotal < 0 ? "text-danger" : "text-muted"}`}>dif. {moeda(c.diferencaTotal)}</span>
                      )}
                      <BadgeCaixa aberto={c.status === "ABERTO"} />
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}
