package com.timedeal.seatreservation.infra;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalInfrastructureFilesTest {
    @Test
    void dockerComposeDefinesLocalInfrastructureServices() throws Exception {
        Path path = Path.of("../infra/docker-compose.yml");
        if (!Files.exists(path)) {
            return;
        }
        String compose = Files.readString(path);

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
        assertThat(compose).contains("traffic-generator:");
        assertThat(compose).contains("SPRING_PROFILES_ACTIVE: local,generator");
        assertThat(compose).contains("TRAFFIC_GENERATOR_TARGET_BASE_URL: http://nginx:8080");
        assertThat(compose).contains("TRAFFIC_GENERATOR_CONTROL_BASE_URL: http://traffic-generator:8080");
        assertThat(compose).contains("APP_INSTANCE_ID: api-a");
        assertThat(compose).contains("APP_INSTANCE_ID: api-b");
        assertThat(compose).contains("worker:");
        assertThat(compose).contains("nginx:");
    }

    @Test
    void nginxProxiesApiServersWithoutStickyRouting() throws Exception {
        Path path = Path.of("../infra/nginx.conf");
        if (!Files.exists(path)) {
            return;
        }
        String nginx = Files.readString(path);

        assertThat(nginx).doesNotContain("ip_hash;");
        assertThat(nginx).contains("server api-a:8080;");
        assertThat(nginx).contains("server api-b:8080;");
        assertThat(nginx).contains("location /api/");
        assertThat(nginx).contains("Access-Control-Allow-Origin");
        assertThat(nginx).contains("return 204;");
        assertThat(nginx).contains("proxy_buffering off;");
        assertThat(nginx).contains("location = /health");
        assertThat(nginx).contains("return 404");
        assertThat(nginx).doesNotContain("""
    location / {
      proxy_pass http://api_servers;
""");
    }

    @Test
    void backendDoesNotShipLegacyStaticSite() {
        assertThat(Files.exists(Path.of("src/main/resources/static/index.html"))).isFalse();
        assertThat(Files.exists(Path.of("src/main/resources/static/app.js"))).isFalse();
        assertThat(Files.exists(Path.of("src/main/resources/static/styles.css"))).isFalse();
    }

    @Test
    void backendDockerfileBuildsJavaSeventeenApplication() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("eclipse-temurin:17-jdk");
        assertThat(dockerfile).contains("eclipse-temurin:17-jre");
        assertThat(dockerfile).contains("bootJar");
    }

    @Test
    void localProfileDefinesPostgresAndRedisDefaults() throws Exception {
        String localProfile = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(localProfile).contains("jdbc:postgresql://localhost:5432/seat_reservation");
        assertThat(localProfile).contains("host: localhost");
    }

    @Test
    void productionDeploymentDocsAndProfileExist() {
        assertThat(Files.exists(Path.of("src/main/resources/application-prod.yml"))).isTrue();
        Path docsPath = Path.of("../docs");
        if (Files.exists(docsPath)) {
            assertThat(Files.exists(Path.of("../docs/deployment/production-v1.md"))).isTrue();
            assertThat(Files.exists(Path.of("../docs/deployment/environment-variables.md"))).isTrue();
            assertThat(Files.exists(Path.of("../docs/deployment/vercel-env.example"))).isTrue();
        }
    }
}
