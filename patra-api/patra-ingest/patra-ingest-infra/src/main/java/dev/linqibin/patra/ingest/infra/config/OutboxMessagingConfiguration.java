package dev.linqibin.patra.ingest.infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/// 发件箱消息发布的配置入口点。
@Configuration
@EnableConfigurationProperties(OutboxMqProperties.class)
public class OutboxMessagingConfiguration {}
