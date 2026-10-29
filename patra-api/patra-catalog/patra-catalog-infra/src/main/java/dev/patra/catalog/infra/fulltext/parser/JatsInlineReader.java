package dev.patra.catalog.infra.fulltext.parser;

import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/// JATS 行内内容 → `Inline` 节点。
///
/// 不认识的行内元素保留其子内容；白名单外的引用类型或找不到目标的引用只保留子节点并计警告。
///
/// @author linqibin
/// @since 0.11.0
final class JatsInlineReader {

  private static final Map<String, String> STYLES =
      Map.of(
          "italic", "italic",
          "bold", "bold",
          "sup", "sup",
          "sub", "sub",
          "underline", "underline",
          "sc", "smallcaps",
          "monospace", "monospace");

  private static final Set<String> XREF_TYPES =
      Set.of("bibr", "fig", "table", "disp-formula", "sec", "fn", "boxed-text");

  /// `person-group` 里算作一位作者的元素。
  private static final Set<String> AUTHORS = Set.of("name", "string-name", "collab");

  private static final Set<String> PMC_LINK_PUB_ID_TYPES = Set.of("pmid", "pmcid");

  private static final Set<String> SKIPPED = Set.of("fn", "inline-supplementary-material", "media");

  private final JatsParseContext ctx;
  private final JatsMathReader math;

  /// 创建行内读取器。
  ///
  /// @param ctx 解析上下文
  /// @param math 公式读取器
  JatsInlineReader(JatsParseContext ctx, JatsMathReader math) {
    this.ctx = ctx;
    this.math = math;
  }

