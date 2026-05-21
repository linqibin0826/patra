package dev.linqibin.starter.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/// Spring Batch 批处理配置属性。
///
/// 使用 `linqibin.starter.batch` 前缀配置批处理行为。
///
/// ## 数据源配置
///
/// 支持独立数据源配置，将 Spring Batch 元数据表存储到共享数据库：
///
/// ```yaml
/// linqibin:
///   starter:
///     batch:
///       datasource:
///         url: jdbc:postgresql://shared-db:5432/batch_meta
///         username: batch_user
///         password: batch_password
/// ```
///
/// 未配置 `datasource.url` 时，使用应用默认数据源（向后兼容）。
///
/// @author Patra Team
/// @since 0.1.0
@ConfigurationProperties(prefix = "linqibin.starter.batch")
@Data
public class BatchProperties {

  /// 是否启用批处理自动配置。
  private boolean enabled = true;

  /// 表前缀。
  private String tablePrefix = "BATCH_";

  /// Chunk 配置。
  private ChunkProperties chunk = new ChunkProperties();

  /// 独立数据源配置（可选）。
  ///
  /// 配置此项后，Spring Batch 元数据将存储到独立数据库，与业务数据隔离。
  private DataSourceProperties datasource = new DataSourceProperties();

  /// Schema 初始化配置。
  private SchemaProperties schema = new SchemaProperties();

  /// 导入限制配置。
  private ImportLimitProperties importLimit = new ImportLimitProperties();

  /// Chunk 批次处理配置。
  @Data
  public static class ChunkProperties {

    /// 默认批次大小。
    private int defaultSize = 5000;

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

    /// 连接最大生存时间（毫秒，默认 30 分钟）。
    ///
    /// 建议设置为小于数据库 `wait_timeout` 的值，避免连接被数据库服务器关闭。
    private long maxLifetime = 1800000;
  }

  /// Schema 初始化配置。
  ///
  /// 控制 Spring Batch 元数据表的自动创建行为。
  ///
  /// ## 配置示例
  ///
  /// ```yaml
  /// patra:
  ///   batch:
  ///     schema:
  ///       initialize: false  # 禁用自动初始化
  /// ```
  @Data
  public static class SchemaProperties {

    /// 是否自动初始化 Spring Batch Schema。
    ///
    /// 默认启用。设置为 `false` 可禁用自动初始化，适用于：
    ///
    /// - 使用 Flyway/Liquibase 管理 Schema 迁移
    /// - 多服务共享数据库，仅由指定服务初始化
    /// - 使用预配置的数据库（Schema 已存在）
    private boolean initialize = true;
  }

  /// 导入记录数限制配置。
  ///
  /// 用于在开发环境中限制批量导入的数据量，避免每次都导入全部数据。
  ///
  /// ## 配置示例
  ///
  /// 开发环境（仅导入 50 万条记录）：
  ///
  /// ```yaml
  /// patra:
  ///   batch:
  ///     import-limit:
  ///       max-records: 500000
  /// ```
  ///
  /// 生产环境（导入全部数据，可省略此配置）：
  ///
  /// ```yaml
  /// patra:
  ///   batch:
  ///     import-limit:
  ///       max-records: -1
  /// ```
  @Data
  public static class ImportLimitProperties {

    /// 最大导入记录数限制。
    ///
    /// - 默认值 `-1` 表示不限制（生产环境导入全部数据）
    /// - 设置为正整数（如 `500000`）时，Reader 读取到该数量后自动终止
    ///
    /// 注意：此配置仅影响 Reader 读取行为，不影响 Writer 写入逻辑。
    private long maxRecords = -1;

    /// 检查是否设置了记录数限制。
    ///
    /// @return 如果 `maxRecords > 0` 返回 true
    public boolean hasLimit() {
      return maxRecords > 0;
    }
  }
}
