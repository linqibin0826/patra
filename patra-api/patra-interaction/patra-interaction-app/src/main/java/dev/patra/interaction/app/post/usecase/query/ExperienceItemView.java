package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostId;
import java.time.Instant;
import lombok.Builder;

/// 一条经验（列表条目，也是详情的主体）。
///
/// @param postId 经验 ID
/// @param fields 结构化字段
/// @param body 正文 Markdown，可空
/// @param createdAt 发布时刻
/// @param edited 是否有修改记录（「已编辑」）
/// @param author 作者；匿名且访客不是作者、或 identity 不认识作者时为 `null`
/// @param anonymous 是否匿名发布
/// @param mine 访客是不是作者
/// @param helpfulCount 有帮助数
/// @param helpful 访客点过有帮助没有；没有会话时为 `null`
/// @param replyCount 经验下未删除的评论总数
@Builder
public record ExperienceItemView(
    PostId postId,
    ExperienceFields fields,
    String body,
    Instant createdAt,
    boolean edited,
    AuthorView author,
    boolean anonymous,
    boolean mine,
    int helpfulCount,
    Boolean helpful,
    int replyCount) {}
