package com.patra.ingest.infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox 消息发布配置入口。
 */
@Configuration
@EnableConfigurationProperties(OutboxMqProperties.class)
public class OutboxMessagingConfiguration {
}
