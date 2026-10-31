package dev.patra.interaction.api.discussion.event;

import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.tool.core.event.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/// 集成事件：评论已发表（根评论或回复；交换机 `interaction.events`，路由键 {@value #ROUTING_KEY}，PAP-141）。
///
/// 收件人规则：先列 `parentAuthorId`（回复了你），再列 `subject.ownerId`（只在根评论且对象是投稿经验时，评论了你的
/// 投稿经验），最后列 `mentionedUserIds`（@ 了你）；同一人只取第一次出现的类型；去掉 `authorId`。
///
/// @param eventId 事件 ID：补投、重投都不变，消费方据此去重
/// @param commentId 新评论
/// @param version 正文版本，发表时恒为 1
/// @param subject 所在对象
/// @param rootCommentId 所在楼的根评论（根评论是它自己）
/// @param parentCommentId 回复的哪一条；根评论为 `null`
/// @param parentAuthorId 被回复评论的作者；根评论、或回复的是已成占位的根评论时为 `null`
/// @param authorId 作者
/// @param authorAnonymous 作者是对象的匿名所有者：提醒里触发人显示「匿名作者」
/// @param mentionedUserIds 正文 @ 到的正常账号，去重、按第一次出现；可能含作者本人
/// @param plainText 全文纯文本（PAP-128），每个 `<@用户ID>` 都是真提及；正文只有 Markdown 结构没有文字时是空串
/// @param publishedAt 发表时刻
@Builder
public record CommentPublished(
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
    String plainText,
    Instant publishedAt)
    implements IntegrationEvent {

  /// 路由键。
  public static final String ROUTING_KEY = "comment.published";

  /// 防御性拷贝，`null` 视为空。
  public CommentPublished {
    mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
  }
}
