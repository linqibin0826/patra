package com.patra.starter.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Batch 批处理配置属性
 *
 * <p>使用 {@code patra.batch} 前缀配置批处理行为
 *
 * @author Patra Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "patra.batch")
@Data
public class BatchProperties {

  /** 是否启用批处理自动配置 */
  private boolean enabled = true;

  /** 表前缀 */
  private String tablePrefix = "BATCH_";

  /** 可观测性配置 */
  private ObservabilityProperties observability = new ObservabilityProperties();

  /** Chunk 配置 */
  private ChunkProperties chunk = new ChunkProperties();

  /** 可观测性配置 */
  @Data
  public static class ObservabilityProperties {

    /** 追踪配置 */
    private TracingProperties tracing = new TracingProperties();

    /** 指标配置 */
    private MetricsProperties metrics = new MetricsProperties();

    /** 日志配置 */
    private LoggingProperties logging = new LoggingProperties();

    /** SkyWalking 追踪配置 */
    @Data
    public static class TracingProperties {
      /** 是否启用追踪 */
      private boolean enabled = true;
    }

    /** Micrometer 指标配置 */
    @Data
    public static class MetricsProperties {
      /** 是否启用指标收集 */
      private boolean enabled = true;
    }

    /** 日志配置 */
    @Data
    public static class LoggingProperties {
      /** 是否启用日志记录 */
      private boolean enabled = true;

      /** 日志级别 */
      private String level = "INFO";
    }
  }

  /** Chunk 批次处理配置 */
  @Data
  public static class ChunkProperties {

    /** 默认批次大小 */
    private int defaultSize = 1000;

    /** 最大批次大小 */
    private int maxSize = 10000;
  }
}
