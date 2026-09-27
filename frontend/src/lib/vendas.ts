import * as z from "zod";
import { deCentavos, paraCentavos, type ProdutoRapido } from "@/lib/estoque";
import type { CatalogoItem } from "@/lib/pets";

// ------------------------------------------------------------------ tipos (espelham os DTOs da API)

export type FormaPagamento = "DINHEIRO" | "CARTAO_CREDITO" | "CARTAO_DEBITO" | "PIX" | "TRANSFERENCIA";
export type StatusVenda = "CONCLUIDA" | "CANCELADA";

export const FORMAS_PAGAMENTO: { valor: FormaPagamento; rotulo: string }[] = [
  { valor: "DINHEIRO", rotulo: "Dinheiro" },
  { valor: "PIX", rotulo: "Pix" },
  { valor: "CARTAO_DEBITO", rotulo: "Cartão de débito" },
  { valor: "CARTAO_CREDITO", rotulo: "Cartão de crédito" },
  { valor: "TRANSFERENCIA", rotulo: "Transferência" },
];

export const rotuloForma = (f: FormaPagamento) => FORMAS_PAGAMENTO.find((x) => x.valor === f)?.rotulo ?? f;

export const STATUS_VENDA: { valor: StatusVenda; rotulo: string }[] = [
  { valor: "CONCLUIDA", rotulo: "Concluída" },
  { valor: "CANCELADA", rotulo: "Cancelada" },
];

export type ItemVenda = { id: number; produto: CatalogoItem; quantidade: number; precoUnitario: number; subtotal: number };

export type Venda = {
  id: number;
  /** Horário local de São Paulo ("2026-09-26T11:03:11.12"). */
  dataVenda: string;
  status: StatusVenda;
  cliente: CatalogoItem | null;
  funcionario: CatalogoItem | null;
  operador: CatalogoItem | null;
  /** null na listagem (itens não carregados). */
  itens: ItemVenda[] | null;
  quantidadeItens: number;
  subtotal: number;
  desconto: number;
  valorTotal: number;
  formaPagamento: FormaPagamento;
  valorRecebido: number | null;
  troco: number | null;
  observacoes: string | null;
  canceladaEm: string | null;
  canceladaPor: CatalogoItem | null;
  motivoCancelamento: string | null;
};

export type PreviaVenda = {
  itens: { produtoId: number; nome: string; precoUnitario: number; quantidade: number; subtotal: number; disponivel: number; suficiente: boolean }[];
  subtotal: number;
  desconto: number;
  total: number;
  troco: number | null;
  podeFinalizar: boolean;
};

export type ResumoDia = { data: string; quantidade: number; total: number };

/** Teto de desconto do papel VENDAS (espelha petconnect.vendas.desconto-maximo-percentual-vendas; a API é quem barra). */
export const TETO_DESCONTO_VENDAS = 10;

/** Prazo de cancelamento (DA-07). */
export const PRAZO_CANCELAMENTO_DIAS = 7;

export function dentroDoPrazoDeCancelamento(dataVenda: string, agora = new Date()): boolean {
  // dataVenda é horário local de SP; comparamos em milissegundos com folga de fuso irrelevante para 7 dias.
  const venda = new Date(`${dataVenda.slice(0, 19)}-03:00`);
  return agora.getTime() - venda.getTime() <= PRAZO_CANCELAMENTO_DIAS * 24 * 60 * 60 * 1000;
}

// ------------------------------------------------------------------ carrinho (dinheiro em centavos)

export type ItemCarrinho = {
  produtoId: number;
  nome: string;
  codigoBarras: string | null;
  /** Preço exibido (visual): a API usa o preço atual do produto na finalização. */
  precoCentavos: number;
  quantidade: number;
  /** Saldo conhecido na última busca/prévia. */
  disponivel: number;
};

export const MAX_ITENS_VENDA = 100;
export const MAX_QUANTIDADE = 10_000;

const paraCentavosNumero = (valor: number) => Math.round(valor * 100);

