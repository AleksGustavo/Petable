"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { abrirCaixa, fecharCaixa, movimentarCaixa } from "@/app/actions/financeiro";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { Diferenca } from "@/components/financeiro-badges";
import { moeda } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { formatarDataHora, formatarInstante } from "@/lib/datas";
import { moedaCentavos } from "@/lib/estoque";
import { diferencasCentavos, type Caixa } from "@/lib/financeiro";
import { FORMAS_PAGAMENTO } from "@/lib/vendas";

// ------------------------------------------------------------------ abertura

export function CaixaAbertura() {
  const [state, action, pending] = useActionState<FormState, FormData>(abrirCaixa, {});
  return (
    <form action={action} className="cartao mx-auto max-w-md space-y-4 p-4 sm:p-6" noValidate>
      <h2 className="text-lg font-semibold">Abrir caixa</h2>
      <p className="text-sm text-muted">Informe o dinheiro que está na gaveta para troco. Vendas, serviços e pagamentos só são registrados com o caixa aberto.</p>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <Campo
        rotulo="Fundo de troco (R$)"
        name="fundoAbertura"
        inputMode="decimal"
        placeholder="0,00"
        defaultValue={state.valores?.fundoAbertura ?? ""}
        erro={state.erros?.fundoAbertura}
        ajuda="Pode ser zero."
      />
      <button type="submit" className="botao-primario w-full" disabled={pending}>
        {pending ? "Abrindo…" : "Abrir caixa"}
      </button>
    </form>
  );
}

// ------------------------------------------------------------------ operação

function MovimentoForm({ tipo }: { tipo: "sangrias" | "suprimentos" }) {
  const [state, action, pending] = useActionState<FormState, FormData>(movimentarCaixa.bind(null, tipo), {});
  const sangria = tipo === "sangrias";
  return (
    <form
      action={action}
      onSubmit={(e) => {
        const valor = String(new FormData(e.currentTarget).get("valor") ?? "");
        if (!window.confirm(`${sangria ? "Retirar" : "Adicionar"} R$ ${valor || "0"} ${sangria ? "do" : "no"} caixa?`)) e.preventDefault();
      }}
      className="space-y-3 rounded-lg border border-border p-4"
      noValidate
      aria-label={sangria ? "Sangria" : "Suprimento"}
    >
      <h3 className="font-medium">{sangria ? "Sangria (retirada)" : "Suprimento (reforço)"}</h3>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <Campo id={`${tipo}-valor`} rotulo="Valor (R$)" name="valor" inputMode="decimal" placeholder="0,00" defaultValue={state.valores?.valor ?? ""} erro={state.erros?.valor} required />
      <Campo id={`${tipo}-motivo`} rotulo="Motivo" name="motivo" maxLength={500} defaultValue={state.valores?.motivo ?? ""} erro={state.erros?.motivo} required />
      <button type="submit" className={`${sangria ? "botao-secundario" : "botao-secundario"} w-full sm:w-auto`} disabled={pending}>
        {pending ? "Registrando…" : sangria ? "Registrar sangria" : "Registrar suprimento"}
      </button>
    </form>
  );
}

export function EsperadoPorForma({ caixa }: { caixa: Caixa }) {
  return (
    <dl className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
      {FORMAS_PAGAMENTO.map((f) => (
        <div key={f.valor} className="flex items-baseline justify-between gap-2 rounded-lg border border-border px-3 py-2">
          <dt className="text-sm text-muted">{f.rotulo}</dt>
          <dd className="font-semibold tabular-nums" data-testid={`esperado-${f.valor}`}>{moeda(caixa.esperado?.[f.valor] ?? 0)}</dd>
        </div>
      ))}
    </dl>
  );
}

