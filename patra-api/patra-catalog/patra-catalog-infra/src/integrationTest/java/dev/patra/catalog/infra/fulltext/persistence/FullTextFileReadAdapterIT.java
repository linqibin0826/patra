package dev.patra.catalog.infra.fulltext.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.read.FullTextFileRef;
import dev.patra.catalog.infra.config.CatalogITMinIOContainerInitializer;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.fulltext.config.FullTextSourceProperties;
import dev.patra.catalog.infra.fulltext.storage.FullTextObjectStorageAdapter;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.objectstorage.autoconfigure.ObjectStorageAutoConfiguration;
import dev.patra.tool.test.container.initializer.MinIOContainerInitializer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `FullTextFileReadAdapter` 集成测试（PG + MinIO）：PDF 与图片的可取判定。
@DataJpaTest
@ContextConfiguration(
    initializers = {
      CatalogITPostgreSQLContainerInitializer.class,
      CatalogITMinIOContainerInitializer.class
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(ObjectStorageAutoConfiguration.class)
@EnableConfigurationProperties(FullTextSourceProperties.class)
@Import({
  FullTextFileReadAdapter.class,
  FullTextObjectStorageAdapter.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("FullTextFileReadAdapter 集成测试")
class FullTextFileReadAdapterIT {

  private static final String PREFIX = "pmc/PMC5/PMC5.1/";

  @Autowired private FullTextFileReadAdapter adapter;
  @Autowired private FullTextObjectStorageAdapter storage;
  @Autowired private JdbcTemplate jdbc;

  @BeforeAll
  static void createBucket() {
    MinIOContainerInitializer.ensureStarted();
    MinIOContainerInitializer.ensureBucket("patra-catalog-fulltext");
  }

  @Test
  @DisplayName("可读篇在 media 清单里且存储里有的图片可取，其余不可取")
  void should_find_figure_only_when_listed_and_stored(@TempDir Path dir) throws Exception {
    insert(9_800_001L, "PARSED", null, "[{\"name\":\"g1.webp\"},{\"name\":\"g2.webp\"}]", false);
    storage.store(
        PREFIX + "g1.webp", Files.write(dir.resolve("g1"), new byte[] {1, 2}), "image/webp");

    FullTextFileRef ref = adapter.findFigure(9_800_001L, "g1.webp").orElseThrow();
    assertThat(ref.objectKey()).isEqualTo(PREFIX + "g1.webp");
    assertThat(ref.contentType()).isEqualTo("image/webp");
    assertThat(ref.size()).isEqualTo(2);
    assertThat(ref.etag()).isNotBlank();
    assertThat(adapter.findFigure(9_800_001L, "g2.webp")).as("清单里有但存储里没有").isEmpty();
    assertThat(adapter.findFigure(9_800_001L, "other.webp")).as("不在清单里").isEmpty();
  }

  @Test
  @DisplayName("PDF 按可下载判定，ETag 用 PDF 的 md5，文件名为 PMCID.pdf")
  void should_find_pdf_when_downloadable() {
    insert(9_800_002L, "PARSED", null, "[]", false);

    FullTextFileRef ref = adapter.findPdf(9_800_002L).orElseThrow();

    assertThat(ref.objectKey()).isEqualTo(PREFIX + "PMC5.1.pdf");
    assertThat(ref.fileName()).isEqualTo("PMC5.pdf");
    assertThat(ref.contentType()).isEqualTo("application/pdf");
    assertThat(ref.size()).isEqualTo(1024);
    assertThat(ref.etag()).isEqualTo("c47862ef2aa62863c623367b42e329ce");
  }

  @Test
  @DisplayName("撤稿篇 PDF 与图片都不可取")
  void should_hide_pdf_and_figures_when_retracted() {
    insert(9_800_003L, "PARSED", null, "[{\"name\":\"g1.webp\"}]", true);

    assertThat(adapter.findPdf(9_800_003L)).isEmpty();
    assertThat(adapter.findFigure(9_800_003L, "g1.webp")).isEmpty();
  }

  @Test
  @DisplayName("NO_BODY 有 PDF 时 PDF 可取、图片不可取")
  void should_allow_pdf_but_not_figures_when_no_body() {
    insert(9_800_004L, "UNAVAILABLE", "NO_BODY", "[{\"name\":\"g1.webp\"}]", false);

    assertThat(adapter.findPdf(9_800_004L)).isPresent();
    assertThat(adapter.findFigure(9_800_004L, "g1.webp")).isEmpty();
  }

  private void insert(long id, String status, String reason, String media, boolean retracted) {
    jdbc.update(
        "INSERT INTO cat_publication (id, provenance_code, pmid, venue_instance_id, title, publication_year)"
            + " VALUES (?, 'PUBMED', ?, 1, 'T', 2025)",
        id,
        String.valueOf(id));
    jdbc.update(
        "INSERT INTO cat_publication_fulltext (id, publication_id, pmcid, pmc_version, status,"
            + " unavailable_reason, object_prefix, pdf_object_key, pdf_size, pdf_md5, media)"
            + " VALUES (?, ?, 'PMC5', 'PMC5.1', ?, ?, ?, ?, 1024, 'c47862ef2aa62863c623367b42e329ce',"
            + " CAST(? AS jsonb))",
        id + 100,
        id,
        status,
        reason,
        PREFIX,
        PREFIX + "PMC5.1.pdf",
        media);
    if (retracted) {
      jdbc.update(
          "INSERT INTO cat_publication_type (id, publication_id, type_id, type_value)"
              + " VALUES (?, ?, 'D016441', 'Retracted Publication')",
          id + 200,
          id);
    }
  }
}
