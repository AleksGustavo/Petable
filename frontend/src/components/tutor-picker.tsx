"use client";

import { useEffect, useId, useState } from "react";
import { mascaraTelefone } from "@/lib/format";

type Tutor = { id: number; nome: string; telefone?: string };

/** Busca o tutor pelo nome/CPF/telefone e grava o id num input escondido `clienteId`. */
export function TutorPicker({
  inicial,
  erro,
  onSelecionar,
  rotulo = "Tutor",
  obrigatorio = true,
}: {
  inicial?: Tutor | null;
  erro?: string;
  onSelecionar?: (tutor: Tutor | null) => void;
  rotulo?: string;
  obrigatorio?: boolean;
}) {
  const [tutor, setTutorState] = useState<Tutor | null>(inicial ?? null);
  const [busca, setBusca] = useState("");
  const [resultados, setResultados] = useState<Tutor[]>([]);
  const [carregando, setCarregando] = useState(false);
  const listaId = useId();

  function setTutor(t: Tutor | null) {
    setTutorState(t);
    onSelecionar?.(t);
  }

  useEffect(() => {
    if (tutor || busca.trim().length < 2) return;
    const controle = new AbortController();
    const timer = setTimeout(async () => {
      setCarregando(true);
      try {
        const r = await fetch(`/api/clientes?busca=${encodeURIComponent(busca)}`, { signal: controle.signal });
        setResultados(r.ok ? await r.json() : []);
      } catch {
        /* abortado ou sem rede */
      } finally {
        setCarregando(false);
      }
    }, 300);
    return () => {
      clearTimeout(timer);
      controle.abort();
    };
  }, [busca, tutor]);

  return (
    <div>
      <label htmlFor="campo-tutor" className="mb-1 block text-sm font-medium">
        {rotulo}
        {obrigatorio && <span className="text-danger"> *</span>}
      </label>
      <input type="hidden" name="clienteId" value={tutor?.id ?? ""} />
      {tutor ? (
        <div className="flex items-center justify-between gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm">
          <span className="truncate font-medium">{tutor.nome}</span>
          <button type="button" className="text-sm font-medium text-brand hover:underline" onClick={() => setTutor(null)}>
            Trocar
          </button>
        </div>
      ) : (
        <>
          <input
            id="campo-tutor"
            className="campo"
            placeholder="Digite nome, CPF ou telefone do tutor"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            aria-invalid={erro ? true : undefined}
            aria-describedby={erro ? "campo-tutor-erro" : undefined}
            aria-controls={listaId}
            autoComplete="off"
          />
          {busca.trim().length >= 2 && (
            <ul id={listaId} className="mt-1 max-h-56 overflow-y-auto rounded-lg border border-border bg-surface shadow-sm">
              {carregando && <li className="px-3 py-2 text-sm text-muted">Buscando…</li>}
              {!carregando && resultados.length === 0 && (
                <li className="px-3 py-2 text-sm text-muted">Nenhum cliente encontrado.</li>
              )}
              {resultados.map((c) => (
                <li key={c.id}>
                  <button
                    type="button"
                    className="flex w-full justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-brand-soft"
                    onClick={() => setTutor(c)}
                  >
                    <span className="truncate">{c.nome}</span>
                    {c.telefone && <span className="shrink-0 text-muted">{mascaraTelefone(c.telefone)}</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {erro && (
        <p id="campo-tutor-erro" className="mt-1 text-xs text-danger">
          {erro}
        </p>
      )}
    </div>
  );
}
