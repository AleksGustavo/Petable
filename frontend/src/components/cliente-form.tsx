"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { salvarCliente } from "@/app/actions/clientes";
import { Campo } from "@/components/campo";
import type { Cliente, FormState } from "@/lib/clientes";
import { mascaraCep, mascaraCpf, mascaraTelefone, somenteDigitos } from "@/lib/format";

const UFS = "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(" ");

function valoresIniciais(c?: Cliente): Record<string, string> {
  const e = c?.endereco;
  return {
    nome: c?.nome ?? "",
    cpf: c?.cpf ? mascaraCpf(c.cpf) : "",
    dataNascimento: c?.dataNascimento ?? "",
    telefone: c ? mascaraTelefone(c.telefone) : "",
    email: c?.email ?? "",
    observacoes: c?.observacoes ?? "",
    cep: e ? mascaraCep(e.cep) : "",
    logradouro: e?.logradouro ?? "",
    numero: e?.numero ?? "",
    complemento: e?.complemento ?? "",
    bairro: e?.bairro ?? "",
    cidade: e?.cidade ?? "",
    uf: e?.uf ?? "",
  };
}

export function ClienteForm({ cliente }: { cliente?: Cliente }) {
  const acao = salvarCliente.bind(null, cliente?.id ?? null);
  const [state, action, pending] = useActionState<FormState, FormData>(acao, {});
  const [v, setV] = useState(() => valoresIniciais(cliente));
  const [buscandoCep, setBuscandoCep] = useState(false);
  const erros = state.erros ?? {};

  const set = (campo: string, mascara?: (s: string) => string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setV((atual) => ({ ...atual, [campo]: mascara ? mascara(e.target.value) : e.target.value }));

  /** Preenche o endereço pelo CEP (ViaCEP). Falha silenciosa: o usuário digita à mão. */
  async function completarCep() {
    const cep = somenteDigitos(v.cep);
    if (cep.length !== 8) return;
    setBuscandoCep(true);
    try {
      const r = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
      const d = await r.json();
      if (!d.erro) {
        setV((atual) => ({
          ...atual,
          logradouro: atual.logradouro || d.logradouro || "",
          bairro: atual.bairro || d.bairro || "",
          cidade: d.localidade || atual.cidade,
          uf: d.uf || atual.uf,
        }));
      }
    } catch {
      /* sem rede ou ViaCEP fora: segue manual */
    } finally {
      setBuscandoCep(false);
    }
  }

  return (
    <form action={action} className="space-y-6" noValidate>
      {state.mensagem && (
        <p role="alert" className="rounded-lg bg-danger-soft px-4 py-3 text-sm text-danger">
          {state.mensagem}
        </p>
      )}

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Dados pessoais</legend>
        <Campo className="sm:col-span-2" rotulo="Nome completo" name="nome" required maxLength={100} value={v.nome} onChange={set("nome")} erro={erros.nome} autoComplete="name" />
        <Campo rotulo="CPF" name="cpf" inputMode="numeric" placeholder="000.000.000-00" value={v.cpf} onChange={set("cpf", mascaraCpf)} erro={erros.cpf} />
        <Campo rotulo="Data de nascimento" name="dataNascimento" type="date" value={v.dataNascimento} onChange={set("dataNascimento")} erro={erros.dataNascimento} />
        <Campo rotulo="Telefone" name="telefone" required inputMode="tel" placeholder="(19) 99999-9999" value={v.telefone} onChange={set("telefone", mascaraTelefone)} erro={erros.telefone} autoComplete="tel" />
        <Campo rotulo="E-mail" name="email" type="email" value={v.email} onChange={set("email")} erro={erros.email} autoComplete="email" />
      </fieldset>

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-6 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Endereço (opcional)</legend>
        <Campo
          className="sm:col-span-2"
          rotulo="CEP"
          name="cep"
          inputMode="numeric"
          placeholder="00000-000"
          value={v.cep}
          onChange={set("cep", mascaraCep)}
          onBlur={completarCep}
          erro={erros.cep}
          ajuda={buscandoCep ? "Buscando endereço…" : "Preenche o endereço automaticamente"}
        />
        <Campo className="sm:col-span-4" rotulo="Logradouro" name="logradouro" value={v.logradouro} onChange={set("logradouro")} erro={erros.logradouro} />
        <Campo className="sm:col-span-2" rotulo="Número" name="numero" value={v.numero} onChange={set("numero")} erro={erros.numero} />
        <Campo className="sm:col-span-4" rotulo="Complemento" name="complemento" value={v.complemento} onChange={set("complemento")} erro={erros.complemento} />
        <Campo className="sm:col-span-2" rotulo="Bairro" name="bairro" value={v.bairro} onChange={set("bairro")} erro={erros.bairro} />
        <Campo className="sm:col-span-3" rotulo="Cidade" name="cidade" value={v.cidade} onChange={set("cidade")} erro={erros.cidade} />
        <div className="sm:col-span-1">
          <label htmlFor="campo-uf" className="mb-1 block text-sm font-medium">UF</label>
          <select id="campo-uf" name="uf" className="campo" value={v.uf} onChange={set("uf")} aria-invalid={erros.uf ? true : undefined}>
            <option value="">—</option>
            {UFS.map((uf) => (
              <option key={uf}>{uf}</option>
            ))}
          </select>
          {erros.uf && <p className="mt-1 text-xs text-danger">{erros.uf}</p>}
        </div>
      </fieldset>

      <fieldset className="cartao p-4 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Observações</legend>
        <textarea name="observacoes" rows={3} maxLength={2000} className="campo" value={v.observacoes} onChange={set("observacoes")} aria-label="Observações" />
      </fieldset>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href="/clientes" className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending}>
          {pending ? "Salvando…" : cliente ? "Salvar alterações" : "Cadastrar cliente"}
        </button>
      </div>
    </form>
  );
}
