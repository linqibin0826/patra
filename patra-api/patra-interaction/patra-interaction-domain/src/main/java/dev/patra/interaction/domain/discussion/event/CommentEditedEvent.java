package dev.patra.interaction.domain.discussion.event;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/// 评论已编辑（正文确有变化时；发布 5 分钟内的编辑也发）。讨论子域在写入的同一事务里发布，订阅方式同
/// {@link CommentPublishedEvent}；同时外送成集成事件 `comment.edited`（PAP-141）。
///
/// @param eventId 事件 ID：发布时生成，外送与补投原样带上，消费方据此去重
/// @param commentId 评论
/// @param version 改完后的正文版本（同一条评论严格递增，消费方据此丢弃晚到的旧编辑）
/// @param subjectId 对象
/// @param subjectType 对象种类
/// @param subjectOwner 对象所有者与是否匿名；文献为 `null`
/// @param rootCommentId 所在楼的根评论
/// @param parentCommentId 回复的是哪一条；根评论为 `null`
/// @param parentAuthorId 编辑时父评论的作者（父评论已成占位时为 `null`）：给「同一条里既回复又 @ 同一人只发一条」去重
/// @param authorId 作者
/// @param authorAnonymous 作者是对象的匿名所有者
/// @param mentionedUserIds 编辑后正文 @ 到的人
/// @param addedMentionedUserIds 这条评论第一次 @ 到的人：提醒只发给他们
/// @param body 编辑后的正文（存储形式）
/// @param plainText 编辑后的正文转成的全文纯文本（PAP-128），保留 `<@用户ID>`
/// @param editedAt 编辑时刻
@Builder
public record CommentEditedEvent(
    UUID eventId,
    long commentId,
    int version,
    SubjectId subjectId,
    SubjectType subjectType,
    SubjectOwner subjectOwner,
    long rootCommentId,
    Long parentCommentId,
    Long parentAuthorId,
    long authorId,
    boolean authorAnonymous,
    List<Long> mentionedUserIds,
    List<Long> addedMentionedUserIds,
    String body,
    String plainText,
    Instant editedAt) {

  /// 事件 ID 必填（不兜底生成，否则补投读回登记行时会换一个 ID）；列表防御性拷贝，`null` 视为空。
  public CommentEditedEvent {
    Objects.requireNonNull(eventId, "eventId");
    mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
    addedMentionedUserIds =
        addedMentionedUserIds == null ? List.of() : List.copyOf(addedMentionedUserIds);
  }
}
