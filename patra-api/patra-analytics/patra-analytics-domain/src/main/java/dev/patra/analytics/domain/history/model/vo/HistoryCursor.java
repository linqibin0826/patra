package dev.patra.analytics.domain.history.model.vo;

import dev.patra.analytics.domain.history.exception.InvalidHistoryCursorException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/// 阅读历史的翻页游标：上一页最后一行的（最近打开时间，文献 ID）。对外是 Base64URL（无填充）编码的
/// `"{毫秒}:{文献 ID}"`，门户只原样回传。
///
/// @param lastReadAt 最近打开时间（毫秒精度）
/// @param articleId 文献 ID
public record HistoryCursor(Instant lastReadAt, long articleId) {

  /// 时间必填。
  public HistoryCursor {
    Objects.requireNonNull(lastReadAt, "lastReadAt");
  }

  /// 创建游标。
  ///
  /// @param lastReadAt 最近打开时间
  /// @param articleId 文献 ID
  /// @return 游标
  public static HistoryCursor of(Instant lastReadAt, long articleId) {
    return new HistoryCursor(lastReadAt, articleId);
  }

  /// 编码成对外的游标。
  ///
  /// @return Base64URL 文本
  public String encode() {
    String plain = lastReadAt.toEpochMilli() + ":" + articleId;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  /// 解析对外的游标。
  ///
  /// @param cursor 门户回传的游标
  /// @return 游标
  /// @throws InvalidHistoryCursorException 不是 {@link #encode} 产出的格式
  public static HistoryCursor decode(String cursor) {
    try {
      String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int colon = plain.indexOf(':');
      if (colon < 0) {
        throw new InvalidHistoryCursorException(cursor);
      }
      long millis = Long.parseLong(plain.substring(0, colon));
      long articleId = Long.parseLong(plain.substring(colon + 1));
      return of(Instant.ofEpochMilli(millis), articleId);
    } catch (IllegalArgumentException e) {
      throw new InvalidHistoryCursorException(cursor);
    }
  }
}
