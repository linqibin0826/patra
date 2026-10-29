package dev.patra.catalog.infra.fulltext.parser;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import dev.patra.catalog.domain.fulltext.model.vo.body.Section;
import dev.patra.catalog.domain.fulltext.model.vo.body.TableCell;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/// JATS 块级内容 → `Block` / `Section`。
///
/// `<p>` 里嵌着块级元素时在两侧切开（Wiley、BMC 的公式与图表常这样写）；
/// 补充材料与多媒体跳过；不认识的块级元素取纯文本成段落并计警告。
///
/// @author linqibin
/// @since 0.11.0
final class JatsBlockReader {

  /// 嵌在 `<p>` 里时要切开段落的块级元素。
  private static final Set<String> BLOCK_IN_PARAGRAPH =
      Set.of(
          "disp-formula",
          "disp-formula-group",
          "fig",
          "fig-group",
          "table-wrap",
          "table-wrap-group",
          "list",
          "def-list",
          "disp-quote",
          "boxed-text",
          "statement",
          "preformat",
          "code",
          "supplementary-material",
          "media");

  /// 结构性元素，由父元素自己处理。
  private static final Set<String> STRUCTURAL =
      Set.of("title", "label", "caption", "object-id", "alt-text", "sec-meta", "permissions");

  private static final Set<String> ORDERED_LIST_TYPES =
      Set.of("order", "alpha-lower", "alpha-upper", "roman-lower", "roman-upper");

  private final JatsParseContext ctx;
  private final JatsInlineReader inline;
  private final JatsMathReader math;

  /// 创建块读取器。
  ///
  /// @param ctx 解析上下文
  /// @param inline 行内读取器
  /// @param math 公式读取器
  JatsBlockReader(JatsParseContext ctx, JatsInlineReader inline, JatsMathReader math) {
    this.ctx = ctx;
    this.inline = inline;
    this.math = math;
  }

  /// 读一个 `<sec>`；补充材料节或读完为空的节返回空。
  ///
  /// @param sec 元素
  /// @return 章节
  Optional<Section> readSection(Element sec) {
    if (isSupplementary(sec)) {
      return Optional.empty();
    }
    List<Block> blocks = new ArrayList<>();
    List<Section> subsections = new ArrayList<>();
    for (Element child : JatsDom.children(sec)) {
      if ("sec".equals(JatsDom.name(child))) {
        readSection(child).ifPresent(subsections::add);
      } else {
        readBlock(child, blocks);
      }
    }
    if (blocks.isEmpty() && subsections.isEmpty()) {
      return Optional.empty();
    }
    String id = ctx.idOrGenerate(JatsDom.attr(sec, "id"), "sec");
    return Optional.of(Section.of(id, title(sec), subsections, blocks));
  }

  /// 读 back 里的 `<fn-group>`：每条脚注一个子章节（id 为脚注 id、标题为脚注标签），供脚注引用定位。
  ///
  /// @param group 元素
  /// @return 章节，没有脚注时为空
  Optional<Section> readFootnoteGroup(Element group) {
    List<Section> footnotes = new ArrayList<>();
    List<Block> loose = new ArrayList<>();
    for (Element child : JatsDom.children(group)) {
      if ("fn".equals(JatsDom.name(child))) {
        List<Block> blocks = readBlocks(child);
        if (!blocks.isEmpty()) {
          List<Inline> title =
              JatsDom.child(child, "label")
                  .map(JatsDom::text)
                  .filter(s -> !s.isEmpty())
                  .<List<Inline>>map(l -> List.of(new Inline.Text(l)))
                  .orElse(List.of());
          footnotes.add(
              Section.of(
                  ctx.idOrGenerate(JatsDom.attr(child, "id"), "fn"), title, List.of(), blocks));
        }
      } else {
        readBlock(child, loose);
      }
    }
    if (footnotes.isEmpty() && loose.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        Section.of(
            ctx.idOrGenerate(JatsDom.attr(group, "id"), "fn-group"),
            title(group),
            footnotes,
            loose));
  }

  /// 读一个容器的全部块（容器里的 `<sec>` 展平为加粗标题段落加内容）。
  ///
  /// @param container 元素
  /// @return 块
  List<Block> readBlocks(Element container) {
    List<Block> blocks = new ArrayList<>();
    for (Element child : JatsDom.children(container)) {
      readBlock(child, blocks);
    }
    return blocks;
  }

