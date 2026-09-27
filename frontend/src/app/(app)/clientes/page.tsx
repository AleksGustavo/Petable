import type { Metadata } from "next";
import Link from "next/link";
import { Aviso } from "@/components/aviso";
import { apiFetch } from "@/lib/api";
import type { Cliente, Pagina } from "@/lib/clientes";
import { mascaraCpf, mascaraTelefone } from "@/lib/format";
import { podeEditarClientes } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Clientes" };

const TAMANHO = 20;

export default async function ClientesPage(props: PageProps<"/clientes">) {
  const sp = await props.searchParams;
  const busca = typeof sp.busca === "string" ? sp.busca : "";
  const pagina = Math.max(0, Number(sp.page) || 0);

  const qs = new URLSearchParams({ page: String(pagina), size: String(TAMANHO) });
  if (busca) qs.set("busca", busca);
  const [sessao, dados] = await Promise.all([
    exigirSessao(),
    apiFetch<Pagina<Cliente>>(`/api/v1/clientes?${qs}`),
  ]);

  const link = (p: number) => `/clientes?${new URLSearchParams({ ...(busca && { busca }), page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Clientes</h1>
          <p className="text-sm text-muted">{dados.totalElements} cadastrado(s)</p>
        </div>
        {podeEditarClientes(sessao.papel) && (
          <Link href="/clientes/novo" className="botao-primario">+ Novo cliente</Link>
        )}
      </header>

      {sp.inativado && <Aviso>Cliente removido da lista de ativos.</Aviso>}
      {sp.anonimizado && <Aviso>Cliente anonimizado: os dados pessoais foram removidos e o histórico foi mantido.</Aviso>}

      <form role="search" className="flex gap-2">
        <label htmlFor="busca" className="sr-only">Buscar cliente</label>
        <input id="busca" name="busca" defaultValue={busca} placeholder="Nome, CPF ou telefone" className="campo" />
        <button type="submit" className="botao-secundario">Buscar</button>
      </form>

      {dados.content.length === 0 ? (
        <div className="cartao p-10 text-center text-sm text-muted">
          {busca ? `Nenhum cliente encontrado para “${busca}”.` : "Nenhum cliente cadastrado ainda."}
        </div>
      ) : (
        <>
          {/* Mobile: cartões */}
          <ul className="space-y-3 md:hidden">
            {dados.content.map((c) => (
              <li key={c.id}>
                <Link href={`/clientes/${c.id}`} className="cartao block p-4 transition hover:border-brand">
                  <p className="font-medium">{c.nome}</p>
                  <p className="mt-1 text-sm text-muted">{mascaraTelefone(c.telefone)}</p>
                  {c.endereco && <p className="text-sm text-muted">{c.endereco.cidade}/{c.endereco.uf}</p>}
                </Link>
              </li>
            ))}
          </ul>

          {/* Desktop: tabela */}
          <div className="cartao hidden overflow-x-auto md:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">Nome</th>
                  <th scope="col" className="px-4 py-3 font-medium">CPF</th>
                  <th scope="col" className="px-4 py-3 font-medium">Telefone</th>
                  <th scope="col" className="px-4 py-3 font-medium">Cidade</th>
                  <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {dados.content.map((c) => (
                  <tr key={c.id} className="hover:bg-brand-soft/40">
                    <td className="px-4 py-3 font-medium">{c.nome}</td>
                    <td className="px-4 py-3 tabular-nums text-muted">{c.cpf ? mascaraCpf(c.cpf) : "—"}</td>
                    <td className="px-4 py-3 tabular-nums">{mascaraTelefone(c.telefone)}</td>
                    <td className="px-4 py-3 text-muted">{c.endereco ? `${c.endereco.cidade}/${c.endereco.uf}` : "—"}</td>
                    <td className="px-4 py-3 text-right">
                      <Link href={`/clientes/${c.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {dados.totalPages > 1 && (
            <nav aria-label="Paginação" className="flex items-center justify-between text-sm">
              {pagina > 0 ? <Link href={link(pagina - 1)} className="botao-secundario">← Anterior</Link> : <span />}
              <span className="text-muted">Página {pagina + 1} de {dados.totalPages}</span>
              {pagina + 1 < dados.totalPages ? <Link href={link(pagina + 1)} className="botao-secundario">Próxima →</Link> : <span />}
            </nav>
          )}
        </>
      )}
    </div>
  );
}
