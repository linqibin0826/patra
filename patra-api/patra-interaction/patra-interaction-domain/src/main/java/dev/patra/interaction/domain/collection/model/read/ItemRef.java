package dev.patra.interaction.domain.collection.model.read;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;

/// 列表里的一条引用：对象与它的「加入时间」（文献库视图是收藏时间，收藏夹视图是加入该夹的时间）。
///
/// @param subjectId 对象
/// @param at 加入时间
public record ItemRef(SubjectId subjectId, Instant at) {

  /// 创建。
  ///
  /// @param subjectId 对象
  /// @param at 加入时间
  /// @return 引用
  public static ItemRef of(SubjectId subjectId, Instant at) {
    return new ItemRef(subjectId, at);
  }
}
