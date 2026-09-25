"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { sair } from "@/app/actions/auth";
import { Logo } from "@/components/logo";

type Item = { href: string; rotulo: string; icone: string; emBreve?: boolean };

const NAV: Item[] = [
  { href: "/dashboard", rotulo: "Início", icone: "M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z" },
  { href: "/clientes", rotulo: "Clientes", icone: "M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0Zm-12 10a8 8 0 0 1 16 0" },
  { href: "/pets", rotulo: "Pets", icone: "M12 13c-3 0-5.5 2.7-5.5 4.9 0 1.5 1.2 2.1 2.5 2.1 1.1 0 1.9-.5 3-.5s1.9.5 3 .5c1.3 0 2.5-.6 2.5-2.1C17.5 15.7 15 13 12 13ZM7 9.5a2 2.4 0 1 0 0 .1M12 7a2 2.4 0 1 0 0 .1M17 9.5a2 2.4 0 1 0 0 .1", emBreve: true },
  { href: "/agendamentos", rotulo: "Agendamentos", icone: "M7 3v3m10-3v3M4 8h16M5 5h14v16H5z", emBreve: true },
  { href: "/pdv", rotulo: "Vendas (PDV)", icone: "M3 4h2l2.4 11h10.2L20 7H6.2M9 20a1 1 0 1 0 0 .1m8-.1a1 1 0 1 0 0 .1", emBreve: true },
  { href: "/produtos", rotulo: "Produtos", icone: "M4 7l8-4 8 4v10l-8 4-8-4zM4 7l8 4 8-4M12 11v10", emBreve: true },
  { href: "/relatorios", rotulo: "Relatórios", icone: "M4 20V10m6 10V4m6 16v-7m4 7H2", emBreve: true },
];

function Icone({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="size-5 shrink-0 fill-none stroke-current" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d={d} />
    </svg>
  );
}

function Navegacao({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav aria-label="Principal" className="flex flex-col gap-1">
      {NAV.map((item) => {
        const ativo = pathname === item.href || pathname.startsWith(`${item.href}/`);
        if (item.emBreve) {
          return (
            <span key={item.href} className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted/70" aria-disabled="true">
              <Icone d={item.icone} />
              {item.rotulo}
              <span className="ml-auto whitespace-nowrap rounded-full bg-border/60 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide">em breve</span>
            </span>
          );
        }
        return (
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
          </Link>
        );
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
        <p className="truncate text-sm font-medium">{username}</p>
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

export function AppShell({ username, papel, children }: { username: string; papel: string; children: ReactNode }) {
  const [aberto, setAberto] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    if (!aberto) return;
    const fechar = (e: KeyboardEvent) => e.key === "Escape" && setAberto(false);
    window.addEventListener("keydown", fechar);
    return () => window.removeEventListener("keydown", fechar);
  }, [aberto]);

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[16rem_1fr]">
      {/* Desktop: barra lateral fixa */}
      <aside className="sticky top-0 hidden h-dvh flex-col gap-6 border-r border-border bg-surface p-4 md:flex">
        <Link href="/dashboard" className="px-2 py-1">
          <Logo />
        </Link>
        <div className="flex-1 overflow-y-auto">
          <Navegacao />
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
            <div className="flex-1 overflow-y-auto">
              <Navegacao key={pathname} onNavigate={() => setAberto(false)} />
            </div>
            <Usuario username={username} papel={papel} />
          </div>
        </div>
      )}

      <main className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}
