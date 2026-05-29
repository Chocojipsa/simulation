package com.timedeal.seatreservation.payment;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class PaymentKafkaConfig {
    private static final short LOCAL_REPLICATION_FACTOR = 1;

    private final int partitions;

    public PaymentKafkaConfig(@Value("${payment.kafka.partitions:5}") int partitions) {
        this.partitions = Math.max(1, partitions);
    }

    @Bean
    NewTopic paymentEventsTopic() {
        return new NewTopic(PaymentSimulationWorker.PAYMENT_EVENTS_TOPIC, partitions, LOCAL_REPLICATION_FACTOR);
    }

    @Bean
    NewTopic paymentResultsTopic() {
        return new NewTopic(PaymentSimulationWorker.PAYMENT_RESULTS_TOPIC, partitions, LOCAL_REPLICATION_FACTOR);
    }

    @Bean
    ProducerFactory<String, Object> paymentProducerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        return createPaymentProducerFactory(bootstrapServers);
    }

    ProducerFactory<String, Object> createPaymentProducerFactory(String bootstrapServers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    KafkaTemplate<String, PaymentRequestedEvent> paymentRequestedKafkaTemplate(
            ProducerFactory<String, Object> paymentProducerFactory
    ) {
        return paymentKafkaTemplate(paymentProducerFactory);
    }

    @Bean
    KafkaTemplate<String, PaymentResultEvent> paymentResultKafkaTemplate(
            ProducerFactory<String, Object> paymentProducerFactory
    ) {
        return paymentKafkaTemplate(paymentProducerFactory);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> KafkaTemplate<String, T> paymentKafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate((ProducerFactory) producerFactory);
    }

    @Bean
    ConsumerFactory<String, Object> paymentConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        return createPaymentConsumerFactory(bootstrapServers);
    }

    ConsumerFactory<String, Object> createPaymentConsumerFactory(String bootstrapServers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.timedeal.seatreservation.payment");
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> paymentConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentConsumerFactory);
        applyListenerConcurrency(factory);
        return factory;
    }

    void applyListenerConcurrency(ConcurrentKafkaListenerContainerFactory<String, Object> factory) {
        factory.setConcurrency(partitions);
    }

    @Bean
    BeanPostProcessor kafkaListenerContainerFactoryConcurrencyPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings("unchecked")
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                    applyListenerConcurrency((ConcurrentKafkaListenerContainerFactory<String, Object>) factory);
                }
                return bean;
            }
        };
    }
}
