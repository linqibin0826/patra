package dev.patra.interaction.app.collection.usecase.library;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;

/// 收藏的结果。
///
/// @param created 是否新收进；已在库里为 `false`
/// @param itemId 条目（= 对象 ID）
public record CollectResult(boolean created, SubjectId itemId) {

  /// 创建。
  ///
  /// @param created 是否新收进
  /// @param itemId 条目
  /// @return 结果
  public static CollectResult of(boolean created, SubjectId itemId) {
    return new CollectResult(created, itemId);
  }
}
