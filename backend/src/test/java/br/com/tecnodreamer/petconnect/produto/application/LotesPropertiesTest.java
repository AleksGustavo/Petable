package br.com.tecnodreamer.petconnect.produto.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LotesPropertiesTest {

    @Test
    void semNenhumaConfiguracaoOControleNasceDesligadoComMargemZero() {
        LotesProperties p = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("petconnect.estoque.lotes", LotesProperties.class);
        assertThat(p.habilitado()).isFalse();
        assertThat(p.diasBloqueioAntesValidade()).isZero();
    }

    @Test
    void oApplicationYmlDeProducaoTambemNasceDesligado() throws Exception {
        String yml = new String(new ClassPathResource("application.yml").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(yml).contains("habilitado: ${PETCONNECT_ESTOQUE_LOTES:false}");
        assertThat(yml).contains("dias-bloqueio-antes-validade: ${PETCONNECT_ESTOQUE_LOTES_DIAS_BLOQUEIO:0}");
    }

    @Test
    void margemDoProdutoSobrescreveAGlobal() {
        LotesProperties p = new LotesProperties(true, 7);
        assertThat(p.margemDoProduto(null)).isEqualTo(7);
        assertThat(p.margemDoProduto(0)).isZero();
        assertThat(p.margemDoProduto(30)).isEqualTo(30);
    }
}
