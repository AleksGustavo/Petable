import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Aviso } from "@/components/aviso";
import { BadgeAtivo, BadgeEstoque } from "@/components/estoque-badges";
import { AjusteEstoqueForm, HistoricoEstoque, ProdutoAcoes, ProdutoDados, ResumoEstoque } from "@/components/produto-detalhes";
import { ProdutoForm } from "@/components/produto-form";
import { ApiError, apiFetch } from "@/lib/api";
import type { Pagina } from "@/lib/clientes";
import { TIPOS_MOVIMENTACAO, type CategoriaProduto, type FornecedorOpcao, type Movimentacao, type Produto } from "@/lib/estoque";
import { podeAjustarEstoque, podeEditarProdutos, podeReativarProdutos } from "@/lib/permissoes";
import { exigirSessao } from "@/lib/session";

export const metadata: Metadata = { title: "Produto" };

const ABAS = [
  { id: "dados", rotulo: "Dados" },
  { id: "estoque", rotulo: "Estoque" },
] as const;
type Aba = (typeof ABAS)[number]["id"];

const AVISOS: Record<string, string> = {
  criado: "Produto cadastrado com estoque zero. A entrada acontece ao receber uma compra.",
  salvo: "Produto atualizado.",
  desativado: "Produto desativado. Saldo e histórico foram preservados.",
  ativado: "Produto reativado.",
  ajuste: "Ajuste de estoque registrado no histórico.",
};

async function carregar(id: string): Promise<Produto> {
  if (!/^\d+$/.test(id)) notFound();
  try {
    return await apiFetch<Produto>(`/api/v1/produtos/${id}`);
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 404) notFound();
    throw e;
  }
}

export default async function ProdutoPage(props: PageProps<"/produtos/[id]">) {
  const { id } = await props.params;
  const sp = await props.searchParams;
  const texto = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const [sessao, produto] = await Promise.all([exigirSessao(), carregar(id)]);
  const aba: Aba = ABAS.some((a) => a.id === texto("aba")) ? (texto("aba") as Aba) : "dados";
  const editavel = podeEditarProdutos(sessao.papel);
  const aviso = AVISOS[texto("ok")];

  const tipo = TIPOS_MOVIMENTACAO.some((t) => t.valor === texto("tipo")) ? texto("tipo") : "";
  const pagina = Math.max(0, Number(texto("page")) || 0);
  const [categorias, fornecedores, historico] = await Promise.all([
    aba === "dados" && editavel ? apiFetch<CategoriaProduto[]>("/api/v1/categorias-produto") : Promise.resolve([]),
    aba === "dados" && editavel ? apiFetch<FornecedorOpcao[]>("/api/v1/fornecedores/opcoes") : Promise.resolve([]),
    aba === "estoque"
      ? apiFetch<Pagina<Movimentacao>>(
          `/api/v1/produtos/${produto.id}/movimentacoes?${new URLSearchParams({ ...(tipo && { tipo }), page: String(pagina), size: "20" })}`,
        )
      : Promise.resolve(null),
  ]);
  const linkHistorico = (p: number) => `/produtos/${produto.id}?${new URLSearchParams({ aba: "estoque", ...(tipo && { tipo }), page: String(p) })}`;

  return (
    <div className="space-y-6">
      <Link href="/produtos" className="text-sm text-muted hover:text-foreground">← Produtos</Link>
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold">{produto.nome}</h1>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-muted">{produto.categoria.nome} · saldo {produto.quantidadeEstoque}</span>
            <BadgeEstoque abaixoDoMinimo={produto.abaixoDoMinimo} />
            <BadgeAtivo ativo={produto.ativo} />
          </div>
        </div>
        <ProdutoAcoes produto={produto} podeDesativar={editavel} podeReativar={podeReativarProdutos(sessao.papel)} />
      </header>

      {aviso && <Aviso>{aviso}</Aviso>}

      <nav aria-label="Seções do produto" className="flex gap-1 overflow-x-auto rounded-lg border border-border bg-surface p-1">
        {ABAS.map((a) => (
          <Link
            key={a.id}
            href={`/produtos/${produto.id}?aba=${a.id}`}
            aria-current={aba === a.id ? "page" : undefined}
            className={`flex-1 whitespace-nowrap rounded-md px-4 py-2 text-center text-sm font-medium transition ${
              aba === a.id ? "bg-brand text-white" : "text-muted hover:bg-brand-soft"
            }`}
          >
            {a.rotulo}
          </Link>
        ))}
      </nav>

      {aba === "dados" &&
        (editavel ? (
          <ProdutoForm key={produto.updatedAt} produto={produto} categorias={categorias} fornecedores={fornecedores} />
        ) : (
          <ProdutoDados produto={produto} />
        ))}

      {aba === "estoque" && historico && (
        <div className="space-y-4">
          <ResumoEstoque produto={produto} />
          {podeAjustarEstoque(sessao.papel) && <AjusteEstoqueForm key={produto.quantidadeEstoque} produto={produto} />}
          <div className="flex flex-wrap items-end justify-between gap-2">
            <h2 className="text-sm font-semibold text-muted">Histórico ({historico.totalElements})</h2>
            <form className="flex gap-2">
              <input type="hidden" name="aba" value="estoque" />
              <label htmlFor="filtro-tipo" className="sr-only">Tipo de movimentação</label>
              <select id="filtro-tipo" name="tipo" defaultValue={tipo} className="campo py-1.5 text-sm">
                <option value="">Todos os tipos</option>
                {TIPOS_MOVIMENTACAO.map((t) => (
                  <option key={t.valor} value={t.valor}>{t.rotulo}</option>
                ))}
              </select>
              <button type="submit" className="botao-secundario py-1.5 text-sm">Filtrar</button>
            </form>
          </div>
          <HistoricoEstoque movimentacoes={historico.content} />
          {historico.totalPages > 1 && (
            <nav aria-label="Paginação do histórico" className="flex items-center justify-between text-sm">
              {pagina > 0 ? <Link href={linkHistorico(pagina - 1)} className="botao-secundario">← Anterior</Link> : <span />}
              <span className="text-muted">Página {pagina + 1} de {historico.totalPages}</span>
              {pagina + 1 < historico.totalPages ? <Link href={linkHistorico(pagina + 1)} className="botao-secundario">Próxima →</Link> : <span />}
            </nav>
          )}
        </div>
      )}
    </div>
  );
}
