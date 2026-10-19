package dev.patra.catalog.integration.config;

import dev.patra.tool.test.container.initializer.MinIOContainerInitializer;

/// catalog E2E 的 MinIO 容器初始化器，预建全文桶。
///
/// @author linqibin
/// @since 0.11.0
public class CatalogE2EMinIOContainerInitializer extends MinIOContainerInitializer {

  /// 全文桶名。
  ///
  /// @return 桶名
  @Override
  protected String getBucketName() {
    return "patra-catalog-fulltext";
  }
}
