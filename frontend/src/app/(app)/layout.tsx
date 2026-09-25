import { AppShell } from "@/components/app-shell";
import { exigirSessao } from "@/lib/session";

export default async function AppLayout({ children }: LayoutProps<"/">) {
  const sessao = await exigirSessao();
  return (
    <AppShell username={sessao.username} papel={sessao.papel}>
      {children}
    </AppShell>
  );
}
