import { AppShell } from "@/components/app-shell";
import { apiFetch } from "@/lib/api";
import { temModulo } from "@/lib/capacidades";
import type { ConfiguracaoLotes } from "@/lib/lotes";
import { carregarCapacidades } from "@/lib/modulos";
import { podeVerLotes } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export default async function AppLayout({ children }: LayoutProps<"/">) {
  const sessao = await exigirSessao();
  const capacidades = await carregarCapacidades();
  // O item "Lotes e validade" só aparece com o módulo contratado, o controle ligado e para quem pode ver lotes (a API confere de novo).
  const lotesHabilitado =
    temModulo(capacidades, "lotes") && podeVerLotes(sessao.papel)
      ? await apiFetch<ConfiguracaoLotes>("/api/v1/estoque/configuracao").then((c) => c.lotesHabilitado, () => false)
      : false;
  return (
    <AppShell username={sessao.username} papel={sessao.papel} lotesHabilitado={lotesHabilitado} capacidades={capacidades}>
      {children}
    </AppShell>
  );
}
