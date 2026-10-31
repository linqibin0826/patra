package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.tool.cqrs.Command;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// 评论已编辑（interaction 的 `comment.edited`）。
///
/// @param eventId 事件 ID（去重键）
/// @param facts 编辑后的事实（`version` ≥ 2，`occurredAt` 是编辑时刻）
/// @param addedMentionedUserIds 这条评论第一次 @ 到的人
public record RecordCommentEditedCommand(
    UUID eventId, CommentFacts facts, List<Long> addedMentionedUserIds)
    implements Command<IngestOutcome> {

  /// 校验非空、防御性拷贝。
  public RecordCommentEditedCommand {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(facts, "facts 不能为 null");
    addedMentionedUserIds =
        addedMentionedUserIds == null ? List.of() : List.copyOf(addedMentionedUserIds);
  }

  /// 构造。
  ///
  /// @param eventId 事件 ID
  /// @param facts 事实
  /// @param addedMentionedUserIds 新增的 @
  /// @return 命令
  public static RecordCommentEditedCommand of(
      UUID eventId, CommentFacts facts, List<Long> addedMentionedUserIds) {
    return new RecordCommentEditedCommand(eventId, facts, addedMentionedUserIds);
  }
}
