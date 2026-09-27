"use client";

import Link from "next/link";
import { useActionState, useEffect, useState } from "react";
import { salvarAgendamento } from "@/app/actions/agendamentos";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { TutorPicker } from "@/components/tutor-picker";
import { moeda, type Agendamento, type PrecoPrevisto, type Servico } from "@/lib/agendamentos";
import type { FormState } from "@/lib/clientes";
import { agoraLocalISO } from "@/lib/datas";
import type { CatalogoItem } from "@/lib/pets";
import type { FuncionarioResumo } from "@/lib/usuarios";

type PetOpcao = { id: number; nome: string };
export type PetInicial = PetOpcao & { tutor: CatalogoItem };

/**
 * Tutor → Pet → Serviço → Data/hora → Preço → Observações.
 * O preço exibido é só prévia: a API recalcula ao salvar.
 */
export function AgendamentoForm({
  servicos,
  petInicial,
  agendamento,
  funcionarios = [],
}: {
  servicos: Servico[];
  petInicial?: PetInicial | null;
  agendamento?: Agendamento;
  /** Só funcionários com conta ativa (a API filtra). Sem nenhum, o campo nem aparece. */
  funcionarios?: FuncionarioResumo[];
}) {
  const acao = salvarAgendamento.bind(null, agendamento?.id ?? null);
  const [state, action, pending] = useActionState<FormState, FormData>(acao, {});
  const erros = state.erros ?? {};

  const [tutorId, setTutorId] = useState<number | null>(petInicial?.tutor.id ?? null);
  const [pets, setPets] = useState<PetOpcao[]>(petInicial ? [{ id: petInicial.id, nome: petInicial.nome }] : []);
  const [petId, setPetId] = useState(petInicial ? String(petInicial.id) : "");
  const [servicoId, setServicoId] = useState(agendamento ? String(agendamento.servico.id) : "");
  const [preco, setPreco] = useState<PrecoPrevisto | null>(null);

  // Tutor escolhido → carrega só os pets ativos dele.
  useEffect(() => {
    if (!tutorId || (petInicial && tutorId === petInicial.tutor.id && pets.length > 1)) return;
    let ativo = true;
    fetch(`/api/pets?clienteId=${tutorId}`)
      .then((r) => (r.ok ? r.json() : []))
      .then((lista: PetOpcao[]) => ativo && setPets(lista))
      .catch(() => undefined);
    return () => {
      ativo = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tutorId]);

  // Pet + serviço → prévia de preço e duração.
  useEffect(() => {
    if (!petId || !servicoId) return;
    let ativo = true;
    fetch(`/api/agendamentos/preco?petId=${petId}&servicoId=${servicoId}`)
      .then((r) => (r.ok ? r.json() : null))
      .then((p: PrecoPrevisto | null) => ativo && setPreco(p))
      .catch(() => undefined);
    return () => {
      ativo = false;
    };
  }, [petId, servicoId]);

  const previa = petId && servicoId ? preco : null;

  return (
    <form action={action} className="space-y-6" noValidate>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">Quem</legend>
        <div className="sm:col-span-2">
          <TutorPicker
            inicial={petInicial?.tutor ?? null}
            onSelecionar={(t) => {
              setTutorId(t?.id ?? null);
              setPets([]);
              setPetId("");
            }}
          />
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="campo-pet" className="mb-1 block text-sm font-medium">
            Pet<span className="text-danger"> *</span>
          </label>
          <select
            id="campo-pet"
            name="petId"
            className="campo"
            value={petId}
            onChange={(e) => setPetId(e.target.value)}
            disabled={!tutorId}
            aria-invalid={erros.petId ? true : undefined}
          >
            <option value="">{tutorId ? "Selecione o pet…" : "Escolha o tutor primeiro"}</option>
            {pets.map((p) => (
              <option key={p.id} value={p.id}>{p.nome}</option>
            ))}
          </select>
          {erros.petId && <p className="mt-1 text-xs text-danger">{erros.petId}</p>}
        </div>
      </fieldset>

      <fieldset className="cartao grid gap-4 p-4 sm:grid-cols-2 sm:p-6">
        <legend className="px-1 text-sm font-semibold text-muted">O quê e quando</legend>
        <div>
          <label htmlFor="campo-servico" className="mb-1 block text-sm font-medium">
            Serviço<span className="text-danger"> *</span>
          </label>
          <select
            id="campo-servico"
            name="servicoId"
            className="campo"
            value={servicoId}
            onChange={(e) => setServicoId(e.target.value)}
            aria-invalid={erros.servicoId ? true : undefined}
          >
            <option value="">Selecione…</option>
            {servicos.map((s) => (
              <option key={s.id} value={s.id}>{s.nome}</option>
            ))}
          </select>
          {erros.servicoId && <p className="mt-1 text-xs text-danger">{erros.servicoId}</p>}
        </div>
        {funcionarios.length > 0 && (
          <div>
            <label htmlFor="campo-funcionario" className="mb-1 block text-sm font-medium">Profissional</label>
            <select
              id="campo-funcionario"
              name="funcionarioId"
              className="campo"
              defaultValue={state.valores?.funcionarioId ?? (agendamento?.funcionario?.disponivel ? String(agendamento.funcionario.id) : "")}
              aria-invalid={erros.funcionarioId ? true : undefined}
            >
              <option value="">Sem funcionário definido</option>
              {funcionarios.map((f) => (
                <option key={f.id} value={f.id}>{f.nome}</option>
              ))}
            </select>
            {agendamento?.funcionario && !agendamento.funcionario.disponivel && (
              <p className="mt-1 text-xs text-danger">{agendamento.funcionario.nome} está indisponível — escolha outro profissional.</p>
            )}
            {erros.funcionarioId && <p className="mt-1 text-xs text-danger">{erros.funcionarioId}</p>}
          </div>
        )}
        <Campo
          rotulo="Data e hora"
          name="dataHora"
          type="datetime-local"
          required
          min={agoraLocalISO()}
          defaultValue={state.valores?.dataHora ?? agendamento?.dataHora.slice(0, 16) ?? ""}
          erro={erros.dataHora}
        />
        <div aria-live="polite" className="rounded-lg border border-dashed border-border px-4 py-3 text-sm sm:col-span-2">
          {previa ? (
            <>
              <p>
                Valor estimado: <strong className="tabular-nums">{moeda(previa.totalEstimado)}</strong>
                <span className="text-muted"> ({previa.origemPreco === "PORTE" ? "preço do porte do pet" : "preço padrão"})</span>
              </p>
              {previa.duracaoMinutos != null && <p>Duração prevista: {previa.duracaoMinutos} minutos</p>}
            </>
          ) : (
            <p className="text-muted">Escolha o pet e o serviço para ver o valor estimado.</p>
          )}
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="campo-obs-agendamento" className="mb-1 block text-sm font-medium">Observações</label>
          <textarea
            id="campo-obs-agendamento"
            name="observacoes"
            rows={3}
            maxLength={2000}
            className="campo"
            defaultValue={state.valores?.observacoes ?? agendamento?.observacoes ?? ""}
          />
        </div>
      </fieldset>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link href={agendamento ? `/agendamentos/${agendamento.id}` : "/agendamentos"} className="botao-secundario">
          Cancelar
        </Link>
        <button type="submit" className="botao-primario" disabled={pending}>
          {pending ? "Salvando…" : agendamento ? "Salvar alterações" : "Confirmar agendamento"}
        </button>
      </div>
    </form>
  );
}
