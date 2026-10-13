import Link from "next/link";

export default function NotFound() {
  return (
    <div className="grid min-h-[60dvh] place-items-center p-6 text-center">
      <div>
        <p className="text-5xl font-semibold text-brand">404</p>
        <p className="mt-2 text-muted">Página ou registro não encontrado.</p>
        <Link href="/dashboard" className="botao-primario mt-6">Voltar ao início</Link>
      </div>
    </div>
  );
}
