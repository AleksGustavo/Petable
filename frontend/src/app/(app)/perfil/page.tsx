import type { Metadata } from "next";
import { atualizarMinhaConta } from "@/app/actions/usuarios";
import { Aviso } from "@/components/aviso";
import { ContaForm } from "@/components/conta-form";
import { PerfilFuncionario } from "@/components/perfil-funcionario";
import { TrocarSenhaForm } from "@/components/trocar-senha-form";
import { apiFetch } from "@/lib/api";
import { rotuloPapel, type Perfil } from "@/lib/usuarios";

export const metadata: Metadata = { title: "Meu perfil" };

export default async function PerfilPage(props: PageProps<"/perfil">) {
  const { ok } = await props.searchParams;
  const perfil = await apiFetch<Perfil>("/api/v1/me");

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Meu perfil</h1>
        <p className="text-sm text-muted">Papel: {rotuloPapel(perfil.papel)}. O papel só pode ser alterado por um administrador.</p>
      </header>
      {ok === "conta" && <Aviso>Conta atualizada.</Aviso>}
      {ok === "funcionario" && <Aviso>Dados de funcionário atualizados.</Aviso>}

      <section className="cartao space-y-4 p-4 sm:p-6" aria-labelledby="sec-minha-conta">
        <h2 id="sec-minha-conta" className="text-sm font-semibold text-muted">Conta</h2>
        <ContaForm key={`${perfil.username}-${perfil.email}`} action={atualizarMinhaConta} username={perfil.username} email={perfil.email} />
      </section>

      <section className="cartao space-y-4 p-4 sm:p-6" aria-labelledby="sec-meu-funcionario">
        <h2 id="sec-meu-funcionario" className="text-sm font-semibold text-muted">Funcionário</h2>
        <PerfilFuncionario funcionario={perfil.funcionario} />
      </section>

      <section className="cartao space-y-4 p-4 sm:p-6" aria-labelledby="sec-senha">
        <h2 id="sec-senha" className="text-sm font-semibold text-muted">Alterar senha</h2>
        <TrocarSenhaForm username={perfil.username} email={perfil.email} />
      </section>
    </div>
  );
}
