package com.patra.ingest.infra.messaging.config;

import com.patra.ingest.infra.config.OutboxMqProperties;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/// RocketMQ 业务通道到技术 Topic 的映射器。
/// 
/// 负责将 Domain 层的业务消息通道映射到 RocketMQ 的技术 Topic 名称。
/// 
/// **设计理念**:
/// 
/// - Domain 层使用两段式 Channel（domain + resource）
///   - Infra 层负责技术实现细节(RocketMQ Topic 命名)
///   - 映射关系集中管理,便于维护和调整
///   - 支持不同环境的 Topic 隔离(通过配置前缀)
///   - 支持从配置文件覆盖默认映射关系
/// 
/// **映射规则**:
/// 
/// - `INGEST_TASK` → `INGEST_TASK` (粗粒度 Topic)
///   - `INGEST_PUBLICATION` → `INGEST_PUBLICATION` (粗粒度 Topic)
///   - 操作类型（READY、FAILED 等）通过 RocketMQ Tags 区分
///   - 支持通过 `patra.ingest.outbox.channel-mapping` 覆盖
///   - 支持通过 `patra.ingest.outbox.topic-prefix` 添加环境前缀
/// 
/// @author linqibin
/// @since 0.2.0
@Component
public class RocketMqChannelMapper {

  /// 默认业务通道到 RocketMQ Topic 的映射表。
/// 
/// 重构后使用粗粒度 Channel 模式：
/// 
/// - INGEST_TASK → INGEST_TASK (资源级别 Topic)
///   - INGEST_PUBLICATION → INGEST_PUBLICATION (资源级别 Topic)
///   - 操作类型（READY、FAILED 等）通过 RocketMQ Tags 区分
/// 
  private static final Map<String, String> DEFAULT_CHANNEL_TO_TOPIC =
      Map.of(
          "INGEST_TASK", "INGEST_TASK",
          "INGEST_PUBLICATION", "INGEST_PUBLICATION");

  private final Map<String, String> channelToTopic;
  private final String topicPrefix;

  /// 构造函数,初始化通道映射和 Topic 前缀。
/// 
/// @param properties Outbox MQ 配置属性
  public RocketMqChannelMapper(OutboxMqProperties properties) {
    // 1. 合并配置的映射和默认映射 (配置优先)
    Map<String, String> mergedMapping = new HashMap<>(DEFAULT_CHANNEL_TO_TOPIC);
    if (properties.getChannelMapping() != null && !properties.getChannelMapping().isEmpty()) {
      mergedMapping.putAll(properties.getChannelMapping());
    }
    this.channelToTopic = Map.copyOf(mergedMapping); // 不可变映射

    // 2. 初始化 Topic 前缀
    this.topicPrefix = properties.getTopicPrefix();
  }

  /// 将业务通道映射到 RocketMQ Topic。
/// 
/// 映射逻辑:
/// 
/// @param channel 业务通道名称(来自 {@link MessageChannels})
/// @return RocketMQ Topic 名称(可能带环境前缀)
/// @throws IllegalArgumentException 如果通道未配置映射或为 null
  public String toTopic(String channel) {
    if (channel == null) {
      throw new IllegalArgumentException("未找到通道 [null] 的 Topic 映射,请在 RocketMqChannelMapper 中配置");
    }
    String topic = channelToTopic.get(channel);
    if (topic == null) {
      throw new IllegalArgumentException(
          String.format("未找到通道 [%s] 的 Topic 映射,请在 RocketMqChannelMapper 中配置", channel));
    }

    // 应用 Topic 前缀 (如果配置了)
    if (StringUtils.hasText(topicPrefix)) {
      return topicPrefix + topic;
    }
    return topic;
  }

  /// 将 RocketMQ Topic 反向映射到业务通道(用于消费端)。
/// 
/// 注意: 如果 Topic 带前缀,会先去除前缀再查找映射。
/// 
/// @param topic RocketMQ Topic 名称
/// @return 业务通道名称,如果未找到映射则返回原 Topic
  public String toChannel(String topic) {
    if (topic == null) {
      return null;
    }

    // 去除 Topic 前缀 (如果有)
    String topicWithoutPrefix = topic;
    if (StringUtils.hasText(topicPrefix) && topic.startsWith(topicPrefix)) {
      topicWithoutPrefix = topic.substring(topicPrefix.length());
    }

    // 反向查找通道
    final String finalTopic = topicWithoutPrefix;
    return channelToTopic.entrySet().stream()
        .filter(entry -> entry.getValue().equals(finalTopic))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(topic); // 未找到映射时返回原 Topic
  }

  /// 检查通道是否已配置映射。
/// 
/// @param channel 业务通道名称
/// @return 如果已配置映射返回 true,否则返回 false(包括 null 时返回 false)
  public boolean hasMapping(String channel) {
    if (channel == null) {
      return false;
    }
    return channelToTopic.containsKey(channel);
  }
}
