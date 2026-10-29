package dev.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.patra.catalog.domain.model.enums.PublicationDateType;
import dev.patra.catalog.domain.model.read.citation.CitationAuthor;
import dev.patra.catalog.domain.model.read.citation.CitationRecord;
import dev.patra.catalog.domain.model.vo.publication.Pagination;
import dev.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/// `CitationRecordReadAdapter` 集成测试：按 ID 批量读引用记录，一批两条 SQL，缩写三级兜底，文章号只在没有页码时给。
///
/// 监视 `JdbcClient` 会换一个 Spring 测试上下文，所以单独成类。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CitationRecordReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("CitationRecordReadAdapter 引用记录读端集成测试")
class CitationRecordReadAdapterIT {

  private static final Instant SYNCED = Instant.parse("2026-05-01T00:00:00Z");

  @Autowired private CitationRecordReadAdapter adapter;
  @Autowired private TestEntityManager em;
  @MockitoSpyBean private JdbcClient jdbcClient;

  @Test
  @DisplayName("500 篇带期刊、期次、作者、PMCID / PII 的文献：只发两条 SQL")
  void should_load_500_records_with_two_statements() {
    Long venueId = saveVenue("The New England journal of medicine", "N Engl J Med");
    // 同一卷期只有一个实例（唯一键 uk_venue_instance_identity），500 篇共用
    Long instanceId = saveInstance(venueId, "385", "16");
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      Long id =
          persist(
              publication("Paper " + i, "3800" + i).venueId(venueId).venueInstanceId(instanceId));
      saveAuthor(id, 1, "Anker", "Stefan D", "SD", null);
      saveAuthor(id, 2, "Butler", "Javed", "J", null);
      saveIdentifier(id, "pmc", "PMC" + i);
      ids.add(id);
    }
    flush();

    clearInvocations(jdbcClient);
    List<CitationRecord> records = adapter.findByIds(ids);

