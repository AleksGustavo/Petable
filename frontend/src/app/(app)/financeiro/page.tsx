import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { DashboardFinanceiro } from "@/components/dashboard-financeiro";
import { FluxoResumo, LancamentosFiltros, LancamentosLista } from "@/components/lancamentos-lista";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import { CATEGORIAS, ORIGENS, type Fluxo, type Lancamento, type ResumoFinanceiroDia } from "@/lib/financeiro";
import { podeAdministrarFinanceiro } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";
import { FORMAS_PAGAMENTO } from "@/lib/vendas";

export const metadata: Metadata = { title: "Financeiro" };

const DATA = /^\d{4}-\d{2}-\d{2}$/;

export default async function FinanceiroPage(props: PageProps<"/financeiro">) {
  const sessao = await exigirSessao();
  if (!podeAdministrarFinanceiro(sessao.papel)) redirect("/dashboard");
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const hoje = hojeISO();
  // Padrão: mês corrente até hoje.
  let dataInicio = DATA.test(texto("dataInicio")) ? texto("dataInicio") : `${hoje.slice(0, 8)}01`;
  let dataFim = DATA.test(texto("dataFim")) ? texto("dataFim") : hoje;
  const invertido = dataFim < dataInicio;
  if (invertido) [dataInicio, dataFim] = [`${hoje.slice(0, 8)}01`, hoje];
  const pertence = (valor: string, lista: { valor: string }[]) => (lista.some((x) => x.valor === valor) ? valor : "");
  const tipo = ["ENTRADA", "SAIDA"].includes(texto("tipo")) ? texto("tipo") : "";
  const categoria = pertence(texto("categoria"), CATEGORIAS);
  const formaPagamento = pertence(texto("formaPagamento"), FORMAS_PAGAMENTO);
  const origem = pertence(texto("origem"), ORIGENS);
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const filtros = { dataInicio, dataFim, ...(tipo && { tipo }), ...(categoria && { categoria }), ...(formaPagamento && { formaPagamento }), ...(origem && { origem }) };

  const [resumo, fluxo, dados] = await Promise.all([
    apiFetch<ResumoFinanceiroDia>("/api/v1/financeiro/resumo-dia"),
    apiFetch<Fluxo>(`/api/v1/financeiro/fluxo?${new URLSearchParams({ dataInicio, dataFim })}`),
    apiFetch<Pagina<Lancamento>>(`/api/v1/financeiro/lancamentos?${new URLSearchParams({ ...filtros, page: String(pagina), size: "20" })}`),
  ]);
  const link = (p: number) => `/financeiro?${new URLSearchParams({ ...filtros, page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Financeiro</h1>
          <p className="text-sm text-muted">Regime de caixa: vendas e serviços entram na hora; compras saem no pagamento.</p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Link href="/financeiro/contas-a-pagar" className="botao-secundario">Contas a pagar</Link>
          <Link href="/financeiro/lancamentos/novo" className="botao-primario">+ Novo lançamento</Link>
        </div>
      </header>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Resumo de hoje">
        <DashboardFinanceiro resumo={resumo} />
      </section>

      <LancamentosFiltros filtros={{ dataInicio, dataFim, tipo, categoria, formaPagamento, origem }} />
      {invertido && <p role="alert" className="text-sm text-danger">A data final não pode ser anterior à inicial. Mostrando o mês atual.</p>}

      <section className="space-y-3" aria-labelledby="sec-fluxo">
        <h2 id="sec-fluxo" className="text-sm font-semibold uppercase tracking-wide text-muted">Fluxo do período</h2>
        <FluxoResumo fluxo={fluxo} />
      </section>

      <section className="space-y-3" aria-labelledby="sec-lancamentos">
        <h2 id="sec-lancamentos" className="text-sm font-semibold uppercase tracking-wide text-muted">Lançamentos ({dados.totalElements})</h2>
        <LancamentosLista lancamentos={dados.content} filtrado={Boolean(tipo || categoria || formaPagamento || origem)} />
        {dados.totalPages > 1 && (
          <nav aria-label="Paginação" className="flex items-center justify-between text-sm">
            {pagina > 0 ? <Link href={link(pagina - 1)} className="botao-secundario">← Anterior</Link> : <span />}
            <span className="text-muted">Página {pagina + 1} de {dados.totalPages}</span>
            {pagina + 1 < dados.totalPages ? <Link href={link(pagina + 1)} className="botao-secundario">Próxima →</Link> : <span />}
          </nav>
        )}
      </section>
    </div>
  );
}
