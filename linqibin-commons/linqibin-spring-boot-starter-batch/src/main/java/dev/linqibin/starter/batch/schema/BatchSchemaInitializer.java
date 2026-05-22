package dev.linqibin.starter.batch.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/// Spring Batch Schema 初始化器。
///
/// 负责检查并初始化 Spring Batch 元数据表。使用 `IF NOT EXISTS` 语法保证幂等性，
/// 可安全地在每次应用启动时执行。
///
/// ## 初始化逻辑
///
/// 1. 检查 `BATCH_JOB_INSTANCE` 表是否存在
/// 2. 不存在则执行 `db/batch/schema-postgresql.sql` 创建所有元数据表
/// 3. 已存在则跳过（幂等）
///
/// ## 使用场景
///
/// - 新环境部署：自动创建 Schema
/// - 应用重启：检测到表存在，跳过初始化
/// - 多服务共享数据库：首个启动的服务创建表，其他服务跳过
///
/// @author Patra Team
/// @since 0.1.0
public class BatchSchemaInitializer {

  private static final Logger log = LoggerFactory.getLogger(BatchSchemaInitializer.class);

  /// 用于检测表是否存在的标志表名。
  private static final String MARKER_TABLE = "BATCH_JOB_INSTANCE";

  /// Schema SQL 资源路径。
  private static final String SCHEMA_RESOURCE = "db/batch/schema-postgresql.sql";

  private final DataSource dataSource;
  private final String tablePrefix;

  /// 标记是否已初始化，使用 AtomicBoolean 保证线程安全。
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  /// 构造函数。
  ///
  /// @param dataSource Batch 使用的数据源
  /// @param tablePrefix 表前缀（默认 `BATCH_`）
  public BatchSchemaInitializer(DataSource dataSource, String tablePrefix) {
    this.dataSource = dataSource;
    this.tablePrefix = tablePrefix;
  }

  /// 执行 Schema 初始化。
  ///
  /// 由 `BatchAutoConfiguration` 在构造函数中调用，确保在 `JobRepository` 创建前完成初始化。
  /// 使用 `compareAndSet` 保证线程安全，多次调用只执行一次。
  public void initialize() {
    // 使用 compareAndSet 保证只有一个线程能进入初始化逻辑
    if (!initialized.compareAndSet(false, true)) {
      log.debug("Spring Batch Schema 已初始化，跳过");
      return;
    }

    if (isSchemaExists()) {
      log.info("检测到 Spring Batch 元数据表已存在，跳过 Schema 初始化");
      return;
    }

    log.info("开始初始化 Spring Batch Schema...");
    executeSchema();
    log.info("Spring Batch Schema 初始化完成");
  }

  /// 检查 Schema 是否已存在。
  ///
  /// 通过检测标志表 `BATCH_JOB_INSTANCE` 判断。
  ///
  /// @return 如果表存在返回 true
  private boolean isSchemaExists() {
    String tableName = tablePrefix + "JOB_INSTANCE";

    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      // 使用 null 作为 catalog 和 schema，让 JDBC 驱动自动选择
      // PG 默认将未加引号的标识符折叠为小写，BATCH_JOB_INSTANCE 实际存储为 batch_job_instance；
      // 查询时同样折叠，行为一致。此处同时检测大写与小写以覆盖两种情形。
      try (ResultSet tables =
          metaData.getTables(null, null, tableName.toUpperCase(Locale.ROOT), new String[] {"TABLE"})) {
        if (tables.next()) {
          return true;
        }
      }
      // 也检查小写（兼容不同配置）
      try (ResultSet tables =
          metaData.getTables(null, null, tableName.toLowerCase(Locale.ROOT), new String[] {"TABLE"})) {
        return tables.next();
      }
    } catch (SQLException e) {
      log.warn("检查 Spring Batch 表是否存在时发生异常，将尝试初始化: {}", e.getMessage());
      return false;
    }
  }

  /// 执行 Schema SQL。
  ///
  /// 使用 Spring 的 `ResourceDatabasePopulator` 执行 SQL 脚本。
  private void executeSchema() {
    Resource schemaResource = new ClassPathResource(SCHEMA_RESOURCE);

    if (!schemaResource.exists()) {
      throw new IllegalStateException("Spring Batch Schema 资源文件不存在: " + SCHEMA_RESOURCE);
    }

    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(schemaResource);
    populator.setContinueOnError(false);
    populator.execute(dataSource);
  }
}
