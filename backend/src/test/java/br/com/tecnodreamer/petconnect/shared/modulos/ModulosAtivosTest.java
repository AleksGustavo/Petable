package br.com.tecnodreamer.petconnect.shared.modulos;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Configuração de módulos (etapa 07): o que sobe, o que recusa o boot e como cada rota é decidida. */
class ModulosAtivosTest {

    private final CatalogoModulos catalogo = CatalogoModulos.carregar();

    private ModulosAtivos de(String configurado) {
        return new ModulosAtivos(configurado, catalogo);
    }

    @Test
    void todosLigaTodosOsModulosExistentes() {
        ModulosAtivos m = de("todos");
        assertThat(m.modoTodos()).isTrue();
        assertThat(m.ativos()).containsExactlyInAnyOrderElementsOf(catalogo.idsDosModulos());
        assertThat(de("  TODOS ").modoTodos()).isTrue();
    }

    @Test
    void listaExplicitaLigaSoOQueFoiContratado() {
        ModulosAtivos m = de("pdv, caixa;produtos\nestoque");
        assertThat(m.modoTodos()).isFalse();
        assertThat(m.ativos()).containsExactly("caixa", "estoque", "pdv", "produtos");
        assertThat(m.ativo("financeiro")).isFalse();
        assertThat(m.ativo("pdv")).isTrue();
    }

    @Test
    void maiusculasEEspacosNaoImportamENucleoEImplicito() {
        assertThat(de(" PDV , Caixa ,PRODUTOS , nucleo ").ativos()).containsExactly("caixa", "pdv", "produtos");
    }

    @Test
    void valorVazioOuSoEspacosRecusaOBoot() {
        assertThatThrownBy(() -> de("")).isInstanceOf(ModulosInvalidosException.class).hasMessageContaining("PETCONNECT_MODULOS está vazio");
        assertThatThrownBy(() -> de("   ")).isInstanceOf(ModulosInvalidosException.class);
        assertThatThrownBy(() -> de(null)).isInstanceOf(ModulosInvalidosException.class);
    }

    @Test
    void idDesconhecidoRecusaOBootEListaOsExistentes() {
        assertThatThrownBy(() -> de("pdv,caixa,produtos,financas"))
                .isInstanceOf(ModulosInvalidosException.class)
                .hasMessageContaining("desconhecido(s): financas")
                .hasMessageContaining("existentes:");
    }

    @Test
    void dependenciaFaltandoRecusaOBootComMensagemQueExplica() {
        assertThatThrownBy(() -> de("pdv"))
                .isInstanceOf(ModulosInvalidosException.class)
                .hasMessageContaining("o módulo 'pdv' exige 'produtos'")
                .hasMessageContaining("o módulo 'pdv' exige 'caixa'");
        assertThatThrownBy(() -> de("lotes,produtos")).hasMessageContaining("o módulo 'lotes' exige 'estoque'");
        assertThat(de("agenda,pets,clientes,caixa,produtos").ativos()).contains("agenda");
        assertThatThrownBy(() -> de("clinica")).hasMessageContaining("exige 'pets'");
    }

    @Test
    void todosNaoPodeSerMisturado() {
        assertThatThrownBy(() -> de("todos,pdv")).isInstanceOf(ModulosInvalidosException.class).hasMessageContaining("não pode ser misturado");
    }

    @Test
    void naoExisteCicloNemPacoteDoCatalogoQueNaoSubaComSuasDependencias() {
        // Cada pacote do catálogo é uma configuração válida de verdade.
        for (String pacote : new String[] {"produtos,estoque,caixa,pdv,relatorios",
                "clientes,produtos,estoque,lotes,caixa,pdv,compras,financeiro,pets,agenda,relatorios",
                "clientes,pets,clinica,agenda,caixa,financeiro,relatorios",
                "clientes,produtos,estoque,lotes,caixa,pdv,compras,financeiro,pets,clinica,agenda,relatorios"}) {
            assertThat(de(pacote).ativos()).isNotEmpty();
        }
    }

    @Test
    void integracaoSoExisteQuandoOsDoisModulosEstaoAtivos() {
        assertThat(de("todos").integracaoAtiva("vacina-estoque")).isTrue();
        assertThat(de("clientes,pets,clinica").integracaoAtiva("vacina-estoque")).isFalse();
        assertThat(de("produtos,estoque,lotes").integracaoAtiva("vacina-estoque")).isFalse();
        assertThat(de("clientes,pets,clinica,produtos,estoque,lotes").integracaoAtiva("vacina-estoque")).isTrue();
    }

