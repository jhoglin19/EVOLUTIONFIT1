package com.evolutionfit.modules.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.plans.PlanService.PlanSnapshot;
import com.evolutionfit.modules.plans.dto.PlanRequest;
import com.evolutionfit.modules.plans.dto.PlanResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;
    @Mock
    private AuditService auditService;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planRepository, auditService);
    }

    private static Plan existing(boolean active) {
        Plan plan = new Plan();
        plan.setId(2L);
        plan.setName("Mensual");
        plan.setDescription("30 días");
        plan.setDurationDays(30);
        plan.setPrice(new BigDecimal("90000.00"));
        plan.setActive(active);
        return plan;
    }

    private void saveReturnsArgument() {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createTrimsNormalizesPriceAndAudits() {
        when(planRepository.existsByName("Anual")).thenReturn(false);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan plan = inv.getArgument(0);
            plan.setId(10L);
            return plan;
        });

        PlanResponse response = service.create(
                new PlanRequest(" Anual ", " ", 365, new BigDecimal("780000")), 7L);

        assertThat(response.name()).isEqualTo("Anual");
        assertThat(response.description()).isNull();
        assertThat(response.price()).isEqualTo(new BigDecimal("780000.00"));
        assertThat(response.active()).isTrue();
        verify(auditService).record(eq("PLAN"), eq(10L), eq(AuditService.ACTION_CREATE), isNull(),
                eq(new PlanSnapshot("Anual", null, 365, new BigDecimal("780000.00"), true)), eq(7L));
    }

    @Test
    void priceZeroIsAllowed() {
        when(planRepository.existsByName("Cortesía")).thenReturn(false);
        saveReturnsArgument();

        assertThat(service.create(new PlanRequest("Cortesía", null, 1, BigDecimal.ZERO), 7L).price())
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void createRejectsDuplicateName() {
        when(planRepository.existsByName("Mensual")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new PlanRequest("Mensual", null, 30, BigDecimal.TEN), 7L))
                .isInstanceOf(ConflictException.class);

        verify(planRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void updateRejectsTheNameOfAnotherPlan() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(existing(true)));
        when(planRepository.existsByNameAndIdNot("Anual", 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(2L, new PlanRequest("Anual", null, 30, BigDecimal.TEN), 7L))
                .isInstanceOf(ConflictException.class);

        verify(planRepository, never()).save(any());
    }

    @Test
    void priceChangeIsAuditedWithPreviousAndNewValue() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(existing(true)));
        when(planRepository.existsByNameAndIdNot("Mensual", 2L)).thenReturn(false);
        saveReturnsArgument();

        service.update(2L, new PlanRequest("Mensual", "30 días", 30, new BigDecimal("95000")), 7L);

        verify(auditService).record("PLAN", 2L, AuditService.ACTION_UPDATE,
                new PlanSnapshot("Mensual", "30 días", 30, new BigDecimal("90000.00"), true),
                new PlanSnapshot("Mensual", "30 días", 30, new BigDecimal("95000.00"), true),
                7L);
    }

    @Test
    void updateWithoutRealChangesIsNotAudited() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(existing(true)));
        when(planRepository.existsByNameAndIdNot("Mensual", 2L)).thenReturn(false);
        saveReturnsArgument();

        // 90000 y 90000.00 son el mismo precio aunque BigDecimal.equals los distinga.
        service.update(2L, new PlanRequest("Mensual", "30 días", 30, new BigDecimal("90000")), 7L);

        verifyNoInteractions(auditService);
    }

    @Test
    void statusChangeIsAudited() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(existing(true)));
        saveReturnsArgument();

        assertThat(service.setActive(2L, false, 7L).active()).isFalse();

        verify(auditService).record("PLAN", 2L, AuditService.ACTION_STATUS_CHANGE,
                new PlanSnapshot("Mensual", "30 días", 30, new BigDecimal("90000.00"), true),
                new PlanSnapshot("Mensual", "30 días", 30, new BigDecimal("90000.00"), false),
                7L);
    }

    @Test
    void settingTheCurrentStatusChangesNothing() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(existing(false)));

        assertThat(service.setActive(2L, false, 7L).active()).isFalse();

        verify(planRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void unknownPlanIsNotFound() {
        when(planRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
