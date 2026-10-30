"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import * as z from "zod";
import { salvarFornecedor } from "@/app/actions/estoque";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import type { FormState } from "@/lib/clientes";
import { fornecedorSchema, type Fornecedor } from "@/lib/estoque";
import { useSelectControlado } from "@/lib/select-controlado";
import { mascaraCep, mascaraCnpj, mascaraTelefone, somenteDigitos } from "@/lib/format";

const UFS = "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(" ");

function valoresIniciais(f?: Fornecedor): Record<string, string> {
  const e = f?.endereco;
  return {
    nomeFantasia: f?.nomeFantasia ?? "",
    razaoSocial: f?.razaoSocial ?? "",
    cnpj: f ? mascaraCnpj(f.cnpj) : "",
    telefone: f?.telefone ? mascaraTelefone(f.telefone) : "",
    email: f?.email ?? "",
    contato: f?.contato ?? "",
    observacoes: f?.observacoes ?? "",
    cep: e ? mascaraCep(e.cep) : "",
    logradouro: e?.logradouro ?? "",
    numero: e?.numero ?? "",
    complemento: e?.complemento ?? "",
    bairro: e?.bairro ?? "",
    cidade: e?.cidade ?? "",
    uf: e?.uf ?? "",
  };
}

export function FornecedorForm({ fornecedor }: { fornecedor?: Fornecedor }) {
  const [state, action, pending] = useActionState<FormState, FormData>(salvarFornecedor.bind(null, fornecedor?.id ?? null), {});
  const [v, setV] = useState(() => ({ ...valoresIniciais(fornecedor), ...(state.valores ?? {}) }));
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const [buscandoCep, setBuscandoCep] = useState(false);
  const erros = { ...(state.erros ?? {}), ...errosLocais };
  const refUf = useSelectControlado(v.uf, state);

  const set = (campo: string, mascara?: (s: string) => string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setV((atual) => ({ ...atual, [campo]: mascara ? mascara(e.target.value) : e.target.value }));

  /** Preenche o endereço pelo CEP (ViaCEP). Falha silenciosa: o usuário digita à mão. */
  async function completarCep() {
    const cep = somenteDigitos(v.cep);
    if (cep.length !== 8) return;
    setBuscandoCep(true);
    try {
      const d = await (await fetch(`https://viacep.com.br/ws/${cep}/json/`)).json();
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

  function validar(e: React.FormEvent<HTMLFormElement>) {
    const r = fornecedorSchema.safeParse(Object.fromEntries(new FormData(e.currentTarget).entries()));
    if (r.success) {
      setErrosLocais({});
      return;
    }
    e.preventDefault();
    setErrosLocais(
      Object.fromEntries(
        Object.entries(z.flattenError(r.error).fieldErrors).map(([k, val]) => [k, (val as string[] | undefined)?.[0] ?? "Inválido"]),
      ),
    );
  }

  return (
    <form action={action} onSubmit={validar} className="space-y-6" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Empresa</legend>
        <Campo rotulo="Nome fantasia" name="nomeFantasia" required maxLength={100} value={v.nomeFantasia} onChange={set("nomeFantasia")} erro={erros.nomeFantasia} />
        <Campo rotulo="Razão social" name="razaoSocial" required maxLength={150} value={v.razaoSocial} onChange={set("razaoSocial")} erro={erros.razaoSocial} />
        <Campo rotulo="CNPJ" name="cnpj" required inputMode="numeric" placeholder="00.000.000/0000-00" value={v.cnpj} onChange={set("cnpj", mascaraCnpj)} erro={erros.cnpj} />
        <Campo rotulo="Telefone" name="telefone" inputMode="tel" placeholder="(19) 3500-0000" value={v.telefone} onChange={set("telefone", mascaraTelefone)} erro={erros.telefone} />
        <Campo rotulo="E-mail" name="email" type="email" maxLength={120} value={v.email} onChange={set("email")} erro={erros.email} />
        <Campo rotulo="Pessoa de contato" name="contato" maxLength={100} value={v.contato} onChange={set("contato")} erro={erros.contato} />
      </fieldset>

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-6 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Endereço</legend>
        <Campo
          className="sm:col-span-2"
          rotulo="CEP"
          name="cep"
          required
          inputMode="numeric"
          placeholder="00000-000"
          value={v.cep}
          onChange={set("cep", mascaraCep)}
          onBlur={completarCep}
          erro={erros.cep}
          ajuda={buscandoCep ? "Buscando endereço…" : "Preenche o endereço automaticamente"}
        />
        <Campo className="sm:col-span-4" rotulo="Logradouro" name="logradouro" required maxLength={150} value={v.logradouro} onChange={set("logradouro")} erro={erros.logradouro} />
        <Campo className="sm:col-span-2" rotulo="Número" name="numero" maxLength={20} value={v.numero} onChange={set("numero")} erro={erros.numero} />
        <Campo className="sm:col-span-4" rotulo="Complemento" name="complemento" maxLength={100} value={v.complemento} onChange={set("complemento")} erro={erros.complemento} />
        <Campo className="sm:col-span-2" rotulo="Bairro" name="bairro" maxLength={100} value={v.bairro} onChange={set("bairro")} erro={erros.bairro} />
        <Campo className="sm:col-span-3" rotulo="Cidade" name="cidade" required maxLength={100} value={v.cidade} onChange={set("cidade")} erro={erros.cidade} />
        <div className="sm:col-span-1">
          <label htmlFor="campo-uf-fornecedor" className="mb-1 block text-sm font-medium">
            UF<span className="text-danger"> *</span>
          </label>
          <select ref={refUf} id="campo-uf-fornecedor" name="uf" className="campo" value={v.uf} onChange={set("uf")} aria-invalid={erros.uf ? true : undefined}>
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
        <Link href={fornecedor ? `/fornecedores/${fornecedor.id}` : "/fornecedores"} className="botao-secundario">Cancelar</Link>
        <button type="submit" className="botao-primario" disabled={pending}>
          {pending ? "Salvando…" : fornecedor ? "Salvar alterações" : "Cadastrar fornecedor"}
        </button>
      </div>
    </form>
  );
}
