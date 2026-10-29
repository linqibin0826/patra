package dev.patra.catalog.infra.publication.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `V11` 在干净库上建出的结构：来源表的原文列用 lz4 压缩，进度表已改名。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("文献来源表结构集成测试")
class PublicationSourceSchemaIT {

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("cat_publication_source.raw_xml 的压缩方式为 lz4")
  void should_create_source_table_with_lz4_raw_xml() {
    String compression =
        jdbc.queryForObject(
            "SELECT attcompression::text FROM pg_attribute"
                + " WHERE attrelid = 'cat_publication_source'::regclass AND attname = 'raw_xml'",
            String.class);

    assertThat(compression).isEqualTo("l");
  }

  @Test
  @DisplayName("进度表改名为 cat_pubmed_file，带 kind 与 skipped_stale_count")
  void should_rename_progress_table_with_kind() {
    Integer columns =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'cat_pubmed_file'"
                + " AND column_name IN ('kind', 'skipped_stale_count')",
            Integer.class);

    assertThat(columns).isEqualTo(2);
  }
}
