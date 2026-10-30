package dev.patra.interaction.domain.subject.model.vo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 「最有帮助」排序的翻页游标：下一页从这一条之后开始，按（有帮助数、发表时刻、ID）三元组定位，讨论与投稿经验共用。
///
/// 对外是不透明字符串 `{有帮助数}.{自 EPOCH 的微秒}.{ID}`。底座不抛子域的异常：格式不对时 {@link #parse} 返回空，
/// 由讨论、投稿经验各自转成自己的 422。
///
/// @param helpfulCount 这一条读出时的有帮助数
/// @param createdAt 这一条的发表时刻
/// @param id 这一条的 ID（评论或帖子）
public record RankCursor(int helpfulCount, Instant createdAt, long id) {

  private static final Pattern FORMAT =
      Pattern.compile("([0-9]{1,10})\\.([0-9]{1,19})\\.([1-9][0-9]{0,18})");

  /// 创建。
  ///
  /// @param helpfulCount 有帮助数
  /// @param createdAt 发表时刻
  /// @param id ID
  /// @return 游标
  public static RankCursor of(int helpfulCount, Instant createdAt, long id) {
    return new RankCursor(helpfulCount, createdAt, id);
  }

  /// 解析请求里的游标（调用方先排除没传的情况）。
  ///
  /// @param raw 原文
  /// @return 游标；格式不对或数字溢出时为空
  public static Optional<RankCursor> parse(String raw) {
    Matcher matcher = FORMAT.matcher(raw);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    try {
      int helpfulCount = Integer.parseInt(matcher.group(1));
      long micros = Long.parseLong(matcher.group(2));
      long id = Long.parseLong(matcher.group(3));
      return Optional.of(of(helpfulCount, Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  /// 格式化成对外的字符串。
  ///
  /// @return `{有帮助数}.{微秒}.{ID}`
  public String format() {
    return helpfulCount + "." + ChronoUnit.MICROS.between(Instant.EPOCH, createdAt) + "." + id;
  }
}
