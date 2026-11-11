package com.evolutionfit.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Celular colombiano: 10 dígitos que empiezan por 3. Acepta el indicativo +57 (o 57) y espacios, guiones,
 * puntos o paréntesis; se guarda solo con los 10 dígitos (ver {@link ColombianPhones#normalize}).
 * Un valor vacío o null es válido: la obligatoriedad la pone @NotBlank.
 */
@Documented
@Constraint(validatedBy = ColombianMobileValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ColombianMobile {

    String message() default ColombianPhones.INVALID_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
