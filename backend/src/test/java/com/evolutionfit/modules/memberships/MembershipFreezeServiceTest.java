package com.evolutionfit.modules.memberships;

import static com.evolutionfit.modules.memberships.MembershipFixtures.CLOCK;
import static com.evolutionfit.modules.memberships.MembershipFixtures.TODAY;
import static com.evolutionfit.modules.memberships.MembershipFixtures.cancelFreeze;
import static com.evolutionfit.modules.memberships.MembershipFixtures.client;
import static com.evolutionfit.modules.memberships.MembershipFixtures.confirmed;
import static com.evolutionfit.modules.memberships.MembershipFixtures.day;
import static com.evolutionfit.modules.memberships.MembershipFixtures.freeze;
import static com.evolutionfit.modules.memberships.MembershipFixtures.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.memberships.dto.CreateFreezeRequest;
import com.evolutionfit.modules.memberships.dto.ExtendFreezeRequest;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipFreezeServiceTest {

    private static final long RECEPTION_ID = 8L;
    private static final long ADMIN_ID = 7L;

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private MembershipFreezeRepository freezeRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private AuditService auditService;

    private MembershipFreezeService service;
    private Client client;
    private final List<Membership> clientMemberships = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MembershipSupport support = new MembershipSupport(clientRepository, membershipRepository, auditService, CLOCK);
        service = new MembershipFreezeService(membershipRepository, freezeRepository, support,
                new MembershipProperties(5, 30));
        client = client(1L, true);
        when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(client));
        when(membershipRepository.findByClientIdOrderByStartDateDescIdDesc(1L)).thenReturn(clientMemberships);
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));
        when(freezeRepository.save(any(MembershipFreeze.class))).thenAnswer(inv -> {
            MembershipFreeze f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(90L);
            }
            return f;
        });
    }

    private Membership stub(Membership m) {
        when(membershipRepository.findClientIdById(m.getId())).thenReturn(Optional.of(1L));
        when(membershipRepository.findById(m.getId())).thenReturn(Optional.of(m));
        clientMemberships.add(m);
        return m;
    }

    private MembershipFreeze stubFreeze(MembershipFreeze f) {
        when(freezeRepository.findClientIdById(f.getId())).thenReturn(Optional.of(1L));
        when(freezeRepository.findById(f.getId())).thenReturn(Optional.of(f));
        return f;
    }

    /** Vigente del 01-11 al 30-11 (vencimiento base). */
    private Membership current() {
        return stub(confirmed(10L, client, day(11, 1)));
    }

    private static CreateFreezeRequest request(LocalDate start, int days) {
        return new CreateFreezeRequest(start, days, " Viaje ");
    }

    // ---- registrar ----

    @Test
    void freezeDaysAreAddedToTheExpiryAndTheClientIsFrozen() {
        Membership m = current();

        MembershipResponse response = service.create(10L, request(TODAY, 10), RECEPTION_ID);

        assertThat(m.getEndDate()).isEqualTo(day(12, 10));
        assertThat(m.getBaseEndDate()).isEqualTo(day(11, 30));
        assertThat(response.status()).isEqualTo(MembershipStatus.FROZEN);
        assertThat(response.freezes()).singleElement().satisfies(f -> {
            assertThat(f.startDate()).isEqualTo(TODAY);
            assertThat(f.endDate()).isEqualTo(day(11, 18));
            assertThat(f.appliedDays()).isEqualTo(10);
            assertThat(f.reason()).isEqualTo("Viaje");
            assertThat(f.status()).isEqualTo(FreezeStatus.ACTIVE);
        });
        verify(auditService).record(eq("MEMBERSHIP_FREEZE"), eq(90L), eq(AuditService.ACTION_CREATE),
                any(), any(), eq(RECEPTION_ID));
    }

    @Test
    void aFreezeCanBeScheduledForTheFuture() {
        Membership m = current();

        MembershipResponse response = service.create(10L, request(day(11, 20), 5), RECEPTION_ID);

        assertThat(response.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(response.freezes().get(0).status()).isEqualTo(FreezeStatus.PENDING);
        assertThat(m.getEndDate()).isEqualTo(day(12, 5));
    }

    @Test
    void freezingMovesTheScheduledNextMembership() {
        current();
        Membership next = stub(confirmed(11L, client, day(12, 1))); // 01-12 a 30-12

        service.create(10L, request(TODAY, 5), RECEPTION_ID);

        assertThat(next.getStartDate()).isEqualTo(day(12, 6));
        assertThat(next.getEndDate()).isEqualTo(day(1, 4));
        verify(auditService).record(eq("MEMBERSHIP"), eq(11L), eq(AuditService.ACTION_RESCHEDULE),
                any(), any(), eq(RECEPTION_ID));
    }

    @Test
    void maximumThirtyDays() {
        current();
        assertThatThrownBy(() -> service.create(10L, request(TODAY, 31), RECEPTION_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("30 días");
        service.create(10L, request(TODAY, 30), RECEPTION_ID);
    }

    @Test
    void startMustBeTodayOrLaterAndNotAfterTheExpiry() {
        current();
        assertThatThrownBy(() -> service.create(10L, request(TODAY.minusDays(1), 5), RECEPTION_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pasada");
        assertThatThrownBy(() -> service.create(10L, request(day(12, 1), 5), RECEPTION_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("vencimiento");
        verify(freezeRepository, never()).save(any());
    }

    @Test
    void onlyOneFreezePerMembership() {
        Membership m = current();
        freeze(1L, m, day(11, 2), 3);

        assertThatThrownBy(() -> service.create(10L, request(TODAY, 5), RECEPTION_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("una por membresía");
    }

    @Test
    void aFreezeCancelledBeforeStartingAllowsAnotherOne() {
        Membership m = current();
        cancelFreeze(freeze(1L, m, day(11, 20), 5), TODAY, 0);

        service.create(10L, request(TODAY, 5), RECEPTION_ID);
        assertThat(m.getEndDate()).isEqualTo(day(12, 5));
    }

    @Test
    void freezeUsedInThePreviousSystemBlocksANewOne() {
        Membership m = current();
        m.setMigrated(true);
        m.setFreezeUsedInPreviousSystem(true);

        assertThatThrownBy(() -> service.create(10L, request(TODAY, 5), RECEPTION_ID))
                .isInstanceOf(ConflictException.class).hasMessageContaining("sistema anterior");
    }

    @Test
    void onlyThePaidMembershipInForceCanBeFrozen() {
        stub(pending(20L, client, TODAY));
        assertThatThrownBy(() -> service.create(20L, request(TODAY, 5), RECEPTION_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pendiente de pago");

        stub(confirmed(21L, client, day(12, 1)));
        assertThatThrownBy(() -> service.create(21L, request(day(12, 1), 5), RECEPTION_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("vigente");

        Membership cancelled = stub(confirmed(22L, client, day(11, 1)));
        cancelled.setStatus(MembershipStatus.CANCELLED);
        assertThatThrownBy(() -> service.create(22L, request(TODAY, 5), RECEPTION_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("anulada");
    }

    @Test
    void registeringLocksTheClientFirst() {
        current();
        service.create(10L, request(TODAY, 5), RECEPTION_ID);

        InOrder order = inOrder(clientRepository, membershipRepository);
        order.verify(clientRepository).findByIdForUpdate(1L);
        order.verify(membershipRepository).findById(10L);
    }

    // ---- cancelar ----

    @Test
    void cancellingBeforeItStartsAddsNothingAndDoesNotUseTheFreeze() {
        Membership m = current();
        MembershipFreeze f = stubFreeze(freeze(1L, m, day(11, 20), 5));

        MembershipResponse response = service.cancel(1L, " Ya no viaja ", ADMIN_ID);

        assertThat(f.getAppliedDays()).isZero();
        assertThat(f.getStatus()).isEqualTo(FreezeStatus.CANCELLED);
        assertThat(f.getCancellationReason()).isEqualTo("Ya no viaja");
        assertThat(m.getEndDate()).isEqualTo(day(11, 30));
        assertThat(response.canFreeze()).isTrue();
        verify(auditService).record(eq("MEMBERSHIP_FREEZE"), eq(1L), eq(AuditService.ACTION_CANCEL),
                any(), any(), eq(ADMIN_ID));
    }

    @Test
    void cancellingAnOngoingFreezeAddsOnlyTheElapsedDaysAndUsesTheFreeze() {
        Membership m = current();
        MembershipFreeze f = stubFreeze(freeze(1L, m, day(11, 5), 10)); // 05 al 14

        MembershipResponse response = service.cancel(1L, "Volvió antes", ADMIN_ID);

        assertThat(f.getAppliedDays()).isEqualTo(4);
        assertThat(m.getEndDate()).isEqualTo(day(12, 4));
        assertThat(response.status()).isEqualTo(MembershipStatus.ACTIVE); // hoy ya puede entrar
        assertThat(response.canFreeze()).isFalse();
    }

    @Test
    void cancellingMovesTheNextMembershipBack() {
        Membership m = current();
        MembershipFreeze f = stubFreeze(freeze(1L, m, day(11, 5), 10)); // vence 10-12
        Membership next = stub(confirmed(11L, client, day(12, 11)));

        service.cancel(1L, "Volvió antes", ADMIN_ID);

        assertThat(next.getStartDate()).isEqualTo(day(12, 5));
    }

    @Test
    void aFinishedOrCancelledFreezeCannotBeCancelled() {
        Membership m = current();
        stubFreeze(freeze(1L, m, day(11, 1), 3)); // terminó el 03
        assertThatThrownBy(() -> service.cancel(1L, "x", ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya terminó");

        Membership other = stub(confirmed(12L, client, day(11, 1)));
        MembershipFreeze cancelled = stubFreeze(freeze(2L, other, day(11, 20), 3));
        cancelFreeze(cancelled, TODAY, 0);
        assertThatThrownBy(() -> service.cancel(2L, "x", ADMIN_ID)).isInstanceOf(ConflictException.class);
    }

    // ---- ampliar ----

    @Test
    void extendingAddsTheNewDaysToTheExpiryAndMovesTheNext() {
        Membership m = current();
        MembershipFreeze f = stubFreeze(freeze(1L, m, TODAY, 5)); // vence 05-12
        Membership next = stub(confirmed(11L, client, day(12, 6)));

        service.extend(1L, new ExtendFreezeRequest(12, "Incapacidad médica"), ADMIN_ID);

        assertThat(f.getEndDate()).isEqualTo(day(11, 20));
        assertThat(f.getAppliedDays()).isEqualTo(12);
        assertThat(m.getEndDate()).isEqualTo(day(12, 12));
        assertThat(next.getStartDate()).isEqualTo(day(12, 13));
        verify(auditService).record(eq("MEMBERSHIP_FREEZE"), eq(1L), eq(AuditService.ACTION_EXTEND),
                any(), any(), eq(ADMIN_ID));
    }

    @Test
    void extensionMustGrowAndStayWithinTheMaximum() {
        Membership m = current();
        stubFreeze(freeze(1L, m, TODAY, 5));

        assertThatThrownBy(() -> service.extend(1L, new ExtendFreezeRequest(5, "x"), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("mayor");
        assertThatThrownBy(() -> service.extend(1L, new ExtendFreezeRequest(31, "x"), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("30 días");
    }

    @Test
    void finishedOrCancelledFreezesCannotBeExtended() {
        Membership m = current();
        stubFreeze(freeze(1L, m, day(11, 1), 3));
        assertThatThrownBy(() -> service.extend(1L, new ExtendFreezeRequest(10, "x"), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya terminó");

        Membership other = stub(confirmed(12L, client, day(11, 1)));
        MembershipFreeze cancelled = stubFreeze(freeze(2L, other, day(11, 20), 3));
        cancelFreeze(cancelled, TODAY, 0);
        assertThatThrownBy(() -> service.extend(2L, new ExtendFreezeRequest(10, "x"), ADMIN_ID))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cancelada");
    }
}
