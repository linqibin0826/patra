package dev.patra.interaction.domain.collection.model.read;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;

/// 文献库里的一条。
///
/// @param subjectId 对象（也是对外的 `itemId`）
/// @param note 备注，没有为 `null`
/// @param collectedAt 收藏时间
public record LibraryEntry(SubjectId subjectId, String note, Instant collectedAt) {

  /// 创建。
  ///
  /// @param subjectId 对象
  /// @param note 备注
  /// @param collectedAt 收藏时间
  /// @return 条目
  public static LibraryEntry of(SubjectId subjectId, String note, Instant collectedAt) {
    return new LibraryEntry(subjectId, note, collectedAt);
  }
}
