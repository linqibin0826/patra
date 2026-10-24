package dev.patra.interaction.domain.collection.policy;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/// 收藏夹名称、简介与备注共用的文本规则。
///
/// 规范化：NFC，去掉首尾的空白与空格类字符（含不换行空格 U+00A0、全角空格 U+3000）。拒绝控制字符与行 / 段分隔符
/// （U+2028 / U+2029），多行文本放行换行符 U+000A。长度按 `BreakIterator` 的扩展字素簇数，另有字节上限 = 字素上限
/// × 4，防止一个字素里塞几百个组合码点。
public final class TextRules {

  /// 字段错误码：必填。
  public static final String REQUIRED = "REQUIRED";

  /// 字段错误码：超长。
  public static final String TOO_LONG = "TOO_LONG";

  /// 字段错误码：含不允许的字符。
  public static final String INVALID_CHARACTER = "INVALID_CHARACTER";

  private static final int LINE_FEED = 0x0A;
  private static final int LINE_SEPARATOR = 0x2028;
  private static final int PARAGRAPH_SEPARATOR = 0x2029;

  /// 只有静态方法。
  private TextRules() {}

  /// NFC 规范化并去掉首尾空白；`null` 视为空串。
  ///
  /// @param raw 原文
  /// @return 规范化后的文本
  public static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
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

  /// 数扩展字素簇。
  ///
  /// @param text 文本
  /// @return 字素数
  public static int graphemeCount(String text) {
    BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    clusters.setText(text);
    int count = 0;
    while (clusters.next() != BreakIterator.DONE) {
      count++;
    }
    return count;
  }

  /// UTF-8 字节数。
  ///
  /// @param text 文本
  /// @return 字节数
  public static int utf8Length(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }

  /// 是否含不允许的字符。
  ///
  /// @param text 文本
  /// @param allowLineFeed 是否放行换行符 U+000A
  /// @return 含时为 `true`
  public static boolean hasRejectedCharacter(String text, boolean allowLineFeed) {
    return text.codePoints()
        .anyMatch(
            cp ->
                (Character.isISOControl(cp) && !(allowLineFeed && cp == LINE_FEED))
                    || cp == LINE_SEPARATOR
                    || cp == PARAGRAPH_SEPARATOR);
  }

  /// 按规则校验一段已规范化的非空文本，违反时抛字段错误。
  ///
  /// @param field 字段名
  /// @param text 规范化后的文本
  /// @param maxGraphemes 字素上限
  /// @param allowLineFeed 是否放行换行
  /// @throws InvalidCollectionFieldsException 含不允许的字符或超长
  public static void check(String field, String text, int maxGraphemes, boolean allowLineFeed) {
    if (hasRejectedCharacter(text, allowLineFeed)) {
      throw violation(field, INVALID_CHARACTER, "含有不允许的字符");
    }
    if (graphemeCount(text) > maxGraphemes || utf8Length(text) > maxGraphemes * 4) {
      throw violation(field, TOO_LONG, "最多 " + maxGraphemes + " 个字");
    }
  }

  /// 一条字段错误的异常。
  ///
  /// @param field 字段名
  /// @param code 原因码
  /// @param message 默认文案
  /// @return 异常
  public static InvalidCollectionFieldsException violation(
      String field, String code, String message) {
    return new InvalidCollectionFieldsException(List.of(FieldViolation.of(field, code, message)));
  }

  /// 空白或空格类字符（含不换行空格、全角空格）。
  ///
  /// @param codePoint 码点
  /// @return 是空白时为 `true`
  private static boolean isSpace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }
}
