package com.patra.starter.rocketmq.core.channel;

/**
 * Channel 标识接口，由领域层枚举实现。
 *
 * <p>设计思路：
 * <ul>
 *   <li>每个微服务在 domain 层定义自己的 ChannelKey 枚举（如 IngestChannels）</li>
 *   <li>枚举值定义 domain、resource、event 三段式标识</li>
 *   <li>Starter 从枚举自动提取 channel 并注册到白名单</li>
 *   <li>保证 SSOT（单一数据源），避免硬编码字符串</li>
 * </ul>
 *
 * <p>示例：
 * <pre>{@code
 * public enum IngestChannels implements ChannelKey {
 *     TASK_READY("ingest", "task", "ready"),
 *     TASK_COMPLETED("ingest", "task", "completed");
 *
 *     private final String domain;
 *     private final String resource;
 *     private final String event;
 *
 *     IngestChannels(String domain, String resource, String event) {
 *         this.domain = domain;
 *         this.resource = resource;
 *         this.event = event;
 *     }
 *
 *     public String domain() { return domain; }
 *     public String resource() { return resource; }
 *     public String event() { return event; }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ChannelKey {

    /**
     * 领域（小写，如 ingest、registry）。
     */
    String domain();

    /**
     * 资源（小写，如 task、article）。
     */
    String resource();

    /**
     * 事件（小写，如 ready、completed）。
     */
    String event();

    /**
     * 完整 channel 字符串（domain.resource.event）。
     */
    default String channel() {
        return domain() + "." + resource() + "." + event();
    }
}
