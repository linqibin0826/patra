package dev.patra.tool.batch.autoconfigure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/// Spring Batch 基础设施配置，前缀 `patra.batch`。
///
/// @param enabled 总开关
/// @param tablePrefix JobRepository 表前缀
/// @param chunk 分块大小
/// @param datasource 独立的 Batch 元数据库；不配 `url` 则用主数据源
/// @param schema 启动时建表
/// @param importLimit 导入条数上限（调试用）
/// @param metrics 进度指标
@Validated
@ConfigurationProperties(prefix = "patra.batch")
public record BatchProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("BATCH_") String tablePrefix,
    @DefaultValue Chunk chunk,
    @DefaultValue Datasource datasource,
    @DefaultValue Schema schema,
    @DefaultValue ImportLimit importLimit,
    @DefaultValue Metrics metrics) {

  /// 分块大小。
  ///
  /// @param defaultSize 默认分块
  /// @param maxSize 允许的最大分块
  public record Chunk(
      @DefaultValue("5000") @Min(1) int defaultSize, @DefaultValue("10000") @Min(1) int maxSize) {}

  /// 独立数据源。
  ///
  /// @param url JDBC URL，空则不建独立数据源
  /// @param username 用户名
  /// @param password 密码
  /// @param driverClassName 驱动类，空则由 URL 推断
  /// @param hikari 连接池参数
  public record Datasource(
      String url,
      String username,
      String password,
      String driverClassName,
      @DefaultValue Hikari hikari) {

    /// 是否配置了独立数据源。
    ///
    /// @return `url` 非空即视为已配置
    public boolean configured() {
      return StringUtils.hasText(url);
    }

    /// HikariCP 连接池参数（毫秒）。
    ///
    /// @param maximumPoolSize 最大连接数
    /// @param minimumIdle 最小空闲连接数
    /// @param connectionTimeout 取连接超时
    /// @param idleTimeout 空闲连接回收时间
    /// @param maxLifetime 连接最长寿命
    public record Hikari(
        @DefaultValue("5") @Min(1) int maximumPoolSize,
        @DefaultValue("2") @Min(0) int minimumIdle,
        @DefaultValue("30000") long connectionTimeout,
        @DefaultValue("600000") long idleTimeout,
        @DefaultValue("1800000") long maxLifetime) {}
  }

  /// 建表开关。
  ///
  /// @param initialize 启动时是否建 Batch 元数据表
  public record Schema(@DefaultValue("true") boolean initialize) {}

  /// 导入上限，`maxRecords <= 0` 表示不限。
  ///
  /// @param maxRecords 最多导入条数
  public record ImportLimit(@DefaultValue("-1") long maxRecords) {

    /// 是否设了上限。
    ///
    /// @return `maxRecords > 0` 即有上限
    public boolean hasLimit() {
      return maxRecords > 0;
    }
  }

  /// 进度指标开关。
  ///
  /// @param enabled 是否注册进度指标监听器
  public record Metrics(@DefaultValue("true") boolean enabled) {}
}
