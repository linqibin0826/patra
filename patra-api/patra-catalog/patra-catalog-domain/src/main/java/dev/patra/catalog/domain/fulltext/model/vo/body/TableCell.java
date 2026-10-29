package dev.patra.catalog.domain.fulltext.model.vo.body;

import java.util.List;

/// 表格单元格。
///
/// @param content 内容
/// @param colspan 跨列数
/// @param rowspan 跨行数
/// @param header 是否表头单元格（`<th>`）
/// @author linqibin
/// @since 0.11.0
public record TableCell(List<Inline> content, int colspan, int rowspan, boolean header) {

  /// 防御性拷贝，`null` 视为空。
  public TableCell {
    content = content != null ? List.copyOf(content) : List.of();
  }

  /// 创建单元格。
  ///
  /// @param content 内容
  /// @param colspan 跨列数
  /// @param rowspan 跨行数
  /// @param header 是否表头
  /// @return 单元格
  public static TableCell of(List<Inline> content, int colspan, int rowspan, boolean header) {
    return new TableCell(content, colspan, rowspan, header);
  }
}
