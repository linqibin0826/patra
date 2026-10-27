package dev.patra.interaction.domain.discussion.model.vo;

import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 翻页游标：下一页从这一条之后开始，按 `(createdAt, commentId)` 定位，删除不会让翻页重复或漏条。
///
/// 对外是不透明字符串 `{自 EPOCH 的微秒}.{评论 ID}`；库里的时刻是微秒精度，往返不丢精度。
///
/// @param createdAt 这一条的发表时刻
/// @param commentId 这一条的评论 ID
public record ThreadCursor(Instant createdAt, long commentId) {

  private static final Pattern FORMAT = Pattern.compile("([0-9]{1,19})\\.([1-9][0-9]{0,18})");

  /// 创建。
  ///
  /// @param createdAt 发表时刻
  /// @param commentId 评论 ID
  /// @return 游标
  public static ThreadCursor of(Instant createdAt, long commentId) {
    return new ThreadCursor(createdAt, commentId);
  }

  /// 解析请求里的游标。
  ///
  /// @param raw 原文，可空
  /// @return 游标；没传时为空
  /// @throws InvalidDiscussionRequestException 格式不对或数字溢出
  public static Optional<ThreadCursor> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = FORMAT.matcher(raw);
    if (!matcher.matches()) {
      throw new InvalidDiscussionRequestException("游标不合法");
    }
    try {
      long micros = Long.parseLong(matcher.group(1));
      long commentId = Long.parseLong(matcher.group(2));
      return Optional.of(of(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), commentId));
    } catch (NumberFormatException ex) {
      throw new InvalidDiscussionRequestException("游标不合法");
    }
  }

  /// 格式化成对外的字符串。
  ///
  /// @return `{微秒}.{评论 ID}`
  public String format() {
    return ChronoUnit.MICROS.between(Instant.EPOCH, createdAt) + "." + commentId;
  }
}
