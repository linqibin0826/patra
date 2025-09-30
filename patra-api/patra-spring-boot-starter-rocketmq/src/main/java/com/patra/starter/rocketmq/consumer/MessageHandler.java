package com.patra.starter.rocketmq.consumer;

import com.patra.starter.rocketmq.core.message.Message;

/**
 * 消息处理器接口。
 *
 * <p>业务方实现此接口并配合 {@link MessageListener} 注解完成消费逻辑。
 *
 * @param <T> 载荷类型
 * @author linqibin
 * @since 0.1.0
 */
public interface MessageHandler<T> {

    /**
     * 处理消息（异常将触发重试）。
     *
     * @param message 消息
     * @throws Exception 处理异常
     */
    void handle(Message<T> message) throws Exception;
}
