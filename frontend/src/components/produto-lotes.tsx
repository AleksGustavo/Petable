"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { acaoSobreLote, ativarControleLote, type AcaoLote } from "@/app/actions/estoque";
import { Aviso } from "@/components/aviso";
import { Campo } from "@/components/campo";
import { BadgeLote } from "@/components/estoque-badges";
import { GradeLotes } from "@/components/lotes-grade";
import type { FormState } from "@/lib/clientes";
import type { Produto } from "@/lib/estoque";
import { formatarData } from "@/lib/format";
import { linhaVazia, regularizacaoSchema, textoPrazo, type LinhaLote, type Lote } from "@/lib/lotes";

type Props = {
  produto: Produto;
  lotes: Lote[];
  /** O controle de lote está ligado nesta instalação (flag do servidor). */
  habilitado: boolean;
  podeGerenciar: boolean;
  hoje: string;
};

export function ProdutoLotes({ produto, lotes, habilitado, podeGerenciar, hoje }: Props) {
  if (!produto.controlaLote) {
    if (!habilitado) {
      return <Aviso>O controle de lote e validade ainda não foi liberado nesta instalação. O estoque deste produto segue como sempre.</Aviso>;
    }
    if (!podeGerenciar) {
      return <p className="cartao p-6 text-sm text-muted">Este produto não controla lote. Só o administrador pode ligar o controle.</p>;
    }
    return <AtivarControle produto={produto} />;
  }
  return (
    <div className="space-y-4">
      <PoliticaDoProduto produto={produto} />
      <ListaDeLotes produto={produto} lotes={lotes} podeGerenciar={podeGerenciar} hoje={hoje} />
    </div>
  );
}

function PoliticaDoProduto({ produto }: { produto: Produto }) {
  return (
    <dl className="cartao grid gap-4 p-4 sm:grid-cols-3 sm:p-6">
      <div>
        <dt className="text-xs uppercase text-muted">Controle de lote</dt>
        <dd className="font-medium">✓ Ligado</dd>
      </div>
      <div>
        <dt className="text-xs uppercase text-muted">Validade</dt>
        <dd className="font-medium">{produto.permiteSemValidade ? "Pode haver lote sem validade" : "Obrigatória em todo lote"}</dd>
      </div>
      <div>
        <dt className="text-xs uppercase text-muted">Bloqueio antes do vencimento</dt>
        <dd className="font-medium">
          {produto.diasBloqueioValidade == null ? "Margem padrão da loja" : `${produto.diasBloqueioValidade} dia(s)`}
        </dd>
      </div>
      <p className="text-xs text-muted sm:col-span-3">
        A venda escolhe sozinha o lote que vence primeiro (FEFO). Lote vencido ou bloqueado nunca é vendido. O saldo do produto ({produto.quantidadeEstoque}) é
        a soma dos lotes.
      </p>
    </dl>
  );
}

// ------------------------------------------------------------------ ativar o controle (regularização do saldo)

