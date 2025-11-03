package com.patra.ingest.domain.messaging;

import java.util.Locale;

/**
 * 消费者组名称生成工具。
 *
 * <p>使用模式 {@code svc-{service}-{consumer}-cg} 生成规范化的消费者组名称。
 *
 * <p>示例:
 *
 * <pre>{@code
 * ConsumerGroups.svc("ingest", "task-ready")  → "svc-ingest-task-ready-cg"
 * ConsumerGroups.svc("REGISTRY", "Provenance") → "svc-registry-provenance-cg"
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ConsumerGroups {
  private ConsumerGroups() {}

  /**
   * 构建小写 kebab-case 风格的消费者组名称。
   *
   * <p>命名规范:
   *
   * <ul>
   *   <li>前缀: {@code svc-}
   *   <li>微服务名: 小写,下划线转换为短横线
   *   <li>责任标识: 小写,下划线转换为短横线
   *   <li>后缀: {@code -cg}
   * </ul>
   *
   * @param service 微服务名称(如 ingest、registry)
   * @param consumer 责任标识(如 relay、task-ready)
   * @return 规范化的消费者组名称
   */
  public static String svc(String service, String consumer) {
    String s = normalize(service);
    String c = normalize(consumer);
    return "svc-" + s + '-' + c + "-cg";
  }

  /**
   * 规范化字符串为小写 kebab-case。
   *
   * @param s 原始字符串
   * @return 规范化后的字符串,null 转换为 "unknown"
   */
  private static String normalize(String s) {
    if (s == null) return "unknown";
    return s.trim().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
