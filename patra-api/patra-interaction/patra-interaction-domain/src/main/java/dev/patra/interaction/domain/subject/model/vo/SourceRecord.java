package dev.patra.interaction.domain.subject.model.vo;

import dev.patra.interaction.domain.subject.model.enums.SourceState;

/// 来源对一个对象的解析结果：存在且带身份锚的才会有这条记录。
///
/// @param sourceId 来源内部 ID（catalog 文献主键）
/// @param naturalKey 身份锚
/// @param state 来源状态
/// @param snapshot 展示快照
public record SourceRecord(
    long sourceId, NaturalKey naturalKey, SourceState state, SubjectSnapshot snapshot) {

  /// 创建。
  ///
  /// @param sourceId 来源内部 ID
  /// @param naturalKey 身份锚
  /// @param state 来源状态
  /// @param snapshot 展示快照
  /// @return 来源记录
  public static SourceRecord of(
      long sourceId, NaturalKey naturalKey, SourceState state, SubjectSnapshot snapshot) {
    return new SourceRecord(sourceId, naturalKey, state, snapshot);
  }
}
