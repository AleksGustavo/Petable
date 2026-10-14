package br.com.tecnodreamer.petconnect.produto.application;

import br.com.tecnodreamer.petconnect.fornecedor.infrastructure.FornecedorRepository;
import br.com.tecnodreamer.petconnect.produto.domain.Produto;
import br.com.tecnodreamer.petconnect.produto.infrastructure.CategoriaProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.infrastructure.ProdutoRepository;
import br.com.tecnodreamer.petconnect.produto.web.ProdutoAtualizacaoRequest;
import br.com.tecnodreamer.petconnect.produto.web.ProdutoRequest;
import br.com.tecnodreamer.petconnect.produto.web.ProdutoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.categoria;
import static br.com.tecnodreamer.petconnect.produto.EstoqueFixtures.produto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProdutoServiceTest {

    @Mock ProdutoRepository produtos;
    @Mock CategoriaProdutoRepository categorias;
    @Mock FornecedorRepository fornecedores;
    @Mock AuditoriaService auditoria;

    ProdutoService service;
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        service = new ProdutoService(produtos, categorias, fornecedores, auditoria, org.mockito.Mockito.mock(EstoqueLoteService.class));
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria(1, "Ração")));
        when(produtos.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProdutoRequest req(String codigo, String venda, Integer minimo) {
        return new ProdutoRequest(" Ração Premium 10kg ", "Seca", 1L, null, codigo, new BigDecimal("120.50"),
                venda == null ? null : new BigDecimal(venda), minimo);
    }

    private java.util.Set<String> camposInvalidos(Object req) {
        return validator.validate(req).stream().map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void deveCriarProdutoComEstoqueZero() {
        ProdutoResponse r = service.criar(req("7891234567890", "169.90", 5));

        assertThat(r.quantidadeEstoque()).isZero();
        assertThat(r.ativo()).isTrue();
        assertThat(r.nome()).isEqualTo("Ração Premium 10kg");
        assertThat(r.precoCusto()).isEqualByComparingTo("120.50");
        verify(auditoria).registrar(eq("PRODUTO_CRIADO"), eq("produto"), any(), anyMap());
    }

    @Test
    void deveCriarProdutoSemCodigoDeBarras() {
        ProdutoResponse r = service.criar(req(null, "10.00", 5));

        assertThat(r.codigoBarras()).isNull();
        verify(produtos, never()).existsByCodigoBarras(anyString());
    }

    @Test
    void deveRejeitarCodigoDeBarrasDuplicado() {
        when(produtos.existsByCodigoBarras("7891234567890")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(req("7891234567890", "10.00", 5))).isInstanceOf(ConflictException.class);
        verify(produtos, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarCodigoDeBarrasInvalido() {
        assertThat(camposInvalidos(req("1234567", "10.00", 5))).contains("codigoBarras");
        assertThat(camposInvalidos(req("78912345678901234", "10.00", 5))).contains("codigoBarras");
        assertThat(camposInvalidos(req("ABC12345", "10.00", 5))).contains("codigoBarras");
        assertThat(camposInvalidos(req("12345678", "10.00", 5))).isEmpty();
    }

    @Test
    void deveRejeitarPrecoVendaZero() {
        assertThat(camposInvalidos(req(null, "0", 5))).contains("precoVenda");
    }

    @Test
    void deveRejeitarEstoqueMinimoNegativo() {
        assertThat(camposInvalidos(req(null, "10.00", -1))).contains("estoqueMinimo");
    }

    @Test
    void deveAtualizarDadosPermitidos() {
        Produto p = produto(10, 7, "20.00", 5);
        when(produtos.findWithDetalhesById(10L)).thenReturn(Optional.of(p));

        service.atualizar(10L, new ProdutoAtualizacaoRequest("Novo nome", null, 1L, null, "12345678", new BigDecimal("30.00"), 2));

        assertThat(p.getNome()).isEqualTo("Novo nome");
        assertThat(p.getPrecoVenda()).isEqualByComparingTo("30.00");
        assertThat(p.getEstoqueMinimo()).isEqualTo(2);
        assertThat(p.getQuantidadeEstoque()).isEqualTo(7);
        assertThat(p.getPrecoCusto()).isEqualByComparingTo("20.00");
        verify(auditoria).registrar(eq("PRODUTO_ATUALIZADO"), eq("produto"), eq(10L), anyMap());
    }

    @Test
    void deveDesativarProdutoPreservandoEstoque() {
        Produto p = produto(10, 7, "20.00", 5);
        when(produtos.findWithDetalhesById(10L)).thenReturn(Optional.of(p));

        service.desativar(10L);

        assertThat(p.isAtivo()).isFalse();
        assertThat(p.getQuantidadeEstoque()).isEqualTo(7);
        verify(auditoria).registrar(eq("PRODUTO_DESATIVADO"), eq("produto"), eq(10L), argThat(d -> Integer.valueOf(7).equals(d.get("saldo"))));
    }

    @Test
    void deveReativarProduto() {
        Produto p = produto(10, 0, null, 5);
        p.desativar();
        when(produtos.findWithDetalhesById(10L)).thenReturn(Optional.of(p));

        service.ativar(10L);

        assertThat(p.isAtivo()).isTrue();
        verify(auditoria).registrar(eq("PRODUTO_ATIVADO"), eq("produto"), eq(10L), anyMap());
    }

    @Test
    void deveIdentificarProdutoAbaixoDoMinimo() {
        assertThat(produto(1, 2, null, 5).isAbaixoDoMinimo()).isTrue();
        assertThat(produto(1, 6, null, 5).isAbaixoDoMinimo()).isFalse();
    }

    @Test
    void deveConsiderarIgualAoMinimoComoAbaixoDoMinimo() {
        assertThat(produto(1, 5, null, 5).isAbaixoDoMinimo()).isTrue();
        assertThat(produto(1, 0, null, 0).isAbaixoDoMinimo()).isTrue();
    }
}
