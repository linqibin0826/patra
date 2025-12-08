package com.patra.catalog.infra.adapter.parser.dto.serfile;

/// Serfile 当前索引子集解析结果。
///
/// 从 Serfile XML 的 `CurrentlyIndexedForSubset` 元素解析出的数据传输对象。
/// 表示期刊当前正在索引的子集信息。
///
/// **XML 结构示例**：
///
/// ```xml
/// <CurrentlyIndexedForSubset CurrentSubset="IM" CurrentIndexingTreatment="Full">
///   Current indexing info
/// </CurrentlyIndexedForSubset>
/// ```
///
/// **子集代码说明**：
///
/// | CurrentSubset | 含义 |
/// |---------------|------|
/// | IM | Index Medicus |
/// | AIM | Abridged Index Medicus |
/// | D | Dental Literature |
/// | N | Nursing Literature |
///
/// **索引处理方式**：
///
/// | CurrentIndexingTreatment | 含义 |
/// |--------------------------|------|
/// | Full | 全文索引 |
/// | Selective | 选择性索引 |
/// | Unknown | 未知 |
///
/// @param content 元素文本内容
/// @param currentSubset 当前子集代码
/// @param currentIndexingTreatment 当前索引处理方式
/// @author linqibin
/// @since 0.1.0
public record SerialCurrentlyIndexedForSubset(
    String content, String currentSubset, String currentIndexingTreatment) {

  /// 创建当前索引子集记录。
  ///
  /// @param content 元素文本内容
  /// @param currentSubset 当前子集代码
  /// @param currentIndexingTreatment 当前索引处理方式
  /// @return 当前索引子集解析结果
  public static SerialCurrentlyIndexedForSubset of(
      String content, String currentSubset, String currentIndexingTreatment) {
    return new SerialCurrentlyIndexedForSubset(content, currentSubset, currentIndexingTreatment);
  }

  /// 判断是否为 Index Medicus 子集。
  ///
  /// @return true 如果是 IM 子集
  public boolean isIndexMedicus() {
    return "IM".equalsIgnoreCase(currentSubset);
  }

  /// 判断是否为全文索引。
  ///
  /// @return true 如果是全文索引
  public boolean isFullIndexing() {
    return "Full".equalsIgnoreCase(currentIndexingTreatment);
  }

  /// 判断是否为选择性索引。
  ///
  /// @return true 如果是选择性索引
  public boolean isSelectiveIndexing() {
    return "Selective".equalsIgnoreCase(currentIndexingTreatment);
  }
}
