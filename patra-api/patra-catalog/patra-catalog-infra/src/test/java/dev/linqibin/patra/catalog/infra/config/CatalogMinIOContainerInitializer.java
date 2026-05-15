package dev.linqibin.patra.catalog.infra.config;

import com.patra.starter.test.container.initializer.MinIOContainerInitializer;

/// Catalog 服务专用 MinIO 容器初始化器。
///
/// 继承 starter-test 的 {@link MinIOContainerInitializer}，指定 catalog 服务的存储桶名。
///
/// ### 使用方式
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = CatalogMinIOContainerInitializer.class)
/// class SomeStorageIT {
///     // ...
/// }
/// ```
///
/// ### 存储桶
///
/// 使用 `patra-catalog-cache` 作为存储桶名，与 MeSH/Venue 文件缓存配置一致。
///
/// @author linqibin
/// @since 0.1.0
/// @see MinIOContainerInitializer
public class CatalogMinIOContainerInitializer extends MinIOContainerInitializer {

  /// 返回 catalog 服务的存储桶名。
  ///
  /// @return 存储桶名 "patra-catalog-cache"
  @Override
  protected String getBucketName() {
    return "patra-catalog-cache";
  }
}
