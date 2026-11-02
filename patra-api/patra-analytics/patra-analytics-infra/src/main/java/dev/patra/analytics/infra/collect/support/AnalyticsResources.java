package dev.patra.analytics.infra.collect.support;

/// `patra-analytics-api` 里资源的位置（JSON Schema、COUNTER-Robots 列表，见埋点方案 §9）。
public final class AnalyticsResources {

  /// schema 的 `$id` 前缀
  public static final String SCHEMA_BASE_ID = "https://patra.linqibin.dev/schemas/analytics/";

  /// schema 目录在 classpath 上的位置，`$id` 前缀映射到这里，校验时不联网
  public static final String SCHEMA_CLASSPATH = "classpath:analytics/schema/";

  /// COUNTER-Robots 列表的 classpath 路径
  public static final String ROBOTS = "analytics/robots/counter-robots.json";

  private AnalyticsResources() {}
}
