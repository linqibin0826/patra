package dev.patra.catalog.infra.parser.jats;

import dev.patra.catalog.domain.model.vo.fulltext.body.Block;
import dev.patra.catalog.domain.model.vo.fulltext.body.Inline;
import org.w3c.dom.Element;

/// JATS 公式 → `Formula` 块 / `MathInline` 行内节点。
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

  /// 行间公式 `<disp-formula>`。
  ///
  /// @param e 元素
  /// @return 公式块
  Block display(Element e) {
    String id = JatsDom.attr(e, "id");
    ctx.registerTarget(id);
    String label = JatsDom.child(e, "label").map(JatsDom::text).orElse(null);
    return Block.Formula.builder().id(id).label(label).build();
  }

  /// 任意位置的 MathML `math` 元素。
  ///
  /// @param e 元素
  /// @return 行内公式
  Inline inline(Element e) {
    return new Inline.MathInline(null);
  }

  /// 行内公式 `<inline-formula>`。
  ///
  /// @param e 元素
  /// @param inlineReader 行内读取器
  /// @return 行内节点
  Inline inlineFormula(Element e, JatsInlineReader inlineReader) {
    return new Inline.MathInline(null);
  }
}