  /// 读一个块级元素，结果追加到 `out`。
  ///
  /// @param e 元素
  /// @param out 输出
  void readBlock(Element e, List<Block> out) {
    String name = JatsDom.name(e);
    if (STRUCTURAL.contains(name)) {
      return;
    }
    switch (name) {
      case "p" -> readParagraph(e, out);
      case "sec", "ack", "notes", "app" -> {
        if (isSupplementary(e)) {
          return;
        }
        List<Inline> title = title(e);
        if (!title.isEmpty()) {
          out.add(new Block.Paragraph(List.of(new Inline.Styled("bold", title))));
        }
        out.addAll(readBlocks(e));
      }
      case "fig" -> figureWithSupplements(e, out);
      case "fig-group", "table-wrap-group", "disp-formula-group", "fn-group", "fn" ->
          out.addAll(readBlocks(e));
      case "table-wrap" -> out.add(table(e));
      case "table" -> out.add(tableOf(null, null, List.of(), e, List.of()));
      case "disp-formula" -> out.add(math.display(e));
      case "list" -> out.add(list(e));
      case "def-list" -> out.add(definitionList(e));
      case "disp-quote" -> out.add(new Block.Quote(readBlocks(e)));
      case "boxed-text", "statement" -> out.add(box(e));
      case "preformat", "code" -> out.add(new Block.Code(e.getTextContent().strip()));
      case "graphic" -> out.add(figureOf(JatsDom.attr(e, "id"), null, List.of(), JatsDom.href(e)));
      case "alternatives" -> preferredAlternative(e).ifPresent(alt -> readBlock(alt, out));
      case "attrib" -> paragraphOf(inline.readChildren(e)).ifPresent(out::add);
      case "supplementary-material", "media" -> {
        // 补充材料与多媒体不展示（release spec 边界 E）
      }
      default -> {
        ctx.warn();
        String text = JatsDom.text(e);
        if (!text.isEmpty()) {
          out.add(new Block.Paragraph(List.of(new Inline.Text(text))));
        }
      }
    }
  }

