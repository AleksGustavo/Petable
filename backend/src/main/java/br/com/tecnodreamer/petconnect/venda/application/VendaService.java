package br.com.tecnodreamer.petconnect.venda.application;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.cliente.infrastructure.ClienteRepository;
import br.com.tecnodreamer.petconnect.financeiro.application.CaixaService;
import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.financeiro.domain.Caixa;
import br.com.tecnodreamer.petconnect.financeiro.domain.Lancamento;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.produto.application.EstoqueService;
import br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import br.com.tecnodreamer.petconnect.venda.domain.FormaPagamento;
import br.com.tecnodreamer.petconnect.venda.domain.ItemVenda;
import br.com.tecnodreamer.petconnect.venda.domain.StatusVenda;
import br.com.tecnodreamer.petconnect.venda.domain.Venda;
import br.com.tecnodreamer.petconnect.venda.infrastructure.VendaRepository;
import br.com.tecnodreamer.petconnect.venda.web.PreviaVendaResponse;
import br.com.tecnodreamer.petconnect.venda.web.ResumoDiaResponse;
import br.com.tecnodreamer.petconnect.venda.web.VendaRequest;
import br.com.tecnodreamer.petconnect.venda.web.VendaResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PDV (etapa 05). Preço, desconto, total e troco são sempre calculados aqui; a
 * baixa de estoque passa pelo {@link EstoqueService} com os produtos travados em
 * ordem crescente de id (mesma ordem do recebimento de compra: sem deadlock).
 */
@Service
@EnableConfigurationProperties(VendasProperties.class)
public class VendaService {

    static final String ENTIDADE = "venda";
    static final int PRAZO_CANCELAMENTO_DIAS = 7;
    /** Limite da coluna valor_total DECIMAL(10,2). */
    static final BigDecimal TOTAL_MAXIMO = new BigDecimal("99999999.99");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final VendaRepository vendas;
    private final ProdutoRepository produtos;
    private final ClienteRepository clientes;
    private final FuncionarioRepository funcionarios;
    private final UsuarioRepository usuarios;
    private final EstoqueService estoque;
    private final AuditoriaService auditoria;
    private final VendasProperties propriedades;
    private final CaixaService caixas;
    private final FinanceiroService financeiro;
    private final Clock clock;

