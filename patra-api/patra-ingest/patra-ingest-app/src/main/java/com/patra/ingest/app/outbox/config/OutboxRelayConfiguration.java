package com.patra.ingest.app.outbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.support.OutboxDestinationResolver;
import com.patra.ingest.app.outbox.support.TaskReadyMessageMapper;
import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox Relay Bean 注册。
 */
@Configuration
public class OutboxRelayConfiguration {

    @Bean
    public OutboxDestinationResolver outboxDestinationResolver(PatraRocketMQProperties rocketMQProperties) {
        return new OutboxDestinationResolver(rocketMQProperties);
    }

    @Bean
    public TaskReadyMessageMapper taskReadyMessageMapper(ObjectMapper objectMapper) {
        return new TaskReadyMessageMapper(objectMapper);
    }
}
