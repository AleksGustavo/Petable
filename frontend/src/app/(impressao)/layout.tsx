import { exigirSessao } from "@/lib/session";

/** Páginas de impressão: autenticadas, mas sem AppShell. */
export default async function ImpressaoLayout({ children }: LayoutProps<"/">) {
  await exigirSessao();
  return <main className="min-h-dvh bg-white py-6 print:py-0">{children}</main>;
}
