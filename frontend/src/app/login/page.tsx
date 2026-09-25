import type { Metadata } from "next";
import { LoginForm } from "@/components/login-form";
import { Logo } from "@/components/logo";

export const metadata: Metadata = { title: "Entrar" };

export default async function LoginPage(props: PageProps<"/login">) {
  const { next } = await props.searchParams;
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
          <LoginForm next={typeof next === "string" ? next : undefined} />
        </div>
      </section>
    </div>
  );
}
