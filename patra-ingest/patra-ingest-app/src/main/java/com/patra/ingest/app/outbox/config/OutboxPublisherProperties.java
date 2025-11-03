package com.patra.ingest.app.outbox.config;

import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Outbox 发布器框架的配置属性。
 *
 * <p>支持通过 Nacos 或 application.yml 动态配置:
 *
 * <pre>
 * papertrace:
 *   outbox:
 *     publisher:
 *       batch-size: 500
 *       max-batch-size: 500
 *       allowed-aggregate-types:
 *         - Task
 *       metrics:
 *         enabled: true
 * </pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "papertrace.outbox.publisher") // 确保此前缀与配置源匹配;如变更需更新文档
public class OutboxPublisherProperties {

  /**
   * 批量插入操作的默认批次大小(默认值: 500)。
   *
   * <p>用于分区大批次以避免超过数据库限制。
   */
  private int batchSize = 500;

  /**
   * IN 子句查询的最大允许批次大小(默认值: 500)。
   *
   * <p>防止大型 IN 查询导致的性能下降。
   */
  private int maxBatchSize = 500;

  /**
   * Micrometer 指标标签基数控制允许的聚合类型。
   *
   * <p>防止 Prometheus 中的指标标签基数爆炸。
   *
   * @see OutboxAggregateTypes
   */
  private Set<String> allowedAggregateTypes =
      new HashSet<>(Set.of(OutboxAggregateTypes.TASK.getCode()));

  /** 指标配置。 */
  private Metrics metrics = new Metrics();

  /** 指标的嵌套配置。 */
  @Data
  public static class Metrics {
    /**
     * 是否启用 Micrometer 指标记录(默认值: true)。
     *
     * <p>设置为 false 可禁用所有 Outbox 指标记录以优化性能。
     */
    private boolean enabled = true;
  }
}
