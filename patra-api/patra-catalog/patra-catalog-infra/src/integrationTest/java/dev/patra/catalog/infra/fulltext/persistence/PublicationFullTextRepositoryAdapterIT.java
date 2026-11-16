package dev.patra.catalog.infra.fulltext.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
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

  /// 不清一级缓存连续保存两次：第二次查到的是第一次新建的那个托管实体，要更新它而不是再插一行。
  @Test
  @DisplayName("同一事务里同一文献连续 save 两次，仍只有一行")
  void should_save_same_fulltext_twice() {
    adapter.save(failed(7003L, 1));
    adapter.save(failed(7003L, 2));
    flushAndClear();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_fulltext WHERE publication_id = 7003",
                Integer.class))
        .isEqualTo(1);
    assertThat(adapter.findByPublicationId(7003L).orElseThrow().attemptCount()).isEqualTo(2);
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

  /// 全文升为主表后带乐观锁：每次更新版本号递增，行数始终为 1。
  @Test
  @DisplayName("抓取、解析先后两次更新同一篇，版本号递增、仍只有一行")
  void should_update_fulltext_status_twice() {
    adapter.save(failed(7004L, 1));
    flushAndClear();
    long inserted = version(7004L);

    adapter.save(fetched(7004L));
    flushAndClear();
    long afterFetch = version(7004L);
    adapter.save(fetched(7004L).toBuilder().status(FullTextStatus.PARSED).parserVersion(1).build());
    flushAndClear();

    assertThat(afterFetch).isGreaterThan(inserted);
    assertThat(version(7004L)).isGreaterThan(afterFetch);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_fulltext WHERE publication_id = 7004",
                Integer.class))
        .isEqualTo(1);
  }

  /// 托管实体读到之后，别的事务已把这行改到新版本：按旧版本写回要撞乐观锁，不能静默覆盖。
  @Test
  @DisplayName("按旧版本写回全文记录时抛乐观锁异常")
  void should_reject_stale_update() {
    adapter.save(failed(7005L, 1));
    flushAndClear();
    adapter.findByPublicationId(7005L);
    jdbc.update(
        "UPDATE cat_publication_fulltext SET version = version + 1 WHERE publication_id = 7005");

    adapter.save(fetched(7005L));

    assertThatThrownBy(() -> em.flush()).isInstanceOf(OptimisticLockException.class);
  }

  /// 一级缓存里的实体读到之后，别的事务已改了这行：锁行时要拿数据库里的当前值，不能返回缓存里的旧值。
  @Test
  @DisplayName("锁行时重读当前值，不用之前读到的旧实体")
  void should_lock_and_reread_row_loaded_before_another_writer() {
    adapter.save(fetched(7006L));
    flushAndClear();
    adapter.findByPublicationId(7006L);
    jdbc.update(
        "UPDATE cat_publication_fulltext SET status = 'PARSED', parser_version = 9,"
            + " version = version + 1 WHERE publication_id = 7006");

    PublicationFullText locked = adapter.lockByPublicationId(7006L).orElseThrow();

    assertThat(locked.status()).isEqualTo(FullTextStatus.PARSED);
    assertThat(locked.parserVersion()).isEqualTo(9);
  }

  @Test
  @DisplayName("没有全文记录时锁行返回空")
  void should_return_empty_when_locking_missing_row() {
    assertThat(adapter.lockByPublicationId(7999L)).isEmpty();
  }

  private long version(long publicationId) {
    return jdbc.queryForObject(
        "SELECT version FROM cat_publication_fulltext WHERE publication_id = ?",
        Long.class,
        publicationId);
  }

  private static PublicationFullText fetched(long publicationId) {
    return PublicationFullText.builder()
        .publicationId(publicationId)
        .pmcid("PMC" + publicationId)
        .pmcVersion("PMC" + publicationId + ".1")
        .status(FullTextStatus.FETCHED)
        .objectPrefix("pmc/PMC" + publicationId + "/PMC" + publicationId + ".1/")
        .fetchedAt(T0)
        .attemptCount(0)
        .build();
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
