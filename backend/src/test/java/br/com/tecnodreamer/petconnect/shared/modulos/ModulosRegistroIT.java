package br.com.tecnodreamer.petconnect.shared.modulos;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partida com todos os módulos (instalação existente): registro do histórico e da auditoria, capacidades, e a política de downgrade
 * (remover módulo nunca apaga dado e é bloqueado por pendência), com as consultas do catálogo executadas de verdade no MySQL.
 */
class ModulosRegistroIT extends ModulosIntegrationTest {

    @Autowired
    RegistroDeModulos registro;
    @Autowired
    ModulosAtivos modulos;
    @Autowired
    AuditoriaService auditoria;

    private final CatalogoModulos catalogo = CatalogoModulos.carregar();

    private Set<String> todos() {
        return new TreeSet<>(catalogo.idsDosModulos());
    }

    private Set<String> sem(String... removidos) {
        Set<String> r = todos();
        r.removeAll(List.of(removidos));
        return r;
    }

    @AfterEach
    void limparHistoricoSimulado() {
        // Mantém só a linha gravada pela partida real (a de menor id).
        jdbc.update("delete from modulo_instalacao_historico where id > (select min(id) from (select id from modulo_instalacao_historico) x)");
    }

    @Test
    void partidaRegistraAListaEfetivaNoHistoricoENaAuditoria() throws Exception {
        var linha = jdbc.queryForMap("select modulos, modo_todos, versao_catalogo from modulo_instalacao_historico order by id limit 1");
        assertThat(linha.get("modulos").toString().split(",")).containsExactlyInAnyOrderElementsOf(catalogo.idsDosModulos());
        assertThat(linha.get("modo_todos")).isIn(true, 1);
        assertThat(((Number) linha.get("versao_catalogo")).intValue()).isEqualTo(catalogo.versao());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auditoria")
                        .param("entidade", "instalacao").param("entidadeId", "modulos").with(bearer(admin())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].acao").value("MODULOS_ALTERADOS"));
    }

    @Test
    void capacidadesComTodosOsModulos() throws Exception {
        consultar("/api/v1/modulos", admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.todos").value(true))
                .andExpect(jsonPath("$.modulos.length()").value(12))
                .andExpect(jsonPath("$.integracoes[0]").value("vacina-estoque"))
                .andExpect(jsonPath("$.relatorios.length()").value(9))
                .andExpect(jsonPath("$.versaoCatalogo").value(1));
    }

    @Test
    void todasAsConsultasDeVerificacaoDoCatalogoSaoSqlValidoEExecutam() {
        int executadas = 0;
        for (var b : catalogo.bloqueios()) {
            if (b.verificacao().trim().toLowerCase().startsWith("select")) {
                Long n = jdbc.queryForObject(b.verificacao(), Long.class);
                assertThat(n).as("bloqueio de " + b.modulo()).isNotNull().isGreaterThanOrEqualTo(0);
                executadas++;
            }
        }
        for (var c : catalogo.condicionais()) {
            assertThat(c.verificacao()).as("condicional de " + c.modulo()).isNotBlank();
            assertThat(jdbc.queryForObject(c.verificacao(), Long.class)).isGreaterThanOrEqualTo(0);
            executadas++;
        }
        assertThat(executadas).as("consultas executadas").isGreaterThanOrEqualTo(8);
    }

    @Test
    void removerLotesComProdutoControladoEBloqueado() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "A:30:3");

        List<String> v = registro.violacoesDeRemocao(todos(), sem("lotes"));

