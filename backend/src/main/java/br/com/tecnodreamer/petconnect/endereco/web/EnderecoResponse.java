package br.com.tecnodreamer.petconnect.endereco.web;

import br.com.tecnodreamer.petconnect.endereco.domain.Endereco;

public record EnderecoResponse(Long id, String cep, String logradouro, String numero, String complemento,
        String bairro, String cidade, String uf) {

    public static EnderecoResponse of(Endereco e) {
        return e == null ? null : new EnderecoResponse(e.getId(), e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
    }
}
