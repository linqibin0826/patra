package dev.patra.catalog.domain.fulltext.model.vo.body;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import lombok.Builder;

/// 块级节点，JSON 里以 `kind` 区分（工程 spec 第 6.2 节表格）。
///
/// 类型名只写在 `@JsonSubTypes.Type(name = …)` 里，record 本身没有 `kind` 字段。
///
/// @author linqibin
/// @since 0.11.0
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Block.Paragraph.class, name = "paragraph"),
  @JsonSubTypes.Type(value = Block.Figure.class, name = "figure"),
  @JsonSubTypes.Type(value = Block.Table.class, name = "table"),
  @JsonSubTypes.Type(value = Block.Formula.class, name = "formula"),
  @JsonSubTypes.Type(value = Block.ListBlock.class, name = "list"),
  @JsonSubTypes.Type(value = Block.Quote.class, name = "quote"),
  @JsonSubTypes.Type(value = Block.Box.class, name = "box"),
  @JsonSubTypes.Type(value = Block.Code.class, name = "code")
})
public sealed interface Block {

  /// 段落。
  ///
  /// @param content 行内内容
  record Paragraph(List<Inline> content) implements Block {

    /// 防御性拷贝，`null` 视为空。
    public Paragraph {
      content = content != null ? List.copyOf(content) : List.of();
    }
  }

  /// 图。
  ///
  /// @param id 图 id
  /// @param label 标签（如 "Figure 1"，照原文）
  /// @param caption 图注
  /// @param image 对象存储里的文件名，匹配不上时为 `null`
  /// @param width 像素宽，未知为 `null`
  /// @param height 像素高，未知为 `null`
  @Builder
  record Figure(
      String id, String label, List<Block> caption, String image, Integer width, Integer height)
      implements Block {

    /// 防御性拷贝，`null` 视为空。
    public Figure {
      caption = caption != null ? List.copyOf(caption) : List.of();
    }
  }

  /// 表。
  ///
  /// @param id 表 id
  /// @param label 标签
  /// @param caption 表题
  /// @param head 表头行
  /// @param body 表体行
  /// @param footnotes 表注
  @Builder
  record Table(
      String id,
      String label,
      List<Block> caption,
      List<List<TableCell>> head,
      List<List<TableCell>> body,
      List<Block> footnotes)
      implements Block {

    /// 防御性拷贝，`null` 视为空。
    public Table {
      caption = caption != null ? List.copyOf(caption) : List.of();
      head = head != null ? head.stream().map(List::copyOf).toList() : List.of();
      body = body != null ? body.stream().map(List::copyOf).toList() : List.of();
      footnotes = footnotes != null ? List.copyOf(footnotes) : List.of();
    }
  }

  /// 行间公式。`mathml` 与 `image` 至多一个非空；`image` 只用于只有公式图片的情况。
  ///
  /// @param id 公式 id
  /// @param label 编号（如 "(1)"）
  /// @param mathml MathML 字符串
  /// @param image 公式图片文件名
  /// @param width 图片像素宽
  /// @param height 图片像素高
  @Builder
  record Formula(
      String id, String label, String mathml, String image, Integer width, Integer height)
      implements Block {}

  /// 列表。
  ///
  /// @param ordered 是否有序
  /// @param items 每项的块
  record ListBlock(boolean ordered, List<List<Block>> items) implements Block {

    /// 防御性拷贝，`null` 视为空。
    public ListBlock {
      items = items != null ? items.stream().map(List::copyOf).toList() : List.of();
    }
  }

  /// 引文块。
  ///
  /// @param blocks 内容
  record Quote(List<Block> blocks) implements Block {

    /// 防御性拷贝，`null` 视为空。
    public Quote {
      blocks = blocks != null ? List.copyOf(blocks) : List.of();
    }
  }

  /// 框（`<boxed-text>`）。
  ///
  /// @param id 框 id
  /// @param title 标题
  /// @param blocks 内容
  record Box(String id, List<Inline> title, List<Block> blocks) implements Block {

    /// 防御性拷贝，`null` 视为空。
    public Box {
      title = title != null ? List.copyOf(title) : List.of();
      blocks = blocks != null ? List.copyOf(blocks) : List.of();
    }
  }

  /// 代码 / 预格式文本，也用于只有 TeX 的公式。
  ///
  /// @param text 文本
  record Code(String text) implements Block {}
}
