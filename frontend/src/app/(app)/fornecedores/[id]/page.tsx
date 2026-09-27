import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { alternarAtivoFornecedor } from "@/app/actions/estoque";
import { AlternarAtivo } from "@/components/alternar-ativo";
import { Aviso } from "@/components/aviso";
import { ComprasLista } from "@/components/compras-lista";
import { BadgeAtivo } from "@/components/estoque-badges";
import { FornecedorForm } from "@/components/fornecedor-form";
import { ApiError, apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import type { Compra, Fornecedor } from "@/lib/estoque";
import { mascaraCep, mascaraCnpj, mascaraTelefone } from "@/lib/format";
import { podeAdministrarFornecedores, podeVerFornecedoresECompras } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Fornecedor" };

const AVISOS: Record<string, string> = {
  criado: "Fornecedor cadastrado.",
  salvo: "Fornecedor atualizado.",
  desativado: "Fornecedor desativado. Ele não aparece em novas compras; o histórico foi mantido.",
  ativado: "Fornecedor reativado.",
};

async function carregar(id: string): Promise<Fornecedor> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Fornecedor>(`/api/v1/fornecedores/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

function Ficha({ f }: { f: Fornecedor }) {
  const e = f.endereco;
  const itens: [string, string][] = [
    ["Razão social", f.razaoSocial],
    ["CNPJ", mascaraCnpj(f.cnpj)],
    ["Telefone", f.telefone ? mascaraTelefone(f.telefone) : "—"],
    ["E-mail", f.email ?? "—"],
    ["Contato", f.contato ?? "—"],
    [
      "Endereço",
      e ? `${e.logradouro}${e.numero ? `, ${e.numero}` : ""}${e.bairro ? ` — ${e.bairro}` : ""}, ${e.cidade}/${e.uf} · ${mascaraCep(e.cep)}` : "—",
    ],
  ];
  return (
    <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
      {itens.map(([rotulo, valor]) => (
        <div key={rotulo} className={rotulo === "Endereço" ? "sm:col-span-3" : ""}>
          <dt className="text-xs uppercase text-muted">{rotulo}</dt>
          <dd className="font-medium">{valor}</dd>
        </div>
      ))}
    </dl>
  );
}

export default async function FornecedorPage(props: PageProps<"/fornecedores/[id]">) {
  const sessao = await exigirSessao();
  if (!podeVerFornecedoresECompras(sessao.papel)) redirect("/dashboard");
  const { id } = await props.params;
  const { ok } = await props.searchParams;
  const fornecedor = await carregar(id);
  const recentes = await apiFetch<Pagina<Compra>>(`/api/v1/compras?fornecedorId=${fornecedor.id}&size=5`);
  const admin = podeAdministrarFornecedores(sessao.papel);
  const aviso = typeof ok === "string" ? AVISOS[ok] : undefined;

  return (
    <div className="space-y-6">
      <Link href="/fornecedores" className="text-sm text-muted hover:text-foreground">← Fornecedores</Link>
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold">{fornecedor.nomeFantasia}</h1>
          <BadgeAtivo ativo={fornecedor.ativo} />
        </div>
        {admin && (
          <AlternarAtivo
            action={alternarAtivoFornecedor.bind(null, fornecedor.id, !fornecedor.ativo)}
            ativo={fornecedor.ativo}
            rotulo={fornecedor.ativo ? "Desativar fornecedor" : "Reativar fornecedor"}
            confirmacao={
              fornecedor.ativo
                ? `Desativar ${fornecedor.nomeFantasia}? Ele deixa de aparecer em novas compras. Não é possível desativar com pedidos em aberto.`
                : `Reativar ${fornecedor.nomeFantasia}?`
            }
          />
        )}
      </header>

      {aviso && <Aviso>{aviso}</Aviso>}

      {admin ? <FornecedorForm key={fornecedor.updatedAt} fornecedor={fornecedor} /> : <Ficha f={fornecedor} />}

      <section className="space-y-3" aria-labelledby="sec-compras">
        <div className="flex items-end justify-between gap-2">
          <h2 id="sec-compras" className="text-sm font-semibold text-muted">Compras recentes</h2>
          {recentes.totalElements > 5 && (
            <Link href={`/compras?fornecedorId=${fornecedor.id}`} className="text-sm font-medium text-brand hover:underline">
              Ver todas ({recentes.totalElements}) →
            </Link>
          )}
        </div>
        <ComprasLista compras={recentes.content} />
      </section>
    </div>
  );
}
