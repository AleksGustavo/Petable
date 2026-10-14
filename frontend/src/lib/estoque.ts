import * as z from "zod";
import type { Endereco } from "@/lib/clientes";
import { hojeISO } from "@/lib/datas";
import { cnpjValido, somenteDigitos } from "@/lib/format";
import type { CatalogoItem } from "@/lib/pets";

// ------------------------------------------------------------------ tipos (espelham os DTOs da API)

export type CategoriaProduto = { id: number; nome: string; descricao: string | null };

export type Produto = {
  id: number;
  nome: string;
  descricao: string | null;
  categoria: CatalogoItem;
  fornecedorPadrao: CatalogoItem | null;
  codigoBarras: string | null;
  precoCusto: number | null;
  precoVenda: number;
  quantidadeEstoque: number;
  estoqueMinimo: number;
  abaixoDoMinimo: boolean;
  ativo: boolean;
  createdAt: string;
  updatedAt: string;
  /** Lotes (etapa 04): opcionais para a API antiga e para os testes que não conhecem lote. */
  controlaLote?: boolean;
  permiteSemValidade?: boolean;
  diasBloqueioValidade?: number | null;
};

export type ProdutoRapido = {
  id: number;
  nome: string;
  codigoBarras: string | null;
  precoVenda: number;
  quantidadeEstoque: number;
  disponivel: boolean;
  /** O que o balcão pode vender agora (produto com lote: só lotes liberados e dentro da validade). Ausente = igual ao saldo. */
  quantidadeVendavel?: number;
  controlaLote?: boolean;
};

export type TipoMovimentacao =
  | "ENTRADA_COMPRA"
  | "SAIDA_VENDA"
  | "AJUSTE"
  | "ESTORNO_VENDA"
  | "PERDA"
  | "VENCIMENTO"
  | "REGULARIZACAO_LOTE";

export type Movimentacao = {
  id: number;
  produtoId: number;
  tipo: TipoMovimentacao;
  quantidade: number;
  saldoAnterior: number;
  saldoResultante: number;
  compraId: number | null;
  vendaId: number | null;
  usuario: CatalogoItem;
  motivo: string | null;
  createdAt: string;
  loteId?: number | null;
  codigoLote?: string | null;
  loteSaldoAnterior?: number | null;
  loteSaldoResultante?: number | null;
  operacaoId?: string | null;
};

export const TIPOS_MOVIMENTACAO: { valor: TipoMovimentacao; rotulo: string }[] = [
  { valor: "ENTRADA_COMPRA", rotulo: "Entrada por compra" },
  { valor: "SAIDA_VENDA", rotulo: "Saída por venda" },
  { valor: "AJUSTE", rotulo: "Ajuste manual" },
  { valor: "ESTORNO_VENDA", rotulo: "Estorno de venda" },
  { valor: "PERDA", rotulo: "Perda de lote" },
  { valor: "VENCIMENTO", rotulo: "Baixa por vencimento" },
  { valor: "REGULARIZACAO_LOTE", rotulo: "Regularização em lotes" },
];

export const rotuloMovimentacao = (t: TipoMovimentacao) => TIPOS_MOVIMENTACAO.find((x) => x.valor === t)?.rotulo ?? t;

export type Fornecedor = {
  id: number;
  nomeFantasia: string;
  razaoSocial: string;
  cnpj: string;
  telefone: string | null;
  email: string | null;
  contato: string | null;
  endereco: Endereco | null;
  observacoes: string | null;
  ativo: boolean;
  createdAt: string;
  updatedAt: string;
};

export type FornecedorOpcao = { id: number; nomeFantasia: string; cnpj: string };

export type StatusCompra = "PEDIDO" | "RECEBIDO" | "CANCELADO";

export const STATUS_COMPRA: { valor: StatusCompra; rotulo: string }[] = [
  { valor: "PEDIDO", rotulo: "Pedido" },
  { valor: "RECEBIDO", rotulo: "Recebido" },
  { valor: "CANCELADO", rotulo: "Cancelado" },
];

export const rotuloStatusCompra = (s: StatusCompra) => STATUS_COMPRA.find((x) => x.valor === s)?.rotulo ?? s;

