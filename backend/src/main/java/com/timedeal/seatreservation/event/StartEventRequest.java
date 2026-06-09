package com.timedeal.seatreservation.event;

public record StartEventRequest(
        Integer aiUserCount,
        Integer aiConcurrency,
        String aiSpeed
) {}
