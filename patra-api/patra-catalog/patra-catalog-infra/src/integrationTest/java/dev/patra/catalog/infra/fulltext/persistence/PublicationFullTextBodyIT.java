package dev.patra.catalog.infra.fulltext.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import dev.patra.catalog.domain.fulltext.model.vo.body.Section;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.json.JsonMapper;

/// 正文表写入与 `has_full_text` 更新的集成测试。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationFullTextRepositoryAdapter.class,
  PatraHibernatePropertiesCustomizer.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("正文表与 has_full_text 集成测试")
class PublicationFullTextBodyIT {

  @Autowired private PublicationFullTextRepositoryAdapter adapter;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("正文以 JSONB upsert，第二次写入覆盖并更新解析器版本")
  void should_upsert_body_as_jsonb_and_read_back() {
    FullTextBody first = body("v1");
    FullTextBody second = body("v2");

    adapter.saveBody(8001L, first, 1);
    adapter.saveBody(8001L, second, 2);
    em.flush();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_fulltext_body WHERE publication_id = 8001",
                Integer.class))
        .isEqualTo(1);
    String json =
        jdbc.queryForObject(
            "SELECT body::text FROM cat_publication_fulltext_body WHERE publication_id = 8001",
            String.class);
    assertThat(jsonMapper.readValue(json, FullTextBody.class)).isEqualTo(second);
    assertThat(
            jdbc.queryForObject(
                "SELECT parser_version FROM cat_publication_fulltext_body WHERE publication_id = 8001",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT jsonb_typeof(body -> 'sections') FROM cat_publication_fulltext_body WHERE publication_id = 8001",
                String.class))
        .isEqualTo("array");
  }

  @Test
  @DisplayName("has_full_text 只改 metadata 这一列，没有 metadata 行时不新建")
  void should_set_has_full_text_without_creating_metadata() {
    jdbc.update(
        "INSERT INTO cat_publication_metadata (id, publication_id, has_full_text, import_batch)"
            + " VALUES (8101, 8002, false, 'b1')");

    adapter.setHasFullText(8002L, true);
    adapter.setHasFullText(8003L, true);
    em.flush();

    assertThat(
            jdbc.queryForMap(
                "SELECT has_full_text, import_batch FROM cat_publication_metadata WHERE publication_id = 8002"))
        .containsEntry("has_full_text", true)
        .containsEntry("import_batch", "b1");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_metadata WHERE publication_id = 8003",
                Integer.class))
        .isZero();
  }

  private static FullTextBody body(String text) {
    return FullTextBody.builder()
        .schemaVersion(FullTextBody.SCHEMA_VERSION)
        .sections(
            List.of(
                Section.of(
                    "s1",
                    List.of(new Inline.Text("T")),
                    List.of(),
                    List.of(new Block.Paragraph(List.of(new Inline.Text(text)))))))
        .wordCount(1)
        .build();
  }
}
