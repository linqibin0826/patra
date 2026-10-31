package dev.patra.notification.domain.inbox.model.vo;

import java.time.Instant;
import java.util.Objects;

/// 一次有帮助点击：谁、什么时候（事件里的 `markedAt`）。
///
/// @param actorId 点的人
/// @param markedAt 点的时刻
public record HelpfulMark(long actorId, Instant markedAt) {

  /// 校验非空。
  public HelpfulMark {
    Objects.requireNonNull(markedAt, "markedAt 不能为 null");
  }

  /// 构造。
  ///
  /// @param actorId 点的人
  /// @param markedAt 点的时刻
  /// @return 点击
  public static HelpfulMark of(long actorId, Instant markedAt) {
    return new HelpfulMark(actorId, markedAt);
  }
}
