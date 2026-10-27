package dev.patra.interaction.app.discussion.service;

import java.util.List;

/// 解析完 @ 的正文。
///
/// @param body 存储形式（`@用户名` 换成了 `<@用户ID>`，伪造的标记已转义）
/// @param mentionedUserIds 正文 @ 到的人，去重、按第一次出现
public record ResolvedBody(String body, List<Long> mentionedUserIds) {

  /// 防御性拷贝。
  public ResolvedBody {
    mentionedUserIds = List.copyOf(mentionedUserIds);
  }

  /// 创建。
  ///
  /// @param body 存储形式
  /// @param mentionedUserIds 提及
  /// @return 正文
  public static ResolvedBody of(String body, List<Long> mentionedUserIds) {
    return new ResolvedBody(body, mentionedUserIds);
  }
}
