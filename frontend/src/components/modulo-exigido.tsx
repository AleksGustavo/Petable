import Link from "next/link";
import type { ReactNode } from "react";
import { moduloAtivo } from "@/lib/modulos";

/** Aviso amigável para um recurso que esta instalação não contratou (sem citar módulos). */
export function RecursoIndisponivel() {
  return (
    <div className="cartao mx-auto max-w-lg space-y-3 p-8 text-center" role="status">
      <h1 className="text-xl font-semibold">Recurso indisponível</h1>
      <p className="text-sm text-muted">Este recurso não está disponível nesta instalação. Fale com o administrador se precisar dele.</p>
      <Link href="/dashboard" className="botao-secundario inline-block">
        Voltar ao início
      </Link>
    </div>
  );
}

/** Layout de segmento: só renderiza o conteúdo se o módulo estiver ativo. A API barra de qualquer forma. */
export async function ModuloExigido({ modulo, children }: { modulo: string; children: ReactNode }) {
  return (await moduloAtivo(modulo)) ? children : <RecursoIndisponivel />;
}
