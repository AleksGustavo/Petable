import type { Metadata } from "next";
import { PaginaPublica } from "@/components/pagina-publica";
import { RedefinirSenhaForm } from "@/components/redefinir-senha-form";

export const metadata: Metadata = { title: "Redefinir senha", referrer: "no-referrer" };

export default async function RedefinirSenhaPage(props: PageProps<"/redefinir-senha">) {
  const { token } = await props.searchParams;
  return (
    <PaginaPublica titulo="Redefinir senha" descricao="Crie uma nova senha para a sua conta.">
      <RedefinirSenhaForm token={typeof token === "string" && token.trim() ? token.trim() : null} />
    </PaginaPublica>
  );
}
