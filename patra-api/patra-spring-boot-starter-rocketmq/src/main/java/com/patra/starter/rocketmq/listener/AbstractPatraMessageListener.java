package com.patra.starter.rocketmq.listener;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import com.patra.starter.rocketmq.model.PatraMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 消费端基类，统一日志、重试、消费位点等设置。
 */
@Slf4j
public abstract class AbstractPatraMessageListener<T> implements RocketMQListener<PatraMessage<T>>, RocketMQPushConsumerLifecycleListener {

    @Autowired(required = false)
    private PatraRocketMQProperties properties;

    @Override
    public void onMessage(PatraMessage<T> message) {
        try {
            handleMessage(message);
        } catch (Exception ex) {
            log.error("消费 MQ 消息失败 eventId={} traceId={} payload={} ", message.getEventId(), message.getTraceId(), message.getPayload(), ex);
            throw ex;
        }
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        if (properties != null) {
            consumer.setMaxReconsumeTimes(properties.getRetry().getMaxAttempts());
        }
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        RocketMQMessageListener listener = getClass().getAnnotation(RocketMQMessageListener.class);
        log.info("RocketMQ Consumer 启动完成, group={} messageModel={} listener={}", consumer.getConsumerGroup(), consumer.getMessageModel(), listener);
    }

    /**
     * 业务消费逻辑，由子类实现。
     */
    protected abstract void handleMessage(PatraMessage<T> message);
}
