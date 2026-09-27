"use client";

import { atualizarMeuFuncionario } from "@/app/actions/usuarios";
import { FuncionarioForm } from "@/components/funcionario-form";
import type { Funcionario } from "@/lib/usuarios";

export function PerfilFuncionario({ funcionario }: { funcionario: Funcionario | null }) {
  if (!funcionario) {
    return <p className="text-sm text-muted">Esta conta não possui um perfil de funcionário vinculado.</p>;
  }
  return <FuncionarioForm action={atualizarMeuFuncionario} funcionario={funcionario} modo="perfil" />;
}
