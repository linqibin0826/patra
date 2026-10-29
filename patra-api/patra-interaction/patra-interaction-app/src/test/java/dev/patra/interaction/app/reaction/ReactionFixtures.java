package dev.patra.interaction.app.reaction;

import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;

/// reaction 单测共用的评论与登记对象。
public final class ReactionFixtures {

  /// 时刻。
  public static final Instant NOW = Instant.parse("2026-10-29T08:00:00Z");

  /// 只有静态方法。
  private ReactionFixtures() {}

  /// 一条根评论。
  ///
  /// @param commentId 评论
  /// @param subjectId 所在对象
  /// @param authorId 作者
  /// @param state 状态
  /// @return 评论
  public static CommentRecord comment(
      long commentId, SubjectId subjectId, long authorId, CommentState state) {
    return CommentRecord.builder()
        .commentId(commentId)
        .subjectId(subjectId)
        .rootId(commentId)
        .authorId(authorId)
        .state(state)
        .body(state == CommentState.ACTIVE ? "正文" : null)
        .createdAt(NOW)
        .build();
  }

  /// 一个登记对象。
  ///
  /// @param subjectId 本地 ID
  /// @param type 种类
  /// @param state 状态
  /// @param owner 所有者，外部对象为 `null`
  /// @return 视图
  public static SubjectView subject(
      SubjectId subjectId, SubjectType type, SubjectState state, SubjectOwner owner) {
    return SubjectView.builder()
        .subjectId(subjectId)
        .type(type)
        .key("1")
        .state(state)
        .owner(owner)
        .build();
  }
}
