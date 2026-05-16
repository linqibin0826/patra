package dev.linqibin.starter.test.container.initializer;

import dev.linqibin.starter.test.container.ContainerRegistry;
import dev.linqibin.starter.test.container.ContainerType;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MinIOContainer;

/// MinIO 容器初始化器（配置化版本）。
///
/// 提供 MinIO 对象存储容器的单例管理和动态配置注入。
/// 通过子类化支持不同服务使用不同的存储桶名。
///
/// ### 核心特性
///
/// - **JVM 内单例**: 同一 JVM 进程内所有测试共享同一个 MinIO 容器实例
/// - **配置化存储桶**: 子类通过重写 `getBucketName()` 指定存储桶名
/// - **自动创建存储桶**: 容器启动后自动创建指定的存储桶
/// - **动态配置注入**: 自动注入对象存储配置到 Spring 测试上下文
/// - **线程安全**: 使用双重检查锁模式确保并发安全
///
/// ### 使用方式
///
/// 方式一：直接使用（默认存储桶 `test-bucket`）
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = MinIOContainerInitializer.class)
/// class SomeStorageIT {
///     // ...
/// }
/// ```
///
/// 方式二：子类化指定存储桶
///
/// ```java
/// public class CatalogMinIOInitializer extends MinIOContainerInitializer {
///     @Override
///     protected String getBucketName() {
///         return "patra-catalog-cache";
///     }
/// }
/// ```
///
/// ### 容器配置
///
/// - **镜像版本**: minio/minio:RELEASE.2024-01-18T22-51-28Z
/// - **默认凭证**: minioadmin / minioadmin
/// - **容器复用策略**: JVM 内复用，JVM 间不复用
///
/// ### 注入的配置项
///
/// - `patra.object-storage.active-provider=minio`
/// - `patra.object-storage.providers.minio.endpoint={动态端口}`
/// - `patra.object-storage.providers.minio.access-key=minioadmin`
/// - `patra.object-storage.providers.minio.secret-key=minioadmin`
///
/// @author linqibin
/// @since 0.1.0
/// @see ContainerRegistry
/// @see ApplicationContextInitializer
public class MinIOContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(MinIOContainerInitializer.class);

  /// MinIO 镜像版本。
  private static final String MINIO_IMAGE = "minio/minio:RELEASE.2024-01-18T22-51-28Z";

  /// 初始化状态标志。
  private static volatile boolean initialized = false;

  /// 同步锁对象。
  private static final Object LOCK = new Object();

  /// 已创建的存储桶记录（支持多个存储桶）。
  private static final java.util.Set<String> createdBuckets =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  /// 获取存储桶名称。
  ///
  /// 子类可重写此方法以使用不同的存储桶名。
  /// 默认返回 `test-bucket`。
  ///
  /// @return 存储桶名称
  protected String getBucketName() {
    return "test-bucket";
  }

  /// 初始化 MinIO 容器（线程安全）。
  ///
  /// 使用双重检查锁模式确保容器只启动一次。
  private void initializeContainer() {
    if (!initialized) {
      synchronized (LOCK) {
        if (!initialized) {
          log.info("========================================");
          log.info("初始化 MinIO TestContainer (线程: {})", Thread.currentThread().getName());
          log.info("========================================");

          MinIOContainer minio = new MinIOContainer(MINIO_IMAGE).withReuse(false);

          minio.start();

          // 注册到全局容器注册表
          ContainerRegistry.register(ContainerType.MINIO, minio);

          log.info("MinIO 容器已启动");
          log.info("  - Endpoint: {}", minio.getS3URL());
          log.info("  - Username: {}", minio.getUserName());
          log.info("========================================");

          initialized = true;
        } else {
          log.debug("MinIO 容器已由其他线程初始化，复用现有实例");
        }
      }
    }
  }

  /// 创建存储桶（如果不存在）。
  ///
  /// 使用 ConcurrentHashMap.newKeySet() 跟踪已创建的存储桶，
  /// 避免重复创建。
  ///
  /// @param minio MinIO 容器实例
  /// @param bucketName 存储桶名称
  private void createBucketIfNotExists(MinIOContainer minio, String bucketName) {
    if (createdBuckets.contains(bucketName)) {
      log.debug("存储桶 {} 已创建，跳过", bucketName);
      return;
    }

    synchronized (LOCK) {
      if (createdBuckets.contains(bucketName)) {
        return;
      }

      try {
        MinioClient minioClient =
            MinioClient.builder()
                .endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword())
                .build();

        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        createdBuckets.add(bucketName);
        log.info("已创建存储桶: {}", bucketName);
      } catch (Exception e) {
        // 如果存储桶已存在，忽略错误
        if (e.getMessage() != null && e.getMessage().contains("BucketAlreadyOwnedByYou")) {
          createdBuckets.add(bucketName);
          log.debug("存储桶 {} 已存在", bucketName);
        } else {
          throw new IllegalStateException("创建存储桶失败: " + bucketName, e);
        }
      }
    }
  }

  /// 初始化 Spring 应用上下文，注入 MinIO 动态配置。
  ///
  /// 注入的配置项:
  ///
  /// - `patra.object-storage.active-provider`: minio
  /// - `patra.object-storage.providers.minio.endpoint`: {动态 S3 URL}
  /// - `patra.object-storage.providers.minio.access-key`: minioadmin
  /// - `patra.object-storage.providers.minio.secret-key`: minioadmin
  ///
  /// @param applicationContext Spring 应用上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    // 确保容器已初始化
    initializeContainer();

    MinIOContainer minio = ContainerRegistry.get(ContainerType.MINIO, MinIOContainer.class);

    if (minio == null) {
      throw new IllegalStateException("MinIO 容器未正确注册");
    }

    // 创建存储桶
    String bucketName = getBucketName();
    createBucketIfNotExists(minio, bucketName);

    log.info("注入 MinIO 动态配置到 Spring 上下文");
    log.info("  - 存储桶: {}", bucketName);

    TestPropertyValues.of(
            "patra.object-storage.active-provider=minio",
            "patra.object-storage.providers.minio.endpoint=" + minio.getS3URL(),
            "patra.object-storage.providers.minio.access-key=" + minio.getUserName(),
            "patra.object-storage.providers.minio.secret-key=" + minio.getPassword())
        .applyTo(applicationContext.getEnvironment());

    log.info("MinIO 动态配置注入完成");
  }

  /// 获取 MinIO 容器实例（供测试代码访问）。
  ///
  /// @return MinIO 容器实例，如果未初始化则返回 null
  public static MinIOContainer getMinIOContainer() {
    return ContainerRegistry.get(ContainerType.MINIO, MinIOContainer.class);
  }

  /// 确保 MinIO 容器已启动（用于非 Spring 上下文测试）。
  ///
  /// 此方法可在 `@BeforeAll` 中直接调用，无需 Spring 上下文。
  /// 如果容器已启动，此方法为幂等操作。
  ///
  /// @return MinIO 容器实例
  public static MinIOContainer ensureStarted() {
    if (!initialized) {
      synchronized (LOCK) {
        if (!initialized) {
          log.info("========================================");
          log.info("初始化 MinIO TestContainer (线程: {})", Thread.currentThread().getName());
          log.info("========================================");

          MinIOContainer minio = new MinIOContainer(MINIO_IMAGE).withReuse(false);

          minio.start();

          // 注册到全局容器注册表
          ContainerRegistry.register(ContainerType.MINIO, minio);

          log.info("MinIO 容器已启动");
          log.info("  - Endpoint: {}", minio.getS3URL());
          log.info("  - Username: {}", minio.getUserName());
          log.info("========================================");

          initialized = true;
        }
      }
    }

    return ContainerRegistry.get(ContainerType.MINIO, MinIOContainer.class);
  }

  /// 确保存储桶已创建（用于非 Spring 上下文测试）。
  ///
  /// 此方法可在 `@BeforeAll` 中调用，确保指定的存储桶存在。
  ///
  /// @param bucketName 存储桶名称
  public static void ensureBucket(String bucketName) {
    MinIOContainer minio = ensureStarted();

    if (createdBuckets.contains(bucketName)) {
      return;
    }

    synchronized (LOCK) {
      if (createdBuckets.contains(bucketName)) {
        return;
      }

      try {
        MinioClient minioClient =
            MinioClient.builder()
                .endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword())
                .build();

        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        createdBuckets.add(bucketName);
        log.info("已创建存储桶: {}", bucketName);
      } catch (Exception e) {
        // 如果存储桶已存在，忽略错误
        if (e.getMessage() != null && e.getMessage().contains("BucketAlreadyOwnedByYou")) {
          createdBuckets.add(bucketName);
          log.debug("存储桶 {} 已存在", bucketName);
        } else {
          throw new IllegalStateException("创建存储桶失败: " + bucketName, e);
        }
      }
    }
  }
}
