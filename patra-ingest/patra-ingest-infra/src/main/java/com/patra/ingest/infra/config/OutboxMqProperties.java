package com.patra.ingest.infra.config;

import static org.springframework.util.CollectionUtils.isEmpty;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 发件箱 MQ 发布属性配置。
 *
 * <p>约束发布器实现的装配,并在启动时执行快速失败验证。
 */
@ConfigurationProperties(prefix = "patra.ingest.outbox")
@Validated
public class OutboxMqProperties {

  private static final String EXPECTED_PUBLISHER = "rocketmq";

  private String publisher = EXPECTED_PUBLISHER;

  private boolean strictChannelWhitelist;

  private Set<String> allowedChannels = new LinkedHashSet<>();

  /** 发送消息超时时间（毫秒），默认 3000ms */
  @Min(100)
  @Max(60000)
  private int sendTimeout = 3000;

  /** 同步发送失败重试次数，默认 2 次 */
  @Min(0)
  @Max(10)
  private int retryTimesWhenSendFailed = 2;

  /** 是否启用顺序消息（全局开关），默认 false */
  private boolean enableOrderly = false;

  /**
   * 业务通道到 RocketMQ Topic 的映射表。
   *
   * <p>示例配置:
   *
   * <pre>
   * patra:
   *   ingest:
   *     outbox:
   *       channel-mapping:
   *         TASK_READY: INGEST_TASK_READY
   *         PUBLICATION_READY: INGEST_PUBLICATION_READY
   * </pre>
   *
   * <p>如果未配置，则使用 RocketMqChannelMapper 中的默认映射。
   */
  private Map<String, String> channelMapping = new LinkedHashMap<>();

  /**
   * Topic 前缀（用于多环境隔离）。
   *
   * <p>示例:
   *
   * <pre>
   * patra:
   *   ingest:
   *     outbox:
   *       topic-prefix: dev-   # 生成的 Topic: dev-INGEST_TASK_READY
   * </pre>
   */
  private String topicPrefix = "";

  @PostConstruct
  public void validate() {
    if (!EXPECTED_PUBLISHER.equalsIgnoreCase(publisher)) {
      throw new IllegalStateException(
          "patra.ingest.outbox.publisher 必须为 'rocketmq',但实际值为 '" + publisher + "'");
    }
    if (strictChannelWhitelist && isEmpty(allowedChannels)) {
      throw new IllegalStateException("strict-channel-whitelist=true 需要至少配置一个允许的通道");
    }
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = Objects.requireNonNullElse(publisher, EXPECTED_PUBLISHER);
  }

  public boolean isStrictChannelWhitelist() {
    return strictChannelWhitelist;
  }

  public void setStrictChannelWhitelist(boolean strictChannelWhitelist) {
    this.strictChannelWhitelist = strictChannelWhitelist;
  }

  public Set<String> getAllowedChannels() {
    return Collections.unmodifiableSet(allowedChannels);
  }

  public void setAllowedChannels(Set<String> allowedChannels) {
    if (allowedChannels == null) {
      this.allowedChannels = new LinkedHashSet<>();
      return;
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String channel : allowedChannels) {
      if (channel == null) {
        continue;
      }
      String value = channel.trim();
      if (!value.isEmpty()) {
        normalized.add(value.toUpperCase(Locale.ROOT));
      }
    }
    this.allowedChannels = normalized;
  }

  public boolean isChannelAllowed(String channel) {
    if (!strictChannelWhitelist) {
      return true;
    }
    if (channel == null) {
      return false;
    }
    String normalized = channel.trim().toUpperCase(Locale.ROOT);
    return allowedChannels.contains(normalized);
  }

  public int getSendTimeout() {
    return sendTimeout;
  }

  public void setSendTimeout(int sendTimeout) {
    this.sendTimeout = sendTimeout;
  }

  public int getRetryTimesWhenSendFailed() {
    return retryTimesWhenSendFailed;
  }

  public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
    this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
  }

  public boolean isEnableOrderly() {
    return enableOrderly;
  }

  public void setEnableOrderly(boolean enableOrderly) {
    this.enableOrderly = enableOrderly;
  }

  public Map<String, String> getChannelMapping() {
    return Collections.unmodifiableMap(channelMapping);
  }

  public void setChannelMapping(Map<String, String> channelMapping) {
    this.channelMapping =
        channelMapping != null ? new LinkedHashMap<>(channelMapping) : new LinkedHashMap<>();
  }

  public String getTopicPrefix() {
    return topicPrefix;
  }

  public void setTopicPrefix(String topicPrefix) {
    this.topicPrefix = Objects.requireNonNullElse(topicPrefix, "").trim();
  }
}
