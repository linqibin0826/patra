package dev.patra.catalog.infra.fulltext.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/// JATS DOM 的元素访问（命名空间敏感解析下，JATS 元素没有命名空间，MathML 与 XLink 有）。
///
/// @author linqibin
/// @since 0.11.0
final class JatsDom {

  /// MathML 命名空间。
  static final String MATHML_NS = "http://www.w3.org/1998/Math/MathML";

  /// XLink 命名空间。
  static final String XLINK_NS = "http://www.w3.org/1999/xlink";

  private JatsDom() {}

  /// 元素的本地名。
  ///
  /// @param node 节点
  /// @return 本地名
  static String name(Node node) {
    return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
  }

  /// 是否 MathML 的 `math` 元素。
  ///
  /// @param node 节点
  /// @return 是否
  static boolean isMathml(Node node) {
    return node.getNodeType() == Node.ELEMENT_NODE
        && MATHML_NS.equals(node.getNamespaceURI())
        && "math".equals(node.getLocalName());
  }

  /// 直接子元素。
  ///
  /// @param parent 父元素
  /// @return 子元素
  static List<Element> children(Element parent) {
    List<Element> result = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        result.add((Element) n);
      }
    }
    return result;
  }

  /// 是否元素且紧跟在另一个元素后面（中间没有文本）。人名的子元素这样相邻时要补空格。
  ///
  /// @param node 节点
  /// @return 是否
  static boolean isElementRightAfterElement(Node node) {
    Node previous = node.getPreviousSibling();
    return node.getNodeType() == Node.ELEMENT_NODE
        && previous != null
        && previous.getNodeType() == Node.ELEMENT_NODE;
  }

  /// 第一个指定本地名的直接子元素。
  ///
  /// @param parent 父元素
  /// @param name 本地名
  /// @return 子元素
  static Optional<Element> child(Element parent, String name) {
    return children(parent).stream().filter(e -> name.equals(name(e))).findFirst();
  }

  /// 第一个指定本地名的后代元素（深度优先）。
  ///
  /// @param parent 父元素
  /// @param name 本地名
  /// @return 后代元素
  static Optional<Element> descendant(Element parent, String name) {
    for (Element child : children(parent)) {
      if (name.equals(name(child))) {
        return Optional.of(child);
      }
      Optional<Element> found = descendant(child, name);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /// 属性值，没有或为空白时为 `null`。
  ///
  /// @param element 元素
  /// @param name 属性名
  /// @return 属性值
  static String attr(Element element, String name) {
    String value = element.getAttribute(name);
    return value == null || value.isBlank() ? null : value.trim();
  }

  /// `xlink:href` 属性值。
  ///
  /// @param element 元素
  /// @return 属性值，没有时为 `null`
  static String href(Element element) {
    String value = element.getAttributeNS(XLINK_NS, "href");
    return value == null || value.isBlank() ? null : value.trim();
  }

  /// 去掉多余空白后的纯文本。
  ///
  /// @param node 节点
  /// @return 文本
  static String text(Node node) {
    return node.getTextContent().replaceAll("\\s+", " ").trim();
  }
}