  /// 读一个元素的全部子节点，并去掉首尾空白。
  ///
  /// @param parent 元素
  /// @return 行内节点
  List<Inline> readChildren(Element parent) {
    List<Inline> out = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      out.addAll(readNode(n));
    }
    return trim(out);
  }

  /// 读表格单元格：多个段落之间、列表项之间插换行。
  ///
  /// @param cell `<td>` / `<th>`
  /// @return 行内节点
  List<Inline> readCell(Element cell) {
    List<Inline> out = new ArrayList<>();
    for (Node n = cell.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.ELEMENT_NODE && "p".equals(JatsDom.name(n))) {
        appendWithBreak(out, readChildren((Element) n));
      } else if (n.getNodeType() == Node.ELEMENT_NODE && "list".equals(JatsDom.name(n))) {
        for (Element item : JatsDom.children((Element) n)) {
          if ("list-item".equals(JatsDom.name(item))) {
            appendWithBreak(out, readCell(item));
          }
        }
      } else {
        out.addAll(readNode(n));
      }
    }
    return trim(out);
  }

  /// 读一个节点。
  ///
  /// @param node 节点
  /// @return 行内节点
  List<Inline> readNode(Node node) {
    if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
      String text = node.getNodeValue().replaceAll("\\s+", " ");
      return text.isEmpty() ? List.of() : List.of(new Inline.Text(text));
    }
    if (node.getNodeType() != Node.ELEMENT_NODE) {
      return List.of();
    }
    Element e = (Element) node;
    if (JatsDom.isMathml(e)) {
      return List.of(math.inline(e));
    }
    String name = JatsDom.name(e);
    String style = STYLES.get(name);
    if (style != null) {
      return List.of(new Inline.Styled(style, merge(readAll(e))));
    }
    return switch (name) {
      case "xref" -> readXref(e);
      case "ext-link", "uri" -> readLink(e);
      case "break" -> List.of(new Inline.Break());
      case "person-group" -> readPersonGroup(e);
      case "name", "string-name" -> readName(e);
      case "pub-id" -> isAddedByPmc(e) ? List.of() : readAll(e);
      case "inline-formula", "alternatives" -> List.of(math.inlineFormula(e, this));
      case "inline-graphic" -> {
        ctx.warn();
        yield List.of();
      }
      default -> SKIPPED.contains(name) ? List.of() : readAll(e);
    };
  }

  /// 读子节点但不去首尾空白（嵌在别的行内内容里）。
  private List<Inline> readAll(Element parent) {
    List<Inline> out = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      out.addAll(readNode(n));
    }
    return out;
  }

  /// 作者组，按 PMC 原文页的显示：两个作者之间只有空白时补 ", "（Frontiers、SAGE 只用换行分隔）；空的 `<etal/>`
  /// 显示为 "et al"，跟在作者后面时同样补 ", "；末尾的空白去掉。
  private List<Inline> readPersonGroup(Element group) {
    List<Inline> out = new ArrayList<>();
    boolean afterAuthor = false;
    boolean pendingSpace = false;
    for (Node n = group.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.TEXT_NODE && n.getNodeValue().isBlank()) {
        pendingSpace = true;
        continue;
      }
      boolean author = n.getNodeType() == Node.ELEMENT_NODE && AUTHORS.contains(JatsDom.name(n));
      boolean emptyEtal = "etal".equals(JatsDom.name(n)) && n.getTextContent().isBlank();
      if (afterAuthor && (author || emptyEtal)) {
        out.add(new Inline.Text(", "));
      } else if (pendingSpace) {
        out.add(new Inline.Text(" "));
      }
      out.addAll(emptyEtal ? List.of(new Inline.Text("et al")) : readNode(n));
      afterAuthor = author;
      pendingSpace = false;
    }
    return out;
  }

  /// 人名，按 PMC 原文页的显示：相邻两个子元素之间没有文本时补一个空格（PLOS 等写成
  /// `<surname>Davis</surname><given-names>FG</given-names>`，显示为 "Davis FG"），`<suffix>` 前补 ", "
  /// （"Cooney WP, III"）；首尾空白去掉（Wiley、Hindawi 的 `string-name` 内部换行，否则逗号前会留空格）。
  private List<Inline> readName(Element name) {
    List<Inline> out = new ArrayList<>();
    for (Node n = name.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (JatsDom.isElementRightAfterElement(n)) {
        out.add(new Inline.Text("suffix".equals(JatsDom.name(n)) ? ", " : " "));
      }
      out.addAll(readNode(n));
    }
    return trim(out);
  }

  /// `pub-id` 是否为 PMC 补上的标识（PMID、PMCID、`assigning-authority="pmc"` 的 DOI）。PMC 原文页不把它们
  /// 显示成文本，只做成 [PubMed] / [PMC free article] / [DOI] 链接；出版方自己写进著录的 DOI 照原文保留。
  private static boolean isAddedByPmc(Element pubId) {
    return PMC_LINK_PUB_ID_TYPES.contains(JatsDom.attr(pubId, "pub-id-type"))
        || "pmc".equals(JatsDom.attr(pubId, "assigning-authority"));
  }

  /// 交叉引用：类型在白名单内且所有目标都会出现在结果里时生成 `Xref`，否则只留子节点。
  private List<Inline> readXref(Element e) {
    List<Inline> children = merge(readAll(e));
    String refType = JatsDom.attr(e, "ref-type");
    String rid = JatsDom.attr(e, "rid");
    List<String> targets = rid == null ? List.of() : Arrays.asList(rid.split("\\s+"));
    if (refType == null
        || !XREF_TYPES.contains(refType)
        || targets.isEmpty()
        || !targets.stream().allMatch(ctx::isKnownTarget)) {
      ctx.warn();
      return children;
    }
    return List.of(new Inline.Xref(refType, targets, children));
  }

  /// 外链：只保留 http / https / doi，DOI 统一成 `https://doi.org/` 形式。
  private List<Inline> readLink(Element e) {
    List<Inline> children = merge(readAll(e));
    String href = JatsDom.href(e);
    String type = JatsDom.attr(e, "ext-link-type");
    if (href == null) {
      href = JatsDom.text(e);
    }
    String normalized = normalizeHref(href, type);
    if (normalized == null) {
      return children;
    }
    return List.of(new Inline.Link(normalized, children));
  }

  /// 规范化链接，不接受的协议返回 `null`。
  private static String normalizeHref(String href, String type) {
    if (href == null || href.isBlank()) {
      return null;
    }
    String lower = href.toLowerCase(Locale.ROOT);
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return href;
    }
    if (lower.startsWith("doi:")) {
      return "https://doi.org/" + href.substring(4).trim();
    }
    if ("doi".equals(type) || lower.startsWith("10.")) {
      return "https://doi.org/" + href;
    }
    return null;
  }

  /// 追加一段内容，已有内容时先插换行。
  private static void appendWithBreak(List<Inline> out, List<Inline> part) {
    if (part.isEmpty()) {
      return;
    }
    if (!out.isEmpty()) {
      out.add(new Inline.Break());
    }
    out.addAll(part);
  }

  /// 合并相邻文本并去掉首尾空白、丢掉空文本。
  ///
  /// @param inlines 行内节点
  /// @return 处理后的节点
  static List<Inline> trim(List<Inline> inlines) {
    List<Inline> merged = new ArrayList<>(merge(inlines));
    if (!merged.isEmpty() && merged.getFirst() instanceof Inline.Text t) {
      String s = t.text().stripLeading();
      merged.set(0, new Inline.Text(s));
    }
    if (!merged.isEmpty() && merged.getLast() instanceof Inline.Text t) {
      String s = t.text().stripTrailing();
      merged.set(merged.size() - 1, new Inline.Text(s));
    }
    merged.removeIf(i -> i instanceof Inline.Text t && t.text().isEmpty());
    return merged;
  }

  /// 合并相邻文本。
  private static List<Inline> merge(List<Inline> inlines) {
    List<Inline> out = new ArrayList<>();
    for (Inline inline : inlines) {
      if (inline instanceof Inline.Text t
          && !out.isEmpty()
          && out.getLast() instanceof Inline.Text prev) {
        out.set(out.size() - 1, new Inline.Text((prev.text() + t.text()).replaceAll(" {2,}", " ")));
      } else {
        out.add(inline);
      }
    }
    return out;
  }
}
