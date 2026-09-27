import Link from "next/link";
import { FotoPet } from "@/components/foto-pet";
import type { Pet } from "@/lib/pets";

export function ClientePets({ clienteId, pets, podeEditar }: { clienteId: number; pets: Pet[]; podeEditar: boolean }) {
  return (
    <section className="cartao p-4 sm:p-6" aria-labelledby="titulo-pets-cliente">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 id="titulo-pets-cliente" className="text-sm font-semibold text-muted">
          Pets ({pets.length})
        </h2>
        {podeEditar && (
          <Link href={`/pets/novo?clienteId=${clienteId}`} className="botao-secundario px-3 py-1.5">
            Adicionar pet
          </Link>
        )}
      </div>
      {pets.length === 0 ? (
        <p className="text-sm text-muted">Nenhum pet cadastrado para este cliente.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {pets.map((p) => (
            <li key={p.id}>
              <Link href={`/pets/${p.id}`} className="flex items-center gap-3 rounded-lg border border-border p-3 transition hover:border-brand">
                <FotoPet pet={p} tamanho={40} />
                <span className="min-w-0">
                  <span className="block truncate font-medium">{p.nome}</span>
                  <span className="block truncate text-xs text-muted">
                    {p.especie.nome}
                    {p.raca && ` · ${p.raca.nome}`}
                    {p.idade && ` · ${p.idade.descricao}`}
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
