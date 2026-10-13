import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { ConteudoRelatorio } from "@/components/relatorio-conteudo";
import { BotaoImprimir } from "@/components/botao-imprimir";
import { formatarInstante, hojeISO } from "@/lib/datas";
import { formatarData } from "@/lib/format";
import { definicao, podeVerRelatorio } from "@/lib/relatorios";
import { carregarRelatorio, lerPeriodo } from "@/lib/relatorios-server";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Imprimir relatório" };

export default async function ImprimirRelatorioPage(props: PageProps<"/relatorios/[tipo]/imprimir">) {
  const sessao = await exigirSessao();
  const { tipo } = await props.params;
  const def = definicao(tipo);
  if (!def) notFound();
  if (!podeVerRelatorio(sessao.papel, def.tipo)) redirect("/relatorios");
  const { dataInicio, dataFim, erro } = lerPeriodo(await props.searchParams, hojeISO());
  if (erro) redirect(`/relatorios/${def.tipo}`);
  const relatorio = await carregarRelatorio(def.tipo, dataInicio, dataFim);

  return (
    <article className="tema-claro mx-auto max-w-5xl space-y-6 px-4">
      <style>{"@media print { @page { size: A4; margin: 12mm; } body { background: #fff; } }"}</style>
      <header className="flex flex-col gap-2 border-b border-border pb-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm">PetConnect</p>
          <h1 className="text-xl font-semibold">{def.titulo}</h1>
          <p className="text-sm">
            Período: {formatarData(dataInicio)} a {formatarData(dataFim)} · Emitido em {formatarInstante(new Date().toISOString())}
          </p>
        </div>
        <BotaoImprimir />
      </header>
      <ConteudoRelatorio relatorio={relatorio} />
    </article>
  );
}