export type ItemCompra = {
  id: number;
  produto: CatalogoItem;
  quantidade: number;
  precoCustoUnitario: number;
  subtotal: number;
  /** O recebimento deste item exige lotes. */
  controlaLote?: boolean;
  /** Todo lote deste produto exige validade (padrão). Falso quando o produto permite lote sem validade. */
  exigeValidade?: boolean;
  /** Lotes recebidos (compra já recebida). */
  lotes?: { loteId: number | null; codigoLote: string; validade: string | null; quantidade: number }[];
};

export type Compra = {
  id: number;
  fornecedor: CatalogoItem;
  funcionario: CatalogoItem | null;
  dataCompra: string;
  dataEntrega: string | null;
  status: StatusCompra;
  formaPagamento: string;
  observacoes: string | null;
  valorTotal: number;
  /** -1 na listagem (itens não carregados). */
  quantidadeItens: number;
  itens: ItemCompra[] | null;
  createdAt: string;
  /** Etapa 06: conta a pagar criada no recebimento. */
  contaPagar?: { id: number; status: "ABERTA" | "PAGA"; dataVencimento: string; valorOriginal: number } | null;
};

// ------------------------------------------------------------------ dinheiro em centavos (sem erro de ponto flutuante)

/**
 * "1.234,56", "12,5" ou "12.50" → centavos inteiros; null se vazio; NaN se inválido
 * ou com mais de duas casas decimais.
 */
export function paraCentavos(valor: string | null | undefined): number | null {
  const texto = (valor ?? "").trim().replace(/^R\$\s*/, "");
  if (!texto) return null;
  const normalizado = texto.includes(",") ? texto.replace(/\./g, "").replace(",", ".") : texto;
  const m = /^(\d{1,8})(?:\.(\d{1,2}))?$/.exec(normalizado);
  if (!m) return Number.NaN;
  return Number(m[1]) * 100 + Number((m[2] ?? "").padEnd(2, "0"));
}

/** Centavos → número com duas casas para o JSON (a API lê como BigDecimal). */
export const deCentavos = (centavos: number) => Math.round(centavos) / 100;

export function moedaCentavos(centavos: number): string {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(centavos / 100);
}

/** Subtotal visual (UX): quantidade × custo, em centavos. A API recalcula. */
export function subtotalCentavos(quantidade: string, custo: string): number {
  const q = /^\d+$/.test(quantidade.trim()) ? Number(quantidade) : 0;
  const c = paraCentavos(custo);
  return c === null || Number.isNaN(c) ? 0 : q * c;
}

export function totalCentavos(itens: { quantidade: string; precoCustoUnitario: string }[]): number {
  return itens.reduce((soma, i) => soma + subtotalCentavos(i.quantidade, i.precoCustoUnitario), 0);
}

// ------------------------------------------------------------------ schemas (mesmas regras dos DTOs da API)

const opcional = (max: number) =>
  z
    .string()
    .max(max, `Use no máximo ${max} caracteres`)
    .optional()
    .transform((v) => (v?.trim() ? v.trim() : null));

const idOpcional = z
  .string()
  .optional()
  .transform((v) => (v && /^\d+$/.test(v) ? Number(v) : null));

const dinheiro = (mensagem: string) =>
  z
    .string()
    .optional()
    .transform((v) => paraCentavos(v))
    .refine((v) => v === null || !Number.isNaN(v), mensagem);

const camposProduto = {
  nome: z
    .string({ error: "Informe o nome" })
    .trim()
    .min(1, "Informe o nome")
    .min(2, "O nome deve ter entre 2 e 100 caracteres")
    .max(100, "O nome deve ter entre 2 e 100 caracteres"),
  descricao: opcional(2000),
  categoriaId: z
    .string({ error: "Selecione a categoria" })
    .regex(/^\d+$/, "Selecione a categoria")
    .transform(Number),
  fornecedorPadraoId: idOpcional,
  codigoBarras: z
    .string()
    .optional()
    .transform((v) => (v?.trim() ? v.trim() : null))
    .refine((v) => v === null || /^\d{8,14}$/.test(v), "O código de barras deve ter de 8 a 14 dígitos"),
  precoVenda: dinheiro("Informe um preço válido")
    .refine((v) => v !== null, "Informe o preço de venda")
    .refine((v) => v === null || Number.isNaN(v) || v > 0, "O preço de venda deve ser maior que zero"),
  estoqueMinimo: z
    .string({ error: "Informe o estoque mínimo" })
    .trim()
    .min(1, "Informe o estoque mínimo")
    .regex(/^-?\d+$/, "Informe um número inteiro")
    .transform(Number)
    .refine((v) => v >= 0, "O estoque mínimo não pode ser negativo")
    .refine((v) => v <= 1_000_000, "O estoque mínimo deve ser no máximo 1.000.000"),
};

