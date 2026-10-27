package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.domain.post.model.vo.PostId;
import lombok.Builder;

/// 经验详情（`/posts/{id}`）。已删除时只有删除标记与期刊。
///
/// @param postId 经验 ID
/// @param deleted 是否已删除
/// @param journal 所在期刊
/// @param experience 经验；已删除时为 `null`
@Builder
public record ExperienceDetailView(
    PostId postId, boolean deleted, JournalRefView journal, ExperienceItemView experience) {}
