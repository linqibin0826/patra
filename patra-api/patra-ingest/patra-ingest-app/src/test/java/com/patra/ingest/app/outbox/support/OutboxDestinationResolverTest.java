package com.patra.ingest.app.outbox.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OutboxDestinationResolverTest {

    @Test
    void resolveShouldComposeTopicAndTag() {
        PatraRocketMQProperties properties = new PatraRocketMQProperties();
        properties.getNaming().setNamespace("DEV");
        OutboxDestinationResolver resolver = new OutboxDestinationResolver(properties);
        String destination = resolver.resolve("ingest.task.ready");
        Assertions.assertEquals("DEV.INGEST.TASK:READY", destination);
    }

    @Test
    void resolveShouldRejectInvalidChannel() {
        PatraRocketMQProperties properties = new PatraRocketMQProperties();
        OutboxDestinationResolver resolver = new OutboxDestinationResolver(properties);
        Assertions.assertThrows(IllegalArgumentException.class, () -> resolver.resolve("task"));
    }
}
