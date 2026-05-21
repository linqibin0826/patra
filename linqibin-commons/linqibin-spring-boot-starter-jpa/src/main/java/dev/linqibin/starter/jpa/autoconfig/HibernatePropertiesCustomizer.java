package dev.linqibin.starter.jpa.autoconfig;

import dev.linqibin.starter.jpa.json.Jackson3JsonFormatMapper;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.context.annotation.Configuration;

/// Hibernate 属性定制器，配置批量写入、性能优化和 JSON 序列化。
///
/// **Hibernate 7.1 批量写入配置**：
///
/// - `hibernate.jdbc.batch_size = 500` - 批量写入大小，与 Spring Batch chunk size 对齐
/// - `hibernate.order_inserts = true` - 按实体类型排序 INSERT，减少 SQL 切换
/// - `hibernate.order_updates = true` - 按实体类型排序 UPDATE
///
/// **注意**：Hibernate 7.x 移除了 `hibernate.jdbc.batch_versioned_data` 配置，
/// 版本化实体的批量更新现在始终启用（自 Hibernate 5.0 起该配置默认为 true）。
///
/// **二级缓存配置**：
///
/// - 默认禁用二级缓存（批量场景下无用且占内存）
/// - 应用可以通过 `spring.jpa.properties.*` 覆盖
///
/// **PostgreSQL + HikariCP 优化**：通过 `hibernate.boot.allow_jdbc_metadata_access=false`
/// 禁止启动期 JDBC metadata 探测，避免 HikariCP 建立连接时的额外 round-trip
/// （Hibernate 7 起替代旧的 `hibernate.temp.use_jdbc_metadata_defaults`）。
///
/// 禁用 metadata 后 Hibernate 无法自动决定 Dialect，需要显式声明数据库。
/// 这里通过 `jakarta.persistence.database-product-name=PostgreSQL` 走
/// `DialectResolver` 路径自动解析为 `PostgreSQLDialect`，从而：
///
/// 1. 不显式设置 `hibernate.dialect` → 不触发 HHH90000025 deprecation 警告
/// 2. 启动期不连数据库即可决定 Dialect → 优化效果保持
///
/// **Jackson 3.x JSON 序列化配置**：
///
/// Hibernate 7.1 无法自动检测 Jackson 3.x（因为包名从 `com.fasterxml.jackson`
/// 改为 `tools.jackson`），需要手动配置 `Jackson3JsonFormatMapper`。
///
/// **注意**：
///
/// 这些配置可以通过 `application.yml` 的 `spring.jpa.properties.hibernate.*` 覆盖。
///
/// @author linqibin
/// @since 0.1.0
/// @see Jackson3JsonFormatMapper
@Configuration
public class HibernatePropertiesCustomizer
    implements org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer {

  /// 默认批量大小。
  private static final int DEFAULT_BATCH_SIZE = 500;

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    // 批量写入配置
    // Hibernate 7.x: BATCH_VERSIONED_DATA 已移除，版本化实体批量更新始终启用
    hibernateProperties.putIfAbsent(AvailableSettings.STATEMENT_BATCH_SIZE, DEFAULT_BATCH_SIZE);
    hibernateProperties.putIfAbsent(AvailableSettings.ORDER_INSERTS, true);
    hibernateProperties.putIfAbsent(AvailableSettings.ORDER_UPDATES, true);

    // 禁用二级缓存（批量场景优化）
    hibernateProperties.putIfAbsent(AvailableSettings.USE_SECOND_LEVEL_CACHE, false);
    hibernateProperties.putIfAbsent(AvailableSettings.USE_QUERY_CACHE, false);

    // PG + HikariCP 公认优化：禁止启动期 JDBC metadata 探测，避免额外 round-trip
    hibernateProperties.putIfAbsent(AvailableSettings.ALLOW_METADATA_ON_BOOT, false);

    // DISALLOW metadata 后必须显式声明数据库；用 product name 走 DialectResolver
    // 自动解析，避免显式 hibernate.dialect 触发 HHH90000025 deprecation 警告
    hibernateProperties.putIfAbsent(AvailableSettings.JAKARTA_HBM2DDL_DB_NAME, "PostgreSQL");

    // 禁用在 JVM 退出时自动创建 SessionFactory
    hibernateProperties.putIfAbsent(AvailableSettings.DELAY_CDI_ACCESS, true);

    // Jackson 3.x JSON 序列化配置
    // Hibernate 7.1 无法自动检测 Jackson 3.x，需要手动配置 FormatMapper
    // 参见:
    // https://discourse.hibernate.org/t/missing-formatmapper-for-json-format-with-jackson-3-x-hibernate-7-x/11819
    hibernateProperties.putIfAbsent(
        AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper());
  }
}
