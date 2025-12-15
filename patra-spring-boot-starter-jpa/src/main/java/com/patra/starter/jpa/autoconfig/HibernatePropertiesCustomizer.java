package com.patra.starter.jpa.autoconfig;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.context.annotation.Configuration;

/// Hibernate 属性定制器，配置批量写入和性能优化。
///
/// **Hibernate 6.6 批量写入配置**：
///
/// - `hibernate.jdbc.batch_size = 500` - 批量写入大小，与 Spring Batch chunk size 对齐
/// - `hibernate.order_inserts = true` - 按实体类型排序 INSERT，减少 SQL 切换
/// - `hibernate.order_updates = true` - 按实体类型排序 UPDATE
/// - `hibernate.jdbc.batch_versioned_data = true` - 支持带版本号实体的批量更新
///
/// **二级缓存配置**：
///
/// - 默认禁用二级缓存（批量场景下无用且占内存）
/// - 应用可以通过 `spring.jpa.properties.*` 覆盖
///
/// **注意**：
///
/// 这些配置可以通过 `application.yml` 的 `spring.jpa.properties.hibernate.*` 覆盖。
///
/// @author linqibin
/// @since 0.1.0
@Configuration
public class HibernatePropertiesCustomizer
    implements org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer {

  /// 默认批量大小。
  private static final int DEFAULT_BATCH_SIZE = 500;

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    // 批量写入配置
    hibernateProperties.putIfAbsent(AvailableSettings.STATEMENT_BATCH_SIZE, DEFAULT_BATCH_SIZE);
    hibernateProperties.putIfAbsent(AvailableSettings.ORDER_INSERTS, true);
    hibernateProperties.putIfAbsent(AvailableSettings.ORDER_UPDATES, true);
    hibernateProperties.putIfAbsent(AvailableSettings.BATCH_VERSIONED_DATA, true);

    // 禁用二级缓存（批量场景优化）
    hibernateProperties.putIfAbsent(AvailableSettings.USE_SECOND_LEVEL_CACHE, false);
    hibernateProperties.putIfAbsent(AvailableSettings.USE_QUERY_CACHE, false);

    // 禁用在 JVM 退出时自动创建 SessionFactory
    hibernateProperties.putIfAbsent(AvailableSettings.DELAY_CDI_ACCESS, true);
  }
}
