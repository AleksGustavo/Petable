"use client";

import { useActionState } from "react";
import { alterarPapelUsuario, alternarAtivoUsuario } from "@/app/actions/usuarios";
import { Aviso } from "@/components/aviso";
import type { FormState } from "@/lib/clientes";
import { PAPEIS, rotuloPapel, type Usuario } from "@/lib/usuarios";

function confirmar(mensagem: string) {
  return (e: React.FormEvent<HTMLFormElement>) => {
    if (!window.confirm(mensagem)) e.preventDefault();
  };
}

/**
 * Papel e ativação de um usuário (só ADMIN). O próprio ADMIN não altera o
 * seu papel nem se desativa; a regra do "último ADMIN" é conferida na API.
 */
export function UsuarioSeguranca({ usuario, ehVoceMesmo }: { usuario: Usuario; ehVoceMesmo: boolean }) {
  const [statePapel, acaoPapel, pendentePapel] = useActionState<FormState, FormData>(alterarPapelUsuario.bind(null, usuario.id), {});
  const [stateAtivo, acaoAtivo, pendenteAtivo] = useActionState<FormState, FormData>(
    alternarAtivoUsuario.bind(null, usuario.id, !usuario.ativo),
    {},
  );
  const mensagem = statePapel.mensagem ?? stateAtivo.mensagem;

  return (
    <div className="space-y-4">
      {mensagem && <Aviso tipo="erro">{mensagem}</Aviso>}
      <p className="text-sm">
        Situação: <strong>{usuario.ativo ? "Ativo" : "Inativo"}</strong> · Papel atual: <strong>{rotuloPapel(usuario.papel)}</strong>
      </p>
      {ehVoceMesmo ? (
        <p className="text-sm text-muted">Esta é a sua conta: papel e ativação só podem ser alterados por outro administrador.</p>
      ) : (
        <>
          <form
            action={acaoPapel}
            onSubmit={confirmar("Alterar o papel deste usuário? As sessões abertas dele serão encerradas.")}
            className="flex flex-col gap-2 sm:flex-row sm:items-end"
          >
            <div className="flex-1">
              <label htmlFor="campo-papel" className="mb-1 block text-sm font-medium">Papel</label>
              <select id="campo-papel" name="papel" defaultValue={usuario.papel} className="campo">
                {PAPEIS.map((p) => (
                  <option key={p.valor} value={p.valor}>{p.rotulo}</option>
                ))}
              </select>
            </div>
            <button type="submit" className="botao-secundario" disabled={pendentePapel}>Alterar papel</button>
          </form>
          <form
            action={acaoAtivo}
            onSubmit={confirmar(
              usuario.ativo
                ? "Desativar este usuário? Ele não conseguirá mais entrar e as sessões abertas serão encerradas. Agendamentos existentes são mantidos."
                : "Reativar este usuário?",
            )}
          >
            <button type="submit" className={usuario.ativo ? "botao-perigo w-full sm:w-auto" : "botao-primario w-full sm:w-auto"} disabled={pendenteAtivo}>
              {usuario.ativo ? "Desativar usuário" : "Reativar usuário"}
            </button>
          </form>
        </>
      )}
    </div>
  );
}
