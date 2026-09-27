package br.com.tecnodreamer.petconnect.funcionario.web;

import br.com.tecnodreamer.petconnect.endereco.web.EnderecoRequest;
import br.com.tecnodreamer.petconnect.funcionario.domain.Sexo;
import br.com.tecnodreamer.petconnect.shared.validation.Cpf;
import br.com.tecnodreamer.petconnect.shared.validation.Telefone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Atualização administrativa: não troca o usuário vinculado. */
public record FuncionarioAtualizacaoRequest(
        @NotBlank(message = "Informe o CPF") @Cpf String cpf,
        @NotBlank(message = "Informe o nome") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres") String nome,
        LocalDate dataNascimento,
        Sexo sexo,
        @Telefone String telefone,
        @Valid EnderecoRequest endereco,
        @Size(max = 2000) String observacoes) {
}
