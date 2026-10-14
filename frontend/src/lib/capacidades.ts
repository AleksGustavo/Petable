/**
 * Capacidades da instalação (etapa 07): o que a API expõe em `GET /api/v1/modulos`.
 * É só conveniência de interface (menu, cards, links): quem decide é a API, que responde 403 `MODULO_INDISPONIVEL`
 * mesmo que alguém chegue à tela por URL direta.
 */
export type Capacidades = {
  todos: boolean;
  modulos: string[];
  integracoes: string[];
  relatorios: string[];
  versaoCatalogo: number;
};

/** Valor de contingência quando a consulta falha: não esconde nada (a API continua barrando o que não existe). */
export const CAPACIDADES_DESCONHECIDAS: Capacidades = { todos: true, modulos: [], integracoes: [], relatorios: [], versaoCatalogo: 0 };

export const temModulo = (c: Capacidades, id: string) => c.todos || c.modulos.includes(id);
export const temIntegracao = (c: Capacidades, id: string) => c.todos || c.integracoes.includes(id);
export const temRelatorio = (c: Capacidades, tipo: string) => (c.todos && c.relatorios.length === 0) || c.relatorios.includes(tipo);

/** Módulo dono de cada segmento de rota da interface (primeiro segmento da URL). */
export const MODULO_DA_ROTA: Record<string, string> = {
  clientes: "clientes",
  pets: "pets",
  agendamentos: "agenda",
  servicos: "agenda",
  produtos: "produtos",
  fornecedores: "compras",
  compras: "compras",
  pdv: "pdv",
  vendas: "pdv",
  caixa: "caixa",
  financeiro: "financeiro",
  relatorios: "relatorios",
};

/** Módulo dono de uma rota da interface, ou `null` quando é núcleo (início, perfil, usuários). */
export function moduloDaRota(pathname: string): string | null {
  if (pathname === "/estoque/lotes" || pathname.startsWith("/estoque/lotes/")) return "lotes";
  const segmento = pathname.split("/")[1] ?? "";
  return MODULO_DA_ROTA[segmento] ?? null;
}
