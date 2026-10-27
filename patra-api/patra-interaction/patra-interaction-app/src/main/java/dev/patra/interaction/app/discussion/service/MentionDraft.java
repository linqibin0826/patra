package dev.patra.interaction.app.discussion.service;

import java.util.List;

/// 本地识别完 @ 的正文，等着向 identity 解析。
///
/// @param text 规范化后的原文
/// @param candidates @ 候选（按原文去重），最多 100 个
public record MentionDraft(String text, List<String> candidates) {

  /// 防御性拷贝。
  public MentionDraft {
    candidates = List.copyOf(candidates);
  }

  /// 创建。
  ///
  /// @param text 原文
  /// @param candidates 候选
  /// @return 草稿
  public static MentionDraft of(String text, List<String> candidates) {
    return new MentionDraft(text, candidates);
  }
}
