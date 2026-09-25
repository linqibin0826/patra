package dev.linqibin.patra.catalog.domain.model.vo.publication;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// PubMed 内联标记（排版标签 + MathML）的纯文本派生工具。
///
/// 标签白名单与前端 `patra-portal/src/lib/rich-inline-text.ts` 的 `FORMATTING_TAGS` + `MATH_TAGS`
/// 同一份；两边任一处增删标签必须同步。[#TAG_PATTERN] 同时用于 SQL `regexp_replace`（PostgreSQL ARE，
/// 带 `i` 标志）与本类的 Java 正则，因此只使用两者共有的语法。
///
/// @author linqibin
/// @since 0.1.0
public final class InlineMarkup {

  /// 白名单标签正则：`<` 或 `</` 紧跟白名单标签名，可选属性段（不含尖括号），可选自闭合 `/`。
  /// 字面尖括号（`x<y and z>0`、`age < 5`）与白名单外标签（`<br>`）都不匹配，按文本保留。
  public static final String TAG_PATTERN =
      "</?(i|b|u|sub|sup|math|mrow|mi|mo|mn|msub|msup|msubsup|mtext|mspace|mstyle|mover|munder"
          + "|munderover|mfrac|msqrt|semantics|annotation)(\\s[^<>]*)?\\s*/?\\s*>";

  private static final Pattern TAG = Pattern.compile(TAG_PATTERN, Pattern.CASE_INSENSITIVE);

  /// 已闭合的 annotation 元素（MathML 公式的 LaTeX 重复表述），整体删除；未闭合的由 TAG 只剥标签。
  private static final Pattern CLOSED_ANNOTATION =
      Pattern.compile(
          "<annotation(\\s[^<>]*)?>.*?</annotation\\s*>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /// HTML 实体：命名 5 个 + 十进制（≤7 位）+ 十六进制（≤6 位）；其余原样保留。
  private static final Pattern ENTITY =
      Pattern.compile("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|amp|lt|gt|quot|apos);");

  private InlineMarkup() {}

  /// 把内联标记文本转为可见纯文本：删除已闭合 annotation 子树 → 剥白名单标签 → 解码实体。
  ///
  /// 解码放最后，解出的 `<` / `>` 是文本，不会被当成标签二次处理。
  ///
  /// @param markup 含内联标记的原文，可为 null
  /// @return 可见纯文本；入参 null 时返回 null
  public static String toPlainText(String markup) {
    if (markup == null) {
      return null;
    }
    String withoutAnnotation = CLOSED_ANNOTATION.matcher(markup).replaceAll("");
    String withoutTags = TAG.matcher(withoutAnnotation).replaceAll("");
    return decodeEntities(withoutTags);
  }

  /// 按 Unicode 码点截断，不会切在代理对中间。
  ///
  /// @param text 待截断文本，可为 null
  /// @param maxCodePoints 最大码点数
  /// @return 截断后的文本；入参 null 时返回 null
  public static String truncate(String text, int maxCodePoints) {
    if (text == null || text.codePointCount(0, text.length()) <= maxCodePoints) {
      return text;
    }
    return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
  }

  /// 解码 HTML 实体；无法识别或码点非法的实体原样保留。
  ///
  /// @param text 剥完标签的文本
  /// @return 解码后的文本
  private static String decodeEntities(String text) {
    Matcher m = ENTITY.matcher(text);
    StringBuilder sb = new StringBuilder(text.length());
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(decode(m.group(1), m.group(0))));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /// 单个实体体 → 字符；数字实体码点非法时返回原实体文本。
  ///
  /// @param body `&` 与 `;` 之间的实体体
  /// @param raw 原实体文本（含 `&` 与 `;`）
  /// @return 解码结果
  private static String decode(String body, String raw) {
    return switch (body) {
      case "amp" -> "&";
      case "lt" -> "<";
      case "gt" -> ">";
      case "quot" -> "\"";
      case "apos" -> "'";
      default -> {
        int codePoint =
            body.charAt(1) == 'x' || body.charAt(1) == 'X'
                ? Integer.parseInt(body.substring(2), 16)
                : Integer.parseInt(body.substring(1));
        yield Character.isValidCodePoint(codePoint) && codePoint != 0
            ? new String(Character.toChars(codePoint))
            : raw;
      }
    };
  }
}
