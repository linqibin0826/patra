package com.patra.ingest.api.messaging;

import com.patra.common.messaging.PublishedChannel;

/**
 * Ingest 模块对外发布的消息通道（API 契约）。
 * <p>
 * <b>设计原则</b>：
 * <ul>
 *   <li>消费方通过此类获取 channel 字符串，避免硬编码和猜测</li>
 *   <li>使用 {@link PublishedChannel} 注解声明载荷类型和业务含义</li>
 *   <li>保持语义稳定，变更需考虑向后兼容</li>
 * </ul>
 * </p>
 *
 * <p><b>消费方使用示例</b>：
 * <pre>{@code
 * @Component
 * @Consumes(channel = IngestPublishedChannels.TASK_READY,
 *           consumer = "my-handler")
 * public class TaskReadyHandler implements PatraMessageHandler<TaskReadyEvent> {
 *     // ...
 * }
 * }</pre>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class IngestPublishedChannels {

    private IngestPublishedChannels() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 采集任务准备就绪事件。
     * <p>
     * 当一个采集任务完成调度准备并等待执行时触发。
     * </p>
     */
    @PublishedChannel(
            description = "采集任务准备就绪事件",
            payloadType = TaskReadyEvent.class
    )
    public static final String TASK_READY = "ingest.task.ready";

    // 未来可扩展其他通道：
    // public static final String TASK_COMPLETED = "ingest.task.completed";
    // public static final String TASK_FAILED = "ingest.task.failed";
}
