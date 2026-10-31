package dev.patra.notification.app.inbox.usecase.ingest;

import dev.patra.tool.cqrs.Command;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 有帮助已点（interaction 的 `helpful.marked`）。`subjectOwnerId` 与 `targetPlainText` 可空：文献没有所有者；被点内容没有
/// 文字时为空（经验没写正文、正文只有 Markdown 结构，或 interaction 查不到它）。`targetType` 原样保留，不认识的值由处理器
/// 确认后跳过。
///
/// @param eventId 事件 ID（去重键）
/// @param targetType 目标种类原值：`COMMENT` / `POST`
/// @param targetId 评论 ID 或帖子 ID
/// @param targetAuthorId 被点内容的作者 = 收件人
/// @param actorId 点的人
/// @param markedAt 点的时刻（按北京时间切天用它）
/// @param subjectId 被点内容所在对象；点经验时是经验自己
/// @param subjectOwnerId 所在对象的所有者（经验作者），可空
/// @param subjectOwnerAnonymous 所在经验是匿名发布的
/// @param targetPlainText 被点内容的纯文本全文，可空
@Builder
public record RecordHelpfulMarkedCommand(
    UUID eventId,
    String targetType,
    long targetId,
    long targetAuthorId,
    long actorId,
    Instant markedAt,
    long subjectId,
    Long subjectOwnerId,
    boolean subjectOwnerAnonymous,
    String targetPlainText)
    implements Command<IngestOutcome> {

  /// 校验非空。
  public RecordHelpfulMarkedCommand {
    Objects.requireNonNull(eventId, "eventId 不能为 null");
    Objects.requireNonNull(markedAt, "markedAt 不能为 null");
  }
}
