import Link from "next/link";
import type { Usuario } from "@/lib/usuarios";
import { rotuloPapel } from "@/lib/usuarios";

function Situacao({ ativo }: { ativo: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium ${ativo ? "border-brand/40 bg-brand-soft text-brand-strong" : "border-border text-muted"}`}>
      <span aria-hidden="true">{ativo ? "●" : "○"}</span>
      {ativo ? "Ativo" : "Inativo"}
    </span>
  );
}

export function UsuariosLista({ usuarios, filtrado = false }: { usuarios: Usuario[]; filtrado?: boolean }) {
  if (usuarios.length === 0) {
    return (
      <p className="cartao p-10 text-center text-sm text-muted">
        {filtrado ? "Nenhum usuário encontrado com os filtros selecionados." : "Nenhum usuário cadastrado."}
      </p>
    );
  }
  return (
    <>
      <ul className="space-y-3 md:hidden">
        {usuarios.map((u) => (
          <li key={u.id}>
            <Link href={`/usuarios/${u.id}`} className="cartao block space-y-1 p-4 transition hover:border-brand">
              <div className="flex items-center justify-between gap-2">
                <p className="truncate font-medium">{u.username}</p>
                <Situacao ativo={u.ativo} />
              </div>
              <p className="truncate text-sm text-muted">{u.email}</p>
              <p className="text-sm">
                {rotuloPapel(u.papel)}
                {u.funcionario && <span className="text-muted"> · {u.funcionario.nome}</span>}
              </p>
            </Link>
          </li>
        ))}
      </ul>
      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Usuário</th>
              <th scope="col" className="px-4 py-3 font-medium">E-mail</th>
              <th scope="col" className="px-4 py-3 font-medium">Papel</th>
              <th scope="col" className="px-4 py-3 font-medium">Funcionário</th>
              <th scope="col" className="px-4 py-3 font-medium">Situação</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {usuarios.map((u) => (
              <tr key={u.id} className={`hover:bg-brand-soft/40 ${u.ativo ? "" : "text-muted"}`}>
                <td className="px-4 py-3 font-medium">{u.username}</td>
                <td className="px-4 py-3">{u.email}</td>
                <td className="px-4 py-3">{rotuloPapel(u.papel)}</td>
                <td className="px-4 py-3">{u.funcionario?.nome ?? "—"}</td>
                <td className="px-4 py-3"><Situacao ativo={u.ativo} /></td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/usuarios/${u.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
