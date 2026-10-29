package dev.patra.catalog.infra.fulltext.parser;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import java.util.Optional;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/// JATS 公式 → `Formula` 块 / `MathInline` 行内节点。
///
/// 取值顺序：直接子元素或 `<alternatives>` 里的 MathML → `tex-math` → `graphic`。
/// MathML 去掉命名空间前缀与声明后序列化，保留 `display`、`columnalign` 等属性，不做规范化
/// （白名单在门户渲染端统一维护）。
///
/// @author linqibin
/// @since 0.11.0
final class JatsMathReader {

  private final JatsParseContext ctx;

  /// 创建公式读取器。
  ///
  /// @param ctx 解析上下文
  JatsMathReader(JatsParseContext ctx) {
    this.ctx = ctx;
  }

  /// 行间公式 `<disp-formula>`：MathML 输出公式块，只有 TeX 输出代码块，只有图片输出带图片的公式块。
  ///
  /// @param e 元素
  /// @return 块
  Block display(Element e) {
    String id = JatsDom.attr(e, "id");
    String label = JatsDom.child(e, "label").map(JatsDom::text).orElse(null);
    if (rendersAsFormula(e)) {
      ctx.registerTarget(id);
    }
    Optional<Element> mathml = find(e, true);
    if (mathml.isPresent()) {
      return Block.Formula.builder().id(id).label(label).mathml(serialize(mathml.get())).build();
    }
    Optional<Element> tex = findNamed(e, "tex-math");
    if (tex.isPresent()) {
      ctx.warn();
      return new Block.Code(stripTex(tex.get().getTextContent()));
    }
    ctx.warn();
    Optional<FullTextMedia> image =
        findNamed(e, "graphic").map(JatsDom::href).flatMap(ctx::matchMedia);
    return Block.Formula.builder()
        .id(id)
        .label(label)
        .image(image.map(FullTextMedia::name).orElse(null))
        .width(image.map(FullTextMedia::width).orElse(null))
        .height(image.map(FullTextMedia::height).orElse(null))
        .build();
  }

  /// 任意位置的 MathML `math` 元素。
  ///
  /// @param e 元素
  /// @return 行内公式
  Inline inline(Element e) {
    return new Inline.MathInline(serialize(e));
  }

  /// 行内公式 `<inline-formula>`（或行内的 `<alternatives>`）：MathML 优先，只有 TeX 时输出文本并计警告。
  ///
  /// @param e 元素
  /// @param inlineReader 行内读取器
  /// @return 行内节点
  Inline inlineFormula(Element e, JatsInlineReader inlineReader) {
    Optional<Element> mathml = find(e, true);
    if (mathml.isPresent()) {
      return inline(mathml.get());
    }
    ctx.warn();
    return findNamed(e, "tex-math")
        .<Inline>map(tex -> new Inline.Text(stripTex(tex.getTextContent())))
        .orElseGet(() -> new Inline.Text(JatsDom.text(e)));
  }

  /// 行间公式是否输出为带 id 的公式块（有 MathML 或公式图片）；只有 TeX 时输出为代码块。
  ///
  /// @param e `<disp-formula>`
  /// @return 是否
  static boolean rendersAsFormula(Element e) {
    return find(e, true).isPresent() || findNamed(e, "tex-math").isEmpty();
  }

  /// 在直接子元素与 `<alternatives>` 里找 MathML。
  private static Optional<Element> find(Element e, boolean lookIntoAlternatives) {
    for (Element child : JatsDom.children(e)) {
      if (JatsDom.isMathml(child)) {
        return Optional.of(child);
      }
      if (lookIntoAlternatives && "alternatives".equals(JatsDom.name(child))) {
        Optional<Element> found = find(child, false);
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }

  /// 在直接子元素与 `<alternatives>` 里找指定元素。
  private static Optional<Element> findNamed(Element e, String name) {
    return JatsDom.child(e, name)
        .or(() -> JatsDom.child(e, "alternatives").flatMap(alt -> JatsDom.child(alt, name)));
  }

  /// 去掉 `\documentclass…\begin{document}` 外壳与外层 `$$…$$` / `$…$`。
  ///
  /// @param tex TeX 原文
  /// @return 去壳后的 TeX
  static String stripTex(String tex) {
    String s = tex;
    int begin = s.indexOf("\\begin{document}");
    if (begin >= 0) {
      int end = s.indexOf("\\end{document}", begin);
      s = s.substring(begin + "\\begin{document}".length(), end >= 0 ? end : s.length());
    }
    s = s.strip();
    if (s.length() >= 4 && s.startsWith("$$") && s.endsWith("$$")) {
      s = s.substring(2, s.length() - 2);
    } else if (s.length() >= 2 && s.startsWith("$") && s.endsWith("$")) {
      s = s.substring(1, s.length() - 1);
    }
    return s.strip();
  }

  /// 序列化 MathML：只用本地名，去掉命名空间声明，文本与属性做 XML 转义。
  ///
  /// @param e `math` 元素
  /// @return MathML 字符串
  static String serialize(Element e) {
    StringBuilder sb = new StringBuilder();
    write(e, sb);
    return sb.toString();
  }

  /// 递归写出一个节点。
  private static void write(Node node, StringBuilder sb) {
    switch (node.getNodeType()) {
      case Node.ELEMENT_NODE -> {
        String name = JatsDom.name(node);
        sb.append('<').append(name);
        NamedNodeMap attrs = node.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
          Node attr = attrs.item(i);
          String attrName = attr.getNodeName();
          if (attrName.equals("xmlns") || attrName.startsWith("xmlns:")) {
            continue;
          }
          sb.append(' ')
              .append(JatsDom.name(attr))
              .append("=\"")
              .append(escape(attr.getNodeValue(), true))
              .append('"');
        }
        if (!node.hasChildNodes()) {
          sb.append("/>");
          return;
        }
        sb.append('>');
        for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
          write(c, sb);
        }
        sb.append("</").append(name).append('>');
      }
      case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
        String text = node.getNodeValue();
        if (!text.isBlank()) {
          sb.append(escape(text, false));
        }
      }
      default -> {
        // 注释与处理指令不输出
      }
    }
  }

  /// XML 转义。
  private static String escape(String value, boolean attribute) {
    String s = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    return attribute ? s.replace("\"", "&quot;") : s;
  }
}
