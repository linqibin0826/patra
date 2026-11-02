package dev.patra.analytics.infra.collect.publish;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// 采集写 Kafka 的 topic 与整批截止时间。
///
/// @param rawTopic 富化事件的 topic
/// @param dlqTopic 不合格事件的 topic
/// @param batchDeadline 一批从开始发送到全部确认的总时限，超时整批返回 503
@ConfigurationProperties("patra.analytics.kafka")
public record KafkaPublishProperties(
    @DefaultValue("events.raw") String rawTopic,
    @DefaultValue("events.dlq") String dlqTopic,
    @DefaultValue("5s") Duration batchDeadline) {}
