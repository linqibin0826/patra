package com.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 交叉引用。
///
/// 表示期刊的交叉引用信息，包括缩写、别名等。
///
/// **交叉引用类型**：
///
/// | 类型 | 含义 |
/// |------|------|
/// | A | Acronym（缩写） |
/// | X | Cross Reference（交叉引用） |
/// | S | See（参见） |
///
/// @param xrType 交叉引用类型（A/X/S）
/// @param xrTitle 交叉引用标题
/// @author linqibin
/// @since 0.1.0
public record PubmedCrossReference(String xrType, String xrTitle) {

  /// 创建交叉引用。
  ///
  /// @param xrType 交叉引用类型
  /// @param xrTitle 交叉引用标题
  /// @return 交叉引用值对象
  public static PubmedCrossReference of(String xrType, String xrTitle) {
    return new PubmedCrossReference(xrType, xrTitle);
  }

  /// 创建缩写类型交叉引用。
  ///
  /// @param xrTitle 缩写标题
  /// @return 缩写交叉引用
  public static PubmedCrossReference acronym(String xrTitle) {
    return new PubmedCrossReference("A", xrTitle);
  }

  /// 创建交叉引用类型。
  ///
  /// @param xrTitle 交叉引用标题
  /// @return 交叉引用
  public static PubmedCrossReference crossRef(String xrTitle) {
    return new PubmedCrossReference("X", xrTitle);
  }

  /// 创建参见类型交叉引用。
  ///
  /// @param xrTitle 参见标题
  /// @return 参见交叉引用
  public static PubmedCrossReference see(String xrTitle) {
    return new PubmedCrossReference("S", xrTitle);
  }

  /// 判断是否为缩写类型。
  public boolean isAcronym() {
    return "A".equalsIgnoreCase(xrType);
  }

  /// 判断是否为交叉引用类型。
  public boolean isCrossRef() {
    return "X".equalsIgnoreCase(xrType);
  }

  /// 判断是否为参见类型。
  public boolean isSee() {
    return "S".equalsIgnoreCase(xrType);
  }
}
