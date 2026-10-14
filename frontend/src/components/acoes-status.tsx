"use client";

import { useActionState } from "react";
import { alterarStatus } from "@/app/actions/agendamentos";
import Link from "next/link";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { acoesPermitidas, moeda, type Agendamento } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import type { Papel } from "@/lib/jwt";
import { LoteDaVacina } from "@/components/lote-da-vacina";
import type { VacinaCatalogo } from "@/lib/pets";
import { FORMAS_PAGAMENTO } from "@/lib/vendas";

function confirmar(mensagem: string) {
  return (e: React.FormEvent<HTMLFormElement>) => {
    if (!window.confirm(mensagem)) e.preventDefault();
  };
}

/** Só mostra as ações válidas para o status e o papel; a API confere de novo. */
export function AcoesStatus({
  agendamento,
  papel,
  horarioAtingido,
  vacinas = [],
  caixaAberto = true,
}: {
  agendamento: Agendamento;
  papel: Papel;
  horarioAtingido: boolean;
  vacinas?: VacinaCatalogo[];
  /** Etapa 06: a conclusão vira receita no caixa de quem conclui; sem caixa, bloqueia e leva a /caixa. */
  caixaAberto?: boolean;
}) {
  const [state, action, pending] = useActionState<FormState, FormData>(alterarStatus.bind(null, agendamento.id), {});
  const erros = state.erros ?? {};
  const acoes = acoesPermitidas(agendamento.status, papel, horarioAtingido);
  if (acoes.length === 0) return null;

  return (
    <section className="cartao space-y-4 p-4 sm:p-6" aria-label="Ações do agendamento">
      <h2 className="text-sm font-semibold text-muted">Ações</h2>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}

      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        {acoes.includes("CONFIRMADO") && (
          <form action={action}>
            <input type="hidden" name="status" value="CONFIRMADO" />
            <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>Confirmar</button>
          </form>
        )}
        {acoes.includes("EM_ANDAMENTO") && (
          <form action={action}>
            <input type="hidden" name="status" value="EM_ANDAMENTO" />
            <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>Iniciar atendimento</button>
          </form>
        )}
      </div>

      {acoes.includes("CONCLUIDO") && !caixaAberto && (
        <div className="space-y-2 rounded-lg border border-border p-4">
          <h3 className="font-medium">Concluir atendimento</h3>
          <Aviso tipo="erro">
            Abra o caixa para concluir o atendimento: o valor do serviço entra como receita no seu caixa.{" "}
            <Link href="/caixa" className="font-medium underline">Abrir caixa</Link>
          </Aviso>
        </div>
      )}

      {acoes.includes("CONCLUIDO") && caixaAberto && (
        <form
          action={action}
          onSubmit={confirmar("Concluir este atendimento? O serviço realizado e a receita no seu caixa serão registrados.")}
          className="grid gap-4 rounded-lg border border-border p-4 sm:grid-cols-2"
          noValidate
        >
          <h3 className="font-medium sm:col-span-2">Concluir atendimento</h3>
          <input type="hidden" name="status" value="CONCLUIDO" />
          <input type="hidden" name="vacinacao" value={agendamento.servico.vacinacao ? "1" : "0"} />
          <Campo
            rotulo="Valor do serviço (R$)"
            name="valorFinal"
            inputMode="decimal"
            defaultValue={state.valores?.valorFinal ?? String(agendamento.totalEstimado).replace(".", ",")}
            ajuda={`Estimado: ${moeda(agendamento.totalEstimado)}. Entra como receita no seu caixa.`}
            erro={erros.valorFinal}
          />
          <div>
            <label htmlFor="campo-forma-conclusao" className="mb-1 block text-sm font-medium">
              Forma de pagamento<span className="text-danger"> *</span>
            </label>
            <select
              key={state.valores?.formaPagamento ?? ""}
              id="campo-forma-conclusao"
              name="formaPagamento"
              className="campo"
              defaultValue={state.valores?.formaPagamento ?? ""}
              aria-invalid={erros.formaPagamento ? true : undefined}
            >
              <option value="">Selecione…</option>
              {FORMAS_PAGAMENTO.map((f) => (
                <option key={f.valor} value={f.valor}>{f.rotulo}</option>
              ))}
            </select>
            {erros.formaPagamento && <p className="mt-1 text-xs text-danger">{erros.formaPagamento}</p>}
          </div>
          <div>
            <label htmlFor="campo-obs-conclusao" className="mb-1 block text-sm font-medium">Observações da conclusão</label>
            <textarea id="campo-obs-conclusao" name="observacoesConclusao" rows={2} maxLength={2000} className="campo" defaultValue={state.valores?.observacoesConclusao} />
          </div>

          {agendamento.servico.vacinacao && (
            <fieldset className="grid gap-4 rounded-lg bg-brand-soft/40 p-3 sm:col-span-2 sm:grid-cols-2">
              <legend className="px-1 text-sm font-semibold">Vacina aplicada</legend>
              <div className="sm:col-span-2">
                <label htmlFor="campo-conclusao-vacina" className="mb-1 block text-sm font-medium">
                  Vacina<span className="text-danger"> *</span>
                </label>
                <select id="campo-conclusao-vacina" name="vacinaId" className="campo" defaultValue={state.valores?.vacinaId ?? ""} aria-invalid={erros.vacinaId ? true : undefined}>
                  <option value="">Selecione…</option>
                  {vacinas.map((v) => (
                    <option key={v.id} value={v.id}>{v.nome}</option>
                  ))}
                </select>
                {(erros.vacinaId || erros.vacina) && <p className="mt-1 text-xs text-danger">{erros.vacinaId ?? erros.vacina}</p>}
              </div>
              <Campo rotulo="Data de aplicação" name="dataAplicacao" type="date" max={hojeISO()} defaultValue={state.valores?.dataAplicacao ?? hojeISO()} erro={erros.dataAplicacao} />
              <Campo rotulo="Próxima dose" name="dataProxima" type="date" defaultValue={state.valores?.dataProxima} erro={erros.dataProxima} />
              <Campo rotulo="Veterinário" name="veterinario" maxLength={100} defaultValue={state.valores?.veterinario} erro={erros.veterinario} />
              <LoteDaVacina vacinas={vacinas} selectId="campo-conclusao-vacina" valorInicial={state.valores?.lote} erro={erros.lote} />
              <div className="sm:col-span-2">
                <label htmlFor="campo-obs-vacina-conclusao" className="mb-1 block text-sm font-medium">Observações da vacina</label>
                <textarea id="campo-obs-vacina-conclusao" name="observacoesVacina" rows={2} maxLength={2000} className="campo" defaultValue={state.valores?.observacoesVacina} />
              </div>
            </fieldset>
          )}
          <div className="sm:col-span-2 sm:text-right">
            <button type="submit" className="botao-primario w-full sm:w-auto" disabled={pending}>Concluir atendimento</button>
          </div>
        </form>
      )}

      {acoes.includes("NAO_COMPARECEU") && (
        <details className="rounded-lg border border-border p-4">
          <summary className="cursor-pointer text-sm font-medium">Marcar não comparecimento</summary>
          <form action={action} onSubmit={confirmar("Marcar que o pet não compareceu?")} className="mt-3 space-y-3">
            <input type="hidden" name="status" value="NAO_COMPARECEU" />
            <Campo rotulo="Motivo (opcional)" name="motivo" maxLength={500} erro={erros.motivo} />
            <button type="submit" className="botao-perigo w-full sm:w-auto" disabled={pending}>Não compareceu</button>
          </form>
        </details>
      )}

      {acoes.includes("CANCELADO") && (
        <details className="rounded-lg border border-danger/30 p-4" open={Boolean(erros.motivo)}>
          <summary className="cursor-pointer text-sm font-medium text-danger">Cancelar agendamento…</summary>
          <form action={action} onSubmit={confirmar("Cancelar este agendamento? Essa ação não pode ser desfeita.")} className="mt-3 space-y-3">
            <input type="hidden" name="status" value="CANCELADO" />
            <Campo rotulo="Motivo do cancelamento" name="motivo" required minLength={3} maxLength={500} defaultValue={state.valores?.motivo} erro={erros.motivo} />
            <button type="submit" className="botao-perigo w-full sm:w-auto" disabled={pending}>Cancelar agendamento</button>
          </form>
        </details>
      )}
    </section>
  );
}
