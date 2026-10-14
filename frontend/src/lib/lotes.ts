import * as z from "zod";
import { hojeISO } from "@/lib/datas";
import type { CatalogoItem } from "@/lib/pets";

// ------------------------------------------------------------------ tipos (espelham os DTOs da API)

export type StatusLote = "DISPONIVEL" | "BLOQUEADO";

export type Lote = {
  id: number;
  produtoId: number;
  produtoNome: string;
  codigoLote: string;
  /** "YYYY-MM-DD"; null = lote sem validade (só se o produto permite). */
  validade: string | null;
  saldo: number;
  status: StatusLote;
  vencido: boolean;
  /** Entra no FEFO hoje: liberado, com saldo e fora da margem de bloqueio. */
  vendavel: boolean;
  custoUnitario: number | null;
  dataRecebimento: string;
  compraId: number | null;
  fornecedorId: number | null;
  createdAt: string;
};

export type LoteAlocado = { loteId: number | null; codigoLote: string; validade: string | null; quantidade: number };

export type Faixa = { deDias: number; ateDias: number; lotes: number; unidades: number };

export type AlertasEstoque = {
  habilitado: boolean;
  hoje: string;
  faixas: Faixa[];
  vencidos: { lotes: number; unidades: number };
  bloqueados: { lotes: number; unidades: number };
  produtosSemControle: number;
  produtosAbaixoDoMinimo: number;
};

export type RastreioLote = {
  lote: Lote;
  entradas: { compraId: number; fornecedor: CatalogoItem; dataCompra: string; dataEntrega: string | null; quantidade: number; custoUnitario: number }[];
  saidas: { vendaId: number; dataVenda: string; statusVenda: "CONCLUIDA" | "CANCELADA"; quantidade: number }[];
  movimentos: {
    id: number;
    tipo: string;
    quantidade: number;
    loteSaldoAnterior: number;
    loteSaldoResultante: number;
    compraId: number | null;
    vendaId: number | null;
    motivo: string | null;
    operacaoId: string | null;
    usuario: CatalogoItem;
    createdAt: string;
  }[];
  totalMovimentos: number;
};

export type VacinaEstoque = { id: number; nome: string; especie: string; produto: CatalogoItem | null };

export type ConfiguracaoLotes = { lotesHabilitado: boolean; faixasAlertaDias: number[] };

export type DestinoEstoque = "RETORNAR" | "DESCARTAR";

export const DESTINOS_ESTOQUE: { valor: DestinoEstoque; rotulo: string; ajuda: string }[] = [
  { valor: "RETORNAR", rotulo: "Voltar ao estoque", ajuda: "O produto está em condição de revenda e volta ao lote de origem." },
  { valor: "DESCARTAR", rotulo: "Descartar (perda)", ajuda: "Produto aberto, danificado ou sem cadeia de frio: entra e sai do lote como perda." },
];

// ------------------------------------------------------------------ situação do lote (texto + ícone, nunca só cor)

export type TomLote = "ok" | "atencao" | "perigo" | "neutro";

export function situacaoLote(l: Pick<Lote, "status" | "vencido" | "vendavel" | "saldo">): { rotulo: string; icone: string; tom: TomLote } {
  if (l.vencido) return { rotulo: "Vencido", icone: "✕", tom: "perigo" };
  if (l.status === "BLOQUEADO") return { rotulo: "Bloqueado", icone: "⛔", tom: "atencao" };
  if (l.saldo <= 0) return { rotulo: "Sem saldo", icone: "○", tom: "neutro" };
  if (!l.vendavel) return { rotulo: "Perto do vencimento", icone: "⚠", tom: "atencao" };
  return { rotulo: "Disponível", icone: "✓", tom: "ok" };
}

/** Dias entre hoje e a validade (negativo = vencido há N dias); null sem validade. */
export function diasParaVencer(validade: string | null, hoje: string = hojeISO()): number | null {
  if (!validade) return null;
  const a = Date.parse(`${hoje}T12:00:00Z`);
  const b = Date.parse(`${validade}T12:00:00Z`);
  return Math.round((b - a) / 86_400_000);
}

export function textoPrazo(validade: string | null, hoje?: string): string {
  const dias = diasParaVencer(validade, hoje);
  if (dias === null) return "sem validade";
  if (dias < 0) return `venceu há ${-dias} dia(s)`;
  if (dias === 0) return "vence hoje";
  return `vence em ${dias} dia(s)`;
}

export function rotuloFaixa(f: Faixa): string {
  return f.deDias === 0 ? `Vencem em até ${f.ateDias} dias` : `Vencem em ${f.deDias} a ${f.ateDias} dias`;
}

// ------------------------------------------------------------------ grade de lotes (recebimento e regularização)

/** Linha da grade: tudo texto, como digitado. */
export type LinhaLote = { codigo: string; validade: string; quantidade: string };

export const linhaVazia = (): LinhaLote => ({ codigo: "", validade: "", quantidade: "" });

const inteiroPositivo = (v: string) => /^\d+$/.test(v.trim()) && Number(v) >= 1 && Number(v) <= 1_000_000;

/** Soma só das linhas com quantidade válida (a conferência em tempo real não quebra com texto no meio da digitação). */
export function somaLinhas(linhas: LinhaLote[]): number {
  return linhas.reduce((s, l) => s + (inteiroPositivo(l.quantidade) ? Number(l.quantidade) : 0), 0);
}

export type ConferenciaSoma = { soma: number; esperado: number; diferenca: number; confere: boolean };

export function conferirSoma(linhas: LinhaLote[], esperado: number): ConferenciaSoma {
  const soma = somaLinhas(linhas);
  return { soma, esperado, diferenca: soma - esperado, confere: soma === esperado };
}