type CamposProduto = {
  nome: string;
  descricao: string | null;
  categoriaId: number;
  fornecedorPadraoId: number | null;
  codigoBarras: string | null;
  precoVenda: number | null;
  estoqueMinimo: number;
};

/** Payload explícito: saldo, ativo e custo médio nunca vão para a API a partir do formulário. */
const payloadProduto = (v: CamposProduto) => ({
  nome: v.nome,
  descricao: v.descricao,
  categoriaId: v.categoriaId,
  fornecedorPadraoId: v.fornecedorPadraoId,
  codigoBarras: v.codigoBarras,
  precoVenda: deCentavos(v.precoVenda as number),
  estoqueMinimo: v.estoqueMinimo,
});

/** Cadastro: custo inicial opcional; depois ele só muda pelo custo médio das compras. */
export const produtoSchema = z
  .object({ ...camposProduto, precoCusto: dinheiro("Informe um custo válido (não negativo)") })
  .transform((v) => ({ ...payloadProduto(v), precoCusto: v.precoCusto === null ? null : deCentavos(v.precoCusto) }));

export const produtoAtualizacaoSchema = z.object(camposProduto).transform(payloadProduto);

export const ajusteEstoqueSchema = z.object({
  loteId: z
    .string()
    .optional()
    .transform((v) => (v && /^\d+$/.test(v) ? Number(v) : null)),
  codigoLote: z
    .string()
    .max(50, "Use no máximo 50 caracteres")
    .optional()
    .transform((v) => (v?.trim() ? v.trim() : null)),
  validade: z
    .string()
    .optional()
    .transform((v) => (v?.trim() ? v.trim() : null))
    .refine((v) => v === null || /^\d{4}-\d{2}-\d{2}$/.test(v), "Data inválida"),
  quantidade: z
    .string({ error: "Informe a quantidade" })
    .trim()
    .regex(/^[+-]?\d+$/, "Informe um número inteiro (use − para saída)")
    .transform(Number)
    .refine((v) => v !== 0, "O ajuste não pode ser zero")
    .refine((v) => Math.abs(v) <= 1_000_000, "O ajuste deve ser de no máximo 1.000.000 unidades"),
  motivo: z
    .string({ error: "Informe o motivo" })
    .trim()
    .min(5, "O motivo deve ter entre 5 e 500 caracteres")
    .max(500, "O motivo deve ter entre 5 e 500 caracteres"),
})
  // Sem lote o payload é o de sempre (quantidade e motivo); os campos de lote só vão quando preenchidos.
  .transform(({ loteId, codigoLote, validade, ...resto }) => ({
    ...resto,
    ...(loteId !== null && { loteId }),
    ...(codigoLote !== null && { codigoLote }),
    ...(validade !== null && { validade }),
  }));

export const fornecedorSchema = z
  .object({
    nomeFantasia: z
      .string({ error: "Informe o nome fantasia" })
      .trim()
      .min(2, "O nome fantasia deve ter entre 2 e 100 caracteres")
      .max(100, "O nome fantasia deve ter entre 2 e 100 caracteres"),
    razaoSocial: z
      .string({ error: "Informe a razão social" })
      .trim()
      .min(2, "A razão social deve ter entre 2 e 150 caracteres")
      .max(150, "A razão social deve ter entre 2 e 150 caracteres"),
    cnpj: z
      .string({ error: "Informe o CNPJ" })
      .refine((v) => cnpjValido(v), "CNPJ inválido")
      .transform(somenteDigitos),
    telefone: z
      .string()
      .optional()
      .transform((v) => (somenteDigitos(v) ? somenteDigitos(v) : null))
      .refine((v) => v === null || v.length === 10 || v.length === 11, "Telefone inválido: informe DDD + número"),
    email: z
      .union([z.literal(""), z.email("E-mail inválido").max(120, "Use no máximo 120 caracteres")])
      .optional()
      .transform((v) => (v ? v : null)),
    contato: opcional(100),
    observacoes: opcional(2000),
    cep: z
      .string()
      .optional()
      .transform((v) => somenteDigitos(v))
      .refine((v) => v.length === 8, "CEP inválido"),
    logradouro: z.string({ error: "Informe o logradouro" }).trim().min(1, "Informe o logradouro").max(150),
    numero: opcional(20),
    complemento: opcional(100),
    bairro: opcional(100),
    cidade: z.string({ error: "Informe a cidade" }).trim().min(1, "Informe a cidade").max(100),
    uf: z.string({ error: "UF inválida" }).regex(/^[A-Za-z]{2}$/, "UF inválida"),
  })
  .transform((v) => ({
    nomeFantasia: v.nomeFantasia,
    razaoSocial: v.razaoSocial,
    cnpj: v.cnpj,
    telefone: v.telefone,
    email: v.email,
    contato: v.contato,
    observacoes: v.observacoes,
    endereco: {
      cep: v.cep,
      logradouro: v.logradouro,
      numero: v.numero,
      complemento: v.complemento,
      bairro: v.bairro,
      cidade: v.cidade,
      uf: v.uf.toUpperCase(),
    },
  }));

