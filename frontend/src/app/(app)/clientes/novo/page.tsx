import type { Metadata } from "next";
import Link from "next/link";
import { ClienteForm } from "@/components/cliente-form";

export const metadata: Metadata = { title: "Novo cliente" };

export default function NovoClientePage() {
  return (
    <div className="space-y-6">
      <header>
        <Link href="/clientes" className="text-sm text-muted hover:text-foreground">← Clientes</Link>
        <h1 className="mt-1 text-2xl font-semibold">Novo cliente</h1>
      </header>
      <ClienteForm />
    </div>
  );
}