export function mensagemSoma(c: ConferenciaSoma): string {
  if (c.confere) return `Os lotes somam ${c.soma}: confere.`;
  return c.diferenca < 0 ? `Faltam ${-c.diferenca} para chegar a ${c.esperado} (soma atual: ${c.soma}).` : `Sobram ${c.diferenca} sobre ${c.esperado} (soma atual: ${c.soma}).`;
}

export type OpcoesLinhas = {
  /** Produto exige validade em todo lote. */
  exigeValidade: boolean;
  /** Quantidade que os lotes precisam somar. */
  esperado: number;
  /** Recebimento não aceita lote já vencido; a regularização do saldo antigo aceita (é o que existe na prateleira). */
  recebimento: boolean;
};

/** Valida a grade e devolve um mapa de erros por campo ("0.codigo", "1.quantidade", "_") — vazio se estiver tudo certo. */
export function validarLinhas(linhas: LinhaLote[], op: OpcoesLinhas, hoje: string = hojeISO()): Record<string, string> {
  const erros: Record<string, string> = {};
  const vistos = new Set<string>();
  linhas.forEach((l, i) => {
    const codigo = l.codigo.trim();
    if (!codigo) erros[`${i}.codigo`] = "Informe o código do lote";
    else if (codigo.length > 50) erros[`${i}.codigo`] = "Use no máximo 50 caracteres";
    else if (vistos.has(codigo.toLowerCase())) erros[`${i}.codigo`] = "Código repetido neste item";
    vistos.add(codigo.toLowerCase());
    if (!l.validade) {
      if (op.exigeValidade) erros[`${i}.validade`] = "Este produto exige validade";
    } else if (!/^\d{4}-\d{2}-\d{2}$/.test(l.validade)) {
      erros[`${i}.validade`] = "Data inválida";
    } else if (op.recebimento && l.validade < hoje) {
      erros[`${i}.validade`] = "Lote já vencido não pode ser recebido";
    }
    if (!inteiroPositivo(l.quantidade)) erros[`${i}.quantidade`] = "Quantidade de 1 a 1.000.000";
  });
  const c = conferirSoma(linhas, op.esperado);
  if (!c.confere) erros._ = mensagemSoma(c);
  return erros;
}

export const paraPayload = (linhas: LinhaLote[]) =>
  linhas.map((l) => ({ codigoLote: l.codigo.trim(), validade: l.validade || null, quantidade: Number(l.quantidade) }));

// ------------------------------------------------------------------ schemas das Server Actions

const linhaSchema = z.object({
  codigo: z.string().default(""),
  validade: z.string().default(""),
  quantidade: z.string().default(""),
});

const linhasDoJson = (texto: string | undefined): LinhaLote[] => {
  try {
    const bruto: unknown = JSON.parse(texto || "[]");
    return z.array(linhaSchema).parse(bruto);
  } catch {
    return [];
  }
};

export { linhasDoJson };

/** Ativação do controle: as linhas já chegam do formulário; a soma exata é conferida aqui e de novo na API. */
export const regularizacaoSchema = (saldo: number) =>
  z
    .object({
      exigirValidade: z.string().optional(),
      diasBloqueioValidade: z
        .string()
        .optional()
        .transform((v) => (v && v.trim() ? v.trim() : null))
        .refine((v) => v === null || (/^\d+$/.test(v) && Number(v) <= 3650), "Informe de 0 a 3650 dias")
        .transform((v) => (v === null ? null : Number(v))),
      lotes: z.string().optional(),
    })
    .transform((v, ctx) => {
      const exigeValidade = v.exigirValidade === "on";
      const linhas = linhasDoJson(v.lotes);
      const erros = validarLinhas(linhas, { exigeValidade, esperado: saldo, recebimento: false });
      for (const [campo, mensagem] of Object.entries(erros)) {
        ctx.addIssue({ code: "custom", path: ["lotes", ...(campo === "_" ? [] : campo.split("."))], message: mensagem });
      }
      return { permiteSemValidade: !exigeValidade, diasBloqueioValidade: v.diasBloqueioValidade, lotes: paraPayload(linhas) };
    });

export const baixaLoteSchema = z.object({
  quantidade: z
    .string({ error: "Informe a quantidade" })
    .trim()
    .regex(/^\d+$/, "Informe um número inteiro positivo")
    .transform(Number)
    .refine((v) => v >= 1 && v <= 1_000_000, "A quantidade deve ser de 1 a 1.000.000"),
  motivo: z
    .string({ error: "Informe o motivo" })
    .trim()
    .min(5, "O motivo deve ter entre 5 e 500 caracteres")
    .max(500, "O motivo deve ter entre 5 e 500 caracteres"),
});

export const motivoLoteSchema = z.object({
  motivo: z
    .string({ error: "Informe o motivo" })
    .trim()
    .min(5, "O motivo deve ter entre 5 e 500 caracteres")
    .max(500, "O motivo deve ter entre 5 e 500 caracteres"),
});

export const destinoSchema = z.enum(["RETORNAR", "DESCARTAR"], { error: "Escolha o que fazer com o produto devolvido" });

/** Lotes do recebimento: { produtoId: linhas } vindo de um campo oculto. */
export function lotesDoRecebimento(texto: string | undefined): { produtoId: number; linhas: LinhaLote[] }[] {
  try {
    const bruto: unknown = JSON.parse(texto || "[]");
    return z.array(z.object({ produtoId: z.number().int().positive(), linhas: z.array(linhaSchema) })).parse(bruto);
  } catch {
    return [];
  }
}
