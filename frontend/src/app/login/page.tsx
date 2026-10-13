import type { Metadata } from "next";
import Link from "next/link";
import { Aviso } from "@/components/aviso";
import { LoginForm } from "@/components/login-form";
import { Logo } from "@/components/logo";

export const metadata: Metadata = { title: "Entrar" };

export default async function LoginPage(props: PageProps<"/login">) {
  const { next, aviso, expirada } = await props.searchParams;
  const mensagem = expirada
    ? "Sua sessão expirou ou foi encerrada. Entre novamente."
    : aviso === "senha-alterada"
      ? "Senha alterada. Entre de novo com a nova senha."
      : aviso === "senha-redefinida"
        ? "Senha redefinida. Entre com a nova senha."
        : null;
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <section className="relative hidden overflow-hidden bg-brand p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <Logo className="text-white [&_.text-brand]:text-accent [&_rect]:fill-white/15" />
        <div className="max-w-md">
          <h1 className="text-4xl font-semibold leading-tight">Tudo do seu petshop num só lugar.</h1>
          <p className="mt-4 text-white/85">Clientes, pets, agendamentos de banho e tosa, carteira de vacinas, PDV e estoque.</p>
        </div>
        <p className="text-sm text-white/70">TecnoDreamer · PetConnect</p>
        <div aria-hidden="true" className="pointer-events-none absolute -right-24 -bottom-24 size-96 rounded-full bg-white/10" />
      </section>

      <section className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm">
          <Logo className="mb-8 lg:hidden" />
          <h2 className="text-2xl font-semibold">Entrar</h2>
          <p className="mt-1 mb-6 text-sm text-muted">Use o usuário cadastrado pelo administrador.</p>
          {mensagem && <div className="mb-4"><Aviso>{mensagem}</Aviso></div>}
          <LoginForm next={typeof next === "string" ? next : undefined} />
          <Link href="/esqueci-senha" className="mt-4 block text-center text-sm text-brand hover:underline">Esqueci minha senha</Link>
        </div>
      </section>
    </div>
  );
}
