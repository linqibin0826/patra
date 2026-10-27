package dev.patra.interaction.domain.discussion.policy;

import dev.patra.interaction.domain.discussion.exception.InvalidCommentException;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.Locale;

/// 评论正文的文本规则（spec 5.3）：针对客户端发来的原文（`@用户名` 形式），换成 `<@用户ID>` 之后不再算长度。
///
/// 换行统一成 `\n`；NFC；去掉首尾的空白与空格类字符（含不换行空格、全角空格）。拒绝控制字符（放行 `\n` 与 `\t`）
/// 与 U+2028 / U+2029。长度按扩展字素簇数 ≤ 5,000，另有 UTF-8 ≤ 20,000 字节。后端不校验 Markdown。
public final class CommentText {

  private static final int LINE_FEED = 0x0A;
  private static final int TAB = 0x09;
  private static final int LINE_SEPARATOR = 0x2028;
  private static final int PARAGRAPH_SEPARATOR = 0x2029;

  /// 只有静态方法。
  private CommentText() {}

  /// 规范化并校验。
  ///
  /// @param raw 原文，可空
  /// @return 规范化后的原文
  /// @throws InvalidCommentException 空、含不允许的字符或超长
  public static String parse(String raw) {
    String text = trim(normalize(raw == null ? "" : raw));
    if (text.isEmpty()) {
      throw InvalidCommentException.required();
    }
    if (text.codePoints().anyMatch(CommentText::isRejected)) {
      throw InvalidCommentException.invalidCharacter();
    }
    if (graphemeCount(text) > DiscussionLimits.MAX_GRAPHEMES
        || text.getBytes(StandardCharsets.UTF_8).length > DiscussionLimits.MAX_BYTES) {
      throw InvalidCommentException.tooLong();
    }
    return text;
  }

  /// 换行统一成 `\n`，再做 NFC。
  ///
  /// @param raw 原文
  /// @return 规范化后的文本
  private static String normalize(String raw) {
    String unified = raw.replace("\r\n", "\n").replace('\r', '\n');
    return Normalizer.normalize(unified, Normalizer.Form.NFC);
  }

  /// 去掉首尾的空白与空格类字符。
  ///
  /// @param text 文本
  /// @return 去完的文本
  private static String trim(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && isSpace(text.codePointAt(start))) {
      start += Character.charCount(text.codePointAt(start));
    }
    while (end > start && isSpace(text.codePointBefore(end))) {
      end -= Character.charCount(text.codePointBefore(end));
    }
    return text.substring(start, end);
  }

  /// 空白或空格类字符。
  ///
  /// @param codePoint 码点
  /// @return 是空白时为 `true`
  private static boolean isSpace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }

  /// 不允许的字符：除换行、制表符外的控制字符，以及行 / 段分隔符。
  ///
  /// @param codePoint 码点
  /// @return 不允许时为 `true`
  private static boolean isRejected(int codePoint) {
    return (Character.isISOControl(codePoint) && codePoint != LINE_FEED && codePoint != TAB)
        || codePoint == LINE_SEPARATOR
        || codePoint == PARAGRAPH_SEPARATOR;
  }

  /// 数扩展字素簇。
  ///
  /// @param text 文本
  /// @return 字素数
  private static int graphemeCount(String text) {
    BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    clusters.setText(text);
    int count = 0;
    while (clusters.next() != BreakIterator.DONE) {
      count++;
    }
    return count;
  }
}
