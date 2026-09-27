import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { ContasFiltros, ContasLista } from "@/components/contas-pagar";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import type { ContaPagar } from "@/lib/financeiro";
import { podeAdministrarFinanceiro } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Contas a pagar" };

export default async function ContasPage(props: PageProps<"/financeiro/contas-a-pagar">) {
  const sessao = await exigirSessao();
  if (!podeAdministrarFinanceiro(sessao.papel)) redirect("/dashboard");
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const situacao = ["VENCIDA", "VENCE_HOJE", "A_VENCER", "PAGA"].includes(texto("situacao")) ? texto("situacao") : "";
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const filtros: Record<string, string> = situacao === "PAGA" ? { status: "PAGA" } : situacao ? { situacao } : {};
  const dados = await apiFetch<Pagina<ContaPagar>>(`/api/v1/financeiro/contas-a-pagar?${new URLSearchParams({ ...filtros, page: String(pagina), size: "20" })}`);
  const link = (p: number) => `/financeiro/contas-a-pagar?${new URLSearchParams({ ...(situacao && { situacao }), page: String(p) })}`;

  return (
    <div className="space-y-6">
      <Link href="/financeiro" className="text-sm text-muted hover:text-foreground">← Financeiro</Link>
      <header>
        <h1 className="text-2xl font-semibold">Contas a pagar</h1>
        <p className="text-sm text-muted">Criadas no recebimento das compras. Pagamento integral, uma única vez.</p>
      </header>
      <ContasFiltros situacao={situacao} />
      <ContasLista contas={dados.content} filtrado={Boolean(situacao)} />
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
