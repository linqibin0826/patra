package com.patra.common.messaging;

import java.lang.annotation.*;

/**
 * 标记一个 channel 为"对外发布的通道"（发布方契约）。
 * <p>
 * 使用场景：在 API 模块的通道声明类或常量上标注，用于：
 * <ul>
 *   <li>明确表达这是对外发布的消息通道</li>
 *   <li>方便消费方识别和引用</li>
 *   <li>工具可扫描生成通道文档</li>
 * </ul>
 * </p>
 *
 * <p>示例：
 * <pre>{@code
 * @PublishedChannel(
 *     description = "采集任务准备就绪事件",
 *     payloadType = TaskReadyEvent.class
 * )
 * public static final String TASK_READY = "ingest.task.ready";
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublishedChannel {
    /**
     * 通道描述（业务含义）
     */
    String description() default "";

    /**
     * 消息载荷类型（供消费方参考）
     */
    Class<?> payloadType() default Void.class;

    /**
     * 是否已废弃（用于演进）
     */
    boolean deprecated() default false;

    /**
     * 废弃说明或迁移指引
     */
    String deprecationNote() default "";
}