    @Test
    void relatorioSoExisteComOModuloDeOrigemEOModuloRelatorios() {
        ModulosAtivos sem = de("produtos,estoque,caixa,pdv");
        assertThat(sem.relatoriosDisponiveis()).as("sem o módulo relatorios nada é publicado").isEmpty();

        ModulosAtivos com = de("produtos,estoque,caixa,pdv,relatorios");
        assertThat(com.relatoriosDisponiveis()).containsExactlyInAnyOrder("vendas", "produtos-mais-vendidos", "curva-abc", "margem", "estoque", "caixas");
        assertThat(com.relatorioDisponivel("contas")).isFalse();
        assertThat(com.relatorioDisponivel("servicos")).isFalse();
        assertThat(de("todos").relatoriosDisponiveis()).hasSize(9);
    }

    @Test
    void decisaoPorRotaSegueOCatalogo() {
        ModulosAtivos m = de("produtos,estoque,caixa,pdv");
        assertThat(m.permite(catalogo.resolver("POST", "/api/v1/vendas"), "/api/v1/vendas")).isTrue();
        assertThat(m.permite(catalogo.resolver("GET", "/api/v1/financeiro/lancamentos"), "/api/v1/financeiro/lancamentos")).isFalse();
        assertThat(m.permite(catalogo.resolver("GET", "/api/v1/lotes"), "/api/v1/lotes")).isFalse();
        assertThat(m.permite(catalogo.resolver("POST", "/api/v1/produtos/9/ajustes-estoque"), "/api/v1/produtos/9/ajustes-estoque")).isTrue();
        assertThat(m.permite(catalogo.resolver("POST", "/api/v1/produtos/9/controle-lote"), "/api/v1/produtos/9/controle-lote")).isFalse();
        // O núcleo nunca depende de módulo.
        assertThat(m.permite(catalogo.resolver("GET", "/api/v1/auth/me"), "/api/v1/auth/me")).isTrue();
        assertThat(m.permite(catalogo.resolver("GET", "/api/v1/usuarios"), "/api/v1/usuarios")).isTrue();
        assertThat(m.permite(catalogo.resolver("GET", "/api/v1/modulos"), "/api/v1/modulos")).isTrue();
        assertThat(Set.of(m.ativos())).isNotEmpty();
    }

    @Test
    void relatorioIndiretoEBloqueadoPeloModuloDeOrigemTambemNaExportacao() {
        ModulosAtivos m = de("produtos,estoque,caixa,pdv,relatorios");
        for (String caminho : new String[] {"/api/v1/relatorios/contas", "/api/v1/relatorios/contas/csv", "/api/v1/relatorios/despesas/csv", "/api/v1/relatorios/servicos"}) {
            assertThat(m.permite(catalogo.resolver("GET", caminho), caminho)).as(caminho).isFalse();
        }
        for (String caminho : new String[] {"/api/v1/relatorios/vendas", "/api/v1/relatorios/vendas/csv", "/api/v1/relatorios/estoque", "/api/v1/relatorios/caixas/csv"}) {
            assertThat(m.permite(catalogo.resolver("GET", caminho), caminho)).as(caminho).isTrue();
        }
        assertThat(ModulosAtivos.tipoDoRelatorio("/api/v1/relatorios/curva-abc/csv")).isEqualTo("curva-abc");
        assertThat(ModulosAtivos.tipoDoRelatorio("/api/v1/clientes")).isNull();
    }

    @Test
    void metodoNuncaFazARotaEscaparDoModulo() {
        // HEAD vale como GET; um método fora da lista da entrada continua caindo no dono do caminho.
        ModulosAtivos m = de("produtos,estoque,caixa,pdv");
        assertThat(m.permite(catalogo.resolver("HEAD", "/api/v1/estoque/alertas"), "/api/v1/estoque/alertas")).isFalse();
        assertThat(m.permite(catalogo.resolver("DELETE", "/api/v1/estoque/alertas"), "/api/v1/estoque/alertas")).isFalse();
        assertThat(m.permite(catalogo.resolver("PUT", "/api/v1/financeiro/lancamentos"), "/api/v1/financeiro/lancamentos")).isFalse();
    }
}
