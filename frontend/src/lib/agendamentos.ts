import * as z from "zod";
import { agoraLocalISO } from "@/lib/datas";
import type { Papel } from "@/lib/jwt";
import type { CatalogoItem, Porte } from "@/lib/pets";

export type StatusAgendamento = "AGENDADO" | "CONFIRMADO" | "EM_ANDAMENTO" | "CONCLUIDO" | "CANCELADO" | "NAO_COMPARECEU";

export type Agendamento = {
  id: number;
  dataHora: string;
  fimPrevisto: string;
  status: StatusAgendamento;
  statusDescricao: string;
  pet: { id: number; nome: string; porte: Porte | null; especieId: number };
  tutor: CatalogoItem;
  servico: { id: number; nome: string; duracaoMinutos: number | null; vacinacao: boolean };
  /** disponivel=false: conta desativada — reatribuição necessária. */
  funcionario: { id: number; nome: string; disponivel: boolean } | null;
  totalEstimado: number;
  observacoes: string | null;
  realizado: { dataServico: string; valor: number; observacoes: string | null } | null;
};

export type Servico = {
  id: number;
  nome: string;
  descricao: string | null;
  preco: number;
  duracaoMinutos: number | null;
  ativo: boolean;
  vacinacao: boolean;
  precosPorPorte: { porte: Porte; preco: number }[];
};

export type PrecoPrevisto = {
  petId: number;
  servicoId: number;
  porte: Porte | null;
  origemPreco: "PORTE" | "PADRAO";
  totalEstimado: number;
  duracaoMinutos: number | null;
};

export const STATUS: Record<StatusAgendamento, { rotulo: string; simbolo: string; classe: string }> = {
  AGENDADO: { rotulo: "Agendado", simbolo: "○", classe: "border-border bg-background text-foreground" },
  CONFIRMADO: { rotulo: "Confirmado", simbolo: "✓", classe: "border-brand/40 bg-brand-soft text-brand-strong" },
  EM_ANDAMENTO: { rotulo: "Em andamento", simbolo: "▶", classe: "border-accent/60 bg-accent/15 text-foreground" },
  CONCLUIDO: { rotulo: "Concluído", simbolo: "✔", classe: "border-brand/40 bg-brand text-white" },
  CANCELADO: { rotulo: "Cancelado", simbolo: "✕", classe: "border-danger/40 bg-danger-soft text-danger" },
  NAO_COMPARECEU: { rotulo: "Não compareceu", simbolo: "!", classe: "border-danger/40 bg-danger-soft text-danger" },
};

/** Espelho da máquina de estados da API — só para decidir quais botões mostrar. */
const TRANSICOES: Record<StatusAgendamento, StatusAgendamento[]> = {
  AGENDADO: ["CONFIRMADO", "CANCELADO", "NAO_COMPARECEU"],
  CONFIRMADO: ["EM_ANDAMENTO", "CANCELADO", "NAO_COMPARECEU"],
  EM_ANDAMENTO: ["CONCLUIDO", "CANCELADO"],
  CONCLUIDO: [],
  CANCELADO: [],
  NAO_COMPARECEU: [],
};

const PAPEIS: Record<StatusAgendamento, Papel[]> = {
  AGENDADO: [],
  CONFIRMADO: ["ADMIN", "VENDAS"],
  CANCELADO: ["ADMIN", "VENDAS"],
  EM_ANDAMENTO: ["ADMIN", "VENDAS", "SERVICO"],
  CONCLUIDO: ["ADMIN", "VENDAS", "SERVICO"],
  NAO_COMPARECEU: ["ADMIN", "VENDAS", "SERVICO"],
};

export function acoesPermitidas(status: StatusAgendamento, papel: Papel, horarioAtingido: boolean): StatusAgendamento[] {
  return TRANSICOES[status].filter(
    (novo) => PAPEIS[novo].includes(papel) && (novo !== "NAO_COMPARECEU" || horarioAtingido),
  );
}

export const editavel = (s: StatusAgendamento) => s === "AGENDADO" || s === "CONFIRMADO";

export function moeda(valor: number | null | undefined): string {
  return valor == null ? "—" : new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(valor);
}

export const hora = (iso: string) => iso.slice(11, 16);
export const dataDe = (iso: string) => iso.slice(0, 10);

const texto = (max: number) =>
  z
    .string()
    .max(max, `Use no máximo ${max} caracteres`)
    .optional()
    .transform((v) => (v?.trim() ? v.trim() : null));

const id = (mensagem: string) => z.string({ error: mensagem }).trim().regex(/^\d+$/, mensagem).transform(Number);

/** Mesmas regras do AgendamentoRequest (o preço nunca vai no payload: a API calcula). */
export const agendamentoSchema = z.object({
  petId: id("Selecione o pet"),
  servicoId: id("Selecione o serviço"),
  funcionarioId: z
    .string()
    .optional()
    .transform((v) => (v && /^\d+$/.test(v) ? Number(v) : null)),
  dataHora: z
    .string({ error: "Informe a data e a hora" })
    .regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/, "Informe a data e a hora")
    .refine((v) => v > agoraLocalISO(), "Escolha uma data e hora futuras"),
  observacoes: texto(2000),
});

const valor = (mensagem: string) =>
  z
    .string()
    .optional()
    .transform((v) => (v?.trim() ? Number(v.replace(",", ".")) : null))
    .refine((v) => v === null || (!Number.isNaN(v) && v >= 0), mensagem);

export const PORTES_SERVICO: Porte[] = ["PEQUENO", "MEDIO", "GRANDE"];

/** Mesmas regras do ServicoRequest. Preços por porte vêm como precoPEQUENO/precoMEDIO/precoGRANDE. */
export const servicoSchema = z
  .object({
    nome: z
      .string({ error: "Informe o nome" })
      .trim()
      .min(1, "Informe o nome")
      .min(2, "O nome deve ter entre 2 e 100 caracteres")
      .max(100, "O nome deve ter entre 2 e 100 caracteres"),
    descricao: texto(1000),
    preco: valor("Informe um preço válido (não negativo)").refine((v) => v !== null, "Informe o preço"),
    duracaoMinutos: z
      .string({ error: "Informe a duração" })
      .trim()
      .regex(/^\d+$/, "A duração deve ser de 5 a 1440 minutos")
      .transform(Number)
      .refine((v) => v >= 5 && v <= 1440, "A duração deve ser de 5 a 1440 minutos"),
    precoPEQUENO: valor("Preço inválido"),
    precoMEDIO: valor("Preço inválido"),
    precoGRANDE: valor("Preço inválido"),
  })
  .transform((v) => ({
    nome: v.nome,
    descricao: v.descricao,
    preco: v.preco as number,
    duracaoMinutos: v.duracaoMinutos,
    precosPorPorte: PORTES_SERVICO.flatMap((porte) => {
      const p = v[`preco${porte}`];
      return p === null ? [] : [{ porte, preco: p }];
    }),
  }));
