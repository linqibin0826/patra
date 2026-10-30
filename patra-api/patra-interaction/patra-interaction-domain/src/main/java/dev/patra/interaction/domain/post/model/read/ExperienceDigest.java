package dev.patra.interaction.domain.post.model.read;

/// 一条投稿经验的结果与正文，给别的子域带进事件（PAP-141：有帮助点经验时，提醒条目的摘录行）。
///
/// @param outline 结果
/// @param body 正文（存储原文），没写为 `null`
public record ExperienceDigest(ExperienceOutline outline, String body) {

  /// 创建。
  ///
  /// @param outline 结果
  /// @param body 正文，可空
  /// @return 摘要
  public static ExperienceDigest of(ExperienceOutline outline, String body) {
    return new ExperienceDigest(outline, body);
  }
}
