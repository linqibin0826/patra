package dev.patra.interaction.domain.discussion.event;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 评论已发表（根评论或回复）。讨论子域在写入评论的同一事务里发布。
///
/// 订阅方（提醒，PAP-123）用同步的 `@EventListener`，同一线程、同一事务；**不要用 `@TransactionalEventListener`**
/// （提交后才执行，提醒会丢）。订阅方抛出异常时整个事务回滚：评论、发表计数、订阅方自己的写入都不落库。
///
/// @param commentId 新评论
/// @param subjectId 对象（永久链接要的 key 由订阅方读时经 `SubjectReadPort` 取：文献的 key 在 catalog 重导后会变）
/// @param subjectType 对象种类（文献 / 投稿经验）
/// @param subjectOwner 对象所有者与是否匿名；文献为 `null`
/// @param rootCommentId 所在楼的根评论（根评论是它自己）
/// @param parentCommentId 回复的是哪一条；根评论为 `null`
/// @param parentAuthorId 被回复评论的作者；根评论、或回复的是已成占位的根评论（「回复这一楼」）时为 `null`
/// @param authorId 作者
/// @param authorAnonymous 作者是对象的匿名所有者：提醒里的触发人要显示「匿名作者」
/// @param mentionedUserIds 正文 @ 到的正常账号，去重、按第一次出现；可能含作者本人
/// @param body 存储形式的正文（Markdown，含 `<@用户ID>`），摘录由订阅方自己截
/// @param publishedAt 发表时刻（与库里的 `created_at` 相同）
@Builder
public record CommentPublishedEvent(
    long commentId,
    SubjectId subjectId,
    SubjectType subjectType,
    SubjectOwner subjectOwner,
    long rootCommentId,
    Long parentCommentId,
    Long parentAuthorId,
    long authorId,
    boolean authorAnonymous,
    List<Long> mentionedUserIds,
    String body,
    Instant publishedAt) {

  /// 防御性拷贝，`null` 视为空。
  public CommentPublishedEvent {
    mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
  }
}
