package com.evolutionfit.modules.memberships;

import static com.evolutionfit.modules.memberships.MembershipFixtures.TODAY;
import static com.evolutionfit.modules.memberships.MembershipFixtures.cancelFreeze;
import static com.evolutionfit.modules.memberships.MembershipFixtures.client;
import static com.evolutionfit.modules.memberships.MembershipFixtures.confirmed;
import static com.evolutionfit.modules.memberships.MembershipFixtures.day;
import static com.evolutionfit.modules.memberships.MembershipFixtures.freeze;
import static com.evolutionfit.modules.memberships.MembershipFixtures.pending;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.evolutionfit.modules.clients.Client;

import org.junit.jupiter.api.Test;

class MembershipRulesTest {

    private final Client client = client(1L, true);

    @Test
    void statusDependsOnTheDate() {
        Membership m = confirmed(1L, client, day(11, 8)); // 08-11 a 07-12

        assertThat(MembershipRules.resolve(m, day(11, 7))).isEqualTo(MembershipStatus.SCHEDULED);
        assertThat(MembershipRules.resolve(m, day(11, 8))).isEqualTo(MembershipStatus.ACTIVE);
        // El último día todavía es válido; el día siguiente está vencida (sin días de gracia).
        assertThat(MembershipRules.resolve(m, day(12, 7))).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(MembershipRules.resolve(m, day(12, 8))).isEqualTo(MembershipStatus.EXPIRED);
    }

    @Test
    void pendingAndCancelledDoNotDependOnTheDate() {
        Membership p = pending(1L, client, TODAY);
        assertThat(MembershipRules.resolve(p, TODAY)).isEqualTo(MembershipStatus.PENDING);
        assertThat(MembershipRules.isInForce(p, TODAY)).isFalse();

        Membership c = confirmed(2L, client, TODAY);
        c.setStatus(MembershipStatus.CANCELLED);
        assertThat(MembershipRules.resolve(c, TODAY)).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(MembershipRules.isInForce(c, TODAY)).isFalse();
    }

    @Test
    void frozenOnlyDuringTheFreezeDays() {
        Membership m = confirmed(1L, client, day(11, 1));
        freeze(1L, m, day(11, 10), 5); // 10 al 14

        assertThat(MembershipRules.resolve(m, day(11, 9))).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(MembershipRules.resolve(m, day(11, 10))).isEqualTo(MembershipStatus.FROZEN);
        assertThat(MembershipRules.resolve(m, day(11, 14))).isEqualTo(MembershipStatus.FROZEN);
        assertThat(MembershipRules.resolve(m, day(11, 15))).isEqualTo(MembershipStatus.ACTIVE);
        // Base 30-11 + 5 días congelados.
        assertThat(m.getEndDate()).isEqualTo(day(12, 5));
        assertThat(MembershipRules.resolve(m, day(12, 5))).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(MembershipRules.resolve(m, day(12, 6))).isEqualTo(MembershipStatus.EXPIRED);
    }

    @Test
    void aCancelledFreezeOnlyCoversTheAppliedDays() {
        Membership m = confirmed(1L, client, day(11, 1));
        MembershipFreeze f = freeze(1L, m, day(11, 5), 10);
        cancelFreeze(f, TODAY, 4); // 5 al 8 congelados

        assertThat(MembershipRules.resolve(m, day(11, 8))).isEqualTo(MembershipStatus.FROZEN);
        assertThat(MembershipRules.resolve(m, TODAY)).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(m.getEndDate()).isEqualTo(day(12, 4));
    }

    @Test
    void freezeStatusDependsOnTheDate() {
        Membership m = confirmed(1L, client, day(11, 1));
        MembershipFreeze f = freeze(1L, m, day(11, 10), 5);

        assertThat(MembershipRules.resolve(f, TODAY)).isEqualTo(FreezeStatus.PENDING);
        assertThat(MembershipRules.resolve(f, day(11, 10))).isEqualTo(FreezeStatus.ACTIVE);
        assertThat(MembershipRules.resolve(f, day(11, 15))).isEqualTo(FreezeStatus.COMPLETED);
        cancelFreeze(f, TODAY, 0);
        assertThat(MembershipRules.resolve(f, day(11, 10))).isEqualTo(FreezeStatus.CANCELLED);
    }

    @Test
    void aFreezeCancelledBeforeStartingDoesNotUseTheOnlyFreeze() {
        Membership m = confirmed(1L, client, day(11, 1));
        MembershipFreeze f = freeze(1L, m, day(11, 20), 5);
        assertThat(MembershipRules.freezeUsed(m)).isTrue();

        cancelFreeze(f, TODAY, 0);
        assertThat(MembershipRules.freezeUsed(m)).isFalse();
        assertThat(MembershipRules.canFreeze(m, TODAY)).isTrue();
    }

    @Test
    void aFreezeCancelledAfterStartingUsesTheOnlyFreeze() {
        Membership m = confirmed(1L, client, day(11, 1));
        cancelFreeze(freeze(1L, m, day(11, 5), 10), TODAY, 4);
        assertThat(MembershipRules.freezeUsed(m)).isTrue();

        // También si se cancela el mismo día en que empieza (0 días aplicados).
        Membership other = confirmed(2L, client, day(11, 1));
        cancelFreeze(freeze(2L, other, TODAY, 10), TODAY, 0);
        assertThat(MembershipRules.freezeUsed(other)).isTrue();
    }

    @Test
    void migratedMembershipCanDeclareTheFreezeAsUsed() {
        Membership m = confirmed(1L, client, day(11, 1));
        m.setMigrated(true);
        m.setFreezeUsedInPreviousSystem(true);
        assertThat(MembershipRules.canFreeze(m, TODAY)).isFalse();
    }

    @Test
    void onlyTheMembershipInForceCanBeFrozen() {
        assertThat(MembershipRules.canFreeze(confirmed(1L, client, day(11, 1)), TODAY)).isTrue();
        assertThat(MembershipRules.canFreeze(confirmed(2L, client, day(12, 1)), TODAY)).isFalse();
        assertThat(MembershipRules.canFreeze(confirmed(3L, client, day(10, 1).minusYears(1)), TODAY)).isFalse();
        assertThat(MembershipRules.canFreeze(pending(4L, client, TODAY), TODAY)).isFalse();
    }

    @Test
    void currentAndNextAreFoundByDate() {
        Membership expired = confirmed(1L, client, day(9, 1).minusYears(1));
        Membership current = confirmed(2L, client, day(11, 1));
        Membership scheduled = confirmed(3L, client, day(12, 1));
        List<Membership> all = List.of(scheduled, current, expired);

        assertThat(MembershipRules.current(all, TODAY)).containsSame(current);
        assertThat(MembershipRules.next(all, TODAY)).containsSame(scheduled);
        assertThat(MembershipRules.isNext(pending(4L, client, TODAY), TODAY)).isTrue();
    }
}
