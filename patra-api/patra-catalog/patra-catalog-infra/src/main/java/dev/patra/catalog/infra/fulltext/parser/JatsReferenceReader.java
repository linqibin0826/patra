package dev.patra.catalog.infra.fulltext.parser;

import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import dev.patra.catalog.domain.fulltext.model.vo.body.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/// JATS 参考文献 `<ref-list>` → `Reference`。
///
/// `<ref>` 下可能直接是 `mixed-citation` / `element-citation`，也可能是 `citation-alternatives`
/// 包着两种（BMC、Nat Commun 几乎全是）。优先 `mixed-citation` 原样转换；没有时由
/// `element-citation` 按"作者. 标题. 期刊. 年;卷:页."拼出。PMID / DOI 先在选用的那种里找，再到另一种里找。
///
/// @author linqibin
/// @since 0.11.0
final class JatsReferenceReader {

  private final JatsInlineReader inline;

  /// 创建参考文献读取器。
  ///
  /// @param inline 行内读取器
  JatsReferenceReader(JatsInlineReader inline) {
    this.inline = inline;
  }

  /// 读一个 `<ref-list>`（含嵌套的 `<ref-list>`）。
  ///
  /// @param refList 元素
  /// @param out 输出
  void read(Element refList, List<Reference> out) {
    for (Element child : JatsDom.children(refList)) {
      switch (JatsDom.name(child)) {
        case "ref" -> out.add(reference(child));
        case "ref-list" -> read(child, out);
        default -> {
          // title 等
        }
      }
    }
  }

  /// 一条参考文献。
  private Reference reference(Element ref) {
    List<Element> citations = new ArrayList<>();
    for (Element child : JatsDom.children(ref)) {
      String name = JatsDom.name(child);
      if ("citation-alternatives".equals(name)) {
        citations.addAll(JatsDom.children(child));
      } else if (name.endsWith("citation")) {
        citations.add(child);
      }
    }
    Optional<Element> mixed =
        citations.stream().filter(c -> "mixed-citation".equals(JatsDom.name(c))).findFirst();
    Optional<Element> element =
        citations.stream().filter(c -> "element-citation".equals(JatsDom.name(c))).findFirst();
    Optional<Element> chosen = mixed.or(() -> element).or(() -> citations.stream().findFirst());

    List<Inline> content =
        mixed.isPresent()
            ? inline.readChildren(mixed.get())
            : element
                .map(JatsReferenceReader::compose)
                .orElseGet(() -> chosen.map(inline::readChildren).orElse(List.of()));
    return Reference.builder()
        .id(JatsDom.attr(ref, "id"))
        .label(
            JatsDom.child(ref, "label").map(JatsDom::text).filter(s -> !s.isEmpty()).orElse(null))
        .content(content)
        .pmid(pubId(chosen, citations, "pmid"))
        .doi(pubId(chosen, citations, "doi"))
        .build();
  }

  /// 由 `element-citation` 拼出"作者. 标题. 期刊. 年;卷:页."。
  private static List<Inline> compose(Element citation) {
    List<String> parts = new ArrayList<>();
    List<String> authors = new ArrayList<>();
    JatsDom.descendant(citation, "person-group")
        .ifPresent(
            group -> {
              for (Element person : JatsDom.children(group)) {
                switch (JatsDom.name(person)) {
                  case "name" -> {
                    String surname = JatsDom.child(person, "surname").map(JatsDom::text).orElse("");
                    String given =
                        JatsDom.child(person, "given-names").map(JatsDom::text).orElse("");
                    authors.add((surname + " " + given).strip());
                  }
                  case "collab" -> authors.add(JatsDom.text(person));
                  case "string-name" -> authors.add(stringName(person));
                  case "etal" -> authors.add("et al");
                  default -> {
                    // 忽略
                  }
                }
              }
            });
    if (!authors.isEmpty()) {
      parts.add(String.join(", ", authors));
    }
    JatsDom.child(citation, "article-title")
        .or(() -> JatsDom.child(citation, "chapter-title"))
        .map(JatsDom::text)
        .filter(s -> !s.isEmpty())
        .ifPresent(parts::add);
    JatsDom.child(citation, "source")
        .map(JatsDom::text)
        .filter(s -> !s.isEmpty())
        .ifPresent(parts::add);

    StringBuilder locator = new StringBuilder();
    JatsDom.child(citation, "year").map(JatsDom::text).ifPresent(locator::append);
    JatsDom.child(citation, "volume")
        .map(JatsDom::text)
        .ifPresent(v -> locator.append(';').append(v));
    Optional<String> fpage = JatsDom.child(citation, "fpage").map(JatsDom::text);
    Optional<String> pages =
        fpage
            .map(
                f ->
                    f + JatsDom.child(citation, "lpage").map(l -> "-" + JatsDom.text(l)).orElse(""))
            .or(() -> JatsDom.child(citation, "elocation-id").map(JatsDom::text));
    pages.ifPresent(p -> locator.append(':').append(p));
    if (!locator.isEmpty()) {
      parts.add(locator.toString());
    }
    String text =
        parts.stream()
            .map(p -> p.endsWith(".") ? p : p + ".")
            .reduce((a, b) -> a + " " + b)
            .orElse("");
    return text.isEmpty() ? List.of() : List.of(new Inline.Text(text));
  }

  /// `string-name` 的文本：相邻两个子元素之间没有文本时补一个空格。
  private static String stringName(Element name) {
    StringBuilder sb = new StringBuilder();
    for (Node n = name.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (JatsDom.isElementRightAfterElement(n)) {
        sb.append(' ');
      }
      sb.append(n.getTextContent());
    }
    return sb.toString().replaceAll("\\s+", " ").strip();
  }

  /// 先在选用的 citation 里找 `pub-id`，再到其他 citation 里找。
  private static String pubId(Optional<Element> chosen, List<Element> citations, String type) {
    List<Element> order = new ArrayList<>();
    chosen.ifPresent(order::add);
    citations.stream().filter(c -> chosen.isEmpty() || c != chosen.get()).forEach(order::add);
    for (Element citation : order) {
      Optional<String> value = findPubId(citation, type);
      if (value.isPresent()) {
        return value.get();
      }
    }
    return null;
  }

  /// 在一个 citation 的后代里找指定类型的 `pub-id`。
  private static Optional<String> findPubId(Element e, String type) {
    for (Element child : JatsDom.children(e)) {
      if ("pub-id".equals(JatsDom.name(child)) && type.equals(JatsDom.attr(child, "pub-id-type"))) {
        String value = JatsDom.text(child);
        if (!value.isEmpty()) {
          return Optional.of(value);
        }
      }
      Optional<String> nested = findPubId(child, type);
      if (nested.isPresent()) {
        return nested;
      }
    }
    return Optional.empty();
  }
}
