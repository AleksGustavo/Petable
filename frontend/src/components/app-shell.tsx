"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { sair } from "@/app/actions/auth";
import { Logo } from "@/components/logo";
import { moduloDaRota, temModulo, type Capacidades } from "@/lib/capacidades";

/** `papeis`: só esses papéis veem o item (a API barra de qualquer forma). `secao`: título antes do item. */
type Item = { href: string; rotulo: string; icone: string; emBreve?: boolean; papeis?: string[]; secao?: string };

/** Só aparece com o controle de lote ligado na instalação e para quem pode ver lotes (a API confere de novo). */
const ITEM_LOTES: Item = {
  href: "/estoque/lotes",
  rotulo: "Lotes e validade",
  icone: "M4 6h16M4 12h16M4 18h10M18 16l2 2 3-4",
  papeis: ["ADMIN", "VENDAS"],
};

const NAV: Item[] = [
  { href: "/dashboard", rotulo: "Início", icone: "M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z" },
  { href: "/clientes", rotulo: "Clientes", icone: "M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0Zm-12 10a8 8 0 0 1 16 0" },
  { href: "/pets", rotulo: "Pets", icone: "M12 13c-3 0-5.5 2.7-5.5 4.9 0 1.5 1.2 2.1 2.5 2.1 1.1 0 1.9-.5 3-.5s1.9.5 3 .5c1.3 0 2.5-.6 2.5-2.1C17.5 15.7 15 13 12 13ZM7 9.5a2 2.4 0 1 0 0 .1M12 7a2 2.4 0 1 0 0 .1M17 9.5a2 2.4 0 1 0 0 .1" },
  { href: "/agendamentos", rotulo: "Agendamentos", icone: "M7 3v3m10-3v3M4 8h16M5 5h14v16H5z" },
  { href: "/produtos", rotulo: "Produtos", icone: "M4 7l8-4 8 4v10l-8 4-8-4zM4 7l8 4 8-4M12 11v10", secao: "Estoque" },
  { href: "/fornecedores", rotulo: "Fornecedores", icone: "M3 21V8l6-4v4l6-4v4l6-4v17zM7 17h2m4 0h2", papeis: ["ADMIN", "VENDAS"] },
  { href: "/compras", rotulo: "Compras", icone: "M6 7h12l-1 13H7zM9 7a3 3 0 0 1 6 0", papeis: ["ADMIN", "VENDAS"] },
  { href: "/pdv", rotulo: "Vendas (PDV)", icone: "M3 4h2l2.4 11h10.2L20 7H6.2M9 20a1 1 0 1 0 0 .1m8-.1a1 1 0 1 0 0 .1", papeis: ["ADMIN", "VENDAS"], secao: "Vendas" },
  { href: "/vendas", rotulo: "Vendas", icone: "M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3", papeis: ["ADMIN", "VENDAS"] },
  { href: "/caixa", rotulo: "Caixa", icone: "M3 7h18v12H3zM3 11h18M7 15h3", secao: "Financeiro" },
  { href: "/financeiro", rotulo: "Financeiro", icone: "M12 3v18M16 7.5c0-1.9-1.8-3-4-3s-4 1.1-4 3 1.8 2.6 4 3 4 1.1 4 3-1.8 3-4 3-4-1.1-4-3", papeis: ["ADMIN"] },
  { href: "/financeiro/contas-a-pagar", rotulo: "Contas a pagar", icone: "M7 3h10v18l-2-1.5L13 21l-2-1.5L9 21l-2-1.5zM9.5 8h5M9.5 12h5", papeis: ["ADMIN"] },
  { href: "/relatorios", rotulo: "Relatórios", icone: "M4 20V10m6 10V4m6 16v-7m4 7H2", papeis: ["ADMIN", "VENDAS"], secao: "Gestão" },
  { href: "/usuarios", rotulo: "Usuários", icone: "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 10a7 7 0 0 1 14 0m1-10a3 3 0 1 0 0-6m2 16a6 6 0 0 0-3-5.2", papeis: ["ADMIN"], secao: "Administração" },
];

function Icone({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="size-5 shrink-0 fill-none stroke-current" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d={d} />
    </svg>
  );
}

