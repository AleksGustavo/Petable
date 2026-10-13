package br.com.tecnodreamer.petconnect.usuario.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(String username, String email, String password) {

    boolean configurado() {
        return username != null && !username.isBlank()
                && email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }
}
