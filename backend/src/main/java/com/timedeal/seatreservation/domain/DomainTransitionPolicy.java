package com.timedeal.seatreservation.domain;

import java.util.Map;
import java.util.Set;

public class DomainTransitionPolicy {
    private static final Map<SeatStatus, Set<SeatStatus>> SEAT_TRANSITIONS = Map.of(
            SeatStatus.AVAILABLE, Set.of(SeatStatus.HELD),
            SeatStatus.HELD, Set.of(SeatStatus.AVAILABLE, SeatStatus.PAYMENT_IN_PROGRESS),
            SeatStatus.PAYMENT_IN_PROGRESS, Set.of(SeatStatus.RESERVED, SeatStatus.AVAILABLE),
            SeatStatus.RESERVED, Set.of()
    );

    private static final Map<VirtualUserStatus, Set<VirtualUserStatus>> USER_TRANSITIONS = Map.of(
            VirtualUserStatus.CREATED, Set.of(VirtualUserStatus.QUEUED),
            VirtualUserStatus.QUEUED, Set.of(VirtualUserStatus.ADMITTED, VirtualUserStatus.SELECTING_SEAT, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.ADMITTED, Set.of(VirtualUserStatus.SELECTING_SEAT, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.SELECTING_SEAT, Set.of(VirtualUserStatus.SEAT_HELD, VirtualUserStatus.FAILED),
            VirtualUserStatus.SEAT_HELD, Set.of(VirtualUserStatus.PAYMENT_IN_PROGRESS, VirtualUserStatus.EXPIRED),
            VirtualUserStatus.PAYMENT_IN_PROGRESS, Set.of(VirtualUserStatus.RESERVED, VirtualUserStatus.FAILED),
            VirtualUserStatus.RESERVED, Set.of(),
            VirtualUserStatus.FAILED, Set.of(),
            VirtualUserStatus.EXPIRED, Set.of()
    );

    public boolean canChangeSeatStatus(SeatStatus from, SeatStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return SEAT_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean canChangeVirtualUserStatus(VirtualUserStatus from, VirtualUserStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return USER_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
