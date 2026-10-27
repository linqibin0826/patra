package dev.patra.interaction.domain.post.model.vo;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.EditKind;

/// 一次编辑的决定。
///
/// @param kind 怎么落库
/// @param after 编辑后的帖子（`NONE` 时就是原帖子）
/// @param replaced 被替换掉的旧版本，只有 `REVISE` 时非空
public record PostEdit(EditKind kind, PostAggregate after, PostRevision replaced) {

  /// 创建。
  ///
  /// @param kind 怎么落库
  /// @param after 编辑后的帖子
  /// @param replaced 旧版本，可空
  /// @return 决定
  public static PostEdit of(EditKind kind, PostAggregate after, PostRevision replaced) {
    return new PostEdit(kind, after, replaced);
  }
}