/** Adiciona 1 unidade (ou soma +1 se já estiver no carrinho), respeitando o saldo conhecido. */
export function adicionarAoCarrinho(itens: ItemCarrinho[], p: ProdutoRapido): { itens: ItemCarrinho[]; erro?: string } {
  if (p.quantidadeEstoque <= 0) return { itens, erro: `${p.nome} está sem estoque.` };
  const existente = itens.find((i) => i.produtoId === p.id);
  if (existente) {
    if (existente.quantidade >= p.quantidadeEstoque) {
      return { itens, erro: `Só há ${p.quantidadeEstoque} unidade(s) de ${p.nome} em estoque.` };
    }
    return {
      itens: itens.map((i) => (i.produtoId === p.id ? { ...i, quantidade: i.quantidade + 1, disponivel: p.quantidadeEstoque } : i)),
    };
  }
  if (itens.length >= MAX_ITENS_VENDA) return { itens, erro: `A venda pode ter no máximo ${MAX_ITENS_VENDA} itens.` };
  return {
    itens: [
      ...itens,
      {
        produtoId: p.id,
        nome: p.nome,
        codigoBarras: p.codigoBarras,
        precoCentavos: paraCentavosNumero(p.precoVenda),
        quantidade: 1,
        disponivel: p.quantidadeEstoque,
      },
    ],
  };
}

/** Quantidade entre 1 e o saldo conhecido (e no máximo 10.000). */
export function alterarQuantidade(itens: ItemCarrinho[], produtoId: number, quantidade: number): ItemCarrinho[] {
  return itens.map((i) => {
    if (i.produtoId !== produtoId) return i;
    const limite = Math.min(i.disponivel, MAX_QUANTIDADE);
    const q = Number.isFinite(quantidade) ? Math.trunc(quantidade) : 1;
    return { ...i, quantidade: Math.max(1, Math.min(q, limite)) };
  });
}

export const removerDoCarrinho = (itens: ItemCarrinho[], produtoId: number) => itens.filter((i) => i.produtoId !== produtoId);

export const subtotalItemCentavos = (i: ItemCarrinho) => i.precoCentavos * i.quantidade;

export const subtotalCarrinhoCentavos = (itens: ItemCarrinho[]) => itens.reduce((s, i) => s + subtotalItemCentavos(i), 0);

/** Atualiza preço e saldo com o que o backend devolveu na prévia; diz se algum preço mudou. */
export function aplicarPrevia(itens: ItemCarrinho[], previa: PreviaVenda): { itens: ItemCarrinho[]; precosMudaram: boolean } {
  let precosMudaram = false;
  const novos = itens.map((i) => {
    const p = previa.itens.find((x) => x.produtoId === i.produtoId);
    if (!p) return i;
    const preco = paraCentavosNumero(p.precoUnitario);
    if (preco !== i.precoCentavos) precosMudaram = true;
    return { ...i, precoCentavos: preco, disponivel: p.disponivel };
  });
  return { itens: novos, precosMudaram };
}

// ------------------------------------------------------------------ persistência (sessionStorage, nunca token)

export const CHAVE_STORAGE = "pc_pdv_carrinho";

export type EstadoCarrinho = { itens: ItemCarrinho[]; chave: string };

type Armazenamento = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export function salvarCarrinho(storage: Armazenamento | undefined, estado: EstadoCarrinho): void {
  try {
    storage?.setItem(CHAVE_STORAGE, JSON.stringify(estado));
  } catch {
    /* modo privado / cota: o carrinho só não sobrevive ao recarregamento */
  }
}

export function limparCarrinhoSalvo(storage: Armazenamento | undefined): void {
  try {
    storage?.removeItem(CHAVE_STORAGE);
  } catch {
    /* idem */
  }
}

const itemSalvo = z.object({
  produtoId: z.number().int().positive(),
  nome: z.string(),
  codigoBarras: z.string().nullable(),
  precoCentavos: z.number().int().nonnegative(),
  quantidade: z.number().int().positive(),
  disponivel: z.number().int().nonnegative(),
});
const estadoSalvo = z.object({ itens: z.array(itemSalvo).max(MAX_ITENS_VENDA), chave: z.uuid() });