export function MovimentosCaixa({ caixa }: { caixa: Caixa }) {
  const movimentos = caixa.movimentos ?? [];
  if (movimentos.length === 0) return <p className="text-sm text-muted">Nenhuma sangria ou suprimento.</p>;
  return (
    <ul className="divide-y divide-border rounded-lg border border-border">
      {movimentos.map((m) => (
        <li key={m.id} className="flex flex-wrap items-baseline justify-between gap-2 px-3 py-2 text-sm">
          <span>
            <strong>{m.tipo === "SANGRIA" ? "Sangria" : "Suprimento"}</strong> · {m.motivo}
            <span className="block text-xs text-muted">{formatarInstante(m.createdAt)} · {m.usuario.nome}</span>
          </span>
          <span className={`font-semibold tabular-nums ${m.tipo === "SANGRIA" ? "text-danger" : "text-brand-strong"}`}>
            {m.tipo === "SANGRIA" ? "−" : "+"} {moeda(m.valor)}
          </span>
        </li>
      ))}
    </ul>
  );
}

export function CaixaOperacao({ caixa }: { caixa: Caixa }) {
  return (
    <div className="space-y-6">
      <section className="cartao space-y-3 p-4 sm:p-6" aria-labelledby="sec-esperado">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="sec-esperado" className="text-sm font-semibold text-muted">Esperado agora</h2>
          <p className="text-sm text-muted">
            Aberto em {formatarDataHora(caixa.abertoEm)} · fundo {moeda(caixa.fundoAbertura)}
          </p>
        </div>
        <EsperadoPorForma caixa={caixa} />
        <p className="text-xs text-muted">Dinheiro = fundo + entradas − saídas em dinheiro + suprimentos − sangrias.</p>
      </section>
      <section className="cartao grid gap-4 p-4 sm:p-6 md:grid-cols-2" aria-label="Movimentos de caixa">
        <MovimentoForm tipo="suprimentos" />
        <MovimentoForm tipo="sangrias" />
        <div className="space-y-2 md:col-span-2">
          <h3 className="text-sm font-semibold text-muted">Sangrias e suprimentos</h3>
          <MovimentosCaixa caixa={caixa} />
        </div>
      </section>
    </div>
  );
}

// ------------------------------------------------------------------ fechamento

