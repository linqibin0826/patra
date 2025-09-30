package com.patra.starter.rocketmq.consumer;

import com.patra.starter.rocketmq.core.channel.ChannelKey;

import java.lang.annotation.*;

/**
 * 消息监听器注解：声明一个消息消费者。
 *
 * <p>设计原则：
 * <ul>
 *   <li>强制使用 ChannelKey 枚举，保证类型安全</li>
 *   <li>consumer 标识用于生成消费组：svc-{service}-{consumer}-cg</li>
 *   <li>框架自动注册容器并启动监听</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @MessageListener(
 *     channelEnum = IngestChannels.class,
 *     channelName = "TASK_READY",
 *     consumer = "relay",
 *     mode = ConsumeMode.CONCURRENT,
 *     concurrency = 2
 * )
 * public class TaskReadyHandler implements MessageHandler<TaskReadyPayload> {
 *     public void handle(Message<TaskReadyPayload> message) {
 *         // 业务处理
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageListener {

    /**
     * Channel 枚举类（必填，必须实现 ChannelKey 接口）。
     */
    Class<? extends Enum<? extends ChannelKey>> channelEnum();

    /**
     * Channel 枚举常量名称（必填，如 "TASK_READY"）。
     */
    String channelName();

    /**
     * 消费者职责标识（用于生成消费组）。
     * <p>格式：svc-{service}-{consumer}-cg
     * <p>例如：relay → svc-ingest-relay-cg
     */
    String consumer();

    /**
     * 消费模式。
     */
    ConsumeMode mode() default ConsumeMode.CONCURRENT;

    /**
     * 并发度（仅 CONCURRENT 模式生效）。
     */
    int concurrency() default 1;

    /**
     * 自定义选择表达式（可选，默认使用 channel.event()）。
     */
    String selector() default "";
}
