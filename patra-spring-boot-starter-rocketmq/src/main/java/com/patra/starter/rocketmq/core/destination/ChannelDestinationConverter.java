package com.patra.starter.rocketmq.core.destination;

import com.patra.starter.rocketmq.core.Channel;

import java.util.Locale;

/**
 * Channel 到 Destination 转换器：将 Channel 转换为 RocketMQ Destination。
 *
 * <p>转换规则：
 * <ul>
 *   <li>channel: domain.resource.event → TOPIC: NAMESPACE.DOMAIN.RESOURCE, TAG: EVENT</li>
 *   <li>namespace 来自环境配置（如 DEV、PROD）</li>
 *   <li>所有段转为大写</li>
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
     */
    public Destination convert(Channel channel) {
        String domain = channel.domain().toUpperCase(Locale.ROOT);
        String resource = channel.resource().toUpperCase(Locale.ROOT);
        String event = channel.event().toUpperCase(Locale.ROOT);

        String topic = namespace.isEmpty()
                ? domain + "." + resource
                : namespace + "." + domain + "." + resource;

        return new Destination(topic, event);
    }
}
