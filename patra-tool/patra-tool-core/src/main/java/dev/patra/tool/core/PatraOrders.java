package dev.patra.tool.core;

/// 工具模块里过滤器、advice、拦截器、贡献者的顺序常量，全仓只在这里写数字。
///
/// core 是纯 Java，用 `Integer.MIN_VALUE` / `MAX_VALUE` 表示 Spring `Ordered` 的最高 / 最低优先级（值相同）。
public final class PatraOrders {

  /// 最高优先级，等于 `Ordered.HIGHEST_PRECEDENCE`。
  public static final int HIGHEST = Integer.MIN_VALUE;

  /// 最低优先级，等于 `Ordered.LOWEST_PRECEDENCE`。
  public static final int LOWEST = Integer.MAX_VALUE;

  /// security 的 advice：把 Spring Security 异常原样抛回过滤器链，必须排在全局处理器之前。
  public static final int ERROR_ADVICE_SECURITY_RETHROW = HIGHEST;

  /// web 的全局异常处理器。
  public static final int ERROR_ADVICE_GLOBAL = HIGHEST + 1;

  /// jpa 的错误映射贡献者，先于服务自己的贡献者。
  public static final int ERROR_MAPPING_JPA = HIGHEST;

  /// 命令拦截器：追踪最外层。
  public static final int CQRS_INTERCEPTOR_TRACING = 50;

  /// 命令拦截器：日志。
  public static final int CQRS_INTERCEPTOR_LOGGING = 100;

  /// 命令拦截器：指标最内层。
  public static final int CQRS_INTERCEPTOR_METRICS = 200;

  /// 高基数标签过滤器最先执行。
  public static final int METER_FILTER_HIGH_CARDINALITY = HIGHEST;

  /// 公共标签过滤器最后执行。
  public static final int METER_FILTER_COMMON_TAGS = LOWEST;

  /// http-client 的请求日志拦截器。
  public static final int HTTP_CLIENT_LOGGING_INTERCEPTOR = 100;

  /// web 的 Long→String 消息转换器定制，排在 Boot 的 0 之后。
  public static final int WEB_JSON_LONG_TO_STRING = 100;

  /// 出默认配置的 EnvironmentPostProcessor，最后运行。
  public static final int DEFAULTS_POST_PROCESSOR = LOWEST;

  private PatraOrders() {}
}
