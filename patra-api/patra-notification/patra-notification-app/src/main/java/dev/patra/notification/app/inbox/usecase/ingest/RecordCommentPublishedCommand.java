package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.tool.cqrs.Command;
import java.util.Objects;
import java.util.UUID;

/// 评论已发表（interaction 的 `comment.published`）。
///
/// @param eventId 事件 ID（去重键）
/// @param facts 发表时的事实
public record RecordCommentPublishedCommand(UUID eventId, CommentFacts facts)
    implements Command<IngestOutcome> {

  /// 校验非空。
  public RecordCommentPublishedCommand {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(facts, "facts 不能为 null");
  }

  /// 构造。
  ///
  /// @param eventId 事件 ID
  /// @param facts 事实
  /// @return 命令
  public static RecordCommentPublishedCommand of(UUID eventId, CommentFacts facts) {
    return new RecordCommentPublishedCommand(eventId, facts);
  }
}
