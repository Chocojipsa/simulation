package com.timedeal.seatreservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SeatReservationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatReservationApplication.class, args);
    }
}
