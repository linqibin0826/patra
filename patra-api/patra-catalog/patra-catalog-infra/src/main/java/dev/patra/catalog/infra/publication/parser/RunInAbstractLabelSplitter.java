package dev.patra.catalog.infra.publication.parser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// PubMed 摘要粘连标签拆分。
///
/// 有些出版方把结构化摘要的段落标签直接写进一个没有 `Label` 属性的 `AbstractText`，
/// 标签和正文之间没有任何分隔，如 `ObjectiveEsophageal cancer….MethodsThe dataset…`。
/// 本类识别这种写法，把整段切成带标签的段落；不满足拆分条件时返回空列表，调用方保持原样。
///
/// 拆分条件（同时满足）：
/// - 文本以词表里的标签词开头，标签词后紧跟大写字母或数字
/// - 后面至少还有一个标签：句末标点（`. ! ? ) ]`）之后紧跟一个首字母大写的短语（最多 6 个词，可用
///   `and` / `or` / `of` / `&` / 逗号连接，如 `Main Outcome Measures`、`Intervention or Exposures`），
///   短语末尾同样紧跟大写字母或数字。正常行文的句末标点后总有空格，不会命中
///
/// 开头标签的词表与中间标签的写法来自 2026-10 对 mini 上 97 篇粘连摘要的实测（全部完整拆开）。
/// 段落内容照原文，只去首尾空白。
public final class RunInAbstractLabelSplitter {

  /// 已知标签词（按长度降序拼进正则，保证 `Objectives` 优先于 `Objective`）。
  private static final List<String> LABELS =
      List.of(
          "Background",
          "Objective",
          "Objectives",
          "Aim",
          "Aims",
          "Purpose",
          "Introduction",
          "Importance",
          "Context",
          "Relevance",
          "Design",
          "Setting",
          "Participants",
          "Subjects",
          "Sample",
          "Intervention",
          "Interventions",
          "Method",
          "Methods",
          "Methodology",
          "Measures",
          "Analysis",
          "Results",
          "Findings",
          "Discussion",
          "Conclusion",
          "Conclusions",
          "Interpretation",
          "Application");

  private static final String LABEL_GROUP =
      LABELS.stream()
          .sorted(Comparator.comparingInt(String::length).reversed())
          .collect(Collectors.joining("|", "(", ")"));

  /// 开头的标签：文本起始处的标签词，后面紧跟大写字母或数字。
  private static final Pattern LEADING =
      Pattern.compile("^" + LABEL_GROUP + "(?=[\\p{Lu}\\p{Nd}])");

  /// 中间的标签：句末标点之后、首字母大写的短语（最多 6 个词），后面紧跟大写字母或数字。
  private static final Pattern INNER =
      Pattern.compile(
          "(?<=[.!?)\\]])(\\p{Lu}\\p{Ll}+(?:(?:,? (?:and|or|of|&) |, | & | )\\p{L}\\p{Ll}+){0,5})"
              + "(?=[\\p{Lu}\\p{Nd}])");

  /// 工具类不可实例化。
  private RunInAbstractLabelSplitter() {}

  /// 把粘连标签的摘要文本拆成带标签的段落。
  ///
  /// @param text 没有 `Label` 的摘要文本（可为 null）
  /// @return 带标签的段落；不满足拆分条件时为空列表
  public static List<LabeledText> split(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    Matcher leading = LEADING.matcher(text);
    if (!leading.find()) {
      return List.of();
    }
    List<int[]> marks = new ArrayList<>();
    marks.add(new int[] {leading.start(1), leading.end(1)});
    Matcher inner = INNER.matcher(text);
    inner.region(leading.end(1), text.length());
    inner.useTransparentBounds(true);
    while (inner.find()) {
      marks.add(new int[] {inner.start(1), inner.end(1)});
    }
    if (marks.size() < 2) {
      return List.of();
    }
    return Stream.iterate(0, i -> i < marks.size(), i -> i + 1)
        .map(i -> toLabeledText(text, marks, i))
        .toList();
  }

  /// 第 `index` 个标签对应的段落：标签之后到下一个标签之前的文本。
  ///
  /// @param text 原文
  /// @param marks 各标签的起止位置
  /// @param index 标签序号
  /// @return 段落
  private static LabeledText toLabeledText(String text, List<int[]> marks, int index) {
    int[] mark = marks.get(index);
    int end = index + 1 < marks.size() ? marks.get(index + 1)[0] : text.length();
    String label = text.substring(mark[0], mark[1]).toUpperCase(Locale.ROOT);
    return LabeledText.of(label, text.substring(mark[1], end).strip());
  }

  /// 拆出的一段带标签的摘要。
  ///
  /// @param label 标签（大写，如 `OBJECTIVE`）
  /// @param content 段落正文（照原文）
  public record LabeledText(String label, String content) {

    /// 创建段落。
    ///
    /// @param label 标签
    /// @param content 段落正文
    /// @return 段落
    public static LabeledText of(String label, String content) {
      return new LabeledText(label, content);
    }
  }
}
