package dev.patra.tool.objectstorage.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.objectstorage.ObjectStorageOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ObjectStorageAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObjectStorageAutoConfiguration.class))
          .withPropertyValues(
              "patra.object-storage.providers.minio.endpoint=http://localhost:9000",
              "patra.object-storage.providers.minio.access-key=a",
              "patra.object-storage.providers.minio.secret-key=b");

  @Test
  void should_register_minio_operations_by_default() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(ObjectStorageOperations.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.object-storage.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ObjectStorageOperations.class));
  }

  @Test
  void should_skip_minio_when_active_provider_is_s3() {
    runner
        .withPropertyValues("patra.object-storage.active-provider=s3")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ObjectStorageOperations.class));
  }

  @Test
  void should_bind_max_file_size_default() {
    runner.run(
        ctx ->
            assertThat(ctx.getBean(ObjectStorageProperties.class).maxFileSize())
                .isEqualTo(104_857_600L));
  }
}
