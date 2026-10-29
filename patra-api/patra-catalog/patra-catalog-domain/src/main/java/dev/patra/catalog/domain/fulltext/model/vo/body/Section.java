package dev.patra.catalog.domain.fulltext.model.vo.body;

import java.util.List;

/// 正文章节。
///
/// @param id 章节 id（JATS 的 `id`，没有时生成），供目录锚点
/// @param title 标题，无标题章节为空列表
/// @param sections 子章节
/// @param blocks 本节的块
/// @author linqibin
/// @since 0.11.0
public record Section(String id, List<Inline> title, List<Section> sections, List<Block> blocks) {

  /// 防御性拷贝，`null` 视为空。
  public Section {
    title = title != null ? List.copyOf(title) : List.of();
    sections = sections != null ? List.copyOf(sections) : List.of();
    blocks = blocks != null ? List.copyOf(blocks) : List.of();
  }

  /// 创建章节。
  ///
  /// @param id 章节 id
  /// @param title 标题
  /// @param sections 子章节
  /// @param blocks 块
  /// @return 章节
  public static Section of(
      String id, List<Inline> title, List<Section> sections, List<Block> blocks) {
    return new Section(id, title, sections, blocks);
  }
}
