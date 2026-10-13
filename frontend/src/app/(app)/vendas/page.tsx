import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { VendasFiltros, VendasLista } from "@/components/vendas-lista";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import { podeVender } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";
import { FORMAS_PAGAMENTO, STATUS_VENDA, type Venda } from "@/lib/vendas";

export const metadata: Metadata = { title: "Vendas" };

const DATA = /^\d{4}-\d{2}-\d{2}$/;

export default async function VendasPage(props: PageProps<"/vendas">) {
  const sessao = await exigirSessao();
  if (!podeVender(sessao.papel)) redirect("/dashboard");
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const hoje = hojeISO();
  // Padrão: hoje. Enviar o formulário com as datas vazias lista todo o período.
  const primeiraVisita = !("dataInicio" in sp) && !("dataFim" in sp);
  const dataInicio = DATA.test(texto("dataInicio")) ? texto("dataInicio") : primeiraVisita ? hoje : "";
  const dataFim = DATA.test(texto("dataFim")) ? texto("dataFim") : primeiraVisita ? hoje : "";
  const invertido = Boolean(dataInicio && dataFim && dataFim < dataInicio);
  const status = STATUS_VENDA.some((s) => s.valor === texto("status")) ? texto("status") : "";
  const formaPagamento = FORMAS_PAGAMENTO.some((f) => f.valor === texto("formaPagamento")) ? texto("formaPagamento") : "";
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const filtros = {
    ...(!invertido && dataInicio && { dataInicio }),
    ...(!invertido && dataFim && { dataFim }),
    ...(status && { status }),
    ...(formaPagamento && { formaPagamento }),
  };
  const dados = await apiFetch<Pagina<Venda>>(`/api/v1/vendas?${new URLSearchParams({ ...filtros, page: String(pagina), size: "20" })}`);
  const link = (p: number) => `/vendas?${new URLSearchParams({ dataInicio, dataFim, status, formaPagamento, page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Vendas</h1>
          <p className="text-sm text-muted">{dados.totalElements} encontrada(s)</p>
        </div>
        <Link href="/pdv" className="botao-primario">Abrir PDV</Link>
      </header>
      <VendasFiltros filtros={{ dataInicio, dataFim, status, formaPagamento }} />
      {invertido && (
        <p role="alert" className="text-sm text-danger">A data final não pode ser anterior à inicial. O filtro de período foi ignorado.</p>
      )}
      <VendasLista vendas={dados.content} filtrado={Object.keys(filtros).length > 0} />
      {dados.totalPages > 1 && (
        <nav aria-label="Paginação" className="flex items-center justify-between text-sm">
          {pagina > 0 ? <Link href={link(pagina - 1)} className="botao-secundario">← Anterior</Link> : <span />}
          <span className="text-muted">Página {pagina + 1} de {dados.totalPages}</span>
          {pagina + 1 < dados.totalPages ? <Link href={link(pagina + 1)} className="botao-secundario">Próxima →</Link> : <span />}
        </nav>
      )}
    </div>
  );
}
