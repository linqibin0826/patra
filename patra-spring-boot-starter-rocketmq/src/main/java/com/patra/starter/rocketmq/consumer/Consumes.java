package com.patra.starter.rocketmq.consumer;


import java.lang.annotation.*;

/**
 * 声明一个 MQ 消费者（运行时注册版本）。
 *
 * <p>核心思想：
 * - 使用领域层定义的 ChannelKey 枚举（例如 IngestChannels）标识通道；
 * - 框架在启动期按规范将 channel → topic/tag/group 自动映射并完成容器注册；
 * - 开发者只需专注处理泛型载荷（实现 {@link PatraMessageHandler}）。
 *
 * 使用示例（ingest）：
 * {@code
 *  @Consumes(channelEnum = com.patra.ingest.domain.messaging.IngestChannels.class,
 *            channelName = "TASK_READY",
 *            consumer = "relay",
 *            mode = ConsumeMode.CONCURRENT,
 *            concurrency = 2)
 *  public class TaskReadyLoggingHandler implements PatraMessageHandler<com.patra.ingest.domain.model.value.TaskReadyMessage> {
 *      public void handle(com.patra.starter.rocketmq.model.PatraMessage<com.patra.ingest.domain.model.value.TaskReadyMessage> msg) {
 *          // 仅记录日志，便于快速验证
 *      }
 *  }
 * }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Consumes {
    /** 领域通道目录（必须是实现了 ChannelKey 的枚举类） */
    Class<? extends Enum<?>> channelEnum();
    /** 枚举常量名称（如 "TASK_READY"），避免在注解中写完整字符串 channel */
    String channelName();
    /** 本消费者的职责名，用于生成 group（svc-{service}-{consumer}-cg）与观测标签 */
    String consumer();
    /** 消费模式：顺序（ORDERLY）或并发（CONCURRENT） */
    ConsumerMode mode() default ConsumerMode.CONCURRENT;
    /** 建议并发度（仅对并发模式生效），实际线程数由容器权衡调整 */
    int concurrency() default 1;
    /** 可选自定义选择表达式（默认从 ChannelKey.event() 推导为大写标签） */
    String selector() default "";
    /** 是否开启幂等去重（基于 eventId/dedupKey），存储实现可插拔 */
    boolean idempotent() default false;
}
