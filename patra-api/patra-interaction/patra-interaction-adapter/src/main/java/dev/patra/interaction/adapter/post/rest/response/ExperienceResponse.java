package dev.patra.interaction.adapter.post.rest.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.patra.interaction.app.post.usecase.query.ExperienceItemView;
import java.time.Instant;
import lombok.Builder;

/// 一条经验：结构化字段平铺在顶层。匿名且访客不是作者时 `author` 为 `null`。
///
/// @param postId 经验 ID
/// @param fields 结构化字段与时长（平铺）
/// @param body 正文 Markdown，可空
/// @param createdAt 发布时刻
/// @param edited 是否有修改记录（「已编辑」）
/// @param author 作者，可空
/// @param anonymous 是否匿名发布
/// @param mine 访客是不是作者
/// @param helpfulCount 有帮助数
/// @param helpful 访客点过有帮助没有；没有会话时为 `null`
/// @param replyCount 经验下未删除的评论总数
@Builder
public record ExperienceResponse(
    Long postId,
    @JsonUnwrapped ExperienceFieldsResponse fields,
    String body,
    Instant createdAt,
    boolean edited,
    AuthorResponse author,
    boolean anonymous,
    boolean mine,
    int helpfulCount,
    Boolean helpful,
    int replyCount) {

  /// 转换。
  ///
  /// @param view 条目
  /// @return 响应
  public static ExperienceResponse from(ExperienceItemView view) {
    return ExperienceResponse.builder()
        .postId(view.postId().value())
        .fields(ExperienceFieldsResponse.from(view.fields()))
        .body(view.body())
        .createdAt(view.createdAt())
        .edited(view.edited())
        .author(AuthorResponse.from(view.author()))
        .anonymous(view.anonymous())
        .mine(view.mine())
        .helpfulCount(view.helpfulCount())
        .helpful(view.helpful())
        .replyCount(view.replyCount())
        .build();
  }
}
