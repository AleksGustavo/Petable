package br.com.tecnodreamer.petconnect.fornecedor.web;

import br.com.tecnodreamer.petconnect.endereco.web.EnderecoResponse;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;

import java.time.Instant;

public record FornecedorResponse(Long id, String nomeFantasia, String razaoSocial, String cnpj, String telefone,
        String email, String contato, EnderecoResponse endereco, String observacoes, boolean ativo, Instant createdAt,
        Instant updatedAt) {

    public static FornecedorResponse of(Fornecedor f) {
        return new FornecedorResponse(f.getId(), f.getNomeFantasia(), f.getRazaoSocial(), f.getCnpj(), f.getTelefone(),
                f.getEmail(), f.getContato(), EnderecoResponse.of(f.getEndereco()), f.getObservacoes(), f.isAtivo(),
                f.getCreatedAt(), f.getUpdatedAt());
    }

    /** Lista: sem carregar o endereço. */
    public static FornecedorResponse resumo(Fornecedor f) {
        return new FornecedorResponse(f.getId(), f.getNomeFantasia(), f.getRazaoSocial(), f.getCnpj(), f.getTelefone(),
                f.getEmail(), f.getContato(), null, null, f.isAtivo(), f.getCreatedAt(), f.getUpdatedAt());
    }
}
