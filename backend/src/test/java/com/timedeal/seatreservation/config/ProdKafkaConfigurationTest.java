package com.timedeal.seatreservation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProdKafkaConfigurationTest {
    @Test
    void prodKafkaProducerSerializesPaymentEventsAsJson() throws IOException {
        PropertySource<?> properties = prodProperties();

        assertThat(properties.getProperty("spring.kafka.producer.key-serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(properties.getProperty("spring.kafka.producer.value-serializer"))
                .isEqualTo("org.springframework.kafka.support.serializer.JsonSerializer");
    }

    @Test
    void prodKafkaConsumerDeserializesPaymentEventsFromJson() throws IOException {
        PropertySource<?> properties = prodProperties();

        assertThat(properties.getProperty("spring.kafka.consumer.key-deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
        assertThat(properties.getProperty("spring.kafka.consumer.value-deserializer"))
                .isEqualTo("org.springframework.kafka.support.serializer.JsonDeserializer");
        assertThat(properties.getProperty("spring.kafka.consumer.properties.spring.json.trusted.packages"))
                .isEqualTo("com.timedeal.seatreservation.payment");
    }

    @Test
    void prodWaitingQueueUsesDemoSizedAdmissionDefaults() throws IOException {
        PropertySource<?> properties = prodProperties();

        assertThat(properties.getProperty("waiting-queue.admission-batch-size"))
                .isEqualTo("${WAITING_QUEUE_ADMISSION_BATCH_SIZE:5}");
        assertThat(properties.getProperty("waiting-queue.selection-ttl-seconds"))
                .isEqualTo("${WAITING_QUEUE_SELECTION_TTL_SECONDS:60}");
    }

    private PropertySource<?> prodProperties() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"));
        return sources.get(0);
    }
}
