"use client";

import { useActionState, useState } from "react";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import type { FormState } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import { mascaraCep, mascaraCpf, mascaraTelefone } from "@/lib/format";
import { SEXOS, type Funcionario } from "@/lib/usuarios";

/**
 * Dados do funcionário. No modo "perfil" (o próprio funcionário) o CPF aparece
 * só para leitura: não é alterável por ele.
 */
export function FuncionarioForm({
  action,
  funcionario,
  modo,
}: {
  action: (prev: FormState, formData: FormData) => Promise<FormState>;
  funcionario?: Funcionario | null;
  modo: "admin" | "perfil";
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(action, {});
  const erros = state.erros ?? {};
  const v = (campo: string, padrao: string | null | undefined) => state.valores?.[campo] ?? padrao ?? "";
  const e = funcionario?.endereco;
  const [cpf, setCpf] = useState(v("cpf", funcionario ? mascaraCpf(funcionario.cpf) : ""));
  const [telefone, setTelefone] = useState(v("telefone", funcionario?.telefone ? mascaraTelefone(funcionario.telefone) : ""));
  const [cep, setCep] = useState(v("cep", e ? mascaraCep(e.cep) : ""));

  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Campo className="sm:col-span-2" rotulo="Nome completo" name="nome" required maxLength={100} autoComplete="name" defaultValue={v("nome", funcionario?.nome)} erro={erros.nome} />
        {modo === "admin" ? (
          <Campo rotulo="CPF" name="cpf" required inputMode="numeric" placeholder="000.000.000-00" value={cpf} onChange={(ev) => setCpf(mascaraCpf(ev.target.value))} erro={erros.cpf} />
        ) : (
          <div>
            <p className="mb-1 text-sm font-medium">CPF</p>
            <p className="rounded-lg border border-border bg-background px-3 py-2 text-sm text-muted">{funcionario ? mascaraCpf(funcionario.cpf) : "—"}</p>
            <p className="mt-1 text-xs text-muted">Só um administrador pode corrigir o CPF.</p>
          </div>
        )}
        <Campo rotulo="Data de nascimento" name="dataNascimento" type="date" max={hojeISO()} defaultValue={v("dataNascimento", funcionario?.dataNascimento)} erro={erros.dataNascimento} />
        <div>
          <label htmlFor="campo-sexo" className="mb-1 block text-sm font-medium">Sexo</label>
          {/* key: remonta com o valor devolvido pela action (o reset do React 19 não reaplica defaultValue em select). */}
          <select key={v("sexo", "")} id="campo-sexo" name="sexo" className="campo" defaultValue={v("sexo", funcionario?.sexo ?? "OUTRO")}>
            {SEXOS.map((s) => (
              <option key={s.valor} value={s.valor}>{s.rotulo}</option>
            ))}
          </select>
        </div>
        <Campo rotulo="Telefone" name="telefone" inputMode="tel" autoComplete="tel" placeholder="(19) 99999-9999" value={telefone} onChange={(ev) => setTelefone(mascaraTelefone(ev.target.value))} erro={erros.telefone} />
      </div>
      <fieldset className="grid gap-4 rounded-lg border border-border p-3 sm:grid-cols-6">
        <legend className="px-1 text-sm font-semibold text-muted">Endereço (opcional)</legend>
        <Campo className="sm:col-span-2" rotulo="CEP" name="cep" inputMode="numeric" autoComplete="postal-code" value={cep} onChange={(ev) => setCep(mascaraCep(ev.target.value))} erro={erros.cep} />
        <Campo className="sm:col-span-4" rotulo="Logradouro" name="logradouro" autoComplete="address-line1" defaultValue={v("logradouro", e?.logradouro)} erro={erros.logradouro} />
        <Campo className="sm:col-span-2" rotulo="Número" name="numero" defaultValue={v("numero", e?.numero)} erro={erros.numero} />
        <Campo className="sm:col-span-4" rotulo="Complemento" name="complemento" defaultValue={v("complemento", e?.complemento)} erro={erros.complemento} />
        <Campo className="sm:col-span-2" rotulo="Bairro" name="bairro" defaultValue={v("bairro", e?.bairro)} erro={erros.bairro} />
        <Campo className="sm:col-span-3" rotulo="Cidade" name="cidade" autoComplete="address-level2" defaultValue={v("cidade", e?.cidade)} erro={erros.cidade} />
        <Campo className="sm:col-span-1" rotulo="UF" name="uf" maxLength={2} autoComplete="address-level1" defaultValue={v("uf", e?.uf)} erro={erros.uf} />
      </fieldset>
      <div>
        <label htmlFor="campo-obs-funcionario" className="mb-1 block text-sm font-medium">Observações</label>
        <textarea id="campo-obs-funcionario" name="observacoes" rows={2} maxLength={2000} className="campo" defaultValue={v("observacoes", funcionario?.observacoes)} />
      </div>
      <div className="sm:text-right">
        <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>
          {pending ? "Salvando…" : funcionario ? "Salvar dados do funcionário" : "Cadastrar funcionário"}
        </button>
      </div>
    </form>
  );
}
