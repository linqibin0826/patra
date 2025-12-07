package com.patra.catalog.domain.model.dto.serfile;

/// Serfile 交叉引用解析结果。
///
/// 从 Serfile XML 的 `CrossReference` 元素解析出的数据传输对象。
/// 表示期刊的交叉引用信息（缩写、别名等）。
///
/// **XML 结构示例**：
///
/// ```xml
/// <CrossReferenceList>
///   <CrossReference XrType="A">
///     <XrTitle>J Full Test Med</XrTitle>
///   </CrossReference>
///   <CrossReference XrType="X">
///     <XrTitle>Journal of Full Test Medicine</XrTitle>
///   </CrossReference>
/// </CrossReferenceList>
/// ```
///
/// **交叉引用类型说明**：
///
/// | XrType | 含义 |
/// |--------|------|
/// | A | Acronym（缩写） |
/// | X | Cross Reference（交叉引用） |
/// | S | See（参见） |
///
/// @param xrType 交叉引用类型（A/X/S）
/// @param xrTitle 交叉引用标题
/// @author linqibin
/// @since 0.1.0
public record SerialCrossReference(String xrType, String xrTitle) {

  /// 创建交叉引用记录。
  ///
  /// @param xrType 交叉引用类型
  /// @param xrTitle 交叉引用标题
  /// @return 交叉引用解析结果
  public static SerialCrossReference of(String xrType, String xrTitle) {
    return new SerialCrossReference(xrType, xrTitle);
  }

  /// 判断是否为缩写类型。
  ///
  /// @return true 如果是缩写
  public boolean isAcronym() {
    return "A".equalsIgnoreCase(xrType);
  }

  /// 判断是否为交叉引用类型。
  ///
  /// @return true 如果是交叉引用
  public boolean isCrossRef() {
    return "X".equalsIgnoreCase(xrType);
  }

  /// 判断是否为参见类型。
  ///
  /// @return true 如果是参见
  public boolean isSee() {
    return "S".equalsIgnoreCase(xrType);
  }
}
