package com.timedeal.seatreservation.payment;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentKafkaConfigTest {
    private final PaymentKafkaConfig config = new PaymentKafkaConfig(5);

    @Test
    void paymentRequestTopicUsesMultiplePartitionsForParallelWorkers() {
        NewTopic topic = config.paymentEventsTopic();

        assertThat(topic.name()).isEqualTo("payment.events");
        assertThat(topic.numPartitions()).isEqualTo(5);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void paymentResultTopicUsesMultiplePartitionsForParallelResultApplication() {
        NewTopic topic = config.paymentResultsTopic();

        assertThat(topic.name()).isEqualTo("payment-results.events");
        assertThat(topic.numPartitions()).isEqualTo(5);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void listenerFactoryUsesConfiguredConcurrency() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        config.applyListenerConcurrency(factory);

        assertThat(new DirectFieldAccessor(factory).getPropertyValue("concurrency")).isEqualTo(5);
    }

    @Test
    void beanPostProcessorAppliesConcurrencyToKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        Object processed = config.kafkaListenerContainerFactoryConcurrencyPostProcessor()
                .postProcessAfterInitialization(factory, "kafkaListenerContainerFactory");

        assertThat(processed).isSameAs(factory);
        assertThat(new DirectFieldAccessor(factory).getPropertyValue("concurrency")).isEqualTo(5);
    }

    @Test
    void producerFactoryUsesJsonSerializationForPaymentEvents() {
        ProducerFactory<String, Object> producerFactory = config.createPaymentProducerFactory("kafka:9092");

        assertThat(producerFactory.getConfigurationProperties())
                .containsEntry("key.serializer", StringSerializer.class)
                .containsEntry("value.serializer", JsonSerializer.class);
    }

    @Test
    void consumerFactoryUsesJsonDeserializationForPaymentEvents() {
        ConsumerFactory<String, Object> consumerFactory = config.createPaymentConsumerFactory("kafka:9092");

        assertThat(consumerFactory.getConfigurationProperties())
                .containsEntry("key.deserializer", StringDeserializer.class)
                .containsEntry("value.deserializer", JsonDeserializer.class)
                .containsEntry(JsonDeserializer.TRUSTED_PACKAGES, "com.timedeal.seatreservation.payment");
    }
}
