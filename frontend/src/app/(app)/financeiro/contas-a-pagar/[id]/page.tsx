import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Aviso } from "@/components/aviso";
import { ContaDetalhes } from "@/components/contas-pagar";
import type { ContaPagar } from "@/lib/financeiro";
import { carregar, resumoCaixaAtual } from "@/lib/financeiro-server";
import { podeAdministrarFinanceiro } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Conta a pagar" };

export default async function ContaPage(props: PageProps<"/financeiro/contas-a-pagar/[id]">) {
  const sessao = await exigirSessao();
  if (!podeAdministrarFinanceiro(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const [conta, caixa] = await Promise.all([carregar<ContaPagar>("/api/v1/financeiro/contas-a-pagar", id), resumoCaixaAtual()]);
  return (
    <div className="space-y-6">
      <Link href="/financeiro/contas-a-pagar" className="text-sm text-muted hover:text-foreground">← Contas a pagar</Link>
      <header>
        <h1 className="text-2xl font-semibold">{conta.descricao}</h1>
        <p className="text-sm text-muted">Conta #{conta.id}</p>
      </header>
      {ok === "paga" && <Aviso>Pagamento registrado: a saída entrou no seu caixa.</Aviso>}
      <ContaDetalhes conta={conta} caixaAberto={caixa.aberto} />
    </div>
  );
}
