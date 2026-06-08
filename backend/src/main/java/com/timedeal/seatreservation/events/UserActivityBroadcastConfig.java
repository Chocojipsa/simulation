package com.timedeal.seatreservation.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timedeal.seatreservation.simulation.UserActivityEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class UserActivityBroadcastConfig {
    public static final String ACTIVITY_CHANNEL = "simulation:activity";
    public static final String SNAPSHOT_CHANNEL = "simulation:snapshot";
    public static final String BATCH_ACTIVITY_CHANNEL = "simulation:batch-activity";

    @Bean
    public org.springframework.core.task.TaskExecutor redisListenerTaskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("redis-listener-");
        executor.initialize();
        return executor;
    }

    @Bean
    RedisMessageListenerContainer container(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter activityListenerAdapter,
            MessageListenerAdapter snapshotListenerAdapter,
            MessageListenerAdapter batchActivityListenerAdapter,
            org.springframework.core.task.TaskExecutor redisListenerTaskExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(activityListenerAdapter, new ChannelTopic(ACTIVITY_CHANNEL));
        container.addMessageListener(snapshotListenerAdapter, new ChannelTopic(SNAPSHOT_CHANNEL));
        container.addMessageListener(batchActivityListenerAdapter, new ChannelTopic(BATCH_ACTIVITY_CHANNEL));
        container.setTaskExecutor(redisListenerTaskExecutor);
        return container;
    }

    @Bean
    MessageListenerAdapter activityListenerAdapter(UserActivitySubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    MessageListenerAdapter snapshotListenerAdapter(SnapshotSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    MessageListenerAdapter batchActivityListenerAdapter(BatchActivitySubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    UserActivitySubscriber userActivitySubscriber(SimulationEventHub eventHub, ObjectMapper objectMapper) {
        return new UserActivitySubscriber(eventHub, objectMapper);
    }

    @Bean
    SnapshotSubscriber snapshotSubscriber(SimulationEventHub eventHub, ObjectMapper objectMapper) {
        return new SnapshotSubscriber(eventHub, objectMapper);
    }

    @Bean
    BatchActivitySubscriber batchActivitySubscriber(SimulationEventHub eventHub, ObjectMapper objectMapper) {
        return new BatchActivitySubscriber(eventHub, objectMapper);
    }
}
