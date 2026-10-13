"use client";

/** Botão de impressão das páginas sem AppShell; some no papel. */
export function BotaoImprimir() {
  return (
    <button type="button" className="botao-primario print:hidden" onClick={() => window.print()}>
      Imprimir
    </button>
  );
}
