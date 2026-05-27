package com.timedeal.seatreservation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SystemClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
