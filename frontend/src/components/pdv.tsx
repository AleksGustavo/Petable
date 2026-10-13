"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { finalizarVenda, previaVenda } from "@/app/actions/vendas";
import { Aviso } from "@/components/aviso";
import { PdvCarrinho } from "@/components/pdv-carrinho";
import { PdvLeitor } from "@/components/pdv-leitor";
import { calcularPagamento, PAGAMENTO_INICIAL, PdvPagamento, type Pagamento } from "@/components/pdv-pagamento";
import { TutorPicker } from "@/components/tutor-picker";
import { moeda } from "@/lib/agendamentos";
import { MENSAGEM_SEM_CAIXA } from "@/lib/financeiro";
import type { ProdutoRapido } from "@/lib/estoque";
import type { Papel } from "@/lib/jwt";
import {
  adicionarAoCarrinho,
  alterarQuantidade,
  aplicarPrevia,
  carregarCarrinho,
  limparCarrinhoSalvo,
  novaChave,
  removerDoCarrinho,
  rotuloForma,
  salvarCarrinho,
  subtotalCarrinhoCentavos,
  type EstadoCarrinho,
  type FormaPagamento,
  type Venda,
  type VendaEntrada,
} from "@/lib/vendas";

type Mensagem = { tipo: "info" | "erro"; texto: string };

const armazenamento = () => (typeof window === "undefined" ? undefined : window.sessionStorage);

/**
 * Frente de caixa. Renderizado só no cliente (o carrinho vem do sessionStorage).
 * Fluxo: leitor/busca → carrinho → prévia no backend → confirmação com o total do
 * backend → finalização com a Idempotency-Key do carrinho.
 */
