package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MigracaoProperties.class)
class MigracaoConfig {
}
