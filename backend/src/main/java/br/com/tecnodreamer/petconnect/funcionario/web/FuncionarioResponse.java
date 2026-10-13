package br.com.tecnodreamer.petconnect.funcionario.web;

import br.com.tecnodreamer.petconnect.endereco.web.EnderecoResponse;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.domain.Sexo;

import java.time.LocalDate;

public record FuncionarioResponse(Long id, Long usuarioId, String username, String nome, String cpf,
        LocalDate dataNascimento, Sexo sexo, String telefone, EnderecoResponse endereco, String observacoes,
        boolean disponivel) {

    public static FuncionarioResponse of(Funcionario f) {
        return new FuncionarioResponse(f.getId(), f.getUsuario().getId(), f.getUsuario().getUsername(), f.getNome(),
                f.getCpf(), f.getDataNascimento(), f.getSexo(), f.getTelefone(), EnderecoResponse.of(f.getEndereco()),
                f.getObservacoes(), f.isDisponivel());
    }
}
