package com.timedeal.seatreservation.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServerIdentity {
    private final String id;

    public ServerIdentity(@Value("${app.instance-id:${HOSTNAME:local}}") String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
