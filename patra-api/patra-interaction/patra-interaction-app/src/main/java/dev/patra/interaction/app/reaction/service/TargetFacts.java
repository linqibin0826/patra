package dev.patra.interaction.app.reaction.service;

import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.subject.model.read.SubjectView;

/// 被点内容进「有帮助已点」事件要带的事实（PAP-141）。
///
/// @param subject 被点内容所在的对象；点经验时是经验自己的登记对象
/// @param experience 目标是投稿经验时它的结果，可空
/// @param plainText 被点内容的全文纯文本，可空（经验没写正文）
public record TargetFacts(SubjectView subject, ExperienceOutline experience, String plainText) {

  /// 创建。
  ///
  /// @param subject 所在对象
  /// @param experience 经验的结果，可空
  /// @param plainText 纯文本，可空
  /// @return 事实
  public static TargetFacts of(
      SubjectView subject, ExperienceOutline experience, String plainText) {
    return new TargetFacts(subject, experience, plainText);
  }
}
