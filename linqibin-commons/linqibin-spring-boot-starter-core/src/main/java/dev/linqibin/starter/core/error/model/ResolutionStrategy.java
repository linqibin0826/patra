package dev.linqibin.starter.core.error.model;

/// 错误解析策略枚举。
///
/// 表示错误解析引擎使用的不同解析策略，用于可观测性和调试。
///
/// **策略执行顺序**（优先级从高到低）：
///
/// 1. {@link #CACHE} - 从缓存直接获取（性能最优）
/// 2. {@link #APPLICATION_EXCEPTION} - ApplicationException 自带错误码
/// 3. {@link #CONTRIBUTOR} - 通过 ErrorMappingContributor SPI 解析
/// 4. {@link #TRAIT} - 通过 HasErrorTraits 接口解析
/// 5. {@link #NAMING} - 通过异常类名启发式解析
/// 6. {@link #CAUSE} - 递归解析原因链
/// 7. {@link #FALLBACK} - 回退到默认服务器错误
///
/// @author linqibin
/// @since 0.1.0
public enum ResolutionStrategy {
  /// 从缓存中获取已解析的策略（95%+ 性能提升）。
  CACHE("cache"),

  /// ApplicationException 自带错误码，直接获取。
  APPLICATION_EXCEPTION("application-exception"),

  /// 通过 ErrorMappingContributor SPI 解析（按优先级执行）。
  CONTRIBUTOR("contributor"),

  /// 通过 HasErrorTraits 接口解析错误特征。
  TRAIT("trait"),

  /// 通过异常类名后缀启发式解析（如 NotFoundException → 404）。
  NAMING("naming"),

  /// 递归解析异常原因链（智能跳过包装异常）。
  CAUSE("cause"),

  /// 回退到默认的 500 服务器错误。
  FALLBACK("fallback");

  private final String value;

  ResolutionStrategy(String value) {
    this.value = value;
  }

  /// 获取策略的字符串值（用于日志和指标）。
  ///
  /// @return 策略名称
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
