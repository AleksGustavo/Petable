import * as z from "zod";
import { deCentavos, paraCentavos } from "@/lib/estoque";
import type { CatalogoItem } from "@/lib/pets";
import { FORMAS_PAGAMENTO, type FormaPagamento } from "@/lib/vendas";

// ------------------------------------------------------------------ tipos (espelham os DTOs da API)

export type TipoLancamento = "ENTRADA" | "SAIDA";
export type CategoriaLancamento =
  | "VENDA_PRODUTO"
  | "SERVICO"
  | "COMPRA_MERCADORIA"
  | "DESPESA_FUNCIONARIO"
  | "DESPESA_OPERACIONAL"
  | "OUTROS";

export const CATEGORIAS: { valor: CategoriaLancamento; rotulo: string; manual: boolean }[] = [
  { valor: "VENDA_PRODUTO", rotulo: "Venda de produto", manual: false },
  { valor: "SERVICO", rotulo: "Serviço", manual: false },
  { valor: "COMPRA_MERCADORIA", rotulo: "Compra de mercadoria", manual: false },
  { valor: "DESPESA_FUNCIONARIO", rotulo: "Despesa com funcionário", manual: true },
  { valor: "DESPESA_OPERACIONAL", rotulo: "Despesa operacional", manual: true },
  { valor: "OUTROS", rotulo: "Outros", manual: true },
];

export const CATEGORIAS_MANUAIS = CATEGORIAS.filter((c) => c.manual);
export const rotuloCategoria = (c: string) => CATEGORIAS.find((x) => x.valor === c)?.rotulo ?? c;

export const ORIGENS: { valor: string; rotulo: string }[] = [
  { valor: "VENDA", rotulo: "Vendas" },
  { valor: "SERVICO", rotulo: "Serviços" },
  { valor: "CONTA_PAGAR", rotulo: "Contas pagas" },
  { valor: "MANUAL", rotulo: "Manuais" },
  { valor: "CAIXA", rotulo: "Sangria/suprimento" },
];

const ROTULO_ORIGEM: Record<string, string> = {
  VENDA: "Venda",
  VENDA_ESTORNO: "Estorno de venda",
  SERVICO: "Serviço",
  CONTA_PAGAR: "Conta a pagar",
  MANUAL: "Manual",
  MANUAL_ESTORNO: "Estorno manual",
  CAIXA_SANGRIA: "Sangria",
  CAIXA_SUPRIMENTO: "Suprimento",
};

export function rotuloOrigem(o: { tipo: string; id: number | null } | null): string {
  if (!o) return "Histórico";
  const nome = ROTULO_ORIGEM[o.tipo] ?? o.tipo;
  return o.id ? `${nome} #${o.id}` : nome;
}

export type Lancamento = {
  id: number;
  tipo: TipoLancamento;
  categoria: CategoriaLancamento;
  descricao: string;
  valor: number;
  dataMovimentacao: string;
  formaPagamento: FormaPagamento | null;
  usuarioId: number | null;
  usuario: CatalogoItem | null;
  caixaId: number | null;
  origem: { tipo: string; id: number | null } | null;
  estornoDeId: number | null;
  estornadoPorId: number | null;
  motivoEstorno: string | null;
  observacoes: string | null;
  estornavel: boolean;
};

export type SituacaoConta = "PAGA" | "VENCIDA" | "VENCE_HOJE" | "A_VENCER";

export const SITUACOES_CONTA: { valor: SituacaoConta; rotulo: string }[] = [
  { valor: "VENCIDA", rotulo: "Vencida" },
  { valor: "VENCE_HOJE", rotulo: "Vence hoje" },
  { valor: "A_VENCER", rotulo: "A vencer" },
  { valor: "PAGA", rotulo: "Paga" },
];

export const rotuloSituacao = (s: SituacaoConta) => SITUACOES_CONTA.find((x) => x.valor === s)?.rotulo ?? s;

