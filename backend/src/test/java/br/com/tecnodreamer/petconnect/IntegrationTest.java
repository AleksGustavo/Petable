package br.com.tecnodreamer.petconnect;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Base dos testes de integração (*IT): contexto completo + MySQL e MongoDB
 * reais via Testcontainers. Todas as subclasses compartilham o mesmo contexto
 * (e os mesmos containers), então não mude a configuração numa subclasse.
 */
@SpringBootTest
// Testes desligam a exportação de métricas por padrão; o PrometheusIT precisa dela (etapa 09).
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, EmailsCapturados.class})
@TestPropertySource(properties = {
        "app.bootstrap-admin.username=" + IntegrationTest.ADMIN_USER,
        "app.bootstrap-admin.email=admin@petconnect.test",
        "app.bootstrap-admin.password=" + IntegrationTest.ADMIN_PASSWORD,
        // Fotos dos pets em diretório descartável (apagado pelo mvn clean).
        "petconnect.storage.pets=target/it-uploads/pets",
        // Lotes (etapa 03): ligado nos testes; o padrão de produção continua falso (LotesDesligadosIT e LotesPropertiesTest).
        "petconnect.estoque.lotes.habilitado=true"
})
public abstract class IntegrationTest {

    protected static final String ADMIN_USER = "admin";
    /** Precisa atender à PoliticaSenha: o AdminBootstrap recusa senha fraca. */
    protected static final String ADMIN_PASSWORD = "Senha-Forte-De-Teste-2026";
}
