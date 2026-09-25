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
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.bootstrap-admin.username=" + IntegrationTest.ADMIN_USER,
        "app.bootstrap-admin.email=admin@petconnect.test",
        "app.bootstrap-admin.password=" + IntegrationTest.ADMIN_PASSWORD
})
public abstract class IntegrationTest {

    protected static final String ADMIN_USER = "admin";
    protected static final String ADMIN_PASSWORD = "senha-forte-de-teste";
}
