import { Logo } from "@/components/logo";

/** Casca das páginas públicas (recuperação de senha), no mesmo visual do login. */
export function PaginaPublica({ titulo, descricao, children }: { titulo: string; descricao: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <Logo className="mb-8" />
        <h1 className="text-2xl font-semibold">{titulo}</h1>
        <p className="mt-1 mb-6 text-sm text-muted">{descricao}</p>
        {children}
      </div>
    </div>
  );
}
