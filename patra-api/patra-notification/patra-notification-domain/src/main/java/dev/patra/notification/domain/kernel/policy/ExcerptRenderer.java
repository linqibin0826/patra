package dev.patra.notification.domain.kernel.policy;

import dev.patra.notification.domain.kernel.model.read.RenderedExcerpt;
import java.text.BreakIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 正文纯文本渲染成摘录（PAP-128 的截断规则），站内与邮件共用，长度与是否压成一行由渠道定。
///
/// 纯文本由 interaction 转出：每个匹配 {@link #MENTION} 的地方都是真提及，长得像提及的普通文字已被断开，不用名单。
///
/// 1. 每个 `<@ID>` 换成 `@名字`；
/// 2. 单行渠道把 `\n` 换成空格（在数字素之前做）；
/// 3. 按扩展字素簇数，`@名字` 整个放得下才放，不出半个名字；
/// 4. 截掉了东西时去掉末尾空白，由显示的一方加省略号。
public final class ExcerptRenderer {

  /// 纯文本里的提及标记。
  public static final Pattern MENTION = Pattern.compile("<@([1-9][0-9]{0,18})>");

  /// 只有静态方法，不实例化。
  private ExcerptRenderer() {}

  /// 渲染。
  ///
  /// @param plainText 评论的纯文本全文
  /// @param names 用户 ID → 显示名字（不含 `@`）；缺了的按 {@link MentionNames#DELETED_USER}
  /// @param maxGraphemes 字素上限
  /// @param singleLine 是否压成一行
  /// @return 摘录
  public static RenderedExcerpt render(
      String plainText, Map<Long, String> names, int maxGraphemes, boolean singleLine) {
    String text = singleLine ? plainText.replace('\n', ' ') : plainText;
    StringBuilder out = new StringBuilder();
    int[] used = {0};
    Matcher matcher = MENTION.matcher(text);
    int last = 0;
    while (matcher.find()) {
      Long userId = userIdOf(matcher.group(1));
      if (userId == null) {
        continue;
      }
      if (!appendLiteral(out, text.substring(last, matcher.start()), used, maxGraphemes)) {
        return truncated(out);
      }
      String mention = "@" + names.getOrDefault(userId, MentionNames.DELETED_USER);
      int length = graphemeCount(mention);
      if (used[0] + length > maxGraphemes) {
        return truncated(out);
      }
      out.append(mention);
      used[0] += length;
      last = matcher.end();
    }
    if (!appendLiteral(out, text.substring(last), used, maxGraphemes)) {
      return truncated(out);
    }
    return RenderedExcerpt.of(out.toString(), false);
  }

  /// 追加一段字面文字，直到用完字素额度。
  ///
  /// @param out 输出
  /// @param literal 文字
  /// @param used 已用字素数（单元素数组，原地累加）
  /// @param maxGraphemes 字素上限
  /// @return 整段都放下了为 `true`
  private static boolean appendLiteral(
      StringBuilder out, String literal, int[] used, int maxGraphemes) {
    BreakIterator graphemes = BreakIterator.getCharacterInstance();
    graphemes.setText(literal);
    int end = 0;
    while (used[0] < maxGraphemes && graphemes.next() != BreakIterator.DONE) {
      end = graphemes.current();
      used[0]++;
    }
    out.append(literal, 0, end);
    return end == literal.length();
  }

  /// 截断后的结果：去掉末尾空白。
  ///
  /// @param out 已输出的文字
  /// @return 标了截断的摘录
  private static RenderedExcerpt truncated(StringBuilder out) {
    return RenderedExcerpt.of(out.toString().stripTrailing(), true);
  }

  /// 提及里的数字转成用户 ID；超出 long 的不是合法 ID。
  ///
  /// @param digits 数字
  /// @return 用户 ID；不合法为 `null`
  private static Long userIdOf(String digits) {
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /// 数扩展字素簇。
  ///
  /// @param text 文字
  /// @return 字素数
  private static int graphemeCount(String text) {
    BreakIterator graphemes = BreakIterator.getCharacterInstance();
    graphemes.setText(text);
    int count = 0;
    while (graphemes.next() != BreakIterator.DONE) {
      count++;
    }
    return count;
  }
}
