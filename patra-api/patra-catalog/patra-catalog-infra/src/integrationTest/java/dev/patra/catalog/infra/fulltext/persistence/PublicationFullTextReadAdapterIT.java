package dev.patra.catalog.infra.fulltext.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.read.FullTextReadModel;
import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import dev.patra.catalog.domain.fulltext.model.vo.body.Section;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
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

/// `PublicationFullTextReadAdapter` 集成测试：可读、只有 PDF、撤稿、未解析四种情况。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationFullTextReadAdapter.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("PublicationFullTextReadAdapter 集成测试")
class PublicationFullTextReadAdapterIT {

  private static final Instant FETCHED_AT = Instant.parse("2026-10-20T05:12:00Z");

  @Autowired private PublicationFullTextReadAdapter adapter;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;

  @Test
  @DisplayName("可读全文返回署名字段与正文")
  void should_return_body_when_readable() {
    FullTextBody body = body();
    insert(9_700_001L, "PARSED", null, "k.pdf", false);
    jdbc.update(
        "INSERT INTO cat_publication_fulltext_body (publication_id, body, parser_version)"
            + " VALUES (?, CAST(? AS jsonb), 1)",
        9_700_001L,
        jsonMapper.writeValueAsString(body));

    FullTextReadModel model = adapter.findForPortal(9_700_001L).orElseThrow();

    assertThat(model.pmcid()).isEqualTo("PMC97");
    assertThat(model.licenseCode()).isEqualTo("CC BY");
    assertThat(model.fetchedAt()).isEqualTo(FETCHED_AT);
    assertThat(model.wordCount()).isEqualTo(1200);
    assertThat(model.manuscript()).isFalse();
    assertThat(model.body()).isEqualTo(body);
  }

  @Test
  @DisplayName("无正文但有 PDF 时返回署名字段、正文与字数为空")
  void should_return_attribution_without_body_when_only_pdf() {
    insert(9_700_002L, "UNAVAILABLE", "NO_BODY", "k.pdf", false);

    FullTextReadModel model = adapter.findForPortal(9_700_002L).orElseThrow();

    assertThat(model.body()).isNull();
    assertThat(model.wordCount()).isNull();
    assertThat(model.licenseCode()).isEqualTo("CC BY");
  }

  @Test
  @DisplayName("撤稿文献返回空")
  void should_return_empty_when_retracted() {
    insert(9_700_003L, "PARSED", null, "k.pdf", true);

    assertThat(adapter.findForPortal(9_700_003L)).isEmpty();
  }

  @Test
  @DisplayName("只抓到未解析且没有 PDF 时返回空")
  void should_return_empty_when_fetched_without_pdf() {
    insert(9_700_004L, "FETCHED", null, null, false);

    assertThat(adapter.findForPortal(9_700_004L)).isEmpty();
  }

  @Test
  @DisplayName("没有全文记录时返回空")
  void should_return_empty_without_record() {
    jdbc.update(
        "INSERT INTO cat_publication (id, provenance_code, pmid, venue_instance_id, title, publication_year)"
            + " VALUES (9700005, 'PUBMED', '9700005', 1, 'T', 2025)");

    assertThat(adapter.findForPortal(9_700_005L)).isEmpty();
  }

  private void insert(long id, String status, String reason, String pdfKey, boolean retracted) {
    jdbc.update(
        "INSERT INTO cat_publication (id, provenance_code, pmid, venue_instance_id, title, publication_year)"
            + " VALUES (?, 'PUBMED', ?, 1, 'T', 2025)",
        id,
        String.valueOf(id));
    jdbc.update(
        "INSERT INTO cat_publication_fulltext (id, publication_id, pmcid, status, unavailable_reason,"
            + " license_code, is_manuscript, pdf_object_key, word_count, fetched_at)"
            + " VALUES (?, ?, 'PMC97', ?, ?, 'CC BY', false, ?, 1200, ?)",
        id + 100,
        id,
        status,
        reason,
        pdfKey,
        Timestamp.from(FETCHED_AT));
    if (retracted) {
      jdbc.update(
          "INSERT INTO cat_publication_type (id, publication_id, type_id, type_value)"
              + " VALUES (?, ?, 'D016441', 'Retracted Publication')",
          id + 200,
          id);
    }
  }

  private static FullTextBody body() {
    return FullTextBody.builder()
        .schemaVersion(FullTextBody.SCHEMA_VERSION)
        .sections(
            List.of(
                Section.of(
                    "s1",
                    List.of(new Inline.Text("Intro")),
                    List.of(),
                    List.of(new Block.Paragraph(List.of(new Inline.Text("x")))))))
        .wordCount(1200)
        .build();
  }
}
