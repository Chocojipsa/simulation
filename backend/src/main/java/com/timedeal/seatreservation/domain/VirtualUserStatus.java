package com.timedeal.seatreservation.domain;

public enum VirtualUserStatus {
    CREATED,
    WAITING_ROOM,
    QUEUED,
    ADMITTED,
    SELECTING_SEAT,
    SEAT_HELD,
    PAYMENT_IN_PROGRESS,
    PAYMENT_FAILED,
    RESERVED,
    FAILED,
    EXPIRED
}