export type ContaPagar = {
  id: number;
  compraId: number | null;
  fornecedor: CatalogoItem | null;
  descricao: string;
  valorOriginal: number;
  saldo: number;
  dataVencimento: string;
  status: "ABERTA" | "PAGA";
  vencida: boolean;
  situacao: SituacaoConta;
  pagaEm: string | null;
  formaPagamento: FormaPagamento | null;
  usuarioPagamento: CatalogoItem | null;
  lancamentoId: number | null;
};

export type PorForma = Record<FormaPagamento, number>;

export type MovimentoCaixa = {
  id: number;
  tipo: "SANGRIA" | "SUPRIMENTO";
  valor: number;
  motivo: string;
  usuario: CatalogoItem;
  createdAt: string;
};

export type Caixa = {
  id: number;
  usuario: CatalogoItem;
  status: "ABERTO" | "FECHADO";
  abertoEm: string;
  fundoAbertura: number;
  fechadoEm: string | null;
  esperado: PorForma | null;
  contado: PorForma | null;
  diferenca: PorForma | null;
  totalEsperado: number | null;
  diferencaTotal: number | null;
  motivoDiferenca: string | null;
  movimentos: MovimentoCaixa[] | null;
};

export type ResumoCaixaAtual = { aberto: boolean; caixaId: number | null; abertoEm: string | null; saldoEsperadoDinheiro: number | null };

export type Fluxo = {
  dataInicio: string;
  dataFim: string;
  entradas: number;
  saidas: number;
  saldo: number;
  porCategoria: { categoria: string; entradas: number; saidas: number; saldo: number }[];
  porFormaPagamento: { formaPagamento: string; entradas: number; saidas: number; saldo: number }[];
};

export type ResumoFinanceiroDia = {
  data: string;
  entradas: number;
  saidas: number;
  saldo: number;
  contasVencidas: number;
  valorContasVencidas: number;
  contasAVencer7Dias: number;
  valorAVencer7Dias: number;
  caixasAbertos: number;
};

export const MENSAGEM_SEM_CAIXA = "Não há caixa aberto para o operador atual. Abra o caixa antes de continuar.";

// ------------------------------------------------------------------ schemas (mesmas regras dos DTOs)

const FORMAS = ["DINHEIRO", "CARTAO_CREDITO", "CARTAO_DEBITO", "PIX", "TRANSFERENCIA"] as const;
const forma = z.enum(FORMAS, { error: "Selecione a forma de pagamento" });
const LIMITE_CENTAVOS = 9_999_999_999;

/** Valor em reais (texto) → centavos > 0. */
const valorPositivo = (rotulo = "O valor") =>
  z
    .string({ error: `${rotulo} é obrigatório` })
    .transform((v) => paraCentavos(v))
    .refine((v) => v !== null, `Informe ${rotulo.toLowerCase().replace(/^o /, "o ")}`)
    .refine((v) => v === null || !Number.isNaN(v), "Informe um valor válido (até duas casas)")
    .refine((v) => v === null || Number.isNaN(v) || v > 0, `${rotulo} deve ser maior que zero`)
    .refine((v) => v === null || Number.isNaN(v) || v <= LIMITE_CENTAVOS, "Valor acima do limite");

const motivo = z
  .string({ error: "Informe o motivo" })
  .trim()
  .min(5, "O motivo deve ter entre 5 e 500 caracteres")
  .max(500, "O motivo deve ter entre 5 e 500 caracteres");

export const lancamentoManualSchema = z
  .object({
    tipo: z.enum(["ENTRADA", "SAIDA"], { error: "Selecione o tipo" }),
    categoria: z.enum(["DESPESA_FUNCIONARIO", "DESPESA_OPERACIONAL", "OUTROS"], {
      error: "Lançamento manual aceita só Despesa com funcionário, Despesa operacional ou Outros.",
    }),
    descricao: z
      .string({ error: "Informe a descrição" })
      .trim()
      .min(3, "A descrição deve ter entre 3 e 100 caracteres")
      .max(100, "A descrição deve ter entre 3 e 100 caracteres"),
    valor: valorPositivo(),
    formaPagamento: forma,
    observacoes: z
      .string()
      .max(2000, "Use no máximo 2000 caracteres")
      .optional()
      .transform((v) => (v?.trim() ? v.trim() : null)),
  })
  .transform((v) => ({ ...v, valor: deCentavos(v.valor as number) }));

