package com.patra.common.messaging;

import java.util.Locale;

/**
 * 消息通道键抽象，描述 domain.resource.event 三级语义。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>提供统一的通道命名规范（domain.resource.event）</li>
 *   <li>作为发布方和消费方的契约基础</li>
 *   <li>不依赖任何具体 MQ 实现细节</li>
 *   <li>放在 patra-common 中，保持 API 模块纯净</li>
 * </ul>
 * </p>
 *
 * <p><b>使用场景</b>：
 * <ul>
 *   <li>发布方：在 API 模块声明 PublishedChannels 实现此接口</li>
 *   <li>消费方：引用发布方 API 获取 channel 字符串</li>
 *   <li>Domain：内部枚举实现此接口（可选）</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ChannelKey {

    /**
     * 业务域，如：ingest / registry / analysis
     * <p>建议小写，对应微服务名或领域边界</p>
     */
    String domain();

    /**
     * 资源或聚合名，如：task / article / plan
     * <p>建议小写，对应核心聚合根或业务对象</p>
     */
    String resource();

    /**
     * 事件名，如：ready / created / updated / deleted
     * <p>建议小写，使用过去时态描述已发生的事实</p>
     */
    String event();

    /**
     * 规范化小写点分段通道，例如：ingest.task.ready
     * <p>默认实现拼接 domain.resource.event</p>
     */
    default String channel() {
        return domain().toUpperCase(Locale.ROOT) + "." + resource().toUpperCase(Locale.ROOT) + "." + event().toUpperCase(Locale.ROOT);
    }
}
