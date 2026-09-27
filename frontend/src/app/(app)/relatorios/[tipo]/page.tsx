import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { ConteudoRelatorio } from "@/components/relatorio-conteudo";
import { RelatorioFiltros } from "@/components/relatorio-filtros";
import { hojeISO } from "@/lib/datas";
import { formatarData } from "@/lib/format";
import { definicao, podeVerRelatorio } from "@/lib/relatorios";
import { carregarRelatorio, lerPeriodo } from "@/lib/relatorios-server";
import { exigirSessao } from "@/lib/session";

export async function generateMetadata(props: PageProps<"/relatorios/[tipo]">): Promise<Metadata> {
  const { tipo } = await props.params;
  return { title: definicao(tipo)?.titulo ?? "Relatório" };
}

export default async function RelatorioPage(props: PageProps<"/relatorios/[tipo]">) {
  const sessao = await exigirSessao();
  const { tipo } = await props.params;
  const def = definicao(tipo);
  if (!def) notFound();
  if (!podeVerRelatorio(sessao.papel, def.tipo)) redirect("/relatorios");
  const hoje = hojeISO();
  const { dataInicio, dataFim, erro } = lerPeriodo(await props.searchParams, hoje);
  const relatorio = erro ? null : await carregarRelatorio(def.tipo, dataInicio, dataFim);

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <Link href="/relatorios" className="text-sm text-muted hover:text-brand">← Relatórios</Link>
        <h1 className="text-2xl font-semibold">{def.titulo}</h1>
        <p className="text-sm text-muted">{def.descricao}</p>
      </header>
      <RelatorioFiltros tipo={def.tipo} dataInicio={dataInicio} dataFim={dataFim} hoje={hoje} erro={erro} />
      {relatorio && (
        <>
          <p className="text-sm text-muted">Período: {formatarData(dataInicio)} a {formatarData(dataFim)}</p>
          <ConteudoRelatorio relatorio={relatorio} />
        </>
      )}
    </div>
  );
}
