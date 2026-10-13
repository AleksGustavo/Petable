package br.com.tecnodreamer.petconnect.fornecedor.web;

import br.com.tecnodreamer.petconnect.endereco.web.EnderecoRequest;
import br.com.tecnodreamer.petconnect.shared.validation.Cnpj;
import br.com.tecnodreamer.petconnect.shared.validation.Telefone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Limites de tamanho = colunas da V1 (nome fantasia 100, razão social 150, e-mail 120, contato 100). */
public record FornecedorRequest(
        @NotBlank(message = "Informe o nome fantasia") @Size(min = 2, max = 100, message = "O nome fantasia deve ter entre 2 e 100 caracteres") String nomeFantasia,
        @NotBlank(message = "Informe a razão social") @Size(min = 2, max = 150, message = "A razão social deve ter entre 2 e 150 caracteres") String razaoSocial,
        @NotBlank(message = "Informe o CNPJ") @Cnpj String cnpj,
        @Telefone String telefone,
        @Email(message = "E-mail inválido") @Size(max = 120) String email,
        @Size(max = 100) String contato,
        @NotNull(message = "Informe o endereço") @Valid EnderecoRequest endereco,
        @Size(max = 2000) String observacoes) {
}
