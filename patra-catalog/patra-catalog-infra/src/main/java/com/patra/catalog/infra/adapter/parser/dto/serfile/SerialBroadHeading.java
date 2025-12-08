package com.patra.catalog.infra.adapter.parser.dto.serfile;

/// Serfile 广泛期刊分类解析结果。
///
/// 从 Serfile XML 的 `BroadJournalHeading` 元素解析出的数据传输对象。
/// 表示期刊所属的广泛学科类别。
///
/// **XML 结构示例**：
///
/// ```xml
/// <BroadJournalHeadingList>
///   <BroadJournalHeading>Science</BroadJournalHeading>
///   <BroadJournalHeading>Medicine</BroadJournalHeading>
/// </BroadJournalHeadingList>
/// ```
///
/// @param heading 分类名称（如 Science, Medicine, Chemistry）
/// @author linqibin
/// @since 0.1.0
public record SerialBroadHeading(String heading) {

  /// 创建广泛分类记录。
  ///
  /// @param heading 分类名称
  /// @return 广泛分类解析结果
  public static SerialBroadHeading of(String heading) {
    return new SerialBroadHeading(heading);
  }
}
