import type { Papel } from "@/lib/jwt";

// ------------------------------------------------------------------ catálogo (DA-09)

export type TipoRelatorio =
  | "vendas"
  | "produtos-mais-vendidos"
  | "curva-abc"
  | "estoque"
  | "margem"
  | "servicos"
  | "despesas"
  | "caixas"
  | "contas";

export type DefinicaoRelatorio = { tipo: TipoRelatorio; titulo: string; descricao: string; papeis: Papel[] };

const TODOS: Papel[] = ["ADMIN", "VENDAS"];
const ADMIN: Papel[] = ["ADMIN"];

export const RELATORIOS: DefinicaoRelatorio[] = [
  { tipo: "vendas", titulo: "Vendas", descricao: "Faturamento, descontos, ticket médio, por dia, forma e operador.", papeis: TODOS },
  { tipo: "produtos-mais-vendidos", titulo: "Produtos mais vendidos", descricao: "Ranking por quantidade e faturamento.", papeis: TODOS },
  { tipo: "curva-abc", titulo: "Curva ABC", descricao: "Classificação dos produtos pelo faturamento (80/15/5).", papeis: TODOS },
  { tipo: "estoque", titulo: "Estoque: valor, giro e cobertura", descricao: "Valor em estoque pelo custo médio, giro e dias de cobertura.", papeis: ADMIN },
  { tipo: "margem", titulo: "Margem estimada", descricao: "Receita menos custo estimado (custo médio atual).", papeis: ADMIN },
  { tipo: "servicos", titulo: "Serviços", descricao: "Receita de serviços por serviço e por funcionário.", papeis: ADMIN },
  { tipo: "despesas", titulo: "Despesas", descricao: "Despesas por categoria (sem sangria e suprimento).", papeis: ADMIN },
  { tipo: "caixas", titulo: "Caixas", descricao: "Caixas fechados e diferenças por operador.", papeis: ADMIN },
  { tipo: "contas", titulo: "Contas a pagar", descricao: "Contas pagas no período e situação atual das abertas.", papeis: ADMIN },
];

export const definicao = (tipo: string) => RELATORIOS.find((r) => r.tipo === tipo);
export const podeVerRelatorio = (papel: Papel, tipo: string) => definicao(tipo)?.papeis.includes(papel) ?? false;
export const relatoriosDoPapel = (papel: Papel) => RELATORIOS.filter((r) => r.papeis.includes(papel));

// ------------------------------------------------------------------ período (DA-02)

export const MAXIMO_DIAS = 366;

const ISO = /^\d{4}-\d{2}-\d{2}$/;
const utc = (iso: string) => Date.UTC(Number(iso.slice(0, 4)), Number(iso.slice(5, 7)) - 1, Number(iso.slice(8, 10)));
const iso = (ms: number) => new Date(ms).toISOString().slice(0, 10);

export function somarDias(data: string, dias: number): string {
  return iso(utc(data) + dias * 86_400_000);
}

export function diasNoPeriodo(inicio: string, fim: string): number {
  return Math.round((utc(fim) - utc(inicio)) / 86_400_000) + 1;
}

/** Padrão: do primeiro dia do mês corrente até hoje. */
export function periodoPadrao(hoje: string) {
  return { dataInicio: `${hoje.slice(0, 8)}01`, dataFim: hoje };
}

export function atalhosPeriodo(hoje: string): { rotulo: string; dataInicio: string; dataFim: string }[] {
  return [
    { rotulo: "Hoje", dataInicio: hoje, dataFim: hoje },
    { rotulo: "7 dias", dataInicio: somarDias(hoje, -6), dataFim: hoje },
    { rotulo: "Mês", ...periodoPadrao(hoje) },
    { rotulo: "30 dias", dataInicio: somarDias(hoje, -29), dataFim: hoje },
  ];
}

/** Mesmas regras da API: datas válidas, fim >= início e no máximo 366 dias. Devolve a mensagem de erro ou null. */
export function erroPeriodo(dataInicio: string, dataFim: string): string | null {
  if (!ISO.test(dataInicio) || !ISO.test(dataFim)) return "Informe as datas inicial e final.";
  if (dataFim < dataInicio) return "A data final não pode ser anterior à inicial.";
  if (diasNoPeriodo(dataInicio, dataFim) > MAXIMO_DIAS) return `O período pode ter no máximo ${MAXIMO_DIAS} dias.`;
  return null;
}

// ------------------------------------------------------------------ respostas da API

export type Contagem = { quantidade: number; valor: number };
export type Grupo = { chave: string; quantidade: number; liquido: number };

export type RelVendas = {
  dataInicio: string;
  dataFim: string;
  quantidade: number;
  bruto: number;
  descontos: number;
  liquido: number;
  ticketMedio: number;
  canceladas: Contagem;
  porDia: { data: string; quantidade: number; liquido: number }[];
  porForma: Grupo[];
  porOperador: Grupo[];
};

export type RelMaisVendidos = {
  itens: { produtoId: number; nome: string; categoria: string; quantidade: number; faturamento: number; participacao: number }[];
  total: number;
};

export type ClasseAbc = "A" | "B" | "C";

export type RelCurvaAbc = {
  itens: { produtoId: number; nome: string; faturamento: number; participacao: number; acumulado: number; classe: ClasseAbc }[];
  resumo: { classe: ClasseAbc; produtos: number; faturamento: number; participacao: number }[];
  total: number;
};

export type RelEstoque = {
  itens: {
    produtoId: number;
    nome: string;
    saldoAtual: number;
    custoMedio: number;
    valorEstoque: number;
    quantidadeVendida: number;
    giro: number | null;
    coberturaDias: number | null;
  }[];
  valorTotalEstoque: number;
  produtosSemCusto: number;
  aviso: string;
};

export type RelMargem = {
  receita: number;
  custoEstimado: number;
  margem: number;
  margemPercentual: number;
  produtosSemCusto: number;
  porProduto: { produtoId: number; nome: string; quantidade: number; receita: number; custoEstimado: number; margem: number; margemPercentual: number }[];
  aviso: string;
};

export type RelServicos = { quantidade: number; total: number; porServico: Grupo[]; porFuncionario: Grupo[] };
export type RelDespesas = { total: number; porCategoria: { categoria: string; valor: number }[] };

export type RelCaixas = {
  quantidade: number;
  diferencaTotal: number;
  porOperador: { operador: string; caixas: number; diferenca: number; faltas: number; sobras: number }[];
  caixas: { id: number; operador: string; abertoEm: string; fechadoEm: string; diferencaTotal: number }[];
};

export type RelContas = {
  pagas: Contagem;
  vencidasEmAberto: Contagem;
  aVencerEmAberto: Contagem;
  itensPagos: { id: number; descricao: string; valor: number; pagaEm: string; formaPagamento: string }[];
};

export type DadosRelatorio =
  | { tipo: "vendas"; dados: RelVendas }
  | { tipo: "produtos-mais-vendidos"; dados: RelMaisVendidos }
  | { tipo: "curva-abc"; dados: RelCurvaAbc }
  | { tipo: "estoque"; dados: RelEstoque }
  | { tipo: "margem"; dados: RelMargem }
  | { tipo: "servicos"; dados: RelServicos }
  | { tipo: "despesas"; dados: RelDespesas }
  | { tipo: "caixas"; dados: RelCaixas }
  | { tipo: "contas"; dados: RelContas };
