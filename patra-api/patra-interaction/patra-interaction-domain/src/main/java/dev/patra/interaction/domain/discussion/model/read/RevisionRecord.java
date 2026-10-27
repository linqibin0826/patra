package dev.patra.interaction.domain.discussion.model.read;

import java.time.Instant;

/// 修改历史里的一个版本。
///
/// @param body 这一版的正文（存储形式，含 `<@用户ID>`）
/// @param at 这一版成为当前版本的时刻：最初版本是发表时刻，之后是那次编辑的时刻
public record RevisionRecord(String body, Instant at) {

  /// 创建。
  ///
  /// @param body 正文
  /// @param at 时刻
  /// @return 版本
  public static RevisionRecord of(String body, Instant at) {
    return new RevisionRecord(body, at);
  }
}
