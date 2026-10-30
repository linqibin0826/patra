package dev.patra.interaction.app.discussion.usecase.query;

import dev.patra.interaction.domain.subject.model.read.UserCard;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 一条评论的完整视图（spec 6.4）。占位只有位置：`deleted` 为真，作者、正文为空，提及为空列表，有帮助数为 0。
///
/// @param commentId 评论 ID
/// @param rootId 所在楼的根评论
/// @param parentId 回复的是哪一条；根评论为 `null`
/// @param deleted 是否占位
/// @param author 作者名片（已按可见性遮蔽）；匿名且访客不是作者、identity 省略、占位时为 `null`
/// @param anonymous 作者是对象的匿名所有者
/// @param owner 作者是对象所有者（「作者」标记）
/// @param mine 这条是访客发的
/// @param replyTo 「回复 @X」的对象；回复根评论或根评论本身为 `null`
/// @param body 正文（存储形式，含 `<@用户ID>`）；占位为 `null`
/// @param mentions 正文里的提及，按第一次出现
/// @param hasReplies 有没有直接回复
/// @param createdAt 发表时刻
/// @param edited 有没有修改记录
/// @param helpfulCount 有帮助数
/// @param helpful 访客点过没有；没有会话或是占位时为 `null`
@Builder
public record CommentView(
    long commentId,
    long rootId,
    Long parentId,
    boolean deleted,
    UserCard author,
    boolean anonymous,
    boolean owner,
    boolean mine,
    ReplyToView replyTo,
    String body,
    List<MentionView> mentions,
    boolean hasReplies,
    Instant createdAt,
    boolean edited,
    int helpfulCount,
    Boolean helpful) {

  /// 防御性拷贝，`null` 视为空。
  public CommentView {
    mentions = mentions == null ? List.of() : List.copyOf(mentions);
  }
}
