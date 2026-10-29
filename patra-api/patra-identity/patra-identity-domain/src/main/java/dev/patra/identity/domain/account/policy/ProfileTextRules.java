package dev.patra.identity.domain.account.policy;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.Locale;

/// 昵称与资料文本（简介、链接标题、职称、科室、机构自由文本）共用的字符规则。
///
/// 拒绝：控制字符、行 / 段分隔符、私用区、未分配码点、孤立代理项、格式字符（Cf），以及以组合用字符开头的字素簇
/// （只由组合字符拼成的文本渲染出来是空白）。格式字符只有一种例外：它是 emoji 组件（ZWJ、标签字符）且所在字素簇
/// 含有扩展象形字，家庭 emoji、旗帜序列才能通过。简介多放行一个换行符 U+000A。
///
/// 字素按 `BreakIterator` 的扩展字素簇数：汉字、带变音符的字母、ZWJ 组合的 emoji、国旗各计 1。
public final class ProfileTextRules {

  private static final int LINE_FEED = 0x0A;

  /// 只有静态方法。
  private ProfileTextRules() {}

  /// 逐个字素簇查拒绝字符。
  ///
  /// @param normalized 规范化之后的文本
  /// @param allowLineFeed 是否放行换行符 U+000A（只有简介放行）
  /// @return 含拒绝字符时为 `true`
  public static boolean hasRejectedCharacter(String normalized, boolean allowLineFeed) {
    BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    clusters.setText(normalized);
    int start = clusters.first();
    for (int end = clusters.next(); end != BreakIterator.DONE; start = end, end = clusters.next()) {
      if (isRejectedCluster(normalized, start, end, allowLineFeed)) {
        return true;
      }
    }
    return false;
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

  /// 去掉首尾的空白与空格类字符。`String.strip()` 不认 U+00A0 等不换行空格，从网页或文档复制来的 URL、
  /// ROR ID、MeSH UI 常常带着它们。
  ///
  /// @param text 文本
  /// @return 去掉首尾空白之后的文本
  public static String stripSpaces(String text) {
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

  /// UTF-8 字节数。
  ///
  /// @param text 文本
  /// @return 字节数
  public static int utf8Length(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }

  /// 空白或空格类字符（含不换行空格、全角空格）。
  ///
  /// @param codePoint 码点
  /// @return 是空白时为 `true`
  private static boolean isSpace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }

  /// 一个字素簇是否含拒绝字符：以组合用字符开头；含控制（可放行换行）、行 / 段分隔、私用、未分配、孤立代理项；
  /// 含格式字符而它不是 emoji 组件，或簇里没有扩展象形字。
  ///
  /// @param text 整个文本
  /// @param start 簇的起点
  /// @param end 簇的终点（不含）
  /// @param allowLineFeed 是否放行换行符
  /// @return 要拒绝时为 `true`
  private static boolean isRejectedCluster(String text, int start, int end, boolean allowLineFeed) {
    int firstType = Character.getType(text.codePointAt(start));
    if (firstType == Character.NON_SPACING_MARK
        || firstType == Character.ENCLOSING_MARK
        || firstType == Character.COMBINING_SPACING_MARK) {
      return true;
    }
    boolean format = false;
    boolean pictographic = false;
    int i = start;
    while (i < end) {
      int cp = text.codePointAt(i);
      int type = Character.getType(cp);
      if (type == Character.CONTROL && !(allowLineFeed && cp == LINE_FEED)) {
        return true;
      }
      if (type == Character.LINE_SEPARATOR
          || type == Character.PARAGRAPH_SEPARATOR
          || type == Character.PRIVATE_USE
          || type == Character.UNASSIGNED
          || type == Character.SURROGATE) {
        return true;
      }
      if (type == Character.FORMAT) {
        if (!Character.isEmojiComponent(cp)) {
          return true;
        }
        format = true;
      }
      if (Character.isExtendedPictographic(cp)) {
        pictographic = true;
      }
      i += Character.charCount(cp);
    }
    return format && !pictographic;
  }
}