function Navegacao({ onNavigate, papel, lotes, capacidades }: { onNavigate?: () => void; papel: string; lotes: boolean; capacidades: Capacidades }) {
  const pathname = usePathname();
  const aposProdutos = NAV.findIndex((i) => i.href === "/produtos") + 1;
  const itens = lotes ? [...NAV.slice(0, aposProdutos), ITEM_LOTES, ...NAV.slice(aposProdutos)] : NAV;
  // Papel e módulo são independentes: o item precisa dos dois. É só conveniência de menu; a API confere de novo.
  const visiveis = itens.filter((item) => {
    const modulo = moduloDaRota(item.href);
    return (!modulo || temModulo(capacidades, modulo)) && (!item.papeis || item.papeis.includes(papel));
  });
  const ativoHref = visiveis
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0]?.href;
  return (
    <nav aria-label="Principal" className="flex flex-col gap-1">
      {visiveis.map((item) => {
        // Só o item mais específico fica ativo (/financeiro/contas-a-pagar não acende /financeiro).
        const ativo = item.href === ativoHref;
        const titulo = item.secao && (
          <p key={`secao-${item.secao}`} className="mt-3 px-3 pb-1 text-[11px] font-semibold uppercase tracking-wide text-muted">
            {item.secao}
          </p>
        );
        if (item.emBreve) {
          return [
            titulo,
            <span key={item.href} className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted/70" aria-disabled="true">
              <Icone d={item.icone} />
              {item.rotulo}
              <span className="ml-auto whitespace-nowrap rounded-full bg-border/60 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide">em breve</span>
            </span>,
          ];
        }
        return [
          titulo,
          <Link
            key={item.href}
            href={item.href}
            onClick={onNavigate}
            aria-current={ativo ? "page" : undefined}
            className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
              ativo ? "bg-brand-soft text-brand-strong" : "hover:bg-brand-soft/60"
            }`}
          >
            <Icone d={item.icone} />
            {item.rotulo}
          </Link>,
        ];
      })}
    </nav>
  );
}

function Usuario({ username, papel }: { username: string; papel: string }) {
  return (
    <div className="flex items-center gap-3 border-t border-border pt-4">
      <span className="grid size-9 place-items-center rounded-full bg-brand text-sm font-semibold uppercase text-white">
        {username.slice(0, 2)}
      </span>
      <div className="min-w-0 flex-1">
        <Link href="/perfil" className="block truncate text-sm font-medium hover:text-brand" aria-label={`Meu perfil (${username})`}>
          {username}
        </Link>
        <p className="text-xs text-muted">{papel}</p>
      </div>
      <form action={sair}>
        <button type="submit" className="botao-secundario px-3 py-1.5 text-xs">
          Sair
        </button>
      </form>
    </div>
  );
}

export function AppShell({ username, papel, lotesHabilitado = false, capacidades, children }: { username: string; papel: string; lotesHabilitado?: boolean; capacidades: Capacidades; children: ReactNode }) {
  const [aberto, setAberto] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    if (!aberto) return;
    const fechar = (e: KeyboardEvent) => e.key === "Escape" && setAberto(false);
    window.addEventListener("keydown", fechar);
    return () => window.removeEventListener("keydown", fechar);
  }, [aberto]);

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[16rem_minmax(0,1fr)]">
      {/* Desktop: barra lateral fixa */}
      <aside className="sticky top-0 hidden h-dvh flex-col gap-6 border-r border-border bg-surface p-4 md:flex">
        <Link href="/dashboard" className="px-2 py-1">
          <Logo />
        </Link>
        <div className="flex-1 overflow-y-auto [scrollbar-color:var(--border)_transparent] [scrollbar-width:thin]">
          <Navegacao papel={papel} lotes={lotesHabilitado} capacidades={capacidades} />
        </div>
        <Usuario username={username} papel={papel} />
      </aside>

      {/* Mobile: barra superior + gaveta */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-border bg-surface/95 px-4 py-3 backdrop-blur md:hidden">
        <Link href="/dashboard">
          <Logo />
        </Link>
        <button
          type="button"
          className="botao-secundario px-2.5"
          aria-label="Abrir menu"
          aria-expanded={aberto}
          aria-controls="menu-mobile"
          onClick={() => setAberto(true)}
        >
          <Icone d="M4 7h16M4 12h16M4 17h16" />
        </button>
      </header>

      {aberto && (
        <div className="fixed inset-0 z-40 md:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <button type="button" aria-label="Fechar menu" className="absolute inset-0 bg-black/40" onClick={() => setAberto(false)} />
          <div id="menu-mobile" className="absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col gap-6 bg-surface p-4 shadow-xl">
            <div className="flex items-center justify-between">
              <Logo />
              <button type="button" className="botao-secundario px-2.5" aria-label="Fechar menu" onClick={() => setAberto(false)}>
                <Icone d="M6 6l12 12M18 6 6 18" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto [scrollbar-color:var(--border)_transparent] [scrollbar-width:thin]">
              <Navegacao key={pathname} papel={papel} lotes={lotesHabilitado} capacidades={capacidades} onNavigate={() => setAberto(false)} />
            </div>
            <Usuario username={username} papel={papel} />
          </div>
        </div>
      )}

      <main className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}
