package com.patra.ingest.domain.messaging;

/**
 * 领域内的“消息通道键”抽象，描述 domain.resource.event 三级语义。
 * <p>纯领域对象，不依赖任何 MQ/框架，便于在应用/基础设施层做适配。</p>
 */
public interface ChannelKey {
    /** 业务域，如：ingest/registry */
    String domain();
    /** 资源或聚合名，如：task/article */
    String resource();
    /** 事件名，如：ready/created/updated */
    String event();

    /** 规范化小写点分段通道，例如：ingest.task.ready */
    default String channel() { return domain() + "." + resource() + "." + event(); }
}

