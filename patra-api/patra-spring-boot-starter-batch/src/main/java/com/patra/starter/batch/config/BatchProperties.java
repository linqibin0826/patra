package com.patra.starter.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/// Spring Batch 批处理配置属性。
///
/// 使用 `patra.batch` 前缀配置批处理行为。
///
/// ## 数据源配置
///
/// 支持独立数据源配置，将 Spring Batch 元数据表存储到共享数据库：
///
/// ```yaml
/// patra:
///   batch:
///     datasource:
///       url: jdbc:mysql://shared-db:3306/batch_meta
///       username: batch_user
///       password: batch_password
/// ```
///
/// 未配置 `datasource.url` 时，使用应用默认数据源（向后兼容）。
///
/// @author Patra Team
/// @since 1.0.0
@ConfigurationProperties(prefix = "patra.batch")
@Data
public class BatchProperties {

  /// 是否启用批处理自动配置。
  private boolean enabled = true;

  /// 表前缀。
  private String tablePrefix = "BATCH_";

  /// 可观测性配置。
  private ObservabilityProperties observability = new ObservabilityProperties();

  /// Chunk 配置。
  private ChunkProperties chunk = new ChunkProperties();

  /// 独立数据源配置（可选）。
  ///
  /// 配置此项后，Spring Batch 元数据将存储到独立数据库，与业务数据隔离。
  private DataSourceProperties datasource = new DataSourceProperties();

  /// 可观测性配置。
  @Data
  public static class ObservabilityProperties {

    /// 追踪配置。
    private TracingProperties tracing = new TracingProperties();

    /// 指标配置。
    private MetricsProperties metrics = new MetricsProperties();

    /// 日志配置。
    private LoggingProperties logging = new LoggingProperties();

    /// SkyWalking 追踪配置。
    @Data
    public static class TracingProperties {
      /// 是否启用追踪。
      private boolean enabled = true;
    }

    /// Micrometer 指标配置。
    @Data
    public static class MetricsProperties {
      /// 是否启用指标收集。
      private boolean enabled = true;
    }

    /// 日志配置。
    @Data
    public static class LoggingProperties {
      /// 是否启用日志记录。
      private boolean enabled = true;

      /// 日志级别。
      private String level = "INFO";
    }
  }

  /// Chunk 批次处理配置。
  @Data
  public static class ChunkProperties {

    /// 默认批次大小。
    private int defaultSize = 1000;

    /// 最大批次大小。
    private int maxSize = 10000;
  }

  /// Batch 元数据独立数据源配置。
  ///
  /// 配置 `url` 属性后启用独立数据源，Spring Batch 元数据将存储到指定数据库。
  @Data
  public static class DataSourceProperties {

    /// JDBC URL（配置此项启用独立数据源）。
    private String url;

    /// 数据库用户名。
    private String username;

    /// 数据库密码。
    private String password;

    /// JDBC 驱动类名（可选，Spring Boot 可自动推断）。
    private String driverClassName;

    /// HikariCP 连接池配置。
    private HikariProperties hikari = new HikariProperties();

    /// 检查是否配置了独立数据源。
    ///
    /// @return 如果配置了有效的 JDBC URL 返回 true
    public boolean isConfigured() {
      return StringUtils.hasText(url);
    }
  }

  /// HikariCP 连接池配置。
  ///
  /// 为 Batch 独立数据源提供连接池配置，默认值适合元数据存储场景。
  @Data
  public static class HikariProperties {

    /// 最大连接池大小（默认 5，元数据操作不需要太多连接）。
    private int maximumPoolSize = 5;

    /// 最小空闲连接数（默认 2）。
    private int minimumIdle = 2;

    /// 连接超时时间（毫秒，默认 30 秒）。
    private long connectionTimeout = 30000;

    /// 空闲连接超时时间（毫秒，默认 10 分钟）。
    private long idleTimeout = 600000;
  }
}
