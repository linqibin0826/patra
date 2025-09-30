package com.patra.ingest.infra.messaging.config;

import com.patra.ingest.domain.messaging.ChannelKey;
import com.patra.ingest.domain.messaging.IngestChannels;
import com.patra.starter.rocketmq.channels.ChannelCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Ingest 模块消息通道注册配置。
 * <p>自动从领域层 {@link IngestChannels} 枚举收集所有 channel 并注册到 RocketMQ Starter 的白名单中。
 * <p>设计原则：领域枚举是唯一数据源（SSOT），基础设施层仅负责适配和注册。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Configuration
public class IngestMessagingConfiguration {

    /**
     * 提供 Ingest 模块的 channel 目录，供 RocketMQ Starter 的 {@link com.patra.starter.rocketmq.channels.ChannelRegistry} 收集。
     * <p>自动从 {@link IngestChannels} 枚举提取所有 channel，确保与领域定义保持一致。
     */
    @Bean
    public ChannelCatalog ingestChannelCatalog() {
        return () -> Arrays.stream(IngestChannels.values())
                          .map(ChannelKey::channel)
                          .collect(Collectors.toSet());
    }
}
