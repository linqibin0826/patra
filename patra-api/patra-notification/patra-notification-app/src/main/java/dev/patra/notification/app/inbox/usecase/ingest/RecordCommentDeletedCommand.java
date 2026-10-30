package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.tool.cqrs.Command;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/// 评论已删除（interaction 的 `comment.deleted`，变占位或整条去掉都算）。
///
/// @param eventId 事件 ID（去重键）
/// @param commentId 评论
/// @param subjectId 所在对象
/// @param deletedAt 删除时刻
public record RecordCommentDeletedCommand(
    UUID eventId, long commentId, long subjectId, Instant deletedAt)
    implements Command<IngestOutcome> {

  /// 校验非空。
  public RecordCommentDeletedCommand {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(deletedAt, "deletedAt 不能为 null");
  }

  /// 构造。
  ///
  /// @param eventId 事件 ID
  /// @param commentId 评论
  /// @param subjectId 所在对象
  /// @param deletedAt 删除时刻
  /// @return 命令
  public static RecordCommentDeletedCommand of(
      UUID eventId, long commentId, long subjectId, Instant deletedAt) {
    return new RecordCommentDeletedCommand(eventId, commentId, subjectId, deletedAt);
  }
}
