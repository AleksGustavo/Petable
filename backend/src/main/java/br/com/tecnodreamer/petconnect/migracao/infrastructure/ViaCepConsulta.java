package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import br.com.tecnodreamer.petconnect.migracao.application.ConsultaCep;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ViaCEP com timeout curto e cache por CEP. Só é chamado com
 * petconnect.migracao.viacep.enabled=true; qualquer falha devolve vazio (DA-18).
 */
@Component
public class ViaCepConsulta implements ConsultaCep {

    private static final Logger log = LoggerFactory.getLogger(ViaCepConsulta.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(String localidade, String uf, Boolean erro) {
    }

    private final RestClient http;
    private final Map<String, Optional<Localidade>> cache = new ConcurrentHashMap<>();

    public ViaCepConsulta(@Value("${petconnect.migracao.viacep.timeout-ms:1500}") int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        this.http = RestClient.builder().baseUrl("https://viacep.com.br/ws").requestFactory(f).build();
    }

    @Override
    public Optional<Localidade> consultar(String cep) {
        if (cep == null || !cep.matches("\\d{8}")) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(cep, this::buscar);
    }

    private Optional<Localidade> buscar(String cep) {
        try {
            Resposta r = http.get().uri("/{cep}/json/", cep).retrieve().body(Resposta.class);
            if (r == null || Boolean.TRUE.equals(r.erro()) || r.localidade() == null || r.uf() == null) {
                return Optional.empty();
            }
            return Optional.of(new Localidade(r.localidade(), r.uf()));
        } catch (RuntimeException e) {
            // Sem o CEP no log (dado de endereço): só o tipo da falha.
            log.warn("ViaCEP indisponível: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
