package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import br.com.tecnodreamer.petconnect.financeiro.domain.CategoriaLancamento;
import br.com.tecnodreamer.petconnect.financeiro.domain.TipoLancamento;
import br.com.tecnodreamer.petconnect.funcionario.domain.Sexo;
import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;

import java.util.Map;
import java.util.Optional;

/**
 * Tabelas de conversão explícitas (§8). Valor desconhecido nunca é adivinhado em
 * silêncio: vira o padrão documentado + código de pendência.
 */
public final class ConversorLegado {

    private static final Map<String, Sexo> SEXOS = Map.of(
            "masculino", Sexo.MASCULINO, "m", Sexo.MASCULINO,
            "feminino", Sexo.FEMININO, "f", Sexo.FEMININO,
            "outro", Sexo.OUTRO, "outros", Sexo.OUTRO, "o", Sexo.OUTRO);

    private static final Map<String, Porte> PORTES = Map.of(
            "pequeno", Porte.PEQUENO, "medio", Porte.MEDIO, "grande", Porte.GRANDE);

    private static final Map<String, StatusAgendamento> STATUS_AGENDAMENTO = Map.of(
            "agendado", StatusAgendamento.AGENDADO,
            "confirmado", StatusAgendamento.CONFIRMADO,
            "em andamento", StatusAgendamento.EM_ANDAMENTO,
            "concluido", StatusAgendamento.CONCLUIDO,
            "cancelado", StatusAgendamento.CANCELADO,
            "nao compareceu", StatusAgendamento.NAO_COMPARECEU);

    private static final Map<String, StatusCompra> STATUS_COMPRA = Map.of(
            "pedido", StatusCompra.PEDIDO, "recebido", StatusCompra.RECEBIDO, "cancelado", StatusCompra.CANCELADO);

    private static final Map<String, FormaPagamento> FORMAS = Map.ofEntries(
            Map.entry("dinheiro", FormaPagamento.DINHEIRO),
            Map.entry("cartao credito", FormaPagamento.CARTAO_CREDITO),
            Map.entry("cartao de credito", FormaPagamento.CARTAO_CREDITO),
            Map.entry("credito", FormaPagamento.CARTAO_CREDITO),
            Map.entry("cartao debito", FormaPagamento.CARTAO_DEBITO),
            Map.entry("cartao de debito", FormaPagamento.CARTAO_DEBITO),
            Map.entry("debito", FormaPagamento.CARTAO_DEBITO),
            Map.entry("pix", FormaPagamento.PIX),
            Map.entry("transferencia", FormaPagamento.TRANSFERENCIA));

    private static final Map<String, CategoriaLancamento> CATEGORIAS = Map.of(
            "venda produto", CategoriaLancamento.VENDA_PRODUTO,
            "servico", CategoriaLancamento.SERVICO,
            "despesa funcionario", CategoriaLancamento.DESPESA_FUNCIONARIO,
            "despesa operacional", CategoriaLancamento.DESPESA_OPERACIONAL,
            "compra mercadoria", CategoriaLancamento.COMPRA_MERCADORIA,
            "outros", CategoriaLancamento.OUTROS);

    private ConversorLegado() {
    }

    public static Conversao<Sexo> sexo(String legado) {
        return conhecido(SEXOS, legado, Sexo.OUTRO, "SEXO_DESCONHECIDO");
    }

    /** Porte é opcional no pet: nulo continua nulo. */
    public static Conversao<Porte> porte(String legado) {
        if (TextoNormalizado.enumeracao(legado) == null) {
            return Conversao.ok(null);
        }
        return conhecido(PORTES, legado, Porte.MEDIO, "PORTE_DESCONHECIDO");
    }

    public static Conversao<StatusAgendamento> statusAgendamento(String legado) {
        return conhecido(STATUS_AGENDAMENTO, legado, StatusAgendamento.CANCELADO, "STATUS_AGENDAMENTO_DESCONHECIDO");
    }

    /** Desconhecido vira CANCELADO: uma compra de status incerto não pode parecer pedido em aberto. */
    public static Conversao<StatusCompra> statusCompra(String legado) {
        return conhecido(STATUS_COMPRA, legado, StatusCompra.CANCELADO, "STATUS_COMPRA_DESCONHECIDO");
    }

    public static Conversao<FormaPagamento> formaPagamento(String legado) {
        return conhecido(FORMAS, legado, FormaPagamento.TRANSFERENCIA, "FORMA_PAGAMENTO_DESCONHECIDA");
    }

    /** Tipo desconhecido não tem padrão seguro: o registro é ignorado. */
    public static Optional<TipoLancamento> tipoFinanceiro(String legado) {
        String s = TextoNormalizado.enumeracao(legado);
        if ("entrada".equals(s)) {
            return Optional.of(TipoLancamento.ENTRADA);
        }
        if ("saida".equals(s)) {
            return Optional.of(TipoLancamento.SAIDA);
        }
        return Optional.empty();
    }

    public static Conversao<CategoriaLancamento> categoriaFinanceira(String legado) {
        return conhecido(CATEGORIAS, legado, CategoriaLancamento.OUTROS, "CATEGORIA_FINANCEIRA_DESCONHECIDA");
    }

    /**
     * 1/true/sim/s → true; 0/false/nao/n → false. Nulo usa o padrão da coluna legada
     * sem pendência; texto desconhecido → false + pendência.
     */
    public static Conversao<Boolean> booleano(Object legado, boolean padraoSeNulo) {
        if (legado == null) {
            return Conversao.ok(padraoSeNulo);
        }
        if (legado instanceof Boolean b) {
            return Conversao.ok(b);
        }
        if (legado instanceof Number n) {
            if (n.intValue() == 1 || n.intValue() == 0) {
                return Conversao.ok(n.intValue() == 1);
            }
            return Conversao.comPendencia(false, "BOOLEANO_DESCONHECIDO");
        }
        String s = TextoNormalizado.enumeracao(legado.toString());
        if (s == null) {
            return Conversao.ok(padraoSeNulo);
        }
        return switch (s) {
            case "1", "true", "sim", "s" -> Conversao.ok(true);
            case "0", "false", "nao", "n" -> Conversao.ok(false);
            default -> Conversao.comPendencia(false, "BOOLEANO_DESCONHECIDO");
        };
    }

    /** Papéis do legado (SuperAdmin, FuncionarioVendas, FuncionarioServico) → nome do papel novo. */
    public static Conversao<String> papel(String legado) {
        String s = TextoNormalizado.comparacao(legado);
        if (s == null) {
            return Conversao.comPendencia("VENDAS", "PAPEL_DESCONHECIDO");
        }
        String compacto = s.replace(" ", "").replace("_", "");
        if (compacto.contains("admin") || compacto.contains("super")) {
            return Conversao.ok("ADMIN");
        }
        if (compacto.contains("servic")) {
            return Conversao.ok("SERVICO");
        }
        if (compacto.contains("venda")) {
            return Conversao.ok("VENDAS");
        }
        return Conversao.comPendencia("VENDAS", "PAPEL_DESCONHECIDO");
    }

    private static <T> Conversao<T> conhecido(Map<String, T> tabela, String legado, T padrao, String pendencia) {
        String chave = TextoNormalizado.enumeracao(legado);
        T valor = chave == null ? null : tabela.get(chave);
        return valor != null ? Conversao.ok(valor) : Conversao.comPendencia(padrao, pendencia);
    }
}
