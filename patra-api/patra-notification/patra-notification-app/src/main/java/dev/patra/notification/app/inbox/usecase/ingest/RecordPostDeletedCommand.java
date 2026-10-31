package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.tool.cqrs.Command;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/// 投稿经验已删除（interaction 的 `post.deleted`）。
///
/// @param eventId 事件 ID（去重键）
/// @param subjectId 这条经验自己的登记对象（它下面评论的所在对象）
/// @param deletedAt 删除时刻
public record RecordPostDeletedCommand(UUID eventId, long subjectId, Instant deletedAt)
    implements Command<IngestOutcome> {

  /// 校验非空。
  public RecordPostDeletedCommand {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(deletedAt, "deletedAt 不能为 null");
  }

  /// 构造。
  ///
  /// @param eventId 事件 ID
  /// @param subjectId 经验的登记对象
  /// @param deletedAt 删除时刻
  /// @return 命令
  public static RecordPostDeletedCommand of(UUID eventId, long subjectId, Instant deletedAt) {
    return new RecordPostDeletedCommand(eventId, subjectId, deletedAt);
  }
}
