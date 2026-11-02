package dev.patra.analytics.domain.ingest.port.topic;

import dev.patra.analytics.domain.ingest.exception.IngestUnavailableException;

/// Kafka topic 的 UUID：放进批次去重令牌，删掉 topic 重建后偏移量从 0 重来也不会和旧令牌相撞（入库设计 §4.3）。
public interface TopicIdPort {

  /// 取 topic 的 UUID（第一次查 broker，之后用缓存）。
  ///
  /// @param topic topic 名
  /// @return topic ID
  /// @throws IngestUnavailableException 查不到（broker 不可用）
  String topicId(String topic);
}
