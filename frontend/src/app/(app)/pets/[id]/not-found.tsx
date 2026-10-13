import Link from "next/link";

export default function PetNaoEncontrado() {
  return (
    <div className="cartao mx-auto max-w-lg p-8 text-center">
      <h1 className="text-lg font-semibold">Pet não encontrado.</h1>
      <p className="mt-2 text-sm text-muted">Ele pode ter sido removido da lista de ativos.</p>
      <Link href="/pets" className="botao-primario mt-6">Voltar para pets</Link>
    </div>
  );
}
