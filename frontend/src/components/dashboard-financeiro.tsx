import Link from "next/link";
import { moeda } from "@/lib/agendamentos";
import { formatarDataHora } from "@/lib/datas";
import type { ResumoCaixaAtual, ResumoFinanceiroDia } from "@/lib/financeiro";

/** Cards do ADMIN: saldo, entradas e saídas do dia, contas vencidas/a vencer e caixas abertos. */
export function DashboardFinanceiro({ resumo }: { resumo: ResumoFinanceiroDia }) {
  const cards: { rotulo: string; valor: string; detalhe?: string; href: string; alerta?: boolean }[] = [
    { rotulo: "Saldo de hoje", valor: moeda(resumo.saldo), detalhe: `Entradas ${moeda(resumo.entradas)} · Saídas ${moeda(resumo.saidas)}`, href: "/financeiro" },
    {
      rotulo: "Contas vencidas",
      valor: String(resumo.contasVencidas),
      detalhe: moeda(resumo.valorContasVencidas),
      href: "/financeiro/contas-a-pagar?situacao=VENCIDA",
      alerta: resumo.contasVencidas > 0,
    },
    {
      rotulo: "A vencer em 7 dias",
      valor: String(resumo.contasAVencer7Dias),
      detalhe: moeda(resumo.valorAVencer7Dias),
      href: "/financeiro/contas-a-pagar?situacao=A_VENCER",
    },
    { rotulo: "Caixas abertos", valor: String(resumo.caixasAbertos), href: "/caixa" },
  ];
  return (
    <>
      {cards.map((c) => (
        <Link key={c.rotulo} href={c.href} className={`cartao group p-5 transition hover:border-brand ${c.alerta ? "border-danger/40" : ""}`}>
          <p className="text-sm text-muted">{c.rotulo}</p>
          <p className={`mt-1 text-3xl font-semibold tabular-nums ${c.alerta ? "text-danger" : ""}`}>{c.valor}</p>
          {c.detalhe && <p className="mt-1 text-sm tabular-nums text-muted">{c.detalhe}</p>}
          {c.alerta && <p className="mt-2 text-sm font-medium text-danger">⚠ Pagar agora →</p>}
        </Link>
      ))}
    </>
  );
}

/** "Meu caixa": aberto desde, dinheiro esperado — ou fechado com atalho para abrir. */
export function MeuCaixa({ resumo }: { resumo: ResumoCaixaAtual }) {
  if (!resumo.aberto) {
    return (
      <Link href="/caixa" className="cartao group flex flex-col justify-between border-dashed p-5 transition hover:border-brand">
        <p className="text-sm text-muted">Meu caixa</p>
        <p className="mt-1 text-lg font-semibold">○ Caixa fechado</p>
        <p className="mt-3 text-sm font-medium text-brand group-hover:underline">Abrir caixa →</p>
      </Link>
    );
  }
  return (
    <Link href="/caixa" className="cartao group p-5 transition hover:border-brand">
      <p className="text-sm text-muted">Meu caixa</p>
      <p className="mt-1 text-lg font-semibold">● Aberto desde {formatarDataHora(resumo.abertoEm)}</p>
      <p className="mt-1 text-sm">
        Dinheiro esperado: <strong className="tabular-nums">{moeda(resumo.saldoEsperadoDinheiro)}</strong>
      </p>
      <p className="mt-3 text-sm font-medium text-brand group-hover:underline">Operar caixa →</p>
    </Link>
  );
}