    public VendaService(VendaRepository vendas, ProdutoRepository produtos, ClienteRepository clientes,
            FuncionarioRepository funcionarios, UsuarioRepository usuarios, EstoqueService estoque,
            AuditoriaService auditoria, VendasProperties propriedades, CaixaService caixas, FinanceiroService financeiro,
            Clock clock) {
        this.vendas = vendas;
        this.produtos = produtos;
        this.clientes = clientes;
        this.funcionarios = funcionarios;
        this.usuarios = usuarios;
        this.estoque = estoque;
        this.auditoria = auditoria;
        this.propriedades = propriedades;
        this.caixas = caixas;
        this.financeiro = financeiro;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Page<VendaResponse> listar(StatusVenda status, FormaPagamento forma, Long clienteId, LocalDate inicio,
            LocalDate fim, Pageable pageable) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new CampoInvalidoException("dataFim", "A data final não pode ser anterior à inicial.");
        }
        return vendas.buscar(status, forma, clienteId, inicio == null ? null : inicio.atStartOfDay(),
                fim == null ? null : fim.plusDays(1).atStartOfDay(), pageable).map(VendaResponse::resumo);
    }

    @Transactional(readOnly = true)
    public VendaResponse buscar(Long id) {
        return comLancamento(vendas.findWithItensById(id).orElseThrow(() -> new NotFoundException("Venda", id)));
    }

    /** Resposta com o id da ENTRADA financeira da venda (nulo em vendas anteriores à V8 sem backfill). */
    private VendaResponse comLancamento(Venda v) {
        return VendaResponse.of(v).comLancamento(financeiro.porChave("VENDA:" + v.getId()).map(Lancamento::getId).orElse(null));
    }

    @Transactional(readOnly = true)
    public ResumoDiaResponse resumoDia(LocalDate data) {
        LocalDate dia = data == null ? LocalDate.now(clock) : data;
        VendaRepository.Resumo r = vendas.resumir(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
        long quantidade = r == null || r.getQuantidade() == null ? 0 : r.getQuantidade();
        BigDecimal total = r == null || r.getTotal() == null ? BigDecimal.ZERO.setScale(2) : r.getTotal();
        return new ResumoDiaResponse(dia, quantidade, total);
    }

    /** Venda já gravada com esta chave (replay idempotente), se houver. */
    @Transactional(readOnly = true)
    public java.util.Optional<Venda> porChave(String chave) {
        return vendas.findByChaveIdempotencia(chave).map(v -> {
            v.getItens().size();
            return v;
        });
    }

    // ------------------------------------------------------------------ prévia

    /** Mesmos cálculos e validações de campo da venda, sem gravar nem travar; falta de estoque não é erro aqui. */
    @Transactional(readOnly = true)
    public PreviaVendaResponse previa(VendaRequest req, Long usuarioId) {
        Usuario operador = usuario(usuarioId);
        Map<Long, Produto> porId = produtosAtivos(req, produtos.findAllById(idsValidados(req)));
        List<PreviaVendaResponse.Item> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        boolean suficiente = true;
        for (VendaRequest.Item item : req.itens()) {
            Produto p = porId.get(item.produtoId());
            BigDecimal preco = p.getPrecoVenda().setScale(2, RoundingMode.HALF_UP);
            BigDecimal sub = preco.multiply(BigDecimal.valueOf(item.quantidade())).setScale(2, RoundingMode.HALF_UP);
            boolean ok = p.getQuantidadeEstoque() >= item.quantidade();
            suficiente &= ok;
            subtotal = subtotal.add(sub);
            itens.add(new PreviaVendaResponse.Item(p.getId(), p.getNome(), preco, item.quantidade(), sub,
                    p.getQuantidadeEstoque(), ok));
        }
        cliente(req.clienteId());
        BigDecimal desconto = desconto(req.desconto(), subtotal, operador);
        BigDecimal total = subtotal.subtract(desconto);
        validarTotal(total);
        BigDecimal recebido = valorRecebido(req.formaPagamento(), req.valorRecebido(), total);
        return new PreviaVendaResponse(itens, subtotal, desconto, total, recebido == null ? null : recebido.subtract(total),
                suficiente);
    }

    // ------------------------------------------------------------------ registrar

    /**
     * Registra a venda numa transação única (DA-04/05/06). A mesma chave do mesmo
     * operador devolve a venda existente sem nova baixa; em corrida, a UNIQUE da V7
     * barra a segunda gravação e {@link RegistroVendaIdempotente} devolve a primeira.
     */
    @Transactional
    public ResultadoVenda registrar(VendaRequest req, String chave, Long usuarioId) {
        String chaveValida = chave(chave);
        var existente = vendas.findByChaveIdempotencia(chaveValida);
        if (existente.isPresent()) {
            return new ResultadoVenda(comLancamento(daMesmaPessoa(existente.get(), usuarioId)), true);
        }
        // DA-03: sem caixa aberto, 409 antes de travar qualquer produto (nada de venda, estoque ou financeiro).
        caixas.exigirAberto(usuarioId);
        Usuario operador = usuario(usuarioId);
        Set<Long> ids = idsValidados(req);
        Map<Long, Produto> travados = produtosAtivos(req, produtos.travarEmOrdem(ids));
        // Estoque conferido com os produtos travados, em ordem de id: nada é gravado se faltar (DA-05).
        List<VendaRequest.Item> emOrdem = req.itens().stream()
                .sorted(Comparator.comparing(VendaRequest.Item::produtoId)).toList();
        for (VendaRequest.Item item : emOrdem) {
            Produto p = travados.get(item.produtoId());
            if (p.getQuantidadeEstoque() < item.quantidade()) {
                throw new EstoqueInsuficienteException(p, item.quantidade());
            }
        }
        Cliente cliente = cliente(req.clienteId());
        Funcionario funcionario = funcionarios.findByUsuarioId(usuarioId).orElse(null);
        Venda venda = new Venda(cliente, funcionario, operador, LocalDateTime.now(clock), req.formaPagamento(),
                limpo(req.observacoes()), chaveValida);
        for (VendaRequest.Item item : req.itens()) {
            venda.adicionarItem(travados.get(item.produtoId()), item.quantidade());
        }
        BigDecimal desconto = desconto(req.desconto(), venda.subtotal(), operador);
        BigDecimal total = venda.subtotal().subtract(desconto);
        validarTotal(total);
        venda.fechar(desconto, valorRecebido(req.formaPagamento(), req.valorRecebido(), total));
        // Caixa travado DEPOIS dos produtos (mesma ordem em venda, cancelamento e pagamento).
        Caixa caixa = caixas.travarAberto(usuarioId);
        venda.registrarNoCaixa(caixa);
        Venda salva = vendas.saveAndFlush(venda);

        for (ItemVenda item : salva.getItens().stream()
                .sorted(Comparator.comparing((ItemVenda i) -> i.getProduto().getId())).toList()) {
            estoque.registrarSaidaVenda(travados.get(item.getProduto().getId()), item.getQuantidade(), salva, usuarioId);
        }
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("vendaId", salva.getId());
        detalhes.put("quantidadeItens", salva.getItens().size());
        detalhes.put("valorTotal", salva.getValorTotal());
        detalhes.put("desconto", salva.getDesconto());
        detalhes.put("formaPagamento", salva.getFormaPagamento().name());
        detalhes.put("clienteId", cliente == null ? null : cliente.getId());
        auditoria.registrar("VENDA_REALIZADA", ENTIDADE, salva.getId(), detalhes);
        // DA-05: mesma transação — se o lançamento falhar, venda e baixa de estoque são desfeitas.
        Lancamento entrada = financeiro.registrarVenda(salva, caixa, usuarioId);
        return new ResultadoVenda(VendaResponse.of(salva).comLancamento(entrada.getId()), false);
    }

    // ------------------------------------------------------------------ cancelar

    /** Só CONCLUIDA, até 7 dias; estorna cada item com ESTORNO_VENDA (inclusive produto desativado). */
    @Transactional
    public VendaResponse cancelar(Long id, String motivo, Long usuarioId) {
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5 || texto.length() > 500) {
            throw new CampoInvalidoException("motivo", "O motivo deve ter entre 5 e 500 caracteres.");
        }
        Venda venda = vendas.travar(id).orElseThrow(() -> new NotFoundException("Venda", id));
        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new ConflictException("A venda já está cancelada.");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        if (venda.getDataVenda().plusDays(PRAZO_CANCELAMENTO_DIAS).isBefore(agora)) {
            throw new ConflictException("O prazo para cancelar esta venda expirou.");
        }
        // DA-07: o estorno financeiro entra no caixa aberto de quem cancela; sem caixa, nada muda.
        caixas.exigirAberto(usuarioId);
        List<ItemVenda> itens = venda.getItens();
        List<Long> ids = itens.stream().map(i -> i.getProduto().getId()).sorted().toList();
        Map<Long, Produto> travados = produtos.travarEmOrdem(ids).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        for (ItemVenda item : itens.stream().sorted(Comparator.comparing((ItemVenda i) -> i.getProduto().getId())).toList()) {
            estoque.registrarEstornoVenda(travados.get(item.getProduto().getId()), item.getQuantidade(), venda, usuarioId);
        }
        venda.cancelar(usuario(usuarioId), texto, agora);
        Venda salva = vendas.saveAndFlush(venda);
        Caixa caixa = caixas.travarAberto(usuarioId);
        financeiro.registrarEstornoVenda(salva, caixa, usuarioId, texto);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("vendaId", id);
        detalhes.put("motivo", texto);
        detalhes.put("valorTotal", salva.getValorTotal());
        auditoria.registrar("VENDA_CANCELADA", ENTIDADE, id, detalhes);
        return comLancamento(salva);
    }

    // ------------------------------------------------------------------ regras

    static String chave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new CampoInvalidoException("idempotencyKey", "Envie o cabeçalho Idempotency-Key.");
        }
        try {
            return UUID.fromString(chave.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new CampoInvalidoException("idempotencyKey", "Idempotency-Key deve ser um UUID.");
        }
    }

    static Venda daMesmaPessoa(Venda venda, Long usuarioId) {
        if (venda.getUsuario() == null || !venda.getUsuario().getId().equals(usuarioId)) {
            throw new ConflictException("Chave de idempotência já utilizada.");
        }
        return venda;
    }

    private static Set<Long> idsValidados(VendaRequest req) {
        if (req.itens() == null || req.itens().isEmpty()) {
            throw new CampoInvalidoException("itens", "Adicione pelo menos um item.");
        }
        Set<Long> ids = new HashSet<>();
        for (VendaRequest.Item item : req.itens()) {
            if (!ids.add(item.produtoId())) {
                throw new CampoInvalidoException("itens", "O mesmo produto aparece mais de uma vez na venda.");
            }
        }
        return ids;
    }

    /** Todos os produtos existem e estão ativos (DA-10); erro aponta a linha do item. */
    private static Map<Long, Produto> produtosAtivos(VendaRequest req, List<Produto> encontrados) {
        Map<Long, Produto> porId = encontrados.stream().collect(Collectors.toMap(Produto::getId, Function.identity()));
        for (int i = 0; i < req.itens().size(); i++) {
            Produto p = porId.get(req.itens().get(i).produtoId());
            if (p == null || !p.isAtivo()) {
                throw new CampoInvalidoException("itens[" + i + "].produtoId",
                        "Produto " + req.itens().get(i).produtoId() + " não encontrado ou inativo.");
            }
        }
        return porId;
    }

    /** Desconto ≥ 0, menor que o subtotal; VENDAS tem teto percentual configurável; ADMIN não (DA-03). */
    BigDecimal desconto(BigDecimal informado, BigDecimal subtotal, Usuario operador) {
        BigDecimal desconto = informado == null ? BigDecimal.ZERO.setScale(2) : informado.setScale(2, RoundingMode.HALF_UP);
        if (desconto.signum() < 0) {
            throw new CampoInvalidoException("desconto", "O desconto não pode ser negativo.");
        }
        if (desconto.compareTo(subtotal) >= 0) {
            throw new CampoInvalidoException("desconto", "O desconto deve ser menor que o subtotal.");
        }
        if (!operador.isAdmin()) {
            BigDecimal teto = propriedades.descontoMaximoPercentualVendas();
            BigDecimal maximo = subtotal.multiply(teto).divide(CEM, 2, RoundingMode.HALF_UP);
            if (desconto.compareTo(maximo) > 0) {
                throw new CampoInvalidoException("desconto", "Desconto acima do permitido para o seu perfil (máximo "
                        + teto.stripTrailingZeros().toPlainString() + "%).");
            }
        }
        return desconto;
    }

    private static void validarTotal(BigDecimal total) {
        if (total.compareTo(TOTAL_MAXIMO) > 0) {
            throw new CampoInvalidoException("itens", "O total da venda excede o limite permitido.");
        }
    }

    /** Dinheiro exige valor recebido ≥ total; nas demais formas o valor é ignorado (DA-09). */
    private static BigDecimal valorRecebido(FormaPagamento forma, BigDecimal informado, BigDecimal total) {
        if (forma != FormaPagamento.DINHEIRO) {
            return null;
        }
        if (informado == null) {
            throw new CampoInvalidoException("valorRecebido", "Informe o valor recebido.");
        }
        BigDecimal recebido = informado.setScale(2, RoundingMode.HALF_UP);
        if (recebido.compareTo(total) < 0) {
            throw new CampoInvalidoException("valorRecebido", "O valor recebido não pode ser menor que o total.");
        }
        return recebido;
    }

    private Cliente cliente(Long clienteId) {
        if (clienteId == null) {
            return null;
        }
        return clientes.findById(clienteId).filter(Cliente::isAtivo)
                .orElseThrow(() -> new CampoInvalidoException("clienteId", "Cliente não encontrado ou inativo."));
    }

    private Usuario usuario(Long usuarioId) {
        return usuarios.findById(usuarioId).orElseThrow(() -> new NotFoundException("Usuário", usuarioId));
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
