package com.patra.starter.rocketmq.core.destination;

import com.patra.starter.rocketmq.core.Channel;

import java.util.Locale;

/**
 * Channel 到 Destination 转换器：将 Channel 转换为 RocketMQ Destination。
 *
 * <p>转换规则：
 * <ul>
 *   <li>channel: domain_resource_event → TOPIC: NAMESPACE_DOMAIN_RESOURCE, TAG: EVENT</li>
 *   <li>namespace 来自环境配置（如 DEV、PROD）</li>
 *   <li>所有段已为大写</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ChannelDestinationConverter {

    private final String namespace;

    public ChannelDestinationConverter(String namespace) {
        this.namespace = namespace != null ? namespace.toUpperCase(Locale.ROOT) : "";
    }

    /**
     * 从 Channel 转换为 Destination。
     * <p>Channel 已使用下划线格式，直接拼接即可。
     */
    public Destination convert(Channel channel) {
        String domain = channel.domain();
        String resource = channel.resource();
        String event = channel.event();

        // 直接使用下划线分隔
        String topic = namespace.isEmpty()
                ? domain + "_" + resource
                : namespace + "_" + domain + "_" + resource;

        return new Destination(topic, event);
    }
}
