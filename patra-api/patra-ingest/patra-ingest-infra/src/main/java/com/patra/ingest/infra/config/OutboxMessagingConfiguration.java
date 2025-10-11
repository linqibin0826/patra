package com.patra.ingest.infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration entrypoint for Outbox message publishing.
 */
@Configuration
@EnableConfigurationProperties(OutboxMqProperties.class)
public class OutboxMessagingConfiguration {
}
