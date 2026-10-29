package dev.patra.catalog.infra.fulltext.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.fulltext.exception.FullTextFileNotFoundException;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFileInfo;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.infra.config.CatalogITMinIOContainerInitializer;
import dev.patra.catalog.infra.fulltext.config.FullTextSourceProperties;
import dev.patra.tool.objectstorage.autoconfigure.ObjectStorageAutoConfiguration;
import dev.patra.tool.test.container.initializer.MinIOContainerInitializer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `FullTextObjectStorageAdapter` 集成测试（真实 MinIO）。
///
/// 初始化器只建 `patra-catalog-cache` 桶；桶不存在时 MinIO 报 `NoSuchBucket`，所以先建全文桶。
@SpringBootTest(classes = FullTextObjectStorageAdapter.class)
@ImportAutoConfiguration(ObjectStorageAutoConfiguration.class)
@EnableConfigurationProperties(FullTextSourceProperties.class)
@ContextConfiguration(initializers = CatalogITMinIOContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("FullTextObjectStorageAdapter 集成测试")
class FullTextObjectStorageAdapterIT {

  private static final String PREFIX = FullTextObjectStoragePort.objectPrefix("PMC1", "PMC1.2");

  @Autowired private FullTextObjectStorageAdapter adapter;

  @BeforeAll
  static void createBucket() {
    MinIOContainerInitializer.ensureStarted();
    MinIOContainerInitializer.ensureBucket("patra-catalog-fulltext");
  }

  @Test
  @DisplayName("对象前缀沿用 PMC 的目录结构")
  void should_build_object_prefix() {
    assertThat(PREFIX).isEqualTo("pmc/PMC1/PMC1.2/");
  }

  @Test
  @DisplayName("写入后读回相同字节")
  void should_store_then_open_with_same_bytes(@TempDir Path dir) throws Exception {
    Path file = Files.writeString(dir.resolve("a.xml"), "<article/>", StandardCharsets.UTF_8);

    adapter.store(PREFIX + "a.xml", file, "application/xml");

    try (StoredFile stored = adapter.open(PREFIX + "a.xml")) {
      assertThat(new String(stored.content().readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("<article/>");
      assertThat(stored.info().size()).isEqualTo(10);
      assertThat(stored.info().contentType()).isEqualTo("application/xml");
    }
  }

  @Test
  @DisplayName("stat 已有对象返回大小、类型与 ETag")
  void should_stat_existing_object(@TempDir Path dir) throws Exception {
    Path file = Files.write(dir.resolve("g.webp"), new byte[] {1, 2, 3});
    adapter.store(PREFIX + "g.webp", file, "image/webp");

    StoredFileInfo info = adapter.stat(PREFIX + "g.webp").orElseThrow();

    assertThat(info.size()).isEqualTo(3);
    assertThat(info.contentType()).isEqualTo("image/webp");
    assertThat(info.etag()).isNotBlank();
  }

  @Test
  @DisplayName("对象不存在时 stat 返回空")
  void should_return_empty_stat_when_missing() {
    assertThat(adapter.stat(PREFIX + "missing.jpg")).isEmpty();
  }

  @Test
  @DisplayName("打开不存在的对象抛 NOT_FOUND 异常")
  void should_throw_not_found_when_opening_missing() {
    assertThatThrownBy(() -> adapter.open(PREFIX + "missing.pdf"))
        .isInstanceOf(FullTextFileNotFoundException.class);
  }
}
