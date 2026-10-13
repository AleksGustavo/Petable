package br.com.tecnodreamer.petconnect.endereco.web;

import br.com.tecnodreamer.petconnect.endereco.domain.Endereco;
import br.com.tecnodreamer.petconnect.shared.validation.Cep;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record EnderecoRequest(
        @NotBlank @Cep String cep,
        @NotBlank @Size(max = 150) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF deve ter 2 letras") String uf) {

    public Endereco criar() {
        return new Endereco(Documentos.somenteDigitos(cep), logradouro.trim(), numero, complemento, bairro,
                cidade.trim(), uf.toUpperCase(Locale.ROOT));
    }

    public void aplicarEm(Endereco endereco) {
        endereco.atualizar(Documentos.somenteDigitos(cep), logradouro.trim(), numero, complemento, bairro,
                cidade.trim(), uf.toUpperCase(Locale.ROOT));
    }
}
