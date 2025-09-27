package com.patra.starter.rocketmq.publisher;

import com.patra.starter.rocketmq.model.PatraMessage;

/**
 * 统一消息发布接口，隐藏底层 MQ 细节。
 */
public interface PatraMessagePublisher {

    void send(String destination, PatraMessage<?> message);
}