/** Conferência por forma: a diferença aparece enquanto digita; qualquer diferença exige motivo. */
export function CaixaFechamento({ caixa }: { caixa: Caixa }) {
  const [state, action, pending] = useActionState<FormState, FormData>(fecharCaixa, {});
  const esperado = (caixa.esperado ?? {}) as Record<string, number>;
  const [contado, setContado] = useState<Record<string, string>>(() =>
    Object.fromEntries(FORMAS_PAGAMENTO.map((f) => [f.valor, state.valores?.[`contado${f.valor}`] ?? ""])),
  );
  const diferencas = diferencasCentavos(esperado, contado);
  const preenchido = FORMAS_PAGAMENTO.every((f) => !Number.isNaN(diferencas[f.valor]));
  const totalDiferenca = FORMAS_PAGAMENTO.reduce((s, f) => s + (Number.isNaN(diferencas[f.valor]) ? 0 : diferencas[f.valor]), 0);
  const temDiferenca = FORMAS_PAGAMENTO.some((f) => !Number.isNaN(diferencas[f.valor]) && diferencas[f.valor] !== 0);
  const erros = state.erros ?? {};

  return (
    <form
      action={action}
      onSubmit={(e) => {
        const msg = temDiferenca
          ? `Fechar o caixa com diferença de ${moedaCentavos(totalDiferenca)}? O fechamento é definitivo.`
          : "Fechar o caixa? O fechamento é definitivo e não há reabertura.";
        if (!window.confirm(msg)) e.preventDefault();
      }}
      className="cartao space-y-4 p-4 sm:p-6"
      noValidate
      aria-label="Fechamento do caixa"
    >
      <h2 className="text-lg font-semibold">Fechar caixa</h2>
      <p className="text-sm text-muted">
        Conte o dinheiro da gaveta. Em cartão, Pix e transferência informe o líquido do extrato (recebido − pago), que pode ser negativo.
      </p>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <input type="hidden" name="esperado" value={JSON.stringify(esperado)} />
      <div className="space-y-2">
        {FORMAS_PAGAMENTO.map((f) => {
          const d = diferencas[f.valor];
          return (
            <div key={f.valor} className="grid grid-cols-[1fr_8rem] items-center gap-x-3 gap-y-1 rounded-lg border border-border p-3 sm:grid-cols-[1fr_8rem_8rem_10rem]">
              <label htmlFor={`contado-${f.valor}`} className="text-sm font-medium">{f.rotulo}</label>
              <input
                id={`contado-${f.valor}`}
                name={`contado${f.valor}`}
                inputMode="decimal"
                placeholder="0,00"
                className="campo text-right tabular-nums"
                value={contado[f.valor]}
                onChange={(e) => setContado((c) => ({ ...c, [f.valor]: e.target.value }))}
                aria-invalid={erros[`contado${f.valor}`] ? true : undefined}
              />
              <p className="text-sm text-muted tabular-nums sm:text-right">esperado {moeda(esperado[f.valor] ?? 0)}</p>
              <p className="text-sm sm:text-right" data-testid={`diferenca-${f.valor}`}>
                {Number.isNaN(d) ? <span className="text-muted">—</span> : <Diferenca valor={d / 100} formatar={moeda} />}
              </p>
              {erros[`contado${f.valor}`] && <p className="col-span-full text-xs text-danger">{erros[`contado${f.valor}`]}</p>}
            </div>
          );
        })}
      </div>
      <p className="text-sm" data-testid="diferenca-total">
        Diferença total: {preenchido ? <Diferenca valor={totalDiferenca / 100} formatar={moeda} /> : <span className="text-muted">preencha todos os valores</span>}
      </p>
      {(temDiferenca || erros.motivoDiferenca) && (
        <div>
          <label htmlFor="campo-motivo-diferenca" className="mb-1 block text-sm font-medium">
            Motivo da diferença<span className="text-danger"> *</span>
          </label>
          <textarea
            id="campo-motivo-diferenca"
            name="motivoDiferenca"
            rows={2}
            maxLength={500}
            className="campo"
            defaultValue={state.valores?.motivoDiferenca ?? ""}
            aria-invalid={erros.motivoDiferenca ? true : undefined}
          />
          {erros.motivoDiferenca && <p className="mt-1 text-xs text-danger">{erros.motivoDiferenca}</p>}
        </div>
      )}
      <button type="submit" className="botao-perigo w-full sm:w-auto" disabled={pending || !preenchido}>
        {pending ? "Fechando…" : "Fechar caixa"}
      </button>
    </form>
  );
}

// ------------------------------------------------------------------ detalhe (histórico)

