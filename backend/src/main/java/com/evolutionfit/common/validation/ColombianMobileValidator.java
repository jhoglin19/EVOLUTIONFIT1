package com.evolutionfit.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ColombianMobileValidator implements ConstraintValidator<ColombianMobile, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || ColombianPhones.normalize(value) != null;
    }
}
