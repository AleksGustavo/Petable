import Link from "next/link";
import { BadgeAtivo } from "@/components/estoque-badges";
import type { Fornecedor } from "@/lib/estoque";
import { mascaraCnpj, mascaraTelefone } from "@/lib/format";
import type { Papel } from "@/lib/jwt";
import { podeAdministrarFornecedores } from "@/lib/permissoes";

export function FornecedoresLista({ fornecedores, papel, filtrado = false }: { fornecedores: Fornecedor[]; papel: Papel; filtrado?: boolean }) {
  const admin = podeAdministrarFornecedores(papel);
  return (
    <div className="space-y-4">
      {admin && (
        <div className="flex justify-end">
          <Link href="/fornecedores/novo" className="botao-primario w-full sm:w-auto">+ Novo fornecedor</Link>
        </div>
      )}
      {fornecedores.length === 0 ? (
        <p className="cartao p-10 text-center text-sm text-muted">
          {filtrado ? "Nenhum fornecedor encontrado com os filtros selecionados." : "Nenhum fornecedor cadastrado."}
        </p>
      ) : (
        <>
          <ul className="space-y-3 md:hidden">
            {fornecedores.map((f) => (
              <li key={f.id}>
                <Link href={`/fornecedores/${f.id}`} className="cartao block space-y-1 p-4 transition hover:border-brand">
                  <div className="flex items-start justify-between gap-2">
                    <p className="font-medium">{f.nomeFantasia}</p>
                    <BadgeAtivo ativo={f.ativo} />
                  </div>
                  <p className="text-sm text-muted">{f.razaoSocial}</p>
                  <p className="text-sm tabular-nums">
                    {mascaraCnpj(f.cnpj)}
                    {f.telefone && ` · ${mascaraTelefone(f.telefone)}`}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
          <div className="cartao hidden overflow-x-auto md:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">Fornecedor</th>
                  <th scope="col" className="px-4 py-3 font-medium">CNPJ</th>
                  <th scope="col" className="px-4 py-3 font-medium">Contato</th>
                  <th scope="col" className="px-4 py-3 font-medium">Situação</th>
                  <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {fornecedores.map((f) => (
                  <tr key={f.id} className={`hover:bg-brand-soft/40 ${f.ativo ? "" : "text-muted"}`}>
                    <td className="px-4 py-3">
                      <p className="font-medium">{f.nomeFantasia}</p>
                      <p className="text-xs text-muted">{f.razaoSocial}</p>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 tabular-nums">{mascaraCnpj(f.cnpj)}</td>
                    <td className="px-4 py-3">
                      {[f.contato, f.telefone && mascaraTelefone(f.telefone), f.email].filter(Boolean).join(" · ") || "—"}
                    </td>
                    <td className="px-4 py-3"><BadgeAtivo ativo={f.ativo} /></td>
                    <td className="px-4 py-3 text-right">
                      <Link href={`/fornecedores/${f.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
