import type { Metadata } from "next";
import Link from "next/link";
import { DashboardEstoque } from "@/components/dashboard-estoque";
import { DashboardFinanceiro, MeuCaixa } from "@/components/dashboard-financeiro";
import { DashboardValidade } from "@/components/dashboard-validade";
import { DashboardVendas } from "@/components/dashboard-vendas";
import { apiFetch } from "@/lib/api";
import type { Cliente, Pagina } from "@/lib/clientes";
import type { ResumoCaixaAtual, ResumoFinanceiroDia } from "@/lib/financeiro";
import type { AlertasEstoque, ConfiguracaoLotes } from "@/lib/lotes";
import { podeAdministrarFinanceiro, podeVender, podeVerLotes } from "@/lib/permissoes";
import { getSessao } from "@/lib/session";
import type { ResumoDia } from "@/lib/vendas";

export const metadata: Metadata = { title: "Início" };

const MODULOS = [
  { titulo: "Relatórios", texto: "Vendas, estoque e atendimentos por período." },
];

export default async function DashboardPage() {
  const sessao = await getSessao();
  const vende = sessao ? podeVender(sessao.papel) : false;
  // SERVICO não acessa vendas: nem chama o resumo do dia.
  const admin = sessao ? podeAdministrarFinanceiro(sessao.papel) : false;
  // Cada card só chama o endpoint que o papel pode acessar.
  const [clientes, estoque, vendasHoje, meuCaixa, financeiro] = await Promise.all([
    apiFetch<Pagina<Cliente>>("/api/v1/clientes?size=1"),
    apiFetch<{ produtosAbaixoDoMinimo: number }>("/api/v1/produtos/resumo-estoque"),
    vende ? apiFetch<ResumoDia>("/api/v1/vendas/resumo-dia") : Promise.resolve(null),
    apiFetch<ResumoCaixaAtual>("/api/v1/caixas/resumo-atual"),
    admin ? apiFetch<ResumoFinanceiroDia>("/api/v1/financeiro/resumo-dia") : Promise.resolve(null),
  ]);

  // Lotes (etapa 04): o card só existe com o controle ligado e para quem pode ver lotes.
  const config = sessao && podeVerLotes(sessao.papel) ? await apiFetch<ConfiguracaoLotes>("/api/v1/estoque/configuracao") : null;
  const validade = config?.lotesHabilitado ? await apiFetch<AlertasEstoque>("/api/v1/estoque/alertas") : null;

  return (
    <div className="space-y-8">
      <header>
        <p className="text-sm text-muted">Olá, {sessao?.username}</p>
        <h1 className="text-2xl font-semibold sm:text-3xl">Painel do petshop</h1>
      </header>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Link href="/clientes" className="cartao group p-5 transition hover:border-brand">
          <p className="text-sm text-muted">Clientes ativos</p>
          <p className="mt-1 text-4xl font-semibold tabular-nums">{clientes.totalElements}</p>
          <p className="mt-3 text-sm font-medium text-brand group-hover:underline">Ver clientes →</p>
        </Link>
        <MeuCaixa resumo={meuCaixa} />
        {vendasHoje && <DashboardVendas resumo={vendasHoje} />}
        {financeiro && <DashboardFinanceiro resumo={financeiro} />}
        <DashboardEstoque produtosAbaixoDoMinimo={estoque.produtosAbaixoDoMinimo} />
        {validade && <DashboardValidade alertas={validade} />}
        <Link href="/clientes/novo" className="cartao flex flex-col justify-between border-dashed p-5 transition hover:border-brand">
          <p className="text-sm text-muted">Atalho</p>
          <p className="mt-1 text-lg font-semibold">Cadastrar novo cliente</p>
          <p className="mt-3 text-sm font-medium text-brand">Abrir formulário →</p>
        </Link>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted">Próximos módulos da migração</h2>
        <ul className="grid gap-3 sm:grid-cols-2">
          {MODULOS.map((m) => (
            <li key={m.titulo} className="cartao p-4">
              <p className="font-medium">{m.titulo}</p>
              <p className="mt-1 text-sm text-muted">{m.texto}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
