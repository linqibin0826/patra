package dev.patra.tool.objectstorage.provider;

import static org.assertj.core.api.Assertions.assertThat;

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

/// MinIO 存储提供者集成测试：用真实 MinIO 容器验证上传前自动建桶的行为。
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
}