  /// 段落：遇到嵌在里面的块级元素就切开。
  private void readParagraph(Element p, List<Block> out) {
    List<Inline> buffer = new ArrayList<>();
    for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.ELEMENT_NODE && BLOCK_IN_PARAGRAPH.contains(JatsDom.name(n))) {
        paragraphOf(buffer).ifPresent(out::add);
        buffer.clear();
        readBlock((Element) n, out);
      } else {
        buffer.addAll(inline.readNode(n));
      }
    }
    paragraphOf(buffer).ifPresent(out::add);
  }

  /// 非空时生成段落。
  private static Optional<Block> paragraphOf(List<Inline> content) {
    List<Inline> trimmed = JatsInlineReader.trim(content);
    return trimmed.isEmpty() ? Optional.empty() : Optional.of(new Block.Paragraph(trimmed));
  }

  /// 章节标题（有 `<label>` 时拼在标题前）。
  private List<Inline> title(Element e) {
    List<Inline> title = JatsDom.child(e, "title").map(inline::readChildren).orElse(List.of());
    Optional<String> label = JatsDom.child(e, "label").map(JatsDom::text).filter(s -> !s.isEmpty());
    if (label.isEmpty() || title.isEmpty()) {
      return title;
    }
    List<Inline> withLabel = new ArrayList<>();
    withLabel.add(new Inline.Text(label.get() + " "));
    withLabel.addAll(title);
    return JatsInlineReader.trim(withLabel);
  }

  /// 图，以及嵌在图里的子图（eLife 的 figure supplement 写成 `<fig>` 里 `<p>` 下的 `<fig>`），
  /// 子图依次输出为独立的图块。
  private void figureWithSupplements(Element fig, List<Block> out) {
    String href = ownGraphic(fig).map(JatsDom::href).orElse(null);
    out.add(figureOf(JatsDom.attr(fig, "id"), label(fig), caption(fig), href));
    for (Element child : JatsDom.children(fig)) {
      if (!"caption".equals(JatsDom.name(child))) {
        nestedFigures(child, out);
      }
    }
  }

  /// 收集一个子树里的嵌套图。
  private void nestedFigures(Element e, List<Block> out) {
    if ("fig".equals(JatsDom.name(e))) {
      figureWithSupplements(e, out);
      return;
    }
    for (Element child : JatsDom.children(e)) {
      nestedFigures(child, out);
    }
  }

  /// 图自己的图片：直接子元素或 `<alternatives>` 里的 `<graphic>`，不进嵌套的子图。
  private static Optional<Element> ownGraphic(Element fig) {
    return JatsDom.child(fig, "graphic")
        .or(() -> JatsDom.child(fig, "alternatives").flatMap(a -> JatsDom.child(a, "graphic")));
  }

  /// 按清单匹配图片后生成图块；匹配不上时图片与宽高为空并计警告。
  private Block figureOf(String id, String label, List<Block> caption, String href) {
    ctx.registerTarget(id);
    Optional<FullTextMedia> media = ctx.matchMedia(href);
    if (media.isEmpty()) {
      ctx.warn();
    }
    return Block.Figure.builder()
        .id(id)
        .label(label)
        .caption(caption)
        .image(media.map(FullTextMedia::name).orElse(null))
        .width(media.map(FullTextMedia::width).orElse(null))
        .height(media.map(FullTextMedia::height).orElse(null))
        .build();
  }

  /// 表；没有 `<table>` 只有图片时按图处理。
  private Block table(Element wrap) {
    String id = JatsDom.attr(wrap, "id");
    Optional<Element> table = JatsDom.descendant(wrap, "table");
    if (table.isEmpty()) {
      String href = JatsDom.descendant(wrap, "graphic").map(JatsDom::href).orElse(null);
      return figureOf(id, label(wrap), caption(wrap), href);
    }
    List<Block> footnotes =
        JatsDom.child(wrap, "table-wrap-foot").map(this::readBlocks).orElse(List.of());
    return tableOf(id, label(wrap), caption(wrap), table.get(), footnotes);
  }

  /// 由 `<table>` 生成表块。
  private Block tableOf(
      String id, String label, List<Block> caption, Element table, List<Block> footnotes) {
    ctx.registerTarget(id);
    List<List<TableCell>> head = new ArrayList<>();
    List<List<TableCell>> body = new ArrayList<>();
    for (Element part : JatsDom.children(table)) {
      switch (JatsDom.name(part)) {
        case "thead" -> rows(part, head);
        case "tbody", "tfoot" -> rows(part, body);
        case "tr" -> body.add(row(part));
        default -> {
          // colgroup / col 只影响列宽，不保留
        }
      }
    }
    return Block.Table.builder()
        .id(id)
        .label(label)
        .caption(caption)
        .head(head)
        .body(body)
        .footnotes(footnotes)
        .build();
  }

  /// 收集一组行。
  private void rows(Element group, List<List<TableCell>> out) {
    for (Element tr : JatsDom.children(group)) {
      if ("tr".equals(JatsDom.name(tr))) {
        out.add(row(tr));
      }
    }
  }

  /// 一行单元格。
  private List<TableCell> row(Element tr) {
    List<TableCell> cells = new ArrayList<>();
    for (Element cell : JatsDom.children(tr)) {
      String name = JatsDom.name(cell);
      if ("td".equals(name) || "th".equals(name)) {
        cells.add(
            TableCell.of(
                inline.readCell(cell),
                span(cell, "colspan"),
                span(cell, "rowspan"),
                "th".equals(name)));
      }
    }
    return cells;
  }

  /// 合并单元格数，缺省或不合法为 1。
  private static int span(Element cell, String attr) {
    String value = JatsDom.attr(cell, attr);
    try {
      return value == null ? 1 : Math.max(1, Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  /// 列表。
  private Block list(Element list) {
    String type = JatsDom.attr(list, "list-type");
    List<List<Block>> items = new ArrayList<>();
    for (Element item : JatsDom.children(list)) {
      if ("list-item".equals(JatsDom.name(item))) {
        items.add(readBlocks(item));
      }
    }
    return new Block.ListBlock(type != null && ORDERED_LIST_TYPES.contains(type), items);
  }

  /// 定义列表：每项是术语段落加定义内容。
  private Block definitionList(Element defList) {
    List<List<Block>> items = new ArrayList<>();
    for (Element item : JatsDom.children(defList)) {
      if (!"def-item".equals(JatsDom.name(item))) {
        continue;
      }
      List<Block> blocks = new ArrayList<>();
      JatsDom.child(item, "term")
          .flatMap(
              term -> paragraphOf(List.of(new Inline.Styled("bold", inline.readChildren(term)))))
          .ifPresent(blocks::add);
      JatsDom.child(item, "def").ifPresent(def -> blocks.addAll(readBlocks(def)));
      items.add(blocks);
    }
    return new Block.ListBlock(false, items);
  }

  /// 框。
  private Block box(Element box) {
    String id = JatsDom.attr(box, "id");
    ctx.registerTarget(id);
    List<Inline> title =
        JatsDom.child(box, "caption")
            .flatMap(c -> JatsDom.child(c, "title"))
            .or(() -> JatsDom.child(box, "title"))
            .map(inline::readChildren)
            .orElse(List.of());
    return new Block.Box(id, title, readBlocks(box));
  }

  /// 图表标签。
  private static String label(Element e) {
    return JatsDom.child(e, "label").map(JatsDom::text).filter(s -> !s.isEmpty()).orElse(null);
  }

  /// 图注 / 表题：标题成一段，其余按块读。
  private List<Block> caption(Element e) {
    Optional<Element> caption = JatsDom.child(e, "caption");
    if (caption.isEmpty()) {
      return List.of();
    }
    List<Block> blocks = new ArrayList<>();
    JatsDom.child(caption.get(), "title")
        .flatMap(t -> paragraphOf(inline.readChildren(t)))
        .ifPresent(blocks::add);
    blocks.addAll(readBlocks(caption.get()));
    return blocks;
  }

  /// `<alternatives>` 里优先取表，其次图片。
  private static Optional<Element> preferredAlternative(Element alternatives) {
    List<Element> children = JatsDom.children(alternatives);
    return children.stream()
        .filter(c -> "table".equals(JatsDom.name(c)))
        .findFirst()
        .or(() -> children.stream().filter(c -> "graphic".equals(JatsDom.name(c))).findFirst());
  }

  /// 补充材料节。
  private static boolean isSupplementary(Element sec) {
    String type = JatsDom.attr(sec, "sec-type");
    return type != null && type.contains("supplementary");
  }
}
