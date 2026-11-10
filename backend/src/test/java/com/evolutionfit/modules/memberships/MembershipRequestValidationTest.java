package com.evolutionfit.modules.memberships;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.evolutionfit.modules.memberships.dto.CreateFreezeRequest;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.memberships.dto.ExtendFreezeRequest;
import com.evolutionfit.modules.memberships.dto.MigratedMembershipRequest;
import com.evolutionfit.modules.memberships.dto.ReasonRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class MembershipRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void reasonIsMandatory() {
        assertThat(invalidFields(new ReasonRequest(" "))).containsExactly("reason");
        assertThat(invalidFields(new ReasonRequest(null))).containsExactly("reason");
        assertThat(invalidFields(new ReasonRequest("x".repeat(256)))).containsExactly("reason");
        assertThat(invalidFields(new ReasonRequest("Cambio de plan"))).isEmpty();
    }

    @Test
    void saleNeedsClientAndPlanButNotAStartDate() {
        assertThat(invalidFields(new CreateMembershipRequest(null, null, null, null)))
                .containsExactlyInAnyOrder("clientId", "planId");
        assertThat(invalidFields(new CreateMembershipRequest(1L, 2L, null, null))).isEmpty();
    }

    @Test
    void freezeNeedsStartDaysAndReason() {
        assertThat(invalidFields(new CreateFreezeRequest(null, null, "")))
                .containsExactlyInAnyOrder("startDate", "days", "reason");
        assertThat(invalidFields(new CreateFreezeRequest(DAY, 0, "Viaje"))).containsExactly("days");
        assertThat(invalidFields(new CreateFreezeRequest(DAY, 30, "Viaje"))).isEmpty();
        assertThat(invalidFields(new ExtendFreezeRequest(10, " "))).containsExactly("reason");
    }

    @Test
    void migrationNeedsDatesAndNoteAndANonNegativePrice() {
        assertThat(invalidFields(new MigratedMembershipRequest(1L, 2L, null, null, null, " ", false, null)))
                .containsExactlyInAnyOrder("startDate", "endDate", "migrationNote");
        assertThat(invalidFields(new MigratedMembershipRequest(1L, 2L, DAY, DAY, new BigDecimal("-1"), "ID 9", false, null)))
                .containsExactly("price");
        assertThat(invalidFields(new MigratedMembershipRequest(1L, 2L, DAY, DAY, null, "ID 9", true, null))).isEmpty();
        assertThat(invalidFields(new MigratedMembershipRequest(1L, 2L, DAY, DAY, BigDecimal.ZERO, "ID 9", true, null))).isEmpty();
    }
}
