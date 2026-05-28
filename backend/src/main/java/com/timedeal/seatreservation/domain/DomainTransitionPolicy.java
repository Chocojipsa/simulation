package com.timedeal.seatreservation.domain;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

public class DomainTransitionPolicy {
    private static final Map<SeatStatus, Set<SeatStatus>> SEAT_TRANSITIONS = Map.of(
            SeatStatus.AVAILABLE, Set.of(SeatStatus.HELD),
            SeatStatus.HELD, Set.of(SeatStatus.AVAILABLE, SeatStatus.PAYMENT_IN_PROGRESS),
            SeatStatus.PAYMENT_IN_PROGRESS, Set.of(SeatStatus.RESERVED, SeatStatus.AVAILABLE),
            SeatStatus.RESERVED, Set.of()
    );

    private static final Map<VirtualUserStatus, Set<VirtualUserStatus>> USER_TRANSITIONS = Map.ofEntries(
            entry(VirtualUserStatus.CREATED, Set.of(VirtualUserStatus.WAITING_ROOM, VirtualUserStatus.QUEUED)),
            entry(VirtualUserStatus.WAITING_ROOM, Set.of(VirtualUserStatus.QUEUED, VirtualUserStatus.EXPIRED)),
            entry(VirtualUserStatus.QUEUED, Set.of(VirtualUserStatus.ADMITTED, VirtualUserStatus.SELECTING_SEAT, VirtualUserStatus.EXPIRED)),
            entry(VirtualUserStatus.ADMITTED, Set.of(VirtualUserStatus.SELECTING_SEAT, VirtualUserStatus.EXPIRED)),
            entry(VirtualUserStatus.SELECTING_SEAT, Set.of(VirtualUserStatus.SEAT_HELD, VirtualUserStatus.FAILED)),
            entry(VirtualUserStatus.SEAT_HELD, Set.of(VirtualUserStatus.PAYMENT_IN_PROGRESS, VirtualUserStatus.EXPIRED)),
            entry(VirtualUserStatus.PAYMENT_IN_PROGRESS, Set.of(VirtualUserStatus.RESERVED, VirtualUserStatus.PAYMENT_FAILED, VirtualUserStatus.FAILED)),
            entry(VirtualUserStatus.PAYMENT_FAILED, Set.of(VirtualUserStatus.QUEUED, VirtualUserStatus.SELECTING_SEAT, VirtualUserStatus.FAILED)),
            entry(VirtualUserStatus.RESERVED, Set.of()),
            entry(VirtualUserStatus.FAILED, Set.of()),
            entry(VirtualUserStatus.EXPIRED, Set.of())
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
