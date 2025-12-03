package com.patra.catalog.infra.adapter.venue;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.infra.adapter.download.FileDownloadAdapter;
import com.patra.catalog.infra.config.OpenAlexCacheProperties;
import com.patra.starter.core.async.AsyncExecutorRegistry;
import com.patra.starter.core.async.AsyncPoolProperties;
import com.patra.starter.objectstorage.MinioStorageProvider;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.ObjectStorageTemplate;
import com.patra.starter.objectstorage.metrics.ObjectStorageMetrics;
import com.patra.starter.restclient.download.DefaultDownloadClient;
import com.patra.starter.restclient.download.DownloadClient;
import com.patra.starter.test.container.initializer.MinIOContainerInitializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.minio.MinioClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/// VenueSourceFileAdapter 集成测试。
///
/// **测试策略**：
///
/// - 使用 CatalogMinIOContainerInitializer 启动 MinIO 容器（JVM 级别复用）
/// - 使用 WireMock 模拟 OpenAlex S3 公开存储桶
/// - 验证差异化缓存策略
///
/// **缓存策略**：
///
/// - **Manifest**：始终从远程获取（动态索引文件，需要实时性）
/// - **分区文件**：缓存优先（静态内容，缓存避免重复下载）
///
/// **测试场景**：
///
/// 1. Manifest 始终从远程下载（无论缓存是否启用）
/// 2. 分区文件缓存未命中时从远程下载并异步上传到缓存
/// 3. 分区文件缓存命中时从对象存储下载
///
/// @author linqibin
/// @since 0.1.0
@Testcontainers
@DisplayName("VenueSourceFileAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueSourceFileAdapterIT {

  private static final String TEST_BUCKET = "patra-catalog-cache";
  private static final String TEST_KEY_PREFIX = "openalex/sources";

  /// 测试用 manifest JSON。
  ///
  /// 包含单个分区文件，用于简化测试流程。
  private static final String TEST_MANIFEST_JSON =
      """
      {
        "entries": [
          {
            "url": "s3://openalex/data/sources/updated_date=2025-01-01/part_000.gz",
            "meta": {
              "content_length": 1024,
              "record_count": 10
            }
          }
        ],
        "meta": {
          "content_length": 1024,
          "record_count": 10
        }
      }
      """;

  /// 测试用分区文件内容（模拟 gzip 压缩数据）。
  private static final byte[] TEST_PARTITION_CONTENT = "test partition content".getBytes();

  // 每个 Nested 测试类使用不同版本号，确保测试隔离
  private static final String VERSION_DISABLED = "disabled";
  private static final String VERSION_MISS = "miss";
  private static final String VERSION_HIT = "hit";

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private static MinioClient minioClient;

  private VenueSourceFileAdapter adapter;
  private FileDownloadPort fileDownloadPort;
  private ObjectStorageOperations objectStorage;
  private AsyncExecutorRegistry asyncExecutorRegistry;
  private OpenAlexCacheProperties cacheProperties;
  private Path downloadedFile;
  private List<Path> downloadedFiles;

  @BeforeAll
  static void setUpMinioClient() {
    // 确保 MinIO 容器已启动并创建存储桶
    MinIOContainerInitializer.ensureBucket(TEST_BUCKET);

    MinIOContainer minio = MinIOContainerInitializer.getMinIOContainer();
    minioClient =
        MinioClient.builder()
            .endpoint(minio.getS3URL())
            .credentials(minio.getUserName(), minio.getPassword())
            .build();
  }

  @BeforeEach
  void setUp() {
    MinIOContainer minio = MinIOContainerInitializer.getMinIOContainer();

    // 初始化 FileDownloadPort
    RestClient restClient = RestClient.builder().build();
    DownloadClient downloadClient = new DefaultDownloadClient(restClient);
    fileDownloadPort = new FileDownloadAdapter(downloadClient, null);

    // 初始化 ObjectStorageOperations（使用 MinIO Provider）
    MinioClient testMinioClient =
        MinioClient.builder()
            .endpoint(minio.getS3URL())
            .credentials(minio.getUserName(), minio.getPassword())
            .build();
    MinioStorageProvider minioProvider =
        new MinioStorageProvider(testMinioClient, 500 * 1024 * 1024); // 500MB

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ObjectStorageMetrics metrics = new ObjectStorageMetrics(meterRegistry);
    RetryTemplate retryTemplate = RetryTemplate.builder().maxAttempts(1).build();
    objectStorage = new ObjectStorageTemplate(minioProvider, retryTemplate, metrics);

    // 初始化 AsyncExecutorRegistry
    asyncExecutorRegistry = new AsyncExecutorRegistry(null);
    AsyncPoolProperties poolProperties = new AsyncPoolProperties();
    poolProperties.setCoreSize(2);
    poolProperties.setMaxSize(4);
    poolProperties.setQueueCapacity(50);
    asyncExecutorRegistry.register("cache-upload", poolProperties);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (downloadedFile != null && Files.exists(downloadedFile)) {
      Files.deleteIfExists(downloadedFile);
    }
    if (downloadedFiles != null) {
      for (Path file : downloadedFiles) {
        if (file != null && Files.exists(file)) {
          Files.deleteIfExists(file);
        }
      }
    }
    // 关闭线程池
    asyncExecutorRegistry.destroy();
  }

  @Nested
  @DisplayName("缓存禁用场景")
  class CacheDisabledTest {

    @BeforeEach
    void setUp() {
      // 禁用缓存，配置 WireMock 作为 S3 基地址
      cacheProperties =
          new OpenAlexCacheProperties(
              false, TEST_BUCKET, TEST_KEY_PREFIX, wireMock.baseUrl(), "data/sources");
      adapter =
          new VenueSourceFileAdapter(
              fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);
    }

    @Test
    @DisplayName("manifest 应始终从远程下载（即使缓存禁用）")
    void fetchManifest_shouldAlwaysDownloadFromRemote() {
      // Given - WireMock 模拟 OpenAlex S3 响应
      wireMock.stubFor(
          get("/data/sources/manifest")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(TEST_MANIFEST_JSON)));

      // When
      OpenAlexManifest manifest = adapter.fetchManifest();

      // Then
      assertThat(manifest).isNotNull();
      assertThat(manifest.entries()).hasSize(1);
      assertThat(manifest.totalRecordCount()).isEqualTo(10);

      // 验证请求到达远程服务器
      wireMock.verify(1, getRequestedFor(urlEqualTo("/data/sources/manifest")));
    }

    @Test
    @DisplayName("缓存禁用时应直接从远程下载分区文件")
    void fetchPartitionFile_cacheDisabled_shouldDownloadFromRemote() {
      // Given
      String relativePath = "updated_date=2025-01-01/part_000.gz";
      wireMock.stubFor(
          get("/data/sources/" + relativePath)
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/gzip")
                      .withBody(TEST_PARTITION_CONTENT)));

      // When
      downloadedFile = adapter.fetchPartitionFile(relativePath);

      // Then
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.exists(downloadedFile)).isTrue();

      // 验证请求到达远程服务器
      wireMock.verify(1, getRequestedFor(urlEqualTo("/data/sources/" + relativePath)));
    }
  }

  @Nested
  @DisplayName("缓存未命中场景")
  class CacheMissTest {

    @BeforeEach
    void setUp() {
      // 启用缓存
      cacheProperties =
          new OpenAlexCacheProperties(
              true, TEST_BUCKET, TEST_KEY_PREFIX, wireMock.baseUrl(), "data/sources");
      adapter =
          new VenueSourceFileAdapter(
              fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);
    }

    @Test
    @DisplayName("缓存未命中时应从远程下载并异步上传到缓存")
    void fetchPartitionFile_cacheMiss_shouldDownloadAndUploadToCache() throws Exception {
      // Given - 使用隔离版本号的缓存键
      String relativePath = "updated_date=2025-01-02/part_001.gz";
      String cacheKey = TEST_KEY_PREFIX + "/" + relativePath;
      assertThat(objectStorage.exists(TEST_BUCKET, cacheKey)).isFalse();

      // WireMock 模拟远程服务器
      wireMock.stubFor(
          get("/data/sources/" + relativePath)
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/gzip")
                      .withBody(TEST_PARTITION_CONTENT)));

      // When
      downloadedFile = adapter.fetchPartitionFile(relativePath);

      // Then - 验证文件下载成功
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.exists(downloadedFile)).isTrue();

      // Then - 验证请求到达远程服务器
      wireMock.verify(1, getRequestedFor(urlEqualTo("/data/sources/" + relativePath)));

      // Then - 等待异步上传完成，验证缓存已更新
      await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(200))
          .untilAsserted(
              () -> {
                assertThat(objectStorage.exists(TEST_BUCKET, cacheKey)).isTrue();
              });
    }

    @Test
    @DisplayName("manifest 始终从远程下载，不使用缓存")
    void fetchManifest_shouldAlwaysDownloadFromRemote_evenWhenCacheEnabled() {
      // Given - 即使缓存启用，manifest 也应该从远程获取
      wireMock.stubFor(
          get("/data/sources/manifest")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(TEST_MANIFEST_JSON)));

      // When
      OpenAlexManifest manifest = adapter.fetchManifest();

      // Then - 验证 manifest 解析正确
      assertThat(manifest).isNotNull();
      assertThat(manifest.entries()).hasSize(1);

      // Then - 验证请求到达远程服务器（每次调用都应该访问远程）
      wireMock.verify(1, getRequestedFor(urlEqualTo("/data/sources/manifest")));
    }
  }

  @Nested
  @DisplayName("缓存命中场景")
  class CacheHitTest {

    private static final String CACHE_KEY_PARTITION =
        TEST_KEY_PREFIX + "/updated_date=2025-01-03/part_002.gz";

    @BeforeEach
    void setUp() throws Exception {
      // 启用缓存
      cacheProperties =
          new OpenAlexCacheProperties(
              true, TEST_BUCKET, TEST_KEY_PREFIX, wireMock.baseUrl(), "data/sources");
      adapter =
          new VenueSourceFileAdapter(
              fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);

      // 预先上传文件到缓存
      uploadToCache(CACHE_KEY_PARTITION, TEST_PARTITION_CONTENT);
    }

    @Test
    @DisplayName("缓存命中时应从对象存储下载而不访问远程服务器")
    void fetchPartitionFile_cacheHit_shouldDownloadFromCache() throws Exception {
      // Given - 设置 WireMock（不应被调用）
      String relativePath = "updated_date=2025-01-03/part_002.gz";
      wireMock.stubFor(
          get("/data/sources/" + relativePath)
              .willReturn(aResponse().withStatus(200).withBody("remote")));

      // When
      downloadedFile = adapter.fetchPartitionFile(relativePath);

      // Then - 验证文件内容来自缓存
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.readAllBytes(downloadedFile)).isEqualTo(TEST_PARTITION_CONTENT);

      // Then - 验证没有请求到达远程服务器
      wireMock.verify(0, getRequestedFor(urlEqualTo("/data/sources/" + relativePath)));
    }

    private void uploadToCache(String key, byte[] content) throws Exception {
      Path tempFile = Files.createTempFile("cache-upload-", ".gz");
      try {
        Files.write(tempFile, content);
        try (var is = Files.newInputStream(tempFile)) {
          objectStorage.upload(
              TEST_BUCKET,
              key,
              is,
              com.patra.starter.objectstorage.domain.ObjectMetadata.builder()
                  .contentLength(content.length)
                  .contentType("application/gzip")
                  .build());
        }
      } finally {
        Files.deleteIfExists(tempFile);
      }
    }
  }
}
