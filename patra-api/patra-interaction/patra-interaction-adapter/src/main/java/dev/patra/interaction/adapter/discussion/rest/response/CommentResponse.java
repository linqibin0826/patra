package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 一条评论（spec 6.4）。占位只有位置：`deleted` 为真，作者、正文为空，有帮助数为 0。
///
/// @param commentId 评论 ID
/// @param rootId 所在楼的根评论
/// @param parentId 回复的是哪一条；根评论为 `null`
/// @param deleted 是否占位（「该评论已删除」）
/// @param author 作者；匿名作者对别人、identity 不认识、占位时为 `null`
/// @param anonymous 作者是匿名作者
/// @param isOwner 作者是对象所有者（「作者」标记）
/// @param mine 这条是访客发的
/// @param replyTo 「回复 @X」；回复根评论或根评论本身为 `null`
/// @param body Markdown 原文，提及写作 `<@用户ID>`；占位为 `null`
/// @param mentions 正文里的提及，按第一次出现
/// @param hasReplies 有没有直接回复
/// @param createdAt 发表时刻
/// @param edited 有没有修改记录（「已编辑」）
/// @param helpfulCount 有帮助数
/// @param helpful 访客点过有帮助没有；没有会话或是占位时为 `null`
@Builder
public record CommentResponse(
    Long commentId,
    Long rootId,
    Long parentId,
    boolean deleted,
    AuthorResponse author,
    boolean anonymous,
    boolean isOwner,
    boolean mine,
    ReplyToResponse replyTo,
    String body,
    List<MentionResponse> mentions,
    boolean hasReplies,
    Instant createdAt,
    boolean edited,
    int helpfulCount,
    Boolean helpful) {

  /// 防御性拷贝。
  public CommentResponse {
    mentions = List.copyOf(mentions);
  }

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static CommentResponse from(CommentView view) {
    return CommentResponse.builder()
        .commentId(view.commentId())
        .rootId(view.rootId())
        .parentId(view.parentId())
        .deleted(view.deleted())
        .author(AuthorResponse.from(view.author()))
        .anonymous(view.anonymous())
        .isOwner(view.owner())
        .mine(view.mine())
        .replyTo(ReplyToResponse.from(view.replyTo()))
        .body(view.body())
        .mentions(view.mentions().stream().map(MentionResponse::from).toList())
        .hasReplies(view.hasReplies())
        .createdAt(view.createdAt())
        .edited(view.edited())
        .helpfulCount(view.helpfulCount())
        .helpful(view.helpful())
        .build();
  }

  /// 一组视图转换。
  ///
  /// @param views 视图
  /// @return 响应
  static List<CommentResponse> fromAll(List<CommentView> views) {
    return views.stream().map(CommentResponse::from).toList();
  }
}
