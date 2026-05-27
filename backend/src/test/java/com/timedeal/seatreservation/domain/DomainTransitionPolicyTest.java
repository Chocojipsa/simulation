package com.timedeal.seatreservation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainTransitionPolicyTest {
    private final DomainTransitionPolicy policy = new DomainTransitionPolicy();

    @Test
    void seatCanMoveFromAvailableToHeld() {
        assertThat(policy.canChangeSeatStatus(SeatStatus.AVAILABLE, SeatStatus.HELD)).isTrue();
    }

    @Test
    void reservedSeatCannotMoveBackToHeld() {
        assertThat(policy.canChangeSeatStatus(SeatStatus.RESERVED, SeatStatus.HELD)).isFalse();
    }

    @Test
    void virtualUserCanFinishReservationAfterPayment() {
        assertThat(policy.canChangeVirtualUserStatus(
                VirtualUserStatus.PAYMENT_IN_PROGRESS,
                VirtualUserStatus.RESERVED
        )).isTrue();
    }
}
