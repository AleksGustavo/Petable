import type { Metadata } from "next";
import Link from "next/link";
import { Aviso } from "@/components/aviso";
import { PetList } from "@/components/pet-list";
import { apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { podeEditarPets } from "@/lib/permissoes";
import type { Pet } from "@/lib/pets";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Pets" };

const TAMANHO = 20;

export default async function PetsPage(props: PageProps<"/pets">) {
  const sp = await props.searchParams;
  const busca = typeof sp.q === "string" ? sp.q.slice(0, 100) : "";
  const pagina = Math.max(0, Number(sp.page) || 0);

  const qs = new URLSearchParams({ page: String(pagina), size: String(TAMANHO) });
  if (busca) qs.set("q", busca);
  const [sessao, dados] = await Promise.all([exigirSessao(), apiFetch<Pagina<Pet>>(`/api/v1/pets?${qs}`)]);
  const link = (p: number) => `/pets?${new URLSearchParams({ ...(busca && { q: busca }), page: String(p) })}`;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Pets</h1>
          <p className="text-sm text-muted">{dados.totalElements} ativo(s)</p>
        </div>
        {podeEditarPets(sessao.papel) && <Link href="/pets/novo" className="botao-primario">+ Novo pet</Link>}
      </header>

      {sp.desativado && <Aviso>Pet removido da lista de ativos.</Aviso>}

      <form role="search" className="flex gap-2">
        <label htmlFor="busca-pet" className="sr-only">Buscar por pet ou tutor</label>
        <input id="busca-pet" name="q" defaultValue={busca} maxLength={100} placeholder="Buscar por pet ou tutor" className="campo" />
        <button type="submit" className="botao-secundario">Buscar</button>
      </form>

      <PetList pets={dados.content} busca={busca} />

      {dados.totalPages > 1 && (
        <nav aria-label="Paginação" className="flex items-center justify-between text-sm">
          {pagina > 0 ? <Link href={link(pagina - 1)} className="botao-secundario">← Anterior</Link> : <span />}
          <span className="text-muted">Página {pagina + 1} de {dados.totalPages}</span>
          {pagina + 1 < dados.totalPages ? <Link href={link(pagina + 1)} className="botao-secundario">Próxima →</Link> : <span />}
        </nav>
      )}
    </div>
  );
}
