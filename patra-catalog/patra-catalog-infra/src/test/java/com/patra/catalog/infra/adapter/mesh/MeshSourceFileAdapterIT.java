package com.patra.catalog.infra.adapter.mesh;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.infra.adapter.download.FileDownloadAdapter;
import com.patra.catalog.infra.config.MeshCacheProperties;
import com.patra.starter.core.async.AsyncExecutorRegistry;
import com.patra.starter.core.async.AsyncPoolProperties;
import com.patra.starter.objectstorage.MinioStorageProvider;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.ObjectStorageTemplate;
import com.patra.starter.objectstorage.metrics.ObjectStorageMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/// MeshSourceFileAdapter 集成测试。
///
/// **测试策略**：
///
/// - 使用 TestContainers 启动 MinIO 容器作为真实对象存储
/// - 使用 WireMock 模拟远程文件服务器
/// - 验证缓存优先策略的完整流程
///
/// **测试场景**：
///
/// 1. 缓存禁用时直接从远程下载
/// 2. 缓存未命中时从远程下载并异步上传到缓存
/// 3. 缓存命中时从对象存储下载
///
/// @author linqibin
/// @since 0.1.0
@Testcontainers
@DisplayName("MeshSourceFileAdapter 集成测试")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class MeshSourceFileAdapterIT {

  private static final String TEST_BUCKET = "patra-catalog-cache";
  private static final String TEST_KEY_PREFIX = "mesh";
  private static final String TEST_XML_CONTENT =
      "<?xml version=\"1.0\"?><DescriptorRecordSet>test content</DescriptorRecordSet>";

  // 每个 Nested 测试类使用不同版本号，确保测试隔离
  private static final String VERSION_DISABLED = "disabled";
  private static final String VERSION_MISS = "miss";
  private static final String VERSION_HIT = "hit";

  @Container
  static MinIOContainer minioContainer =
      new MinIOContainer("minio/minio:RELEASE.2024-01-18T22-51-28Z");

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private static MinioClient minioClient;

  private MeshSourceFileAdapter adapter;
  private FileDownloadPort fileDownloadPort;
  private ObjectStorageOperations objectStorage;
  private AsyncExecutorRegistry asyncExecutorRegistry;
  private MeshCacheProperties cacheProperties;
  private Path downloadedFile;

  @BeforeAll
  static void setUpMinioClient() throws Exception {
    minioClient =
        MinioClient.builder()
            .endpoint(minioContainer.getS3URL())
            .credentials(minioContainer.getUserName(), minioContainer.getPassword())
            .build();

    // 创建测试存储桶
    minioClient.makeBucket(MakeBucketArgs.builder().bucket(TEST_BUCKET).build());
  }

  @BeforeEach
  void setUp() {
    // 初始化 FileDownloadPort
    RestClient restClient = RestClient.builder().build();
    fileDownloadPort = new FileDownloadAdapter(restClient);

    // 初始化 ObjectStorageOperations（使用 MinIO Provider）
    MinioClient testMinioClient =
        MinioClient.builder()
            .endpoint(minioContainer.getS3URL())
            .credentials(minioContainer.getUserName(), minioContainer.getPassword())
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
    // 关闭线程池
    asyncExecutorRegistry.destroy();
  }

  @Nested
  @DisplayName("缓存禁用场景")
  class CacheDisabledTest {

    @BeforeEach
    void setUp() {
      // 禁用缓存
      cacheProperties = new MeshCacheProperties(false, TEST_BUCKET, TEST_KEY_PREFIX);
      adapter =
          new MeshSourceFileAdapter(
              fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);
    }

    @Test
    @DisplayName("缓存禁用时应直接从远程下载 Descriptor 文件")
    void fetchDescriptorFile_cacheDisabled_shouldDownloadFromRemote() throws Exception {
      // Given - WireMock 模拟远程服务器
      wireMock.stubFor(
          get("/mesh/descdisabled.xml")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/xml")
                      .withBody(TEST_XML_CONTENT)));

      URI remoteUrl = URI.create(wireMock.baseUrl() + "/mesh/descdisabled.xml");

      // When
      downloadedFile = adapter.fetchDescriptorFile(VERSION_DISABLED, remoteUrl);

      // Then
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.exists(downloadedFile)).isTrue();
      assertThat(Files.readString(downloadedFile)).isEqualTo(TEST_XML_CONTENT);

      // 验证请求到达远程服务器
      wireMock.verify(1, getRequestedFor(urlEqualTo("/mesh/descdisabled.xml")));
    }

    @Test
    @DisplayName("缓存禁用时应直接从远程下载 Qualifier 文件")
    void fetchQualifierFile_cacheDisabled_shouldDownloadFromRemote() throws Exception {
      // Given
      String qualifierContent =
          "<?xml version=\"1.0\"?><QualifierRecordSet>qualifiers</QualifierRecordSet>";
      wireMock.stubFor(
          get("/mesh/qualdisabled.xml")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/xml")
                      .withBody(qualifierContent)));

      URI remoteUrl = URI.create(wireMock.baseUrl() + "/mesh/qualdisabled.xml");

      // When
      downloadedFile = adapter.fetchQualifierFile(VERSION_DISABLED, remoteUrl);

      // Then
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.readString(downloadedFile)).isEqualTo(qualifierContent);
    }
  }

  @Nested
  @DisplayName("缓存未命中场景")
  class CacheMissTest {

    @BeforeEach
    void setUp() {
      // 启用缓存
      cacheProperties = new MeshCacheProperties(true, TEST_BUCKET, TEST_KEY_PREFIX);
      adapter =
          new MeshSourceFileAdapter(
              fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);
    }

    @Test
    @DisplayName("缓存未命中时应从远程下载并异步上传到缓存")
    void fetchDescriptorFile_cacheMiss_shouldDownloadAndUploadToCache() throws Exception {
      // Given - 使用隔离版本号的缓存键
      String cacheKey = "mesh/descriptors/descmiss.xml";
      assertThat(objectStorage.exists(TEST_BUCKET, cacheKey)).isFalse();

      // WireMock 模拟远程服务器
      wireMock.stubFor(
          get("/mesh/descmiss.xml")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/xml")
                      .withBody(TEST_XML_CONTENT)));

      URI remoteUrl = URI.create(wireMock.baseUrl() + "/mesh/descmiss.xml");

      // When
      downloadedFile = adapter.fetchDescriptorFile(VERSION_MISS, remoteUrl);

      // Then - 验证文件下载成功
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.readString(downloadedFile)).isEqualTo(TEST_XML_CONTENT);

      // Then - 验证请求到达远程服务器
      wireMock.verify(1, getRequestedFor(urlEqualTo("/mesh/descmiss.xml")));

      // Then - 等待异步上传完成，验证缓存已更新
      await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(200))
          .untilAsserted(
              () -> {
                assertThat(objectStorage.exists(TEST_BUCKET, cacheKey)).isTrue();
              });
    }
  }

  @Nested
  @DisplayName("缓存命中场景")
  class CacheHitTest {

    private static final String CACHE_KEY = "mesh/descriptors/deschit.xml";

    @BeforeEach
    void setUp() throws Exception {
      // 启用缓存
      cacheProperties = new MeshCacheProperties(true, TEST_BUCKET, TEST_KEY_PREFIX);
      adapter =
          new MeshSourceFileAdapter(
              fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);

      // 预先上传文件到缓存
      uploadToCache(CACHE_KEY, TEST_XML_CONTENT);
    }

    @Test
    @DisplayName("缓存命中时应从对象存储下载而不访问远程服务器")
    void fetchDescriptorFile_cacheHit_shouldDownloadFromCache() throws Exception {
      // Given - 设置 WireMock（不应被调用）
      wireMock.stubFor(
          get("/mesh/deschit.xml").willReturn(aResponse().withStatus(200).withBody("remote")));

      URI remoteUrl = URI.create(wireMock.baseUrl() + "/mesh/deschit.xml");

      // When
      downloadedFile = adapter.fetchDescriptorFile(VERSION_HIT, remoteUrl);

      // Then - 验证文件内容来自缓存
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.readString(downloadedFile)).isEqualTo(TEST_XML_CONTENT);

      // Then - 验证没有请求到达远程服务器
      wireMock.verify(0, getRequestedFor(urlEqualTo("/mesh/deschit.xml")));
    }

    private void uploadToCache(String key, String content) throws Exception {
      Path tempFile = Files.createTempFile("cache-upload-", ".xml");
      try {
        Files.writeString(tempFile, content);
        try (var is = Files.newInputStream(tempFile)) {
          objectStorage.upload(
              TEST_BUCKET,
              key,
              is,
              com.patra.starter.objectstorage.domain.ObjectMetadata.builder()
                  .contentLength(content.length())
                  .contentType("application/xml")
                  .build());
        }
      } finally {
        Files.deleteIfExists(tempFile);
      }
    }
  }
}