export const estornoSchema = z.object({ motivo });

export const pagamentoContaSchema = z.object({ formaPagamento: forma });

export const aberturaCaixaSchema = z
  .object({
    fundoAbertura: z
      .string()
      .optional()
      .transform((v) => paraCentavos(v) ?? 0)
      .refine((v) => !Number.isNaN(v), "Informe um valor válido (até duas casas)")
      .refine((v) => v <= LIMITE_CENTAVOS, "Valor acima do limite"),
  })
  .transform((v) => ({ fundoAbertura: deCentavos(v.fundoAbertura) }));

export const movimentoCaixaSchema = z
  .object({ valor: valorPositivo(), motivo })
  .transform((v) => ({ valor: deCentavos(v.valor as number), motivo: v.motivo }));

// ------------------------------------------------------------------ fechamento de caixa

/**
 * Valor contado em centavos. Dinheiro é físico (>= 0); nas formas eletrônicas o valor é o
 * LÍQUIDO do extrato (entradas − saídas do caixa) e pode ser negativo (ex.: conta paga por Pix).
 */
export function contadoCentavos(forma: string, texto: string | undefined): number | null {
  const t = (texto ?? "").trim();
  const negativo = forma !== "DINHEIRO" && /^[-−]/.test(t);
  const c = paraCentavos(negativo ? t.slice(1) : t);
  return c === null || Number.isNaN(c) ? c : negativo ? -c : c;
}

/** Diferença por forma (contado − esperado), em centavos. Contado ausente/inválido conta como NaN. */
export function diferencasCentavos(esperado: Record<string, number>, contado: Record<string, string>) {
  const r = {} as Record<FormaPagamento, number>;
  for (const f of FORMAS) {
    const c = contadoCentavos(f, contado[f]);
    r[f] = c === null || Number.isNaN(c) ? Number.NaN : c - Math.round((esperado[f] ?? 0) * 100);
  }
  return r;
}

/**
 * Fechamento: os cinco valores contados são obrigatórios; qualquer diferença exige
 * motivo (5–500). {@code esperadoCentavos} vem da prévia do backend (só para UX).
 */
export function fechamentoSchema(esperado: Record<string, number>) {
  return z
    .object({
      ...Object.fromEntries(
        FORMAS.map((f) => [
          `contado${f}`,
          z
            .string({ error: "Informe o valor contado" })
            .transform((v) => contadoCentavos(f, v))
            .refine((v) => v !== null, "Informe o valor contado")
            .refine((v) => v === null || !Number.isNaN(v), "Valor inválido"),
        ]),
      ),
      motivoDiferenca: z.string().optional(),
    })
    .superRefine((v, ctx) => {
      const valores = v as Record<string, number | null | string | undefined>;
      const temDiferenca = FORMAS.some((f) => {
        const c = valores[`contado${f}`];
        return typeof c === "number" && c !== Math.round((esperado[f] ?? 0) * 100);
      });
      const m = (v.motivoDiferenca ?? "").trim();
      if (temDiferenca && (m.length < 5 || m.length > 500)) {
        ctx.addIssue({ code: "custom", path: ["motivoDiferenca"], message: "Explique a diferença (5 a 500 caracteres)." });
      }
    })
    .transform((v) => {
      const valores = v as Record<string, number>;
      return {
        contado: Object.fromEntries(FORMAS.map((f) => [f, deCentavos(valores[`contado${f}`])])) as Record<FormaPagamento, number>,
        motivoDiferenca: v.motivoDiferenca?.trim() ? v.motivoDiferenca.trim() : null,
      };
    });
}

export { FORMAS_PAGAMENTO };
