package br.com.tecnodreamer.petconnect.cliente.web;

import br.com.tecnodreamer.petconnect.endereco.web.EnderecoRequest;
import br.com.tecnodreamer.petconnect.shared.validation.Cpf;
import br.com.tecnodreamer.petconnect.shared.validation.Telefone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ClienteRequest(
        @NotBlank @Size(max = 100) String nome,
        @Cpf String cpf,
        @Past LocalDate dataNascimento,
        @NotBlank @Telefone String telefone,
        @Email @Size(max = 120) String email,
        @Valid EnderecoRequest endereco,
        @Size(max = 2000) String observacoes) {
}
