package dev.patra.interaction.api.discussion.event;

import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.tool.core.event.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/// 集成事件：评论已编辑（正文确有变化时，含发表 5 分钟内的原地修改；交换机 `interaction.events`，路由键
/// {@value #ROUTING_KEY}，PAP-141）。
///
/// 乱序：比新旧用 `version`（同一条评论严格递增），已存版本 ≥ 它就不覆盖摘录；`addedMentionedUserIds` 的 @ 提醒照常补。
///
/// @param eventId 事件 ID：补投、重投都不变，消费方据此去重
/// @param commentId 评论
/// @param version 改完后的正文版本，≥ 2
/// @param subject 所在对象
/// @param rootCommentId 所在楼的根评论
/// @param parentCommentId 回复的哪一条；根评论为 `null`
/// @param parentAuthorId 编辑时父评论的作者；父评论已成占位时为 `null`
/// @param authorId 作者
/// @param authorAnonymous 作者是对象的匿名所有者
/// @param mentionedUserIds 改完后正文 @ 到的人
/// @param addedMentionedUserIds 这条评论第一次 @ 到的人（以前任何一版都没 @ 过）：只给他们发 @ 提醒，作者本人由消费方去掉
/// @param plainText 改完后的全文纯文本（PAP-128）
/// @param editedAt 编辑时刻（只作展示）
@Builder
public record CommentEdited(
    UUID eventId,
    long commentId,
    int version,
    SubjectContext subject,
    long rootCommentId,
    Long parentCommentId,
    Long parentAuthorId,
    long authorId,
    boolean authorAnonymous,
    List<Long> mentionedUserIds,
    List<Long> addedMentionedUserIds,
    String plainText,
    Instant editedAt)
    implements IntegrationEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "comment.edited";

  /// 防御性拷贝，`null` 视为空。
  public CommentEdited {
    mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
    addedMentionedUserIds =
        addedMentionedUserIds == null ? List.of() : List.copyOf(addedMentionedUserIds);
  }
}
