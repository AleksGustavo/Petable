import { AppShell } from "@/components/app-shell";
import { apiFetch } from "@/lib/api";
import type { ConfiguracaoLotes } from "@/lib/lotes";
import { podeVerLotes } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export default async function AppLayout({ children }: LayoutProps<"/">) {
  const sessao = await exigirSessao();
  // O item "Lotes e validade" só aparece com o controle ligado; se a consulta falhar, o menu fica como sempre foi.
  const lotesHabilitado = podeVerLotes(sessao.papel)
    ? await apiFetch<ConfiguracaoLotes>("/api/v1/estoque/configuracao").then((c) => c.lotesHabilitado, () => false)
    : false;
  return (
    <AppShell username={sessao.username} papel={sessao.papel} lotesHabilitado={lotesHabilitado}>
      {children}
    </AppShell>
  );
}