export function CaixaDetalhes({ caixa }: { caixa: Caixa }) {
  const fechado = caixa.status === "FECHADO";
  return (
    <div className="space-y-6">
      <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
        {[
          ["Operador", caixa.usuario.nome],
          ["Abertura", formatarDataHora(caixa.abertoEm)],
          ["Fechamento", fechado ? formatarDataHora(caixa.fechadoEm) : "Em aberto"],
          ["Fundo de troco", moeda(caixa.fundoAbertura)],
        ].map(([rotulo, valor]) => (
          <div key={rotulo}>
            <dt className="text-xs uppercase text-muted">{rotulo}</dt>
            <dd className="font-medium">{valor}</dd>
          </div>
        ))}
        {fechado && caixa.diferencaTotal !== null && (
          <div>
            <dt className="text-xs uppercase text-muted">Diferença total</dt>
            <dd><Diferenca valor={caixa.diferencaTotal} formatar={moeda} /></dd>
          </div>
        )}
        {caixa.motivoDiferenca && (
          <div className="sm:col-span-3">
            <dt className="text-xs uppercase text-muted">Motivo da diferença</dt>
            <dd className="text-sm">{caixa.motivoDiferenca}</dd>
          </div>
        )}
      </dl>
      {fechado ? (
        <section className="cartao overflow-x-auto" aria-label="Conferência">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Forma</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Esperado</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Contado</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Diferença</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {FORMAS_PAGAMENTO.map((f) => (
                <tr key={f.valor}>
                  <td className="px-4 py-3">{f.rotulo}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{moeda(caixa.esperado?.[f.valor] ?? 0)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{moeda(caixa.contado?.[f.valor] ?? 0)}</td>
                  <td className="px-4 py-3 text-right"><Diferenca valor={caixa.diferenca?.[f.valor] ?? 0} formatar={moeda} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : (
        <EsperadoPorForma caixa={caixa} />
      )}
      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-muted">Sangrias e suprimentos</h2>
        <MovimentosCaixa caixa={caixa} />
      </section>
      {fechado && (
        <Link href={`/caixa/${caixa.id}/comprovante`} target="_blank" className="botao-secundario w-full sm:w-auto">
          Imprimir comprovante de fechamento
        </Link>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ comprovante (impressão)

export function ComprovanteCaixa({ caixa }: { caixa: Caixa }) {
  const movimentos = caixa.movimentos ?? [];
  return (
    <div className="mx-auto w-full max-w-[80mm] space-y-3 bg-white p-4 font-mono text-[12px] leading-snug text-black">
      <header className="text-center">
        <p className="text-sm font-bold">PetConnect</p>
        <p>Fechamento de caixa — documento não fiscal</p>
      </header>
      <p>
        Caixa nº {caixa.id} · {caixa.usuario.nome}
        <br />
        Abertura: {formatarDataHora(caixa.abertoEm)}
        <br />
        Fechamento: {caixa.fechadoEm ? formatarDataHora(caixa.fechadoEm) : "EM ABERTO"}
      </p>
      <p>Fundo de troco: {moeda(caixa.fundoAbertura)}</p>
      <hr className="border-dashed border-black" />
      {/* Um bloco por forma: em 80 mm não cabem quatro colunas de valores. */}
      <ul className="space-y-1.5">
        {FORMAS_PAGAMENTO.map((f) => (
          <li key={f.valor}>
            <p className="font-bold">{f.rotulo}</p>
            <p className="flex justify-between"><span>Esperado</span><span>{moeda(caixa.esperado?.[f.valor] ?? 0)}</span></p>
            <p className="flex justify-between"><span>Contado</span><span>{caixa.contado ? moeda(caixa.contado[f.valor]) : "—"}</span></p>
            <p className="flex justify-between"><span>Diferença</span><span>{caixa.diferenca ? moeda(caixa.diferenca[f.valor]) : "—"}</span></p>
          </li>
        ))}
      </ul>
      <p className="font-bold">Diferença total: {caixa.diferencaTotal !== null ? moeda(caixa.diferencaTotal) : "—"}</p>
      {caixa.motivoDiferenca && <p>Motivo: {caixa.motivoDiferenca}</p>}
      <hr className="border-dashed border-black" />
      <p className="font-bold">Suprimentos e sangrias</p>
      {movimentos.length === 0 ? (
        <p>Nenhum.</p>
      ) : (
        <ul>
          {movimentos.map((m) => (
            <li key={m.id} className="flex justify-between gap-2">
              <span>{m.tipo === "SANGRIA" ? "Sangria" : "Suprimento"}: {m.motivo}</span>
              <span className="whitespace-nowrap">{m.tipo === "SANGRIA" ? "-" : "+"}{moeda(m.valor)}</span>
            </li>
          ))}
        </ul>
      )}
      <hr className="border-dashed border-black" />
      <p className="pt-6 text-center">______________________________<br />Assinatura do operador</p>
      <button type="button" className="botao-primario w-full print:hidden" onClick={() => window.print()}>
        Imprimir
      </button>
    </div>
  );
}
