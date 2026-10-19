package dev.patra.catalog.infra.parser.jats;

import dev.patra.catalog.domain.model.vo.fulltext.body.Block;
import dev.patra.catalog.domain.model.vo.fulltext.body.Inline;
import dev.patra.catalog.domain.model.vo.fulltext.body.Section;
import java.util.List;

/// 正文字数（工程 spec 第 6.4 节口径）。
///
/// 计：顶层块、章节与 back 里的文本，含章节标题、图注、表题、表注；
/// 不计：参考文献、公式（`formula` 块与 `math` 行内节点）、表格单元格、`code` 块。
/// 每个文本容器（段落、标题）先拼成一段字符串再按空白切分，跨样式边界的同一个词只算一次。
///
/// @author linqibin
/// @since 0.11.0
final class JatsWordCounter {

  private JatsWordCounter() {}

  /// 统计字数。
  ///
  /// @param blocks 顶层块
  /// @param sections 章节
  /// @param back 文末附加
  /// @return 字数
  static int count(List<Block> blocks, List<Section> sections, List<Section> back) {
    return blocks(blocks) + sections(sections) + sections(back);
  }

  private static int sections(List<Section> sections) {
    int total = 0;
    for (Section s : sections) {
      total += words(s.title()) + blocks(s.blocks()) + sections(s.sections());
    }
    return total;
  }

  private static int blocks(List<Block> blocks) {
    int total = 0;
    for (Block block : blocks) {
      total +=
          switch (block) {
            case Block.Paragraph p -> words(p.content());
            case Block.Figure f -> blocks(f.caption());
            case Block.Table t -> blocks(t.caption()) + blocks(t.footnotes());
            case Block.ListBlock l -> l.items().stream().mapToInt(JatsWordCounter::blocks).sum();
            case Block.Quote q -> blocks(q.blocks());
            case Block.Box b -> words(b.title()) + blocks(b.blocks());
            case Block.Formula f -> 0;
            case Block.Code c -> 0;
          };
    }
    return total;
  }

  private static int words(List<Inline> inlines) {
    StringBuilder sb = new StringBuilder();
    append(inlines, sb);
    String text = sb.toString().strip();
    return text.isEmpty() ? 0 : text.split("\\s+").length;
  }

  private static void append(List<Inline> inlines, StringBuilder sb) {
    for (Inline inline : inlines) {
      switch (inline) {
        case Inline.Text t -> sb.append(t.text());
        case Inline.Styled s -> append(s.children(), sb);
        case Inline.Xref x -> append(x.children(), sb);
        case Inline.Link l -> append(l.children(), sb);
        case Inline.MathInline m -> sb.append(' ');
        case Inline.Break b -> sb.append(' ');
      }
    }
  }
}
