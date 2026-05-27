package com.timedeal.seatreservation.infra;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalInfrastructureFilesTest {
    @Test
    void dockerComposeDefinesLocalInfrastructureServices() throws Exception {
        String compose = Files.readString(Path.of("../infra/docker-compose.yml"));

        assertThat(compose).contains("postgres:");
        assertThat(compose).contains("redis:");
        assertThat(compose).contains("kafka:");
        assertThat(compose).contains("apache/kafka:3.7.2");
        assertThat(compose).doesNotContain("bitnami/kafka");
        assertThat(compose).contains("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1");
        assertThat(compose).contains("healthcheck:");
        assertThat(compose).contains("condition: service_healthy");
        assertThat(compose).contains("/dev/tcp/127.0.0.1/8080");
        assertThat(compose).contains("""
      api-a:
        condition: service_healthy
""");
        assertThat(compose).contains("""
      api-b:
        condition: service_healthy
""");
        assertThat(compose).contains("api-a:");
        assertThat(compose).contains("api-b:");
        assertThat(compose).contains("worker:");
        assertThat(compose).contains("nginx:");
    }

    @Test
    void nginxProxiesApiServersAndDisablesBufferingForSse() throws Exception {
        String nginx = Files.readString(Path.of("../infra/nginx.conf"));

        assertThat(nginx).contains("ip_hash;");
        assertThat(nginx).contains("server api-a:8080;");
        assertThat(nginx).contains("server api-b:8080;");
        assertThat(nginx).contains("location = /simulations");
        assertThat(nginx).contains("proxy_buffering off;");
    }

    @Test
    void backendDockerfileBuildsJavaSeventeenApplication() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("eclipse-temurin:17-jdk");
        assertThat(dockerfile).contains("eclipse-temurin:17-jre");
        assertThat(dockerfile).contains("bootJar");
    }

    @Test
    void localProfileDefinesPostgresRedisAndKafkaDefaults() throws Exception {
        String localProfile = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(localProfile).contains("jdbc:postgresql://localhost:5432/seat_reservation");
        assertThat(localProfile).contains("host: localhost");
        assertThat(localProfile).contains("bootstrap-servers: localhost:9094");
    }
}
