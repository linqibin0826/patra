package dev.patra.notification.domain.mail.policy;

import java.text.BreakIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 文献标题转成邮件里的纯文字（spec 8.3）。PubMed 标题带内联标记：门户按白名单渲染，邮件里只留文字——去掉白名单里的
/// 标签（排版组 `i b sub sup u`、`math` 与 MathML 元素），`annotation` 连内容一起去掉，再解码常见实体；白名单外的标签、
/// 裸 `<` 原样保留。输出由模板转义，不会被当成 HTML。
public final class PublicationTitleText {

  /// 标题为空时的写法（同门户）。
  public static final String UNTITLED = "一篇文献的讨论";

  /// 截掉内容时末尾加的省略号（不计入长度）。
  private static final String ELLIPSIS = "…";

  /// MathML 的 `annotation`（摘要里的 TeX 原文），连内容一起去掉。
  private static final Pattern ANNOTATION =
      Pattern.compile(
          "<annotation(?:\\s[^<>]*)?>.*?</annotation\\s*>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /// 白名单里的开闭标签：排版组与 MathML（`math`、`semantics`、以 `m` 开头的元素）。
  private static final Pattern WHITELISTED_TAG =
      Pattern.compile(
          "</?(?:i|b|sub|sup|u|semantics|m[a-z]*)(?:\\s[^<>]*)?\\s*/?\\s*>",
          Pattern.CASE_INSENSITIVE);

  /// 命名实体与数字实体。
  private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);");

  /// 认识的命名实体。
  private static final Map<String, String> NAMED =
      Map.of("amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ");

  /// 只有静态方法，不实例化。
  private PublicationTitleText() {}

  /// 转成纯文字。
  ///
  /// @param raw 原始标题，可空
  /// @return 纯文字；为空或空白时是 {@value #UNTITLED}
  public static String plain(String raw) {
    if (raw == null || raw.isBlank()) {
      return UNTITLED;
    }
    String text = ANNOTATION.matcher(raw).replaceAll("");
    text = WHITELISTED_TAG.matcher(text).replaceAll("");
    return decodeEntities(text).strip();
  }

  /// 按扩展字素簇截断，截掉了内容就加 `…`。
  ///
  /// @param text 文字
  /// @param maxGraphemes 字素上限
  /// @return 截好的文字
  public static String truncate(String text, int maxGraphemes) {
    BreakIterator graphemes = BreakIterator.getCharacterInstance();
    graphemes.setText(text);
    int end = 0;
    for (int count = 0; count < maxGraphemes; count++) {
      int next = graphemes.next();
      if (next == BreakIterator.DONE) {
        return text;
      }
      end = next;
    }
    return end == text.length() ? text : text.substring(0, end).stripTrailing() + ELLIPSIS;
  }

  /// 解码命名实体与数字实体；不认识的、不合法的码点原样保留。
  ///
  /// @param text 文字
  /// @return 解码后的文字
  private static String decodeEntities(String text) {
    Matcher matcher = ENTITY.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          out, Matcher.quoteReplacement(decode(matcher.group(1), matcher.group())));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /// 解码一个实体。
  ///
  /// @param body `&` 与 `;` 之间的部分
  /// @param original 整个实体，认不出时原样返回
  /// @return 解码结果
  private static String decode(String body, String original) {
    if (!body.startsWith("#")) {
      return NAMED.getOrDefault(body, original);
    }
    boolean hex = body.length() > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X');
    try {
      int code = Integer.parseInt(body.substring(hex ? 2 : 1), hex ? 16 : 10);
      boolean valid =
          code > 0 && code <= Character.MAX_CODE_POINT && !(code >= 0xD800 && code <= 0xDFFF);
      return valid ? Character.toString(code) : original;
    } catch (NumberFormatException ex) {
      return original;
    }
  }
}