export function Pdv({
  papel,
  buscarProdutos,
  caixaAberto: caixaInicial = true,
}: {
  papel: Papel;
  buscarProdutos?: (q: string) => Promise<ProdutoRapido[]>;
  /** Etapa 06: sem caixa aberto a venda é bloqueada (a API responde 409 de qualquer forma). */
  caixaAberto?: boolean;
}) {
  const [caixaAberto, setCaixaAberto] = useState(caixaInicial);
  const [carrinho, setCarrinho] = useState<EstadoCarrinho>(() => carregarCarrinho(armazenamento()) ?? { itens: [], chave: novaChave() });
  const [pagamento, setPagamento] = useState<Pagamento>(PAGAMENTO_INICIAL);
  const [cliente, setCliente] = useState<{ id: number; nome: string } | null>(null);
  const [observacoes, setObservacoes] = useState("");
  const [mensagem, setMensagem] = useState<Mensagem | null>(null);
  const [erros, setErros] = useState<Record<string, string>>({});
  const [destaque, setDestaque] = useState<number | null>(null);
  const [pendente, setPendente] = useState(false);
  const [concluida, setConcluida] = useState<{ venda: Venda; repetida: boolean } | null>(null);

  const { itens, chave } = carrinho;
  const subtotal = subtotalCarrinhoCentavos(itens);

  // Persiste carrinho + chave (sobrevive a recarregar; nunca guarda token).
  useEffect(() => {
    if (itens.length > 0) salvarCarrinho(armazenamento(), carrinho);
    else limparCarrinhoSalvo(armazenamento());
  }, [carrinho, itens.length]);

  function atualizarItens(novos: EstadoCarrinho["itens"]) {
    setCarrinho((atual) => ({ ...atual, itens: novos }));
    setDestaque(null);
    setErros({});
  }

  function adicionar(p: ProdutoRapido) {
    const r = adicionarAoCarrinho(itens, p);
    atualizarItens(r.itens);
    setMensagem(r.erro ? { tipo: "erro", texto: r.erro } : null);
  }

  function entrada(forma: FormaPagamento): VendaEntrada {
    const { desconto, recebido } = calcularPagamento(pagamento, subtotal);
    return {
      clienteId: cliente?.id ?? null,
      itens: itens.map((i) => ({ produtoId: i.produtoId, quantidade: i.quantidade })),
      descontoCentavos: desconto,
      formaPagamento: forma,
      valorRecebidoCentavos: recebido === null || Number.isNaN(recebido) ? null : recebido,
      observacoes,
    };
  }

  async function finalizar() {
    const forma = pagamento.forma;
    if (pendente || !forma || itens.length === 0 || !caixaAberto) return;
    setPendente(true);
    setMensagem(null);
    setErros({});
    try {
      const dados = entrada(forma);
      const p = await previaVenda(dados);
      if (!p.ok) {
        setErros(p.erros ?? {});
        setMensagem({ tipo: "erro", texto: p.mensagem });
        return;
      }
      const { itens: atualizados, precosMudaram } = aplicarPrevia(itens, p.previa);
      setCarrinho((atual) => ({ ...atual, itens: atualizados }));
      const faltando = p.previa.itens.find((i) => !i.suficiente);
      if (faltando) {
        setDestaque(faltando.produtoId);
        setMensagem({ tipo: "erro", texto: `Estoque insuficiente para ${faltando.nome}: disponível ${faltando.disponivel}, solicitado ${faltando.quantidade}.` });
        return;
      }
      if (precosMudaram) {
        setMensagem({ tipo: "info", texto: "Preços atualizados pelo sistema. Confira o total e finalize novamente." });
        return;
      }
      if (!window.confirm(`Finalizar a venda de ${moeda(p.previa.total)} em ${rotuloForma(forma)}?`)) return;

      const r = await finalizarVenda(dados, chave);
      if (!r.ok && r.status === 409 && r.mensagem === MENSAGEM_SEM_CAIXA) {
        // Caixa fechado em outra aba: bloqueia, mas preserva carrinho e chave.
        setCaixaAberto(false);
        return;
      }
      if (!r.ok) {
        setErros(r.erros ?? {});
        if (r.produtoId) setDestaque(r.produtoId);
        setMensagem({ tipo: "erro", texto: r.mensagem });
        return;
      }
      setConcluida({ venda: r.venda, repetida: r.repetida });
      setCarrinho({ itens: [], chave: novaChave() });
      limparCarrinhoSalvo(armazenamento());
      setPagamento(PAGAMENTO_INICIAL);
      setCliente(null);
      setObservacoes("");
    } finally {
      setPendente(false);
    }
  }

  if (concluida) {
    const { venda, repetida } = concluida;
    return (
      <section className="cartao mx-auto max-w-md space-y-4 p-6 text-center" aria-live="polite">
        <p className="text-4xl" aria-hidden="true">✓</p>
        <h2 className="text-xl font-semibold">Venda #{venda.id} concluída</h2>
        {repetida && <p className="text-sm text-muted">Esta venda já tinha sido registrada; nada foi cobrado de novo.</p>}
        <p className="text-2xl font-semibold tabular-nums">{moeda(venda.valorTotal)}</p>
        <p className="text-sm text-muted">{rotuloForma(venda.formaPagamento)}</p>
        {venda.troco !== null && venda.troco > 0 && (
          <p className="text-lg">
            Troco: <strong className="tabular-nums">{moeda(venda.troco)}</strong>
          </p>
        )}
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Link href={`/vendas/${venda.id}/comprovante`} target="_blank" className="botao-secundario">Imprimir comprovante</Link>
          <button type="button" className="botao-primario" onClick={() => setConcluida(null)} autoFocus>
            Nova venda
          </button>
        </div>
      </section>
    );
  }

  return (
    <div className="grid gap-6 pb-24 xl:grid-cols-[minmax(0,1fr)_22rem] xl:pb-0">
      <div className="space-y-4">
        <section className="cartao space-y-4 p-4 sm:p-6" aria-label="Produtos">
          <PdvLeitor onAdicionar={adicionar} buscar={buscarProdutos} desabilitado={pendente} />
        </section>
        {!caixaAberto && (
          <Aviso tipo="erro">
            Abra o caixa para realizar vendas.{" "}
            <Link href="/caixa" className="font-medium underline">Abrir caixa</Link>
          </Aviso>
        )}
        {mensagem && <Aviso tipo={mensagem.tipo}>{mensagem.texto}</Aviso>}
        {erros.itens && <Aviso tipo="erro">{erros.itens}</Aviso>}
        <section className="cartao space-y-3 p-4 sm:p-6" aria-labelledby="titulo-carrinho">
          <h2 id="titulo-carrinho" className="text-sm font-semibold text-muted">
            Carrinho ({itens.length} {itens.length === 1 ? "item" : "itens"})
          </h2>
          <PdvCarrinho
            itens={itens}
            destaque={destaque}
            onQuantidade={(id, q) => atualizarItens(alterarQuantidade(itens, id, q))}
            onRemover={(id) => atualizarItens(removerDoCarrinho(itens, id))}
            onLimpar={() => atualizarItens([])}
          />
        </section>
      </div>

      <aside className="cartao h-fit space-y-5 p-4 sm:p-6 xl:sticky xl:top-6" aria-label="Pagamento">
        <TutorPicker key={chave} rotulo="Cliente (opcional)" obrigatorio={false} erro={erros.clienteId} onSelecionar={(t) => setCliente(t)} />
        <PdvPagamento
          papel={papel}
          subtotalCentavos={subtotal}
          valor={pagamento}
          onChange={setPagamento}
          onFinalizar={finalizar}
          pendente={pendente}
          erros={erros}
          vazio={itens.length === 0 || !caixaAberto}
        />
        <div>
          <label htmlFor="pdv-observacoes" className="mb-1 block text-sm font-medium">Observações</label>
          <textarea id="pdv-observacoes" rows={2} maxLength={2000} className="campo" value={observacoes} onChange={(e) => setObservacoes(e.target.value)} />
        </div>
      </aside>

      {/* Celular: total e Finalizar fixos no rodapé (a validação completa é a mesma do painel). */}
      <div className="fixed inset-x-0 bottom-0 z-20 flex items-center justify-between gap-3 border-t border-border bg-surface/95 px-4 py-3 backdrop-blur md:left-64 xl:hidden">
        <p className="text-sm">
          Total <strong className="tabular-nums">{moeda(calcularPagamento(pagamento, subtotal).total / 100)}</strong>
          {!pagamento.forma && itens.length > 0 && <span className="block text-xs text-muted">Escolha a forma de pagamento</span>}
        </p>
        <button type="button" className="botao-primario px-4 py-2 text-sm" disabled={pendente || itens.length === 0 || !pagamento.forma || !caixaAberto} onClick={finalizar}>
          {pendente ? "Finalizando…" : "Finalizar"}
        </button>
      </div>
    </div>
  );
}
