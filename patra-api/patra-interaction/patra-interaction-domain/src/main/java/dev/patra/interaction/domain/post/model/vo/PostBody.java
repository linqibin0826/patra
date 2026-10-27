package dev.patra.interaction.domain.post.model.vo;

import dev.patra.tool.core.error.field.FieldViolation;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/// 帖子正文：Markdown 原文，后端只校验长度与字符，不解析、不渲染（决策 C），也不解析 @。
///
/// 规范化：`\r\n`、`\r` 换成 `\n`，NFC，去掉首尾的空白与空格类字符；去完为空等于没有正文。长度按扩展字素簇数
/// ≤ 2,000，另有字节上限 = 字素上限 × 4，防止一个字素里塞很多组合码点。放行换行与制表符，拒绝其余控制字符与行 /
/// 段分隔符（U+2028 / U+2029）。写法同 collection 的文本规则，子域之间不共享私有包，所以在这里另写一份。
///
/// @param value 规范化后的正文
public record PostBody(String value) {

  /// 字段名。
  public static final String FIELD = "body";

  /// 字素上限。
  public static final int MAX_GRAPHEMES = 2000;

  /// 字段错误码：超长。
  public static final String TOO_LONG = "TOO_LONG";

  /// 字段错误码：含不允许的字符。
  public static final String INVALID_CHARACTER = "INVALID_CHARACTER";

  private static final int LINE_FEED = 0x0A;
  private static final int TAB = 0x09;
  private static final int LINE_SEPARATOR = 0x2028;
  private static final int PARAGRAPH_SEPARATOR = 0x2029;

  /// 解析用户输入。不合法时往 `violations` 里加一条字段错误并返回空。
  ///
  /// @param raw 原文，可空
  /// @param violations 收集字段错误
  /// @return 正文；空白或不合法时为空
  public static Optional<PostBody> parse(String raw, Collection<FieldViolation> violations) {
    String text = normalize(raw);
    if (text.isEmpty()) {
      return Optional.empty();
    }
    if (hasRejectedCharacter(text)) {
      violations.add(FieldViolation.of(FIELD, INVALID_CHARACTER, "正文含有不允许的字符"));
      return Optional.empty();
    }
    if (graphemeCount(text) > MAX_GRAPHEMES
        || text.getBytes(StandardCharsets.UTF_8).length > MAX_GRAPHEMES * 4) {
      violations.add(FieldViolation.of(FIELD, TOO_LONG, "正文最多 2,000 字"));
      return Optional.empty();
    }
    return Optional.of(new PostBody(text));
  }

  /// 统一换行、NFC 并去掉首尾空白；`null` 视为空串。
  ///
  /// @param raw 原文
  /// @return 规范化后的文本
  private static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    String text =
        Normalizer.normalize(raw.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
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
  private static int graphemeCount(String text) {
    BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    clusters.setText(text);
    int count = 0;
    while (clusters.next() != BreakIterator.DONE) {
      count++;
    }
    return count;
  }

  /// 是否含不允许的字符：换行、制表符以外的控制字符，或行 / 段分隔符。
  ///
  /// @param text 文本
  /// @return 含时为 `true`
  private static boolean hasRejectedCharacter(String text) {
    return text.codePoints()
        .anyMatch(
            cp ->
                (Character.isISOControl(cp) && cp != LINE_FEED && cp != TAB)
                    || cp == LINE_SEPARATOR
                    || cp == PARAGRAPH_SEPARATOR);
  }

  /// 空白或空格类字符（含不换行空格、全角空格）。
  ///
  /// @param codePoint 码点
  /// @return 是空白时为 `true`
  private static boolean isSpace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }
}
