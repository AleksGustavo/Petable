import Link from "next/link";
import { FotoPet } from "@/components/foto-pet";
import { rotuloPorte, type Pet } from "@/lib/pets";

/** Lista de pets: cartões no celular, tabela no md+. */
export function PetList({ pets, busca }: { pets: Pet[]; busca?: string }) {
  if (pets.length === 0) {
    return (
      <div className="cartao p-10 text-center text-sm text-muted">
        {busca ? "Nenhum pet encontrado para esta busca." : "Nenhum pet cadastrado. Cadastre o primeiro pet para começar."}
      </div>
    );
  }

  return (
    <>
      <ul className="space-y-3 md:hidden">
        {pets.map((p) => (
          <li key={p.id}>
            <Link href={`/pets/${p.id}`} className="cartao flex items-center gap-3 p-4 transition hover:border-brand">
              <FotoPet pet={p} />
              <div className="min-w-0">
                <p className="truncate font-medium">{p.nome}</p>
                <p className="truncate text-sm text-muted">
                  {p.especie.nome}
                  {p.raca && ` · ${p.raca.nome}`}
                </p>
                <p className="truncate text-sm text-muted">Tutor: {p.tutor.nome}</p>
              </div>
            </Link>
          </li>
        ))}
      </ul>

      <div className="cartao hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Pet</th>
              <th scope="col" className="px-4 py-3 font-medium">Espécie / raça</th>
              <th scope="col" className="px-4 py-3 font-medium">Tutor</th>
              <th scope="col" className="px-4 py-3 font-medium">Idade</th>
              <th scope="col" className="px-4 py-3 font-medium">Porte</th>
              <th scope="col" className="px-4 py-3"><span className="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {pets.map((p) => (
              <tr key={p.id} className="hover:bg-brand-soft/40">
                <td className="px-4 py-3">
                  <span className="flex items-center gap-3 font-medium">
                    <FotoPet pet={p} tamanho={36} />
                    {p.nome}
                  </span>
                </td>
                <td className="px-4 py-3 text-muted">
                  {p.especie.nome}
                  {p.raca && ` · ${p.raca.nome}`}
                </td>
                <td className="px-4 py-3">
                  <Link href={`/clientes/${p.tutor.id}`} className="hover:underline">{p.tutor.nome}</Link>
                </td>
                <td className="px-4 py-3 text-muted">{p.idade?.descricao ?? "—"}</td>
                <td className="px-4 py-3 text-muted">{rotuloPorte(p.porte)}</td>
                <td className="px-4 py-3 text-right">
                  <Link href={`/pets/${p.id}`} className="font-medium text-brand hover:underline">Abrir</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
