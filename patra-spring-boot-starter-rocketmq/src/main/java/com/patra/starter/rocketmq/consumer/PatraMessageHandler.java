package com.patra.starter.rocketmq.consumer;

import com.patra.starter.rocketmq.model.PatraMessage;

/**
 * 业务消费处理接口。
 *
 * <p>开发者实现该接口并配合 {@link Consumes} 注解完成监听注册。</p>
 * <p>建议：方法内部仅做业务编排，避免长阻塞；异常直接抛出以触发重试策略。</p>
 */
public interface PatraMessageHandler<T> {
    /**
     * 处理一条消息（异常将触发重试/死信流程）。
     */
    void handle(PatraMessage<T> message) throws Exception;
}

