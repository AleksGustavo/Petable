"use client";

import { useEffect, useRef, useState } from "react";
import { moeda } from "@/lib/agendamentos";
import type { ProdutoRapido } from "@/lib/estoque";

export async function buscarProdutosNaApi(q: string): Promise<ProdutoRapido[]> {
  const r = await fetch(`/api/produtos/busca?${new URLSearchParams({ q })}`);
  return r.ok ? r.json() : [];
}

const CODIGO = /^\d{8,14}$/;

/**
 * Campo único para leitor de código de barras e busca por nome. Enter (ou Tab,
 * que alguns leitores enviam) com um código exato adiciona direto; texto mostra a
 * lista. Produto sem estoque aparece desabilitado.
 */
export function PdvLeitor({
  onAdicionar,
  buscar = buscarProdutosNaApi,
  desabilitado = false,
}: {
  onAdicionar: (p: ProdutoRapido) => void;
  buscar?: (q: string) => Promise<ProdutoRapido[]>;
  desabilitado?: boolean;
}) {
  const [termo, setTermo] = useState("");
  const [resultados, setResultados] = useState<ProdutoRapido[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const campo = useRef<HTMLInputElement>(null);

  // Busca por texto com debounce (códigos completos são tratados no Enter/Tab).
  useEffect(() => {
    const q = termo.trim();
    if (q.length < 2 || CODIGO.test(q)) return;
    let cancelado = false;
    const t = setTimeout(async () => {
      setBuscando(true);
      try {
        const lista = await buscar(q);
        if (!cancelado) setResultados(lista);
      } finally {
        if (!cancelado) setBuscando(false);
      }
    }, 250);
    return () => {
      cancelado = true;
      clearTimeout(t);
    };
  }, [termo, buscar]);

  function limpar() {
    setTermo("");
    setResultados([]);
    campo.current?.focus();
  }

  function adicionar(p: ProdutoRapido) {
    onAdicionar(p);
    setAviso(null);
    limpar();
  }

  async function confirmarCodigo(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key !== "Enter" && e.key !== "Tab") return;
    const q = termo.trim();
    if (!q) return;
    e.preventDefault();
    if (!CODIGO.test(q)) {
      // Texto: Enter escolhe o único resultado, se houver só um disponível.
      const disponiveis = resultados.filter((r) => r.disponivel);
      if (disponiveis.length === 1) adicionar(disponiveis[0]);
      return;
    }
    setBuscando(true);
    try {
      const lista = await buscar(q);
      const exato = lista.find((p) => p.codigoBarras === q);
      if (exato && exato.disponivel) {
        adicionar(exato);
      } else if (exato) {
        setAviso(`${exato.nome} está sem estoque.`);
        setResultados(lista);
      } else {
        setAviso(`Nenhum produto ativo com o código ${q}.`);
        setResultados(lista);
      }
    } finally {
      setBuscando(false);
    }
  }

  const visiveis = termo.trim().length < 2 ? [] : resultados;

  return (
    <div className="space-y-2">
      <label htmlFor="pdv-busca" className="block text-sm font-medium">Código de barras ou nome do produto</label>
      <input
        id="pdv-busca"
        ref={campo}
        type="search"
        autoFocus
        autoComplete="off"
        inputMode="search"
        className="campo text-base"
        placeholder="Passe o leitor ou digite (mín. 2 caracteres)"
        value={termo}
        onChange={(e) => {
          setTermo(e.target.value);
          setAviso(null);
        }}
        onKeyDown={confirmarCodigo}
        disabled={desabilitado}
      />
      {buscando && <p className="text-xs text-muted" role="status">Buscando…</p>}
      {aviso && <p className="text-sm text-danger" role="alert">{aviso}</p>}
      {!buscando && !aviso && termo.trim().length >= 2 && !CODIGO.test(termo.trim()) && visiveis.length === 0 && (
        <p className="text-xs text-muted">Nenhum produto ativo encontrado.</p>
      )}
      {visiveis.length > 0 && (
        <ul className="divide-y divide-border rounded-lg border border-border" aria-label="Produtos encontrados">
          {visiveis.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
              <span className="min-w-0">
                <span className="block truncate font-medium">{p.nome}</span>
                <span className="text-xs text-muted tabular-nums">
                  {moeda(p.precoVenda)} · {p.codigoBarras ?? "sem código"} · saldo {p.quantidadeVendavel ?? p.quantidadeEstoque}{p.controlaLote ? " (lotes válidos)" : ""}
                </span>
              </span>
              <button
                type="button"
                className="botao-secundario shrink-0 px-3 py-1 text-xs"
                disabled={!p.disponivel}
                onClick={() => adicionar(p)}
                aria-label={p.disponivel ? `Adicionar ${p.nome}` : `${p.nome} sem estoque`}
              >
                {p.disponivel ? "Adicionar" : "Sem estoque"}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
