package br.com.tecnodreamer.petconnect.venda.web;

import br.com.tecnodreamer.petconnect.pet.application.CarteiraVacinaService;
import br.com.tecnodreamer.petconnect.pet.web.LancamentoVacinaResponse;
import br.com.tecnodreamer.petconnect.pet.web.VacinaLancamentoRequest;
import br.com.tecnodreamer.petconnect.produto.domain.EstoqueInsuficienteException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lotes, etapa 04, item 7, opção A: a vacina do catálogo é ligada a um produto de estoque com controle de lote e aplicar a dose
 * baixa uma unidade do lote que vence primeiro, com o lote físico gravado na carteira.
 */
class VacinaEstoqueIT extends LoteIntegrationTest {

    @Autowired
    CarteiraVacinaService carteiraService;

    private final LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));

    /** O vínculo fica no banco compartilhado: desfaz ao fim de cada teste para não afetar os ITs de carteira de vacina. */
    @org.junit.jupiter.api.AfterEach
    void desligarVinculos() {
        jdbc.update("update vacina set produto_id = null");
    }

    /** Cada teste usa uma vacina diferente do catálogo (o vínculo é por vacina e o banco é compartilhado). */
    private long vacina(String especie, String nome) {
        long id = vacinaId(especie, nome);
        jdbc.update("update vacina set produto_id = null where id = ?", id);
        return id;
    }

    private void vincular(long vacinaId, long produtoId) throws Exception {
        mvc.perform(put("/api/v1/vacinas/" + vacinaId + "/produto").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": " + produtoId + "}")).andExpect(status().isOk());
    }

    private String dose(long vacinaId, String lote) {
        return """
                {"vacinaId": %d, "dataAplicacao": "%s", "dataProxima": "%s", "veterinario": "Dra. Ana", "lote": %s}"""
                .formatted(vacinaId, hoje, hoje.plusYears(1), lote == null ? "null" : "\"" + lote + "\"");
    }

    private org.springframework.test.web.servlet.ResultActions aplicar(long petId, long vacinaId, String lote) throws Exception {
        return mvc.perform(post("/api/v1/pets/" + petId + "/vacinas").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content(dose(vacinaId, lote)));
    }

    private long novoPet() throws Exception {
        return criarPet(criarCliente(unico("Tutor Vacina Estoque")), unico("Pet"), "Cão");
    }

    // ------------------------------------------------------------------ vínculo

    @Test
    void soAdminLigaAVacinaAoProdutoEOProdutoPrecisaControlarLote() throws Exception {
        long vacina = vacina("Cão", "Raiva (Antirrábica Canina)");
        long comLote = produtoControlado("80.00", "R-1:90:5");
        long semLote = produto("80.00", 3);
        String corpo = "{\"produtoId\": " + comLote + "}";

        mvc.perform(put("/api/v1/vacinas/" + vacina + "/produto").with(bearer(vendas())).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/vacinas/" + vacina + "/produto").with(comPapel("SERVICO")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/vacinas/" + vacina + "/produto").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": " + semLote + "}")).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/vacinas/" + vacina + "/produto").with(bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": 999999999}")).andExpect(status().isNotFound());

        vincular(vacina, comLote);
        mvc.perform(get("/api/v1/produtos/" + comLote + "/vacinas").with(bearer(vendas())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(vacina)).andExpect(jsonPath("$[0].produto.id").value(comLote));
        mvc.perform(get("/api/v1/vacinas/estoque").with(bearer(admin()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vacinas/estoque").with(bearer(vendas()))).andExpect(status().isForbidden());
        assertThat(acoesAuditadas("vacina", vacina)).contains("VACINA_PRODUTO_VINCULADO");

        mvc.perform(delete("/api/v1/vacinas/" + vacina + "/produto").with(bearer(admin()))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/produtos/" + comLote + "/vacinas").with(bearer(admin()))).andExpect(jsonPath("$.length()").value(0));
        assertThat(acoesAuditadas("vacina", vacina)).contains("VACINA_PRODUTO_DESVINCULADO");
    }

    @Test
    void catalogoInformaQuandoAAplicacaoBaixaEstoque() throws Exception {
        long vacina = vacina("Cão", "V8 Polivalente");
        long produto = produtoControlado("60.00", "C-1:90:2");
        long especie = especieId("Cão");

        mvc.perform(get("/api/v1/especies/" + especie + "/vacinas").with(bearer(admin())))
                .andExpect(jsonPath("$[?(@.id==" + vacina + ")].baixaEstoque").value(false));
        vincular(vacina, produto);
        mvc.perform(get("/api/v1/especies/" + especie + "/vacinas").with(bearer(admin())))
                .andExpect(jsonPath("$[?(@.id==" + vacina + ")].baixaEstoque").value(true));
    }

    // ------------------------------------------------------------------ aplicação

    @Test
    void aplicarTiraUmaDoseDoLoteQueVenceAntesEGravaOLoteNaCarteira() throws Exception {
        long vacina = vacina("Cão", "Raiva (Antirrábica Canina)");
        long produto = produtoControlado("80.00", "TARDE:90:3", "CEDO:30:2");
        vincular(vacina, produto);
        long pet = novoPet();

        String corpo = aplicar(pet, vacina, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lote").value("CEDO")).andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(corpo, "$.loteEstoqueId")).longValue()).isEqualTo(loteId(produto, "CEDO"));

        assertThat(saldoLote(produto, "CEDO")).isEqualTo(1);
        assertThat(saldo(produto)).isEqualTo(4);
        assertThat(movimentosDoLote(produto, "CEDO", "CONSUMO_CLINICO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select lote_id from carteira_vacina where pet_id = ?", Long.class, pet)).isEqualTo(loteId(produto, "CEDO"));
        long lancamento = ((Number) JsonPath.read(corpo, "$.id")).longValue();

        // O movimento e a auditoria do estoque não carregam dado do pet nem do tutor: só o número do lançamento.
        String motivo = jdbc.queryForObject("select motivo from movimentacao_estoque where produto_id = ? and tipo = 'CONSUMO_CLINICO'", String.class, produto);
        assertThat(motivo).isEqualTo("Aplicação de vacina (lançamento " + lancamento + ")");
        assertThat(acoesAuditadas("produto", produto)).contains("ESTOQUE_CONSUMO_CLINICO");
        assertThat(acoesAuditadas(pet)).contains("VACINA_PET_REGISTRADA");

        // Segunda e terceira doses: o FEFO acaba o lote que vence primeiro e passa para o outro.
        aplicar(novoPet(), vacina, null).andExpect(status().isCreated()).andExpect(jsonPath("$.lote").value("CEDO"));
        aplicar(novoPet(), vacina, null).andExpect(status().isCreated()).andExpect(jsonPath("$.lote").value("TARDE"));
        assertThat(saldoLote(produto, "CEDO")).isZero();
        assertThat(saldoLote(produto, "TARDE")).isEqualTo(2);
    }

    @Test
    void semLoteDisponivelAAplicacaoNaoEGravada() throws Exception {
        long vacina = vacina("Cão", "V10 Polivalente");
        long produto = produtoControlado("80.00", "VELHO:-1:4", "BLOQ:60:2");
        postLote("/api/v1/produtos/" + produto + "/lotes/" + loteId(produto, "BLOQ") + "/bloquear", "{\"motivo\": \"Quarentena de teste\"}").andExpect(status().isOk());
        vincular(vacina, produto);
        long pet = novoPet();

        aplicar(pet, vacina, null).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from carteira_vacina where pet_id = ?", Integer.class, pet)).isZero();
        assertThat(saldo(produto)).isEqualTo(6);
        assertThat(movimentacoes(produto, "CONSUMO_CLINICO")).isZero();
    }

    @Test
    void loteDigitadoNaoValeQuandoOEstoqueEscolheOLote() throws Exception {
        long vacina = vacina("Cão", "Giardia Canina");
        long produto = produtoControlado("80.00", "G-1:60:3");
        vincular(vacina, produto);
        long pet = novoPet();

        aplicar(pet, vacina, "L-DIGITADO").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.lote").exists());
        assertThat(saldoLote(produto, "G-1")).isEqualTo(3);
    }

    @Test
    void vacinaSemVinculoContinuaComoAntesComLoteDigitado() throws Exception {
        long vacina = vacina("Cão", "Gripe Canina (Bordetella)");
        long pet = novoPet();

        aplicar(pet, vacina, "L-MAO").andExpect(status().isCreated()).andExpect(jsonPath("$.lote").value("L-MAO"))
                .andExpect(jsonPath("$.loteEstoqueId").doesNotExist());
        assertThat(jdbc.queryForObject("select lote_id from carteira_vacina where pet_id = ?", Long.class, pet)).isNull();
    }

    @Test
    void removerOLancamentoNaoDevolveADoseEFicaDitoNaAuditoria() throws Exception {
        long vacina = vacina("Cão", "V8 Polivalente");
        long produto = produtoControlado("80.00", "X-1:60:2");
        vincular(vacina, produto);
        long pet = novoPet();
        String corpo = aplicar(pet, vacina, null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long lancamento = ((Number) JsonPath.read(corpo, "$.id")).longValue();

        mvc.perform(delete("/api/v1/pets/" + pet + "/vacinas/" + lancamento).with(bearer(admin()))).andExpect(status().isNoContent());

        assertThat(saldoLote(produto, "X-1")).isEqualTo(1);
        assertThat(movimentosDoLote(produto, "X-1", "CONSUMO_CLINICO")).isEqualTo(1);
        assertThat(acoesAuditadas(pet)).contains("VACINA_PET_REMOVIDA");
    }

    // ------------------------------------------------------------------ concorrência

    @Test
    void duasAplicacoesDaUltimaDoseSoUmaConfirma() throws Exception {
        long vacina = vacina("Cão", "V10 Polivalente");
        long produto = produtoControlado("80.00", "U-1:60:1");
        vincular(vacina, produto);
        long adminId = adminId();
        long petA = novoPet();
        long petB = novoPet();
        VacinaLancamentoRequest req = new VacinaLancamentoRequest(vacina, hoje, hoje.plusYears(1), "Dra. Ana", null, null);

        List<Object> r = emParalelo(List.of(
                () -> carteiraService.registrar(petA, req, adminId),
                () -> carteiraService.registrar(petB, req, adminId)));

        assertThat(r).as(r.toString()).filteredOn(LancamentoVacinaResponse.class::isInstance).hasSize(1);
        assertThat(r).filteredOn(o -> o instanceof ConflictException && !(o instanceof EstoqueInsuficienteException)).hasSize(1);
        assertThat(saldoLote(produto, "U-1")).isZero();
        assertThat(saldo(produto)).isZero();
        assertThat(movimentacoes(produto, "CONSUMO_CLINICO")).isEqualTo(1);
    }

    private static List<Object> emParalelo(List<Callable<Object>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<Object> t : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return t.call();
                }));
            }
            largada.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> f : futuros) {
                try {
                    resultados.add(f.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
