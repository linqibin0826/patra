package dev.patra.tool.objectstorage.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.tool.objectstorage.domain.ObjectMetadata;
import dev.patra.tool.objectstorage.domain.UploadResult;
import dev.patra.tool.test.container.initializer.MinIOContainerInitializer;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;

/// MinIO 存储提供者集成测试：用真实 MinIO 容器验证上传前自动建桶、删除与按前缀删除。
@DisplayName("MinIO 存储提供者集成测试")
class MinioStorageProviderIT {

  private static final int CONCURRENT_UPLOADS = 16;

  private static final int ROUNDS = 5;

  private static MinioClient minioClient;

  /// 启动（或复用）MinIO 容器并建好客户端。
  @BeforeAll
  static void startMinio() {
    MinIOContainer minio = MinIOContainerInitializer.ensureStarted();
    minioClient =
        MinioClient.builder()
            .endpoint(minio.getS3URL())
            .credentials(minio.getUserName(), minio.getPassword())
            .build();
  }

  /// 多个线程同时向尚不存在的桶上传时，并发建桶不应让任何一次上传失败。
  ///
  /// 竞态窗口在「查桶不存在 → 建桶」之间，单轮未必撞上，所以换新桶跑多轮。
  @Test
  @DisplayName("并发首次上传到不存在的桶时全部成功")
  void should_succeed_all_uploads_when_uploading_concurrently_to_missing_bucket() throws Exception {
    try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_UPLOADS)) {
      for (int round = 0; round < ROUNDS; round++) {
        MinioStorageProvider provider = new MinioStorageProvider(minioClient, 1024 * 1024);
        String bucket = "race-" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<UploadResult>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_UPLOADS; i++) {
          String key = "object-" + i + ".txt";
          futures.add(
              executor.submit(
                  () -> {
                    startGate.await();
                    byte[] content = key.getBytes(StandardCharsets.UTF_8);
                    return provider.upload(
                        bucket,
                        key,
                        new ByteArrayInputStream(content),
                        ObjectMetadata.builder().contentLength(content.length).build());
                  }));
        }
        startGate.countDown();

        for (Future<UploadResult> future : futures) {
          assertThat(future.get().getBucketName()).isEqualTo(bucket);
        }
      }
    }
  }

  /// 删除对象后查不到它；对象已不存在时再删不抛。
  @Test
  @DisplayName("删除对象，键不存在时静默")
  void should_delete_object_and_ignore_missing_key() {
    MinioStorageProvider provider = new MinioStorageProvider(minioClient, 1024 * 1024);
    String bucket = newBucket();
    put(provider, bucket, "a/1");

    provider.delete(bucket, "a/1");

    assertThat(provider.statObject(bucket, "a/1")).isEmpty();
    provider.delete(bucket, "a/1");
  }

  /// 只删前缀下的对象：`avatar/1/` 不能连带 `avatar/10/`。
  @Test
  @DisplayName("按前缀删除只删前缀下的对象并返回个数")
  void should_delete_only_objects_under_prefix() {
    MinioStorageProvider provider = new MinioStorageProvider(minioClient, 1024 * 1024);
    String bucket = newBucket();
    for (String key : List.of("avatar/1/x", "avatar/1/y", "avatar/10/z", "avatar/2/w")) {
      put(provider, bucket, key);
    }

    int deleted = provider.deleteByPrefix(bucket, "avatar/1/");

    assertThat(deleted).isEqualTo(2);
    assertThat(provider.statObject(bucket, "avatar/1/x")).isEmpty();
    assertThat(provider.statObject(bucket, "avatar/1/y")).isEmpty();
    assertThat(provider.statObject(bucket, "avatar/10/z")).isPresent();
    assertThat(provider.statObject(bucket, "avatar/2/w")).isPresent();
  }

  /// 超过一批（1000 个）也全部删掉。
  @Test
  @DisplayName("按前缀删除超过一批的对象")
  void should_delete_more_than_one_batch() {
    MinioStorageProvider provider = new MinioStorageProvider(minioClient, 1024 * 1024);
    String bucket = newBucket();
    for (int i = 0; i < 1001; i++) {
      put(provider, bucket, "p/" + i);
    }

    int deleted = provider.deleteByPrefix(bucket, "p/");

    assertThat(deleted).isEqualTo(1001);
    assertThat(provider.deleteByPrefix(bucket, "p/")).isZero();
  }

  /// 桶不存在时视为已删光。
  @Test
  @DisplayName("桶不存在时按前缀删除返回 0")
  void should_return_zero_when_bucket_missing() {
    MinioStorageProvider provider = new MinioStorageProvider(minioClient, 1024 * 1024);

    assertThat(provider.deleteByPrefix(newBucket(), "avatar/1/")).isZero();
  }

  /// 空前缀会删光整桶，拒绝。
  @Test
  @DisplayName("空前缀被拒绝")
  void should_reject_blank_prefix() {
    MinioStorageProvider provider = new MinioStorageProvider(minioClient, 1024 * 1024);

    assertThatThrownBy(() -> provider.deleteByPrefix(newBucket(), ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 生成一个还不存在的桶名。
  ///
  /// @return 桶名
  private static String newBucket() {
    return "del-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /// 上传一个 1 字节的对象。
  ///
  /// @param provider 提供者
  /// @param bucket 桶
  /// @param key 键
  private static void put(MinioStorageProvider provider, String bucket, String key) {
    provider.upload(
        bucket,
        key,
        new ByteArrayInputStream(new byte[] {1}),
        ObjectMetadata.builder().contentLength(1).build());
  }
}