/** Restaura o carrinho salvo; qualquer coisa inválida é descartada. */
export function carregarCarrinho(storage: Armazenamento | undefined): EstadoCarrinho | null {
  try {
    const bruto = storage?.getItem(CHAVE_STORAGE);
    if (!bruto) return null;
    const r = estadoSalvo.safeParse(JSON.parse(bruto));
    return r.success ? r.data : null;
  } catch {
    return null;
  }
}

// ------------------------------------------------------------------ desconto e troco (UX; a API recalcula)

export type ModoDesconto = "valor" | "percentual";

/** Desconto em centavos a partir de "R$" ou "%"; NaN se inválido; 0 se vazio. */
export function descontoCentavos(modo: ModoDesconto, texto: string, subtotalCentavos: number): number {
  if (!texto.trim()) return 0;
  if (modo === "valor") {
    const c = paraCentavos(texto);
    return c === null ? 0 : c;
  }
  const pct = Number(texto.trim().replace(",", "."));
  if (!Number.isFinite(pct) || pct < 0 || pct > 100 || !/^\d+([.,]\d{1,2})?$/.test(texto.trim())) return Number.NaN;
  return Math.round((subtotalCentavos * pct) / 100);
}

export function trocoCentavos(recebidoCentavos: number | null, totalCentavos: number): number | null {
  return recebidoCentavos === null || Number.isNaN(recebidoCentavos) ? null : recebidoCentavos - totalCentavos;
}

// ------------------------------------------------------------------ schema do payload (mesmas regras do VendaRequest)

export const vendaSchema = z
  .object({
    clienteId: z.number().int().positive().nullable().optional(),
    itens: z
      .array(
        z.object({
          produtoId: z.number().int().positive(),
          quantidade: z
            .number({ error: "Informe a quantidade" })
            .int("A quantidade deve ser um número inteiro")
            .min(1, "A quantidade deve ser de 1 a 10.000")
            .max(MAX_QUANTIDADE, "A quantidade deve ser de 1 a 10.000"),
        }),
      )
      .min(1, "Adicione pelo menos um item")
      .max(MAX_ITENS_VENDA, "A venda pode ter no máximo 100 itens"),
    descontoCentavos: z.number().int().min(0, "O desconto não pode ser negativo").refine((v) => !Number.isNaN(v), "Desconto inválido"),
    formaPagamento: z.enum(["DINHEIRO", "CARTAO_CREDITO", "CARTAO_DEBITO", "PIX", "TRANSFERENCIA"], {
      error: "Selecione a forma de pagamento",
    }),
    valorRecebidoCentavos: z.number().int().nonnegative().nullable().optional(),
    observacoes: z.string().max(2000, "Use no máximo 2000 caracteres").optional(),
  })
  .superRefine((v, ctx) => {
    const vistos = new Set<number>();
    v.itens.forEach((i, idx) => {
      if (vistos.has(i.produtoId)) ctx.addIssue({ code: "custom", path: ["itens", idx, "produtoId"], message: "Produto repetido" });
      vistos.add(i.produtoId);
    });
    if (v.formaPagamento === "DINHEIRO" && (v.valorRecebidoCentavos === null || v.valorRecebidoCentavos === undefined)) {
      ctx.addIssue({ code: "custom", path: ["valorRecebido"], message: "Informe o valor recebido." });
    }
  })
  .transform((v) => ({
    clienteId: v.clienteId ?? null,
    itens: v.itens.map((i) => ({ produtoId: i.produtoId, quantidade: i.quantidade })),
    desconto: deCentavos(v.descontoCentavos),
    formaPagamento: v.formaPagamento,
    valorRecebido:
      v.formaPagamento === "DINHEIRO" && v.valorRecebidoCentavos !== null && v.valorRecebidoCentavos !== undefined
        ? deCentavos(v.valorRecebidoCentavos)
        : null,
    observacoes: v.observacoes?.trim() ? v.observacoes.trim() : null,
  }));

export type VendaEntrada = z.input<typeof vendaSchema>;
export type VendaPayload = z.output<typeof vendaSchema>;

/** Chave de idempotência do carrinho (UUID v4). `randomUUID` só existe em contexto seguro; há fallback. */
export function novaChave(): string {
  const c = globalThis.crypto;
  if (typeof c?.randomUUID === "function") return c.randomUUID();
  const b = c.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