function AtivarControle({ produto }: { produto: Produto }) {
  const saldo = produto.quantidadeEstoque;
  const [state, action, pending] = useActionState<FormState, FormData>(ativarControleLote.bind(null, produto.id, saldo), {});
  const [linhas, setLinhas] = useState<LinhaLote[]>(saldo > 0 ? [linhaVazia()] : []);
  const [exigir, setExigir] = useState(true);
  const [errosLocais, setErrosLocais] = useState<Record<string, string>>({});
  const erros = { ...(state.erros ?? {}), ...errosLocais };
  const errosDaGrade = Object.fromEntries(
    Object.entries(erros)
      .filter(([k]) => k.startsWith("lotes."))
      .map(([k, v]) => [k.slice("lotes.".length), v]),
  );

  function validar(e: React.FormEvent<HTMLFormElement>) {
    const dados = new FormData(e.currentTarget);
    dados.set("lotes", JSON.stringify(linhas));
    const r = regularizacaoSchema(saldo).safeParse(Object.fromEntries(dados.entries()));
    if (!r.success) {
      e.preventDefault();
      const mapa: Record<string, string> = {};
      for (const i of r.error.issues) mapa[i.path.join(".") || "_"] ??= i.message;
      setErrosLocais(mapa);
      return;
    }
    setErrosLocais({});
    const resumo = saldo > 0 ? `O saldo de ${saldo} unidade(s) será distribuído em ${linhas.length} lote(s).` : "O produto está sem saldo; os lotes entrarão nos próximos recebimentos.";
    if (!window.confirm(`Ligar o controle de lote de ${produto.nome}? ${resumo} Depois de ligado, o controle não pode ser desligado. A operação fica registrada na auditoria.`)) {
      e.preventDefault();
    }
  }

  return (
    <form action={action} onSubmit={validar} className="cartao space-y-5 p-4 sm:p-6" noValidate>
      <div>
        <h2 className="text-base font-semibold">Controlar por lote</h2>
        <p className="mt-1 text-sm text-muted">
          Cada entrada passa a ter código de lote e validade, e a venda usa primeiro o lote que vence antes. Use para ração, medicamento, vacina e alimento.
          Brinquedos e acessórios não precisam.
        </p>
      </div>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      <input type="hidden" name="lotes" value={JSON.stringify(linhas)} />

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label className="flex items-start gap-2 text-sm font-medium" htmlFor="campo-exigir-validade">
            <input
              id="campo-exigir-validade"
              type="checkbox"
              name="exigirValidade"
              checked={exigir}
              onChange={(e) => setExigir(e.target.checked)}
              className="mt-1"
              aria-describedby="ajuda-exigir-validade"
            />
            Exigir validade em todos os lotes
          </label>
          <p id="ajuda-exigir-validade" className="mt-1 text-xs text-muted">
            Recomendado. Sem isso, o sistema aceita lote sem data e ele só será vendido depois dos que têm validade.
          </p>
        </div>
        <Campo
          rotulo="Bloquear a venda quantos dias antes do vencimento?"
          name="diasBloqueioValidade"
          inputMode="numeric"
          placeholder="Vazio = margem padrão da loja"
          defaultValue={state.valores?.diasBloqueioValidade ?? ""}
          erro={erros.diasBloqueioValidade}
          ajuda="Opcional. Ex.: 30 para vacina. Lote vencido nunca é vendido, com ou sem margem."
        />
      </div>

      {saldo > 0 ? (
        <div className="space-y-2">
          <Aviso>
            O produto já tem <strong>{saldo}</strong> unidade(s) em estoque sem lote. Informe os lotes reais que estão na prateleira: a soma tem de ser exatamente {saldo}.
            Nenhum lote ou validade é inventado pelo sistema.
          </Aviso>
          <GradeLotes
            prefixo={`reg-${produto.id}`}
            linhas={linhas}
            onChange={setLinhas}
            esperado={saldo}
            rotuloEsperado="saldo atual"
            exigeValidade={exigir}
            erros={errosDaGrade}
          />
        </div>
      ) : (
        <p className="text-sm text-muted">Sem saldo no momento: o controle liga direto e os lotes entram no próximo recebimento.</p>
      )}

      <button type="submit" className="botao-primario" disabled={pending}>
        {pending ? "Ligando…" : "Ligar controle de lote"}
      </button>
    </form>
  );
}

// ------------------------------------------------------------------ lista e ações

