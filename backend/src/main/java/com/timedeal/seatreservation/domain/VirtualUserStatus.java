package com.timedeal.seatreservation.domain;

public enum VirtualUserStatus {
    CREATED,
    QUEUED,
    ADMITTED,
    SELECTING_SEAT,
    SEAT_HELD,
    PAYMENT_IN_PROGRESS,
    RESERVED,
    FAILED,
    EXPIRED
}
