package dev.patra.notification.domain.inbox.model.vo;

import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/// 一条评论在发表或编辑那一刻的事实，由 interaction 的集成事件带来（决策 A：事件携带状态，不回查 interaction）。
///
/// @param commentId 评论
/// @param version 这一版正文的版本（发表 = 1，每次编辑 +1）
/// @param subjectId 所在对象（interaction 登记表 ID）
/// @param subjectType 对象类型；事件里是不认识的值时为 `null`
/// @param subjectOwnerId 投稿经验的作者；文献为 `null`
/// @param parentCommentId 回复的是哪一条；根评论为 `null`
/// @param parentAuthorId 被回复评论的作者；根评论、或父评论已成占位时为 `null`
/// @param authorId 作者
/// @param authorAnonymous 作者是对象的匿名所有者
/// @param mentionedUserIds 这一版正文 @ 到的人，去重保序，可能含作者本人
/// @param plainText 这一版正文的纯文本全文（PAP-128，含 `<@ID>`）
/// @param occurredAt 发表或编辑的时刻
@Builder
public record CommentFacts(
    long commentId,
    int version,
    long subjectId,
    SubjectType subjectType,
    Long subjectOwnerId,
    Long parentCommentId,
    Long parentAuthorId,
    long authorId,
    boolean authorAnonymous,
    List<Long> mentionedUserIds,
    String plainText,
    Instant occurredAt) {

  /// 防御性拷贝与非空校验，`null` 的提及名单视为空。
  public CommentFacts {
    mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
    Objects.requireNonNull(plainText, "plainText 不能为 null");
    Objects.requireNonNull(occurredAt, "occurredAt 不能为 null");
  }

  /// 是否根评论。
  ///
  /// @return 没有父评论
  public boolean root() {
    return parentCommentId == null;
  }

  /// 这条评论给某个人的提醒草稿：触发人是作者，排序时间是发表或编辑的时刻。
  ///
  /// @param recipientId 收件人
  /// @param type 提醒类型
  /// @param createdAt 写入信箱的时刻
  /// @return 草稿
  public NotificationDraft draftFor(long recipientId, NotificationType type, Instant createdAt) {
    return NotificationDraft.builder()
        .recipientId(recipientId)
        .type(type)
        .actorId(authorId)
        .actorAnonymous(authorAnonymous)
        .subjectId(subjectId)
        .commentId(commentId)
        .occurredAt(occurredAt)
        .createdAt(createdAt)
        .build();
  }

  /// 是不是投稿经验下的根评论（经验作者要收「评论了你的投稿经验」）。
  ///
  /// @return 根评论且对象是经验、有作者
  public boolean rootOnPost() {
    return root() && subjectType == SubjectType.POST && subjectOwnerId != null;
  }
}