function ListaDeLotes({ produto, lotes, podeGerenciar, hoje }: { produto: Produto; lotes: Lote[]; podeGerenciar: boolean; hoje: string }) {
  if (lotes.length === 0) {
    return <p className="cartao p-8 text-center text-sm text-muted">Nenhum lote ainda. Eles aparecem no próximo recebimento de compra.</p>;
  }
  return (
    <ul className="space-y-3" aria-label="Lotes do produto">
      {lotes.map((l) => (
        <li key={l.id} className="cartao space-y-3 p-4 sm:p-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="font-semibold">Lote {l.codigoLote}</p>
              <p className="text-sm text-muted">
                {l.validade ? `Validade ${formatarData(l.validade)} · ${textoPrazo(l.validade, hoje)}` : "Sem validade"}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <p className="text-2xl font-semibold tabular-nums" aria-label={`Saldo do lote ${l.codigoLote}`}>{l.saldo}</p>
              <BadgeLote lote={l} />
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-4 text-sm">
            <Link href={`/estoque/lotes/${l.id}`} className="font-medium text-brand hover:underline">Rastreabilidade →</Link>
            {l.compraId && <Link href={`/compras/${l.compraId}`} className="text-muted hover:text-foreground">Compra #{l.compraId}</Link>}
          </div>
          {podeGerenciar && <AcoesDoLote produto={produto} lote={l} />}
        </li>
      ))}
    </ul>
  );
}

function AcoesDoLote({ produto, lote }: { produto: Produto; lote: Lote }) {
  return (
    <details className="rounded-lg border border-border">
      <summary className="cursor-pointer px-3 py-2 text-sm font-medium">Ações do lote {lote.codigoLote}</summary>
      <div className="grid gap-5 border-t border-border p-3 md:grid-cols-2">
        {lote.status === "DISPONIVEL" ? (
          <FormAcaoLote
            produto={produto}
            lote={lote}
            acao="bloquear"
            titulo="Bloquear (quarentena)"
            botao="Bloquear lote"
            confirmacao={`Bloquear o lote ${lote.codigoLote}? Ele deixa de ser vendido até ser liberado. O saldo é mantido.`}
          />
        ) : (
          <FormAcaoLote
            produto={produto}
            lote={lote}
            acao="desbloquear"
            titulo="Liberar para venda"
            botao="Liberar lote"
            confirmacao={`Liberar o lote ${lote.codigoLote} para venda?`}
          />
        )}
        {lote.saldo > 0 && (
          <FormAcaoLote
            produto={produto}
            lote={lote}
            acao="perdas"
            titulo="Registrar perda ou descarte"
            botao="Registrar perda"
            comQuantidade
            confirmacao={`Registrar perda no lote ${lote.codigoLote}? O saldo diminui e a ação não pode ser desfeita.`}
          />
        )}
        {lote.saldo > 0 && lote.vencido && (
          <FormAcaoLote
            produto={produto}
            lote={lote}
            acao="vencimentos"
            titulo="Baixar lote vencido"
            botao="Baixar vencido"
            comQuantidade
            confirmacao={`Baixar o lote vencido ${lote.codigoLote}? O saldo diminui e a ação não pode ser desfeita.`}
          />
        )}
      </div>
    </details>
  );
}

function FormAcaoLote({
  produto,
  lote,
  acao,
  titulo,
  botao,
  confirmacao,
  comQuantidade = false,
}: {
  produto: Produto;
  lote: Lote;
  acao: AcaoLote;
  titulo: string;
  botao: string;
  confirmacao: string;
  comQuantidade?: boolean;
}) {
  const [state, action, pending] = useActionState<FormState, FormData>(acaoSobreLote.bind(null, produto.id, lote.id, acao), {});
  const id = `${acao}-${lote.id}`;
  return (
    <form
      action={action}
      onSubmit={(e) => {
        if (!window.confirm(confirmacao)) e.preventDefault();
      }}
      className="space-y-3"
      noValidate
    >
      <h3 className="text-sm font-semibold">{titulo}</h3>
      {state.mensagem && <Aviso tipo="erro">{state.mensagem}</Aviso>}
      {comQuantidade && (
        <Campo
          rotulo="Quantidade"
          name="quantidade"
          id={`${id}-quantidade`}
          inputMode="numeric"
          defaultValue={state.valores?.quantidade ?? ""}
          erro={state.erros?.quantidade}
          ajuda={`Até ${lote.saldo}.`}
          required
        />
      )}
      <Campo rotulo="Motivo" name="motivo" id={`${id}-motivo`} maxLength={500} defaultValue={state.valores?.motivo ?? ""} erro={state.erros?.motivo} required />
      <button type="submit" className={acao === "perdas" || acao === "vencimentos" ? "botao-perigo" : "botao-secundario"} disabled={pending}>
        {pending ? "Aguarde…" : botao}
      </button>
    </form>
  );
}
