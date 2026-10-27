package dev.patra.interaction.domain.post.model.vo;

import java.time.Instant;

/// 修改历史里的一版：被替换掉的旧内容。
///
/// @param fields 这一版的结构化字段
/// @param body 这一版的正文，可空
/// @param writtenAt 这一版成为当前内容的时刻
/// @param replacedAt 被替换的时刻
public record PostRevision(
    ExperienceFields fields, PostBody body, Instant writtenAt, Instant replacedAt) {

  /// 创建。
  ///
  /// @param fields 结构化字段
  /// @param body 正文
  /// @param writtenAt 成为当前内容的时刻
  /// @param replacedAt 被替换的时刻
  /// @return 一版
  public static PostRevision of(
      ExperienceFields fields, PostBody body, Instant writtenAt, Instant replacedAt) {
    return new PostRevision(fields, body, writtenAt, replacedAt);
  }
}
