package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Não aceita {@code ativo}, {@code fotoUrl} nem timestamps: esses só mudam por endpoints próprios. */
public record PetRequest(
        @NotNull(message = "Selecione o tutor") Long clienteId,
        @NotBlank(message = "Informe o nome") @Size(max = 50) String nome,
        @NotNull(message = "Selecione a espécie") Long especieId,
        Long racaId,
        LocalDate dataNascimento,
        @DecimalMin(value = "0.00", inclusive = false, message = "O peso deve ser maior que zero")
        @DecimalMax(value = "999.99", message = "O peso deve ser no máximo 999,99 kg")
        @Digits(integer = 3, fraction = 2, message = "Use no máximo duas casas decimais") BigDecimal peso,
        @Size(max = 30) String cor,
        Porte porte,
        Boolean castrado,
        @Size(max = 2000) String observacoes) {
}