export type ItemCompraForm = { produtoId: number; nome: string; quantidade: string; precoCustoUnitario: string };

const itemCompraSchema = z.object({
  produtoId: z.number({ error: "Selecione o produto" }).int().positive("Selecione o produto"),
  quantidade: z
    .string()
    .trim()
    .regex(/^\d+$/, "A quantidade deve ser de 1 a 1.000.000")
    .transform(Number)
    .refine((v) => v >= 1 && v <= 1_000_000, "A quantidade deve ser de 1 a 1.000.000"),
  precoCustoUnitario: z
    .string()
    .transform((v) => paraCentavos(v))
    .refine((v) => v !== null && !Number.isNaN(v), "Informe um custo válido (até duas casas)")
    .refine((v) => v === null || Number.isNaN(v) || v > 0, "O custo unitário deve ser maior que zero"),
});

/**
 * Pedido de compra. Os itens chegam como lista (o formulário envia JSON num campo
 * oculto). Não há total, status nem subtotal no payload: a API calcula.
 */
export const compraSchema = z
  .object({
    fornecedorId: z.string({ error: "Selecione o fornecedor" }).regex(/^\d+$/, "Selecione o fornecedor").transform(Number),
    funcionarioId: idOpcional,
    dataCompra: z
      .string({ error: "Informe a data da compra" })
      .regex(/^\d{4}-\d{2}-\d{2}$/, "Informe a data da compra")
      .refine((v) => v <= hojeISO(), "A data da compra não pode ser futura"),
    formaPagamento: z
      .string({ error: "Informe a forma de pagamento" })
      .trim()
      .min(2, "A forma de pagamento deve ter entre 2 e 50 caracteres")
      .max(50, "A forma de pagamento deve ter entre 2 e 50 caracteres"),
    observacoes: opcional(2000),
    itens: z
      .array(itemCompraSchema, { error: "Adicione pelo menos um item" })
      .min(1, "Adicione pelo menos um item")
      .max(100, "A compra pode ter no máximo 100 itens"),
  })
  .superRefine((v, ctx) => {
    const vistos = new Set<number>();
    v.itens.forEach((item, i) => {
      if (vistos.has(item.produtoId)) {
        ctx.addIssue({ code: "custom", path: ["itens", i, "produtoId"], message: "Este produto já está na compra" });
      }
      vistos.add(item.produtoId);
    });
  })
  .transform((v) => ({
    fornecedorId: v.fornecedorId,
    funcionarioId: v.funcionarioId,
    dataCompra: v.dataCompra,
    formaPagamento: v.formaPagamento,
    observacoes: v.observacoes,
    itens: v.itens.map((i) => ({
      produtoId: i.produtoId,
      quantidade: i.quantidade,
      precoCustoUnitario: deCentavos(i.precoCustoUnitario as number),
    })),
  }));

export const cancelamentoSchema = z.object({
  motivo: z
    .string({ error: "Informe o motivo" })
    .trim()
    .min(5, "O motivo deve ter entre 5 e 500 caracteres")
    .max(500, "O motivo deve ter entre 5 e 500 caracteres"),
});

/** Erros do Zod com o caminho completo ("itens.1.quantidade"), para destacar a linha certa. */
export function errosPorCaminho(error: z.ZodError): Record<string, string> {
  const erros: Record<string, string> = {};
  for (const issue of error.issues) {
    const chave = issue.path.join(".") || "_";
    erros[chave] ??= issue.message;
    const raiz = String(issue.path[0] ?? "_");
    erros[raiz] ??= issue.message;
  }
  return erros;
}
