package com.patra.catalog.infra.adapter.parser.support;

/// XML 解析上下文。
///
/// 封装解析过程中传递的上下文信息，如 MeSH 版本号等。
/// 使用 record 确保不可变性和值语义。
///
/// **使用示例**：
/// ```java
/// // 解析特定版本
/// var context = XmlParsingContext.of("2025");
///
/// // 无版本号场景
/// var context = XmlParsingContext.empty();
/// ```
///
/// @param meshVersion MeSH 版本号（如 "2025"），可为 null
/// @author linqibin
/// @since 0.1.0
public record XmlParsingContext(String meshVersion) {

  /// 创建包含版本号的解析上下文。
  ///
  /// @param meshVersion MeSH 版本号
  /// @return 解析上下文实例
  public static XmlParsingContext of(String meshVersion) {
    return new XmlParsingContext(meshVersion);
  }

  /// 创建空的解析上下文（无版本号）。
  ///
  /// @return 版本号为 null 的解析上下文
  public static XmlParsingContext empty() {
    return new XmlParsingContext(null);
  }
}
