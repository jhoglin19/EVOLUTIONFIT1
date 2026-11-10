package com.evolutionfit.modules.memberships;

import static com.evolutionfit.modules.memberships.MembershipFixtures.CLOCK;
import static com.evolutionfit.modules.memberships.MembershipFixtures.TODAY;
import static com.evolutionfit.modules.memberships.MembershipFixtures.client;
import static com.evolutionfit.modules.memberships.MembershipFixtures.confirmed;
import static com.evolutionfit.modules.memberships.MembershipFixtures.day;
import static com.evolutionfit.modules.memberships.MembershipFixtures.monthly;
import static com.evolutionfit.modules.memberships.MembershipFixtures.pending;
import static com.evolutionfit.modules.memberships.MembershipFixtures.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.memberships.MembershipSupport.MembershipSnapshot;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;
import com.evolutionfit.modules.memberships.dto.MigratedMembershipRequest;
import com.evolutionfit.modules.plans.Plan;
import com.evolutionfit.modules.plans.PlanRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipServiceTest {

    private static final long ADMIN_ID = 7L;

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private AuditService auditService;

    private MembershipService service;
    private Client client;
    private final List<Membership> clientMemberships = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MembershipSupport support = new MembershipSupport(clientRepository, membershipRepository, auditService, CLOCK);
        service = new MembershipService(membershipRepository, clientRepository, planRepository, support,
                new MembershipProperties(5, 30));
        client = client(1L, true);
        when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(client));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(planRepository.findById(2L)).thenReturn(Optional.of(monthly()));
        when(membershipRepository.findByClientIdOrderByStartDateDescIdDesc(1L)).thenReturn(clientMemberships);
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> {
            Membership m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(50L);
            }
            return m;
        });
    }

    private Membership saved() {
        ArgumentCaptor<Membership> captor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CreateMembershipRequest sale(java.time.LocalDate start) {
        return new CreateMembershipRequest(1L, 2L, start, null);
    }

    private static MigratedMembershipRequest migration(java.time.LocalDate start, java.time.LocalDate end,
                                                       BigDecimal price, boolean freezeUsed) {
        return new MigratedMembershipRequest(1L, 2L, start, end, price, " ID 345 en sistema anterior ", freezeUsed, null);
    }

    // ---- venta ----

    @Test
    void newMembershipIsPendingFromTodayAndCopiesThePlan() {
        MembershipResponse response = service.create(sale(null), ADMIN_ID);

        Membership m = saved();
        assertThat(m.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(m.getStartDate()).isEqualTo(TODAY);
        assertThat(m.getEndDate()).isEqualTo(day(12, 8));
        assertThat(m.getBaseEndDate()).isEqualTo(day(12, 8));
        assertThat(m.getPlanName()).isEqualTo("Mensual");
        assertThat(m.getPlanDurationDays()).isEqualTo(30);
        assertThat(m.getPriceAtPurchase()).isEqualByComparingTo("90000");
        assertThat(m.isMigrated()).isFalse();
        assertThat(response.status()).isEqualTo(MembershipStatus.PENDING);
        verify(auditService).record(eq("MEMBERSHIP"), eq(50L), eq(AuditService.ACTION_CREATE), isNull(),
                any(MembershipSnapshot.class), eq(ADMIN_ID));
    }

    @Test
    void laterPlanChangesDoNotAlterTheSoldMembership() {
        service.create(sale(null), ADMIN_ID);
        Membership m = saved();
        m.getPlan().setName("Mensual nuevo");
        m.getPlan().setPrice(new BigDecimal("120000"));

        assertThat(m.getPlanName()).isEqualTo("Mensual");
        assertThat(m.getPriceAtPurchase()).isEqualByComparingTo("90000");
    }

    @Test
    void newMembershipAcceptsAFutureStart() {
        service.create(sale(day(11, 20)), ADMIN_ID);
        assertThat(saved().getStartDate()).isEqualTo(day(11, 20));
    }

    @Test
    void newMembershipRejectsAPastStart() {
        assertThatThrownBy(() -> service.create(sale(TODAY.minusDays(1)), ADMIN_ID))
                .isInstanceOf(BusinessException.class);
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void renewalBeforeExpiryStartsTheDayAfter() {
        clientMemberships.add(confirmed(10L, client, day(11, 8))); // vence 07-12

        service.create(sale(null), ADMIN_ID);
        assertThat(saved().getStartDate()).isEqualTo(day(12, 8));
    }

    @Test
    void renewalAfterExpiryStartsToday() {
        clientMemberships.add(confirmed(10L, client, day(10, 1).minusYears(1))); // vencida el 30-10

        service.create(sale(null), ADMIN_ID);
        assertThat(saved().getStartDate()).isEqualTo(TODAY);
    }

    @Test
    void onlyOneNextMembershipCountingPendingAndScheduled() {
        clientMemberships.add(pending(10L, client, TODAY));
        assertThatThrownBy(() -> service.create(sale(null), ADMIN_ID)).isInstanceOf(ConflictException.class);

        clientMemberships.clear();
        clientMemberships.add(confirmed(11L, client, day(11, 1)));
        clientMemberships.add(confirmed(12L, client, day(12, 1))); // programada
        assertThatThrownBy(() -> service.create(sale(null), ADMIN_ID)).isInstanceOf(ConflictException.class);
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void aCancelledMembershipDoesNotCountAsNext() {
        Membership cancelled = pending(10L, client, TODAY);
        cancelled.setStatus(MembershipStatus.CANCELLED);
        clientMemberships.add(cancelled);

        service.create(sale(null), ADMIN_ID);
        assertThat(saved().getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void inactiveClientOrPlanCannotBuy() {
        client.setActive(false);
        assertThatThrownBy(() -> service.create(sale(null), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("inactivo");

        client.setActive(true);
        Plan retired = plan(3L, "Viejo", 30, "1", false);
        when(planRepository.findById(3L)).thenReturn(Optional.of(retired));
        assertThatThrownBy(() -> service.create(new CreateMembershipRequest(1L, 3L, null, null), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("plan está inactivo");
    }

    @Test
    void theClientIsLockedBeforeItsMembershipsAreRead() {
        service.create(sale(null), ADMIN_ID);

        InOrder order = inOrder(clientRepository, membershipRepository);
        order.verify(clientRepository).findByIdForUpdate(1L);
        order.verify(membershipRepository).findByClientIdOrderByStartDateDescIdDesc(1L);
    }

    // ---- migradas ----

    @Test
    void migratedMembershipKeepsItsRealDatesAndNeedsNoPayment() {
        MembershipResponse response = service.createMigrated(
                migration(day(10, 15).minusYears(1), day(11, 25), null, false), ADMIN_ID);

        Membership m = saved();
        assertThat(m.isMigrated()).isTrue();
        assertThat(m.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(m.getStartDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 15));
        assertThat(m.getEndDate()).isEqualTo(day(11, 25));
        assertThat(m.getBaseEndDate()).isEqualTo(day(11, 25));
        assertThat(m.getPriceAtPurchase()).isEqualByComparingTo("90000"); // precio del plan por defecto
        assertThat(m.getMigrationNote()).isEqualTo("ID 345 en sistema anterior");
        assertThat(response.canFreeze()).isTrue();
    }

    @Test
    void migratedMembershipAcceptsAnEditedPriceAndTheUsedFreezeFlag() {
        service.createMigrated(migration(day(11, 1), day(11, 30), new BigDecimal("0"), true), ADMIN_ID);

        Membership m = saved();
        assertThat(m.getPriceAtPurchase()).isEqualByComparingTo("0");
        assertThat(m.isFreezeUsedInPreviousSystem()).isTrue();
        assertThat(MembershipRules.canFreeze(m, TODAY)).isFalse();
    }

    @Test
    void onlyValidMembershipsAreMigrated() {
        assertThatThrownBy(() -> service.createMigrated(migration(day(10, 1).minusYears(1), TODAY.minusDays(1), null, false), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("vigentes");
        assertThatThrownBy(() -> service.createMigrated(migration(TODAY.plusDays(1), day(12, 30), null, false), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no puede ser futura");
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void migrationIsRejectedIfTheClientAlreadyHasACurrentOrNextMembership() {
        clientMemberships.add(pending(10L, client, TODAY));
        assertThatThrownBy(() -> service.createMigrated(migration(day(11, 1), day(11, 30), null, false), ADMIN_ID))
                .isInstanceOf(ConflictException.class);

        clientMemberships.clear();
        clientMemberships.add(confirmed(11L, client, day(11, 1)));
        assertThatThrownBy(() -> service.createMigrated(migration(day(11, 1), day(11, 30), null, false), ADMIN_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void migrationAlsoLocksTheClientFirst() {
        service.createMigrated(migration(day(11, 1), day(11, 30), null, false), ADMIN_ID);

        InOrder order = inOrder(clientRepository, membershipRepository);
        order.verify(clientRepository).findByIdForUpdate(1L);
        order.verify(membershipRepository).findByClientIdOrderByStartDateDescIdDesc(1L);
    }

    // ---- anulación ----

    private Membership stubMembership(Membership m) {
        when(membershipRepository.findClientIdById(m.getId())).thenReturn(Optional.of(1L));
        when(membershipRepository.findById(m.getId())).thenReturn(Optional.of(m));
        clientMemberships.add(m);
        return m;
    }

    @Test
    void cancellingKeepsTheMembershipWithItsReasonAndAudits() {
        Membership m = stubMembership(confirmed(10L, client, day(11, 1)));

        MembershipResponse response = service.cancel(10L, " Cambio de plan ", ADMIN_ID);

        assertThat(response.status()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(m.getCancellationReason()).isEqualTo("Cambio de plan");
        assertThat(m.getCancelledBy()).isEqualTo(ADMIN_ID);
        assertThat(m.getCancelledAt()).isNotNull();
        verify(membershipRepository, never()).delete(any(Membership.class));
        verify(membershipRepository, never()).deleteById(anyLong());
        verify(auditService).record(eq("MEMBERSHIP"), eq(10L), eq(AuditService.ACTION_CANCEL),
                any(MembershipSnapshot.class), any(MembershipSnapshot.class), eq(ADMIN_ID));
        // Cambiar de plan = anular y registrar otra, que ya puede empezar hoy.
        service.create(sale(null), ADMIN_ID);
    }

    @Test
    void cancellingTheCurrentMembershipMovesTheNextOneToToday() {
        stubMembership(confirmed(10L, client, day(11, 1)));            // vence 30-11
        Membership next = stubMembership(confirmed(11L, client, day(12, 1))); // programada 01-12 a 30-12

        service.cancel(10L, "Cambio de plan", ADMIN_ID);

        assertThat(next.getStartDate()).isEqualTo(TODAY);
        assertThat(next.getBaseEndDate()).isEqualTo(day(12, 8));
        assertThat(next.getEndDate()).isEqualTo(day(12, 8));
        assertThat(next.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(auditService).record(eq("MEMBERSHIP"), eq(11L), eq(AuditService.ACTION_RESCHEDULE),
                any(), any(), eq(ADMIN_ID));
    }

    @Test
    void cancellingAScheduledMembershipDoesNotMoveOthers() {
        Membership current = stubMembership(confirmed(10L, client, day(11, 1)));
        stubMembership(confirmed(11L, client, day(12, 1)));

        service.cancel(11L, "Error", ADMIN_ID);

        assertThat(current.getStartDate()).isEqualTo(day(11, 1));
        verify(auditService, never()).record(any(), any(), eq(AuditService.ACTION_RESCHEDULE), any(), any(), any());
    }

    @Test
    void cancellingAFrozenMembershipCancelsItsOngoingFreezeKeepingOnlyElapsedDays() {
        Membership m = stubMembership(confirmed(10L, client, day(11, 1))); // base 30-11
        MembershipFreeze f = MembershipFixtures.freeze(1L, m, day(11, 5), 10); // 05 al 14, en curso

        service.cancel(10L, "Cambio de plan", ADMIN_ID);

        assertThat(f.getStatus()).isEqualTo(FreezeStatus.CANCELLED);
        assertThat(f.getAppliedDays()).isEqualTo(4); // 05 al 08
        assertThat(f.getCancellationReason()).isEqualTo("Membresía anulada: Cambio de plan");
        assertThat(f.getCancelledBy()).isEqualTo(ADMIN_ID);
        assertThat(f.getCancelledAt()).isNotNull();
        assertThat(m.getEndDate()).isEqualTo(day(12, 4));
        assertThat(m.getStatus()).isEqualTo(MembershipStatus.CANCELLED);
        verify(auditService).record(eq("MEMBERSHIP_FREEZE"), eq(1L), eq(AuditService.ACTION_CANCEL),
                any(), any(), eq(ADMIN_ID));
    }

    @Test
    void cancellingAMembershipCancelsAScheduledFreezeWithoutDays() {
        Membership m = stubMembership(confirmed(10L, client, day(11, 1)));
        MembershipFreeze f = MembershipFixtures.freeze(1L, m, day(11, 20), 5);

        service.cancel(10L, "Error", ADMIN_ID);

        assertThat(f.getStatus()).isEqualTo(FreezeStatus.CANCELLED);
        assertThat(f.getAppliedDays()).isZero();
        assertThat(m.getEndDate()).isEqualTo(day(11, 30));
    }

    @Test
    void cancellingAMembershipLeavesFinishedFreezesUntouched() {
        Membership m = stubMembership(confirmed(10L, client, day(11, 1)));
        MembershipFreeze f = MembershipFixtures.freeze(1L, m, day(11, 2), 3); // terminó el 04
        f.setStatus(FreezeStatus.COMPLETED);

        service.cancel(10L, "Error", ADMIN_ID);

        assertThat(f.getStatus()).isEqualTo(FreezeStatus.COMPLETED);
        assertThat(f.getAppliedDays()).isEqualTo(3);
        assertThat(f.getCancellationReason()).isNull();
        assertThat(m.getEndDate()).isEqualTo(day(12, 3));
        verify(auditService, never()).record(eq("MEMBERSHIP_FREEZE"), any(), any(), any(), any(), any());
    }

    @Test
    void aCancelledMembershipCannotBeCancelledAgain() {
        Membership m = stubMembership(confirmed(10L, client, day(11, 1)));
        m.setStatus(MembershipStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancel(10L, "Otra vez", ADMIN_ID)).isInstanceOf(ConflictException.class);
    }

    // ---- sincronización ----

    @Test
    void syncCopiesTheCalculatedStatus() {
        Membership expired = confirmed(10L, client, day(10, 1).minusYears(1));
        expired.setStatus(MembershipStatus.ACTIVE); // copia vieja
        Membership started = confirmed(11L, client, day(11, 1));
        started.setStatus(MembershipStatus.SCHEDULED); // copia vieja
        when(membershipRepository.findByStatusIn(any())).thenReturn(List.of(expired, started));

        assertThat(service.syncStoredStatuses()).isEqualTo(2);
        assertThat(expired.getStatus()).isEqualTo(MembershipStatus.EXPIRED);
        assertThat(started.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }
}
