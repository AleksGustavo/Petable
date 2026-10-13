package br.com.tecnodreamer.petconnect.cliente.web;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.endereco.web.EnderecoResponse;

import java.time.Instant;
import java.time.LocalDate;

public record ClienteResponse(Long id, String nome, String cpf, LocalDate dataNascimento, String telefone,
        String email, EnderecoResponse endereco, String observacoes, boolean ativo, Instant createdAt,
        Instant updatedAt) {

    public static ClienteResponse of(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNome(), c.getCpf(), c.getDataNascimento(), c.getTelefone(),
                c.getEmail(), EnderecoResponse.of(c.getEndereco()), c.getObservacoes(), c.isAtivo(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