    verify(jdbcClient, times(2)).sql(anyString());
    assertThat(records).hasSize(500);
    CitationRecord any = records.get(0);
    assertThat(any.journalTitle()).isEqualTo("The New England journal of medicine");
    assertThat(any.volume()).isEqualTo("385");
    assertThat(any.issue()).isEqualTo("16");
    assertThat(any.pmcid()).startsWith("PMC");
    assertThat(any.authors()).extracting(CitationAuthor::family).containsExactly("Anker", "Butler");
  }

  @Test
  @DisplayName("软删与不存在的 ID 不返回")
  void should_skip_soft_deleted_and_missing() {
    Long active = persist(publication("Active", "1001"));
    Long deleted = persist(publication("Deleted", "1002"));
    flush();
    softDelete(deleted);

    assertThat(adapter.findByIds(List.of(active, deleted, 999_999_999L)))
        .extracting(CitationRecord::id)
        .containsExactly(active);
  }

  @Test
  @DisplayName("期刊缩写：ISO 缩写 → MedlineTA（cat_venue.abbreviated_title）→ 期刊全称")
  void should_fall_back_journal_abbreviation_iso_then_medline_ta_then_title() {
    Long withMedline = saveVenue("Scientific reports", "Sci Rep");
    Long withoutAbbr = saveVenue("The Lancet", null);
    Long iso =
        persist(
            publication("Iso", "2001").venueId(withMedline).journalIsoAbbreviation("Sci. Rep."));
    Long medline = persist(publication("Medline", "2002").venueId(withMedline));
    Long title = persist(publication("Title", "2003").venueId(withoutAbbr));
    flush();

    Map<Long, CitationRecord> byId = index(adapter.findByIds(List.of(iso, medline, title)));

    assertThat(byId.get(iso).journalAbbreviation()).isEqualTo("Sci. Rep.");
    assertThat(byId.get(medline).journalAbbreviation()).isEqualTo("Sci Rep");
    assertThat(byId.get(title).journalAbbreviation()).isEqualTo("The Lancet");
  }

  @Test
  @DisplayName("文章号：没有页码时取 PII；有页码时为空；页码与出版日期按列读出")
  void should_derive_article_number_only_without_pagination() {
    Long noPages = persist(publication("No pages", "3001").publicationMonth(null));
    saveIdentifier(noPages, "pii", "12345");
    Long withPages =
        persist(
            publication("With pages", "3002")
                .medlinePgn("1451-61")
                .startPage("1451")
                .endPage("1461")
                .publicationMonth(10)
                .publicationDay(14));
    saveIdentifier(withPages, "pii", "e0123456");
    flush();

    Map<Long, CitationRecord> byId = index(adapter.findByIds(List.of(noPages, withPages)));

    assertThat(byId.get(noPages).articleNumber()).isEqualTo("12345");
    assertThat(byId.get(noPages).publishedDate().toIsoString()).isEqualTo("2021");
    assertThat(byId.get(withPages).articleNumber()).isNull();
    assertThat(byId.get(withPages).pagination().medlinePgn()).isEqualTo("1451-61");
    assertThat(byId.get(withPages).publishedDate().toIsoString()).isEqualTo("2021-10-14");
  }

  @Test
  @DisplayName("标题去掉内联标记")
  void should_strip_inline_markup_from_title() {
    Long id = persist(publication("Effect of CO<sub>2</sub> on <i>E. coli</i>", "4001"));
    flush();

    assertThat(adapter.findByIds(List.of(id)))
        .singleElement()
        .extracting(CitationRecord::title)
        .isEqualTo("Effect of CO2 on E. coli");
  }

  @Test
  @DisplayName("作者按 author_order；团体作者只有 collective；没有作者为空列表")
  void should_keep_author_order_and_collective_author() {
    Long id = persist(publication("Authors", "5001"));
    saveAuthor(id, 3, null, null, null, "EMPEROR-Preserved Trial Investigators");
    saveAuthor(id, 1, "Anker", "Stefan D", "SD", null);
    saveAuthor(id, 2, "Butler", null, "J", null);
    Long none = persist(publication("No authors", "5002"));
    flush();

    Map<Long, CitationRecord> byId = index(adapter.findByIds(List.of(id, none)));

    assertThat(byId.get(id).authors())
        .containsExactly(
            CitationAuthor.of("Anker", "Stefan D", "SD", null),
            CitationAuthor.of("Butler", null, "J", null),
            CitationAuthor.of(null, null, null, "EMPEROR-Preserved Trial Investigators"));
    assertThat(byId.get(none).authors()).isEmpty();
  }

  @Test
  @DisplayName("空入参返回空列表；超过 500 个抛出")
  void should_return_empty_for_empty_input_and_reject_over_500() {
    assertThat(adapter.findByIds(List.of())).isEmpty();
    List<Long> tooMany = LongStream.rangeClosed(1, 501).boxed().toList();
    assertThatThrownBy(() -> adapter.findByIds(tooMany))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ===== fixtures =====

  private static Map<Long, CitationRecord> index(List<CitationRecord> records) {
    return records.stream().collect(Collectors.toMap(CitationRecord::id, Function.identity()));
  }

  private void flush() {
    em.flush();
    em.clear();
  }

  private void softDelete(Long id) {
    em.getEntityManager()
        .createNativeQuery("UPDATE cat_publication SET deleted_at = now() WHERE id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }

  private Long saveVenue(String title, String abbreviatedTitle) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle(title);
    v.setAbbreviatedTitle(abbreviatedTitle);
    v.setProvenanceCode("PUBMED");
    v.setCountryCode("US");
    em.persist(v);
    return v.getId();
  }

  private Long saveInstance(Long venueId, String volume, String issue) {
    long id = SnowflakeIdGenerator.getId();
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO cat_venue_instance (id, venue_id, volume, issue, publication_year)"
                + " VALUES (:id, :venue, :volume, :issue, 2021)")
        .setParameter("id", id)
        .setParameter("venue", venueId)
        .setParameter("volume", volume)
        .setParameter("issue", issue)
        .executeUpdate();
    return id;
  }

  private void saveAuthor(
      Long publicationId, int order, String last, String fore, String initials, String collective) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO cat_publication_author (id, publication_id, author_order, display_name,"
                + " last_name, fore_name, initials, collective_name)"
                + " VALUES (:id, :pub, :order, :display, :last, :fore, :initials, :collective)")
        .setParameter("id", SnowflakeIdGenerator.getId())
        .setParameter("pub", publicationId)
        .setParameter("order", order)
        .setParameter("display", collective != null ? collective : last + " " + initials)
        .setParameter("last", last)
        .setParameter("fore", fore)
        .setParameter("initials", initials)
        .setParameter("collective", collective)
        .executeUpdate();
  }

  private void saveIdentifier(Long publicationId, String type, String value) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value)"
                + " VALUES (:id, :pub, :type, :value)")
        .setParameter("id", SnowflakeIdGenerator.getId())
        .setParameter("pub", publicationId)
        .setParameter("type", type)
        .setParameter("value", value)
        .executeUpdate();
  }

  private PublicationEntity.PublicationEntityBuilder<?, ?> publication(String title, String pmid) {
    return PublicationEntity.builder()
        .id(SnowflakeIdGenerator.getId())
        .provenanceCode("PUBMED")
        .pmid(pmid)
        .title(title)
        .publicationYear(2021)
        .isOa(false)
        .authorsComplete(true)
        .citationCount(0)
        .lastSyncedAt(SYNCED);
  }

  private Long persist(PublicationEntity.PublicationEntityBuilder<?, ?> builder) {
    PublicationEntity p = builder.build();
    em.persist(p);
    return p.getId();
  }

  @Test
  @DisplayName("全字段：每一列都按列读出，PMCID / PII 取同类型中 id 最小的一条")
  void should_map_every_column_when_record_is_fully_populated() {
    Long venueId = saveVenue("The Lancet", "Lancet");
    Long instanceId = saveInstance(venueId, "397", "10275");
    Long id =
        persist(
            publication("Aspirin <i>in vivo</i>", "33000001")
                .doi("10.1016/x")
                .venueId(venueId)
                .venueInstanceId(instanceId)
                .journalIsoAbbreviation("Lancet (London)")
                .medlinePgn("e1-e9")
                .startPage("e1")
                .endPage("e9")
                .publicationMonth(2)
                .publicationDay(27)
                .languageCode("eng"));
    saveIdentifier(id, "pmc", "PMC1");
    saveIdentifier(id, "pmc", "PMC2");
    saveIdentifier(id, "pii", "S0140");
    saveAuthor(id, 2, "Doe", "Anne", "A", null);
    saveAuthor(id, 1, "Smith", "John", "J", null);
    flush();

    CitationRecord expected =
        CitationRecord.builder()
            .id(id)
            .pmid("33000001")
            .pmcid("PMC1")
            .doi("10.1016/x")
            .title("Aspirin in vivo")
            .authors(
                List.of(
                    CitationAuthor.of("Smith", "John", "J", null),
                    CitationAuthor.of("Doe", "Anne", "A", null)))
            .journalTitle("The Lancet")
            .journalAbbreviation("Lancet (London)")
            .volume("397")
            .issue("10275")
            .pagination(Pagination.of("e1-e9", "e1", "e9"))
            .publishedDate(PublicationDate.of(PublicationDateType.PUBLISHED, 2021, 2, 27))
            .language("eng")
            .build();

    assertThat(adapter.findByIds(List.of(id))).containsExactly(expected);
  }

  @Test
  @DisplayName("只有必填列：可空列为 null、作者为空列表、页码为空")
  void should_leave_optional_columns_null_when_record_has_only_required_columns() {
    long id = SnowflakeIdGenerator.getId();
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO cat_publication (id, provenance_code, title) VALUES (:id, 'PUBMED', 'Bare')")
        .setParameter("id", id)
        .executeUpdate();

    CitationRecord expected = CitationRecord.builder().id(id).title("Bare").build();

    assertThat(adapter.findByIds(List.of(id))).containsExactly(expected);
  }
}
