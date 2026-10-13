"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import * as z from "zod";
import { salvarServico } from "@/app/actions/agendamentos";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { PORTES_SERVICO, servicoSchema, type Servico } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { rotuloPorte } from "@/lib/pets";

const decimal = (v: number | null | undefined) => (v == null ? "" : String(v).replace(".", ","));

export function ServicoForm({ servico }: { servico?: Servico }) {
  const [state, action, pending] = useActionState<FormState, FormData>(salvarServico.bind(null, servico?.id ?? null), {});
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const erros = { ...(state.erros ?? {}), ...errosLocais };
  const valor = (campo: string, padrao: string) => state.valores?.[campo] ?? padrao;
  const precoDoPorte = (porte: string) => servico?.precosPorPorte.find((p) => p.porte === porte)?.preco;

  /** Valida no navegador antes de enviar (a Server Action e a API validam de novo). */
  function validar(e: React.FormEvent<HTMLFormElement>) {
    const dados = Object.fromEntries(new FormData(e.currentTarget).entries());
    const r = servicoSchema.safeParse(dados);
    if (r.success) {
      setErrosLocais({});
      return;
    }
    e.preventDefault();
    setErrosLocais(
      Object.fromEntries(
        Object.entries(z.flattenError(r.error).fieldErrors).map(([k, v]) => [k, (v as string[] | undefined)?.[0] ?? "Inválido"]),
      ),
    );
  }

  return (
    <form action={action} onSubmit={validar} className="space-y-6" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Serviço</legend>
        <Campo className="sm:col-span-3" rotulo="Nome" name="nome" required maxLength={100} defaultValue={valor("nome", servico?.nome ?? "")} erro={erros.nome} />
        <Campo rotulo="Preço padrão (R$)" name="preco" required inputMode="decimal" defaultValue={valor("preco", decimal(servico?.preco))} erro={erros.preco} />
        <Campo rotulo="Duração (minutos)" name="duracaoMinutos" required inputMode="numeric" defaultValue={valor("duracaoMinutos", servico?.duracaoMinutos ? String(servico.duracaoMinutos) : "")} erro={erros.duracaoMinutos} />
        <div className="sm:col-span-3">
          <label htmlFor="campo-descricao-servico" className="mb-1 block text-sm font-medium">Descrição</label>
          <textarea id="campo-descricao-servico" name="descricao" rows={2} maxLength={1000} className="campo" defaultValue={valor("descricao", servico?.descricao ?? "")} />
        </div>
      </fieldset>

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Preço por porte (opcional)</legend>
        <p className="text-sm text-muted sm:col-span-3">
          Quando o pet tem porte e há preço para ele, este valor substitui o padrão. Pet sem porte usa sempre o padrão.
        </p>
        {PORTES_SERVICO.map((porte) => (
          <Campo
            key={porte}
            rotulo={`${rotuloPorte(porte)} (R$)`}
            name={`preco${porte}`}
            inputMode="decimal"
            defaultValue={valor(`preco${porte}`, decimal(precoDoPorte(porte)))}
            erro={erros[`preco${porte}`]}
          />
        ))}
        {erros.precosPorPorte && <p className="text-xs text-danger sm:col-span-3">{erros.precosPorPorte}</p>}
      </fieldset>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href="/servicos" className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending}>
          {pending ? "Salvando…" : servico ? "Salvar alterações" : "Cadastrar serviço"}
        </button>
      </div>
    </form>
  );
}
