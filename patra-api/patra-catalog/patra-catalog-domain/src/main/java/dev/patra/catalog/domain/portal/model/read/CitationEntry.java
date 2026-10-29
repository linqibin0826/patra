package dev.patra.catalog.domain.portal.model.read;

/// 一条格式化好的引用。
///
/// @param label 编号样式的序号原样（`[1]`、`1.`）；APA 等不编号的样式为 null
/// @param text 不含序号的纯文本条目
/// @author linqibin
/// @since 0.1.0
public record CitationEntry(String label, String text) {

  /// 创建引用条目。
  ///
  /// @param label 序号，可空
  /// @param text 正文
  /// @return 引用条目
  public static CitationEntry of(String label, String text) {
    return new CitationEntry(label, text);
  }
}
