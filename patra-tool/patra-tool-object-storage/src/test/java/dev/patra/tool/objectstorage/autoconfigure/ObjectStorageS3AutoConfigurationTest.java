package dev.patra.tool.objectstorage.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.objectstorage.ObjectStorageOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class ObjectStorageS3AutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ObjectStorageAutoConfiguration.class, ObjectStorageS3AutoConfiguration.class));

  @Test
  void should_register_s3_operations_when_active_provider_is_s3() {
    runner
        .withPropertyValues(
            "patra.object-storage.active-provider=s3",
            "patra.object-storage.providers.s3.region=us-east-1",
            "patra.object-storage.providers.s3.access-key=a",
            "patra.object-storage.providers.s3.secret-key=b")
        .run(
            ctx ->
                assertThat(ctx)
                    .hasSingleBean(S3Client.class)
                    .hasSingleBean(ObjectStorageOperations.class));
  }

  @Test
  void should_not_register_s3_client_for_minio_provider() {
    runner
        .withPropertyValues(
            "patra.object-storage.providers.minio.endpoint=http://localhost:9000",
            "patra.object-storage.providers.minio.access-key=a",
            "patra.object-storage.providers.minio.secret-key=b")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(S3Client.class));
  }

  @Test
  void should_stay_quiet_without_aws_sdk_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(S3Client.class))
        .withPropertyValues(
            "patra.object-storage.providers.minio.endpoint=http://localhost:9000",
            "patra.object-storage.providers.minio.access-key=a",
            "patra.object-storage.providers.minio.secret-key=b")
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }
}
