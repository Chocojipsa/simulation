package com.timedeal.seatreservation.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEventStateStoreTest {
    private final UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void createsReadyMetadataAndStartsCountdownOnce() {
        InMemoryLiveEventStateStore store = new InMemoryLiveEventStateStore();
        Instant now = Instant.parse("2026-05-28T12:00:00Z");

        LiveEventMetadata ready = store.getOrCreate(eventId, now);
        LiveEventMetadata countdown = store.startCountdown(eventId, now, Duration.ofSeconds(60), Duration.ofMinutes(5));
        LiveEventMetadata duplicate = store.startCountdown(eventId, now.plusSeconds(10), Duration.ofSeconds(60), Duration.ofMinutes(5));

        assertThat(ready.statusAt(now)).isEqualTo(LiveEventStatus.READY);
        assertThat(countdown.statusAt(now)).isEqualTo(LiveEventStatus.COUNTDOWN);
        assertThat(countdown.opensAt()).isEqualTo(now.plusSeconds(60));
        assertThat(countdown.endsAt()).isEqualTo(now.plusSeconds(360));
        assertThat(duplicate.opensAt()).isEqualTo(countdown.opensAt());
        assertThat(duplicate.generation()).isEqualTo(1);
    }

    @Test
    void derivesOpenAndEndedFromClock() {
        InMemoryLiveEventStateStore store = new InMemoryLiveEventStateStore();
        Instant now = Instant.parse("2026-05-28T12:00:00Z");

        LiveEventMetadata metadata = store.startCountdown(eventId, now, Duration.ofSeconds(60), Duration.ofMinutes(5));

        assertThat(metadata.statusAt(now.plusSeconds(61))).isEqualTo(LiveEventStatus.OPEN);
        assertThat(metadata.statusAt(now.plusSeconds(361))).isEqualTo(LiveEventStatus.ENDED);
    }

    @Test
    void resetAfterEndedIncrementsGenerationAndClearsAiFlag() {
        InMemoryLiveEventStateStore store = new InMemoryLiveEventStateStore();
        Instant now = Instant.parse("2026-05-28T12:00:00Z");
        store.startCountdown(eventId, now, Duration.ofSeconds(60), Duration.ofMinutes(5));
        assertThat(store.claimAiStart(eventId)).isTrue();
        assertThat(store.claimAiStart(eventId)).isFalse();

        LiveEventMetadata reset = store.reset(eventId, now.plusSeconds(400));

        assertThat(reset.generation()).isEqualTo(2);
        assertThat(reset.statusAt(now.plusSeconds(400))).isEqualTo(LiveEventStatus.READY);
        assertThat(reset.aiStarted()).isFalse();
        assertThat(reset.opensAt()).isNull();
        assertThat(reset.endsAt()).isNull();
    }
}
