"use client";

export default function Erro({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="cartao mx-auto max-w-lg p-6 text-center">
      <h1 className="text-lg font-semibold">Algo deu errado</h1>
      <p className="mt-2 text-sm text-muted">{error.message || "Erro inesperado ao carregar a página."}</p>
      <button type="button" className="botao-primario mt-4" onClick={reset}>
        Tentar de novo
      </button>
    </div>
  );
}
