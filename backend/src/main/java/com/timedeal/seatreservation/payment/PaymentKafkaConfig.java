package com.timedeal.seatreservation.payment;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

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
