import type { Metadata } from "next";
import Link from "next/link";
import { apiFetch } from "@/lib/api";
import type { Cliente, Pagina } from "@/lib/clientes";
import { getSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Início" };

const MODULOS = [
  { titulo: "Pets", texto: "Cadastro, carteira de vacinas e histórico de consultas." },
  { titulo: "Agendamentos", texto: "Banho, tosa e consultas com preço por porte." },
  { titulo: "Vendas (PDV)", texto: "Frente de caixa com baixa automática de estoque." },
  { titulo: "Produtos e estoque", texto: "Categorias, fornecedores, compras e alerta de mínimo." },
];

export default async function DashboardPage() {
  const [sessao, clientes] = await Promise.all([
    getSessao(),
    apiFetch<Pagina<Cliente>>("/api/v1/clientes?size=1"),
  ]);

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
