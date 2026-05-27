package com.timedeal.seatreservation.simulation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StaticDashboardContentTest {
    @Test
    void dashboardShowsWaitingQueueList() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        String script = Files.readString(Path.of("src/main/resources/static/app.js"));

        assertThat(html).contains("대기열");
        assertThat(html).contains("waitingQueue");
        assertThat(script).contains("renderWaitingQueue");
        assertThat(script).contains("QUEUED");
    }
}
