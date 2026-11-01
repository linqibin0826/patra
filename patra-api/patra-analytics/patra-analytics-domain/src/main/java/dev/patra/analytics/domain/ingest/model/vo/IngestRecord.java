package dev.patra.analytics.domain.ingest.model.vo;

/// 从 Kafka 拉到的一条消息（入库只关心它在哪、值是什么）。
///
/// @param topicId topic 的 UUID（删掉重建会变）
/// @param partition 分区号
/// @param offset 偏移量
/// @param value 消息值（富化事件或 dlq 记录的 JSON）；空消息为 `null`
public record IngestRecord(String topicId, int partition, long offset, String value) {

  /// 创建一条消息。
  ///
  /// @param topicId topic 的 UUID
  /// @param partition 分区号
  /// @param offset 偏移量
  /// @param value 消息值
  /// @return 消息
  public static IngestRecord of(String topicId, int partition, long offset, String value) {
    return new IngestRecord(topicId, partition, offset, value);
  }
}
