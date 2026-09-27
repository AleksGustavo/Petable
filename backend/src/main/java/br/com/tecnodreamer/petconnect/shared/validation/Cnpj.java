package br.com.tecnodreamer.petconnect.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Aceita valor formatado ou só dígitos. {@code null}/vazio é válido (use {@code @NotBlank} se obrigatório). */
@Documented
@Constraint(validatedBy = Cnpj.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cnpj {

    String message() default "CNPJ inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Cnpj, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || Documentos.cnpjValido(value);
        }
    }
}