        assertThat(v).anyMatch(m -> m.contains("'lotes'") && m.contains("controla lote"));
        // Nenhum dado foi apagado só por avaliar a remoção.
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ?", Integer.class, p)).isEqualTo(1);
    }

    @Test
    void removerCaixaComCaixaAbertoEPdvComVendaCancelavelSaoBloqueados() throws Exception {
        long p = produto("10.00", 0);
        semearSaldo(p, 2);
        admin(); // abre o caixa do ADMIN
        venderOk(admin(), vendaJson("PIX", "0.00", "null", p, 1));

        assertThat(registro.violacoesDeRemocao(todos(), sem("caixa"))).anyMatch(m -> m.contains("'caixa'"));
        assertThat(registro.violacoesDeRemocao(todos(), sem("pdv"))).anyMatch(m -> m.contains("'pdv'") && m.contains("cancelável"));
    }

    @Test
    void removerComprasComPedidoEmAbertoEFinanceiroComContaAbertaSaoBloqueados() throws Exception {
        long p = produto("10.00", 0);
        long fornecedor = criarFornecedor(unico("Fornecedor Módulos"));
        long compra = criarCompra(fornecedor, String.valueOf(p), "3:5.00");

        assertThat(registro.violacoesDeRemocao(todos(), sem("compras"))).anyMatch(m -> m.contains("'compras'") && m.contains("pedido"));

        receber(compra, hoje()).andExpect(status().isOk()); // cria a conta a pagar ABERTA
        assertThat(registro.violacoesDeRemocao(todos(), sem("financeiro"))).anyMatch(m -> m.contains("'financeiro'") && m.contains("contas a pagar"));
    }

    @Test
    void removerClinicaOuTerAgendaSemClinicaComServicoDeVacinacaoAtivoEBloqueado() {
        jdbc.update("insert ignore into servico (nome, preco, duracao_minutos) values ('Vacinação', 80.00, 15)");
        jdbc.update("update servico set ativo = true where nome = 'Vacinação'");

        assertThat(registro.violacoesDeRemocao(todos(), sem("clinica"))).anyMatch(m -> m.contains("'clinica'") && m.contains("vacinação"));
        // E a lista atual sem clínica, mas com agenda, viola a dependência condicional.
        assertThat(registro.violacoesCondicionais(sem("clinica"))).anyMatch(m -> m.contains("'agenda'") && m.contains("'clinica'"));
        // Sem agenda, a condicional não se aplica.
        assertThat(registro.violacoesCondicionais(sem("clinica", "agenda"))).isEmpty();
    }

    @Test
    void removerModuloSemPendenciaOuSemRemoverNadaNaoViolaNada() {
        assertThat(registro.violacoesDeRemocao(todos(), todos())).isEmpty();
        assertThat(registro.violacoesDeRemocao(null, sem("lotes"))).as("primeira partida").isEmpty();
        assertThat(registro.violacoesDeRemocao(todos(), sem("relatorios"))).as("relatórios não guarda estado").isEmpty();
        assertThat(registro.violacoesDeRemocao(sem("lotes"), todos())).as("adicionar módulo").isEmpty();
    }

    @Test
    void partidaComRemocaoProibidaNaoSobeENaoRegistraNada() throws Exception {
        long p = produto("10.00", 0);
        semearLotes(p, "A:30:3");
        // Lista anterior: todos os módulos. Atual (válida em si): sem lotes nem tudo que depende dela.
        jdbc.update("insert into modulo_instalacao_historico (modulos, modo_todos, versao_catalogo) values (?, true, 1)", String.join(",", todos()));
        long linhasAntes = jdbc.queryForObject("select count(*) from modulo_instalacao_historico", Long.class);
        ModulosAtivos reduzido = new ModulosAtivos("produtos,estoque,caixa,pdv", catalogo);
        RegistroDeModulos naPartida = new RegistroDeModulos(jdbc, reduzido, auditoria);

        assertThatThrownBy(() -> naPartida.run(new DefaultApplicationArguments()))
                .isInstanceOf(ModulosInvalidosException.class)
                .hasMessageContaining("Configuração de módulos recusada")
                .hasMessageContaining("'lotes'")
                .hasMessageContaining("Nenhum dado foi alterado");

        assertThat(jdbc.queryForObject("select count(*) from modulo_instalacao_historico", Long.class)).as("nada registrado").isEqualTo(linhasAntes);
        assertThat(jdbc.queryForObject("select count(*) from lote_estoque where produto_id = ?", Integer.class, p)).isEqualTo(1);
    }

    @Test
    void partidaComListaValidaSemPendenciaRegistraAMudanca() {
        jdbc.update("insert into modulo_instalacao_historico (modulos, modo_todos, versao_catalogo) values (?, true, 1)", String.join(",", todos()));
        // Sem pendência de relatórios: remover é permitido e fica registrado.
        ModulosAtivos semRelatorios = new ModulosAtivos(String.join(",", sem("relatorios")), catalogo);
        new RegistroDeModulos(jdbc, semRelatorios, auditoria).run(new DefaultApplicationArguments());

        List<String> ultima = jdbc.queryForList("select modulos from modulo_instalacao_historico order by id desc limit 1", String.class);
        assertThat(new ArrayList<>(List.of(ultima.get(0).split(",")))).doesNotContain("relatorios").contains("pdv");
        assertThat(jdbc.queryForObject("select modo_todos from modulo_instalacao_historico order by id desc limit 1", Boolean.class)).isFalse();
    }
}
