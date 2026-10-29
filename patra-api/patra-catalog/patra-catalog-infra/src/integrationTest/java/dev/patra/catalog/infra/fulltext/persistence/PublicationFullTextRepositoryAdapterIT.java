package dev.patra.catalog.infra.fulltext.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import jakarta.persistence.EntityManager;
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

/// `PublicationFullTextRepositoryAdapter` 集成测试。
///
/// 导入 `PatraHibernatePropertiesCustomizer` 与 `JacksonAutoConfiguration`，让 `media` JSONB
/// 走生产同款 Jackson 3 的 FormatMapper。
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
@DisplayName("PublicationFullTextRepositoryAdapter 集成测试")
class PublicationFullTextRepositoryAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-20T05:00:00Z");

  @Autowired private PublicationFullTextRepositoryAdapter adapter;
  @Autowired private EntityManager em;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("同一文献第二次 save 更新原行而不是新增")
  void should_insert_then_update_by_publication_id() {
    adapter.save(failed(7001L, 1));
    flushAndClear();
    adapter.save(
        failed(7001L, 2).toBuilder().status(FullTextStatus.FETCH_FAILED).lastError("x").build());
    flushAndClear();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_fulltext WHERE publication_id = 7001",
                Integer.class))
        .isEqualTo(1);
    PublicationFullText saved = adapter.findByPublicationId(7001L).orElseThrow();
    assertThat(saved.attemptCount()).isEqualTo(2);
    assertThat(saved.lastError()).isEqualTo("x");
  }

  @Test
  @DisplayName("全部字段往返一致，含宽高为空的图片")
  void should_round_trip_all_fields() {
    PublicationFullText fullText =
        PublicationFullText.builder()
            .publicationId(7002L)
            .pmcid("PMC13583816")
            .pmcVersion("PMC13583816.1")
            .status(FullTextStatus.PARSED)
            .licenseCode("CC BY")
            .manuscript(false)
            .objectPrefix("pmc/PMC13583816/PMC13583816.1/")
            .xmlMd5("02e7188728a46592ce9bd142b6dc76cd")
            .pdfObjectKey("pmc/PMC13583816/PMC13583816.1/PMC13583816.1.pdf")
            .pdfSize(2_621_440L)
            .pdfMd5("c47862ef2aa62863c623367b42e329ce")
            .media(
                List.of(
                    FullTextMedia.of("bmjopen-16-9-g001.webp", 1600, 2159),
                    FullTextMedia.of("bmjopen-16-9-g002.webp", null, null)))
            .fetchedAt(T0)
            .parserVersion(1)
            .parsedAt(T0.plusSeconds(60))
            .wordCount(5230)
            .attemptCount(0)
            .build();

    adapter.save(fullText);
    flushAndClear();

    assertThat(adapter.findByPublicationId(7002L)).contains(fullText);
  }

  @Test
  @DisplayName("没有图片时 media 存为空数组")
  void should_default_media_to_empty_list() {
    adapter.save(
        PublicationFullText.builder()
            .publicationId(7003L)
            .pmcid("PMC1")
            .status(FullTextStatus.UNAVAILABLE)
            .unavailableReason(FullTextUnavailableReason.NOT_IN_BUCKET)
            .nextAttemptAt(T0)
            .attemptCount(1)
            .build());
    flushAndClear();

    assertThat(adapter.findByPublicationId(7003L).orElseThrow().media()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT media::text FROM cat_publication_fulltext WHERE publication_id = 7003",
                String.class))
        .isEqualTo("[]");
  }

  private static PublicationFullText failed(long publicationId, int attempts) {
    return PublicationFullText.builder()
        .publicationId(publicationId)
        .pmcid("PMC1")
        .status(FullTextStatus.FETCH_FAILED)
        .attemptCount(attempts)
        .nextAttemptAt(T0)
        .build();
  }

  private void flushAndClear() {
    em.flush();
    em.clear();
  }
}
