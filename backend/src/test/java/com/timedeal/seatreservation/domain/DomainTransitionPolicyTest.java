package com.timedeal.seatreservation.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DomainTransitionPolicyTest {
    private static final Map<SeatStatus, Set<SeatStatus>> ALLOWED_SEAT_TRANSITIONS = Map.of(
            SeatStatus.AVAILABLE, Set.of(SeatStatus.HELD),
            SeatStatus.HELD, Set.of(SeatStatus.AVAILABLE, SeatStatus.PAYMENT_IN_PROGRESS),
            SeatStatus.PAYMENT_IN_PROGRESS, Set.of(SeatStatus.RESERVED, SeatStatus.AVAILABLE),
            SeatStatus.RESERVED, Set.of()
    );

    private static final Map<VirtualUserStatus, Set<VirtualUserStatus>> ALLOWED_USER_TRANSITIONS = Map.of(
            VirtualUserStatus.CREATED, Set.of(VirtualUserStatus.QUEUED),
            VirtualUserStatus.QUEUED, Set.of(VirtualUserStatus.ADMITTED, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.ADMITTED, Set.of(VirtualUserStatus.SELECTING_SEAT, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.SELECTING_SEAT, Set.of(VirtualUserStatus.SEAT_HELD, VirtualUserStatus.FAILED),
            VirtualUserStatus.SEAT_HELD, Set.of(VirtualUserStatus.PAYMENT_IN_PROGRESS, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.PAYMENT_IN_PROGRESS, Set.of(VirtualUserStatus.RESERVED, VirtualUserStatus.FAILED),
            VirtualUserStatus.RESERVED, Set.of(),
            VirtualUserStatus.FAILED, Set.of(),
            VirtualUserStatus.EXPIRED, Set.of()
    );

    private final DomainTransitionPolicy policy = new DomainTransitionPolicy();

    @Test
    void onlyAllowedSeatTransitionsCanChangeStatus() {
        for (SeatStatus from : SeatStatus.values()) {
            for (SeatStatus to : SeatStatus.values()) {
                assertThat(policy.canChangeSeatStatus(from, to))
                        .as("seat transition from %s to %s", from, to)
                        .isEqualTo(ALLOWED_SEAT_TRANSITIONS.get(from).contains(to));
            }
        }
    }

    @Test
    void seatTransitionWithNullReturnsFalse() {
        assertThat(policy.canChangeSeatStatus(null, SeatStatus.HELD)).isFalse();
        assertThat(policy.canChangeSeatStatus(SeatStatus.AVAILABLE, null)).isFalse();
        assertThat(policy.canChangeSeatStatus(null, null)).isFalse();
    }

    @Test
    void onlyAllowedVirtualUserTransitionsCanChangeStatus() {
        for (VirtualUserStatus from : VirtualUserStatus.values()) {
            for (VirtualUserStatus to : VirtualUserStatus.values()) {
                assertThat(policy.canChangeVirtualUserStatus(from, to))
                        .as("virtual user transition from %s to %s", from, to)
                        .isEqualTo(ALLOWED_USER_TRANSITIONS.get(from).contains(to));
            }
        }
    }

    @Test
    void virtualUserTransitionWithNullReturnsFalse() {
        assertThat(policy.canChangeVirtualUserStatus(null, VirtualUserStatus.QUEUED)).isFalse();
        assertThat(policy.canChangeVirtualUserStatus(VirtualUserStatus.CREATED, null)).isFalse();
        assertThat(policy.canChangeVirtualUserStatus(null, null)).isFalse();
    }
}
