package com.evolutionfit.modules.plans;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import com.evolutionfit.modules.plans.dto.PlanRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlanRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(PlanRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3650})
    void durationLimitsAreAccepted(int days) {
        assertThat(invalidFields(new PlanRequest("Plan", null, days, BigDecimal.TEN))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 3651})
    void durationOutOfRangeIsRejected(int days) {
        assertThat(invalidFields(new PlanRequest("Plan", null, days, BigDecimal.TEN)))
                .containsExactly("durationDays");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "9999999999.99"})
    void acceptedPrices(String price) {
        assertThat(invalidFields(new PlanRequest("Plan", null, 30, new BigDecimal(price)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.234", "12345678901"})
    void rejectedPrices(String price) {
        assertThat(invalidFields(new PlanRequest("Plan", null, 30, new BigDecimal(price))))
                .containsExactly("price");
    }

    @Test
    void requiredFieldsAndLengthsAreEnforced() {
        assertThat(invalidFields(new PlanRequest(" ", "a".repeat(256), null, null)))
                .containsExactlyInAnyOrder("name", "description", "durationDays", "price");
        assertThat(invalidFields(new PlanRequest("a".repeat(81), null, 30, BigDecimal.TEN)))
                .containsExactly("name");
    }
}
