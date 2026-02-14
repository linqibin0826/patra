package com.patra.catalog.infra.parser.support;

/// XML 解析上下文（扩展点）。
///
/// 当前为空实现，保留接口设计以便未来扩展。
/// 使用单例模式避免不必要的对象创建。
///
/// **设计说明**：
///
/// - 版本信息（如 meshVersion）由 Application 层在解析后通过
///   `withMeshVersion()` 方法设置，不再通过 Context 传递
/// - 保留 Context 接口是为了支持未来可能的扩展需求
///
/// @author linqibin
/// @since 0.1.0
public record XmlParsingContext() {

  private static final XmlParsingContext EMPTY = new XmlParsingContext();

  /// 获取空的解析上下文单例。
  ///
  /// @return 解析上下文单例
  public static XmlParsingContext empty() {
    return EMPTY;
  }
}
