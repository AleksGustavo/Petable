import type { Metadata } from "next";
import { EsqueciSenhaForm } from "@/components/esqueci-senha-form";
import { PaginaPublica } from "@/components/pagina-publica";

export const metadata: Metadata = { title: "Esqueci a senha" };

export default function EsqueciSenhaPage() {
  return (
    <PaginaPublica titulo="Esqueci a senha" descricao="Informe o e-mail da sua conta para receber um link de redefinição.">
      <EsqueciSenhaForm />
    </PaginaPublica>
  );
}
