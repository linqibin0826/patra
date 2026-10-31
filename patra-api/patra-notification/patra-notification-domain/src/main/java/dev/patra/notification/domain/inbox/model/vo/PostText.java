package dev.patra.notification.domain.inbox.model.vo;

import java.time.Instant;
import java.util.Objects;

/// 点经验时条目上存的经验正文：以点击时刻最晚的那条事件带来的为准（PAP-144 spec 7.3）。经验没有编辑事件、没有版本号，
/// 只能拿点击时刻比新旧；「最晚者为准」与消息到达顺序无关。
///
/// @param value 经验正文的纯文本全文；经验没有文字时为空
/// @param markedAt 这段正文来自哪一刻的点击
public record PostText(String value, Instant markedAt) {

  /// 校验时刻非空。
  public PostText {
    Objects.requireNonNull(markedAt, "markedAt 不能为 null");
  }

  /// 构造。
  ///
  /// @param value 正文，可空
  /// @param markedAt 点击时刻
  /// @return 正文
  public static PostText of(String value, Instant markedAt) {
    return new PostText(value, markedAt);
  }

  /// 并入一条点击带来的正文：比现在的晚就换成它（空正文也换），否则不变。
  ///
  /// @param value 这条点击带来的正文，可空
  /// @param at 这条点击的时刻
  /// @return 换了是新的；没换是自己
  public PostText advance(String value, Instant at) {
    return at.isAfter(markedAt) ? new PostText(value, at) : this;
  }
}
