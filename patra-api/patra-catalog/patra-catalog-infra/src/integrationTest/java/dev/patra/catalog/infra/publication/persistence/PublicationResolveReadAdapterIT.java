package dev.patra.catalog.infra.publication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.patra.catalog.domain.publication.model.read.PublicationResolveReadModel;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.venue.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `PublicationResolveReadAdapter` 集成测试：按内部 ID / PMID 批量解析，软删行照样返回并带 `deleted`。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PublicationResolveReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("PublicationResolveReadAdapter 批量解析集成测试")
class PublicationResolveReadAdapterIT {

  private static final Instant SYNCED = Instant.parse("2026-05-01T00:00:00Z");

  @Autowired private PublicationResolveReadAdapter adapter;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("按 ID：存在与软删两种行都返回，软删行带标题 / 期刊 / 年份；不存在的 ID 不出现")
  void should_return_active_and_soft_deleted_rows_by_ids() {
    Long venueId = saveVenue("Lancet");
    Long active = persist(publication("Active paper", "38001234", 2021).venueId(venueId));
    Long deleted = persist(publication("Deleted paper", "37000001", 2019).venueId(venueId));
    flush();
    softDelete(deleted);

    Map<Long, PublicationResolveReadModel> byId =
        index(adapter.findByIds(List.of(active, deleted, 999_999_999L)));

    assertThat(byId).containsOnlyKeys(active, deleted);
    PublicationResolveReadModel a = byId.get(active);
    assertThat(a.deleted()).isFalse();
    assertThat(a.pmid()).isEqualTo("38001234");
    assertThat(a.title()).isEqualTo("Active paper");
    assertThat(a.venueName()).isEqualTo("Lancet");
    assertThat(a.publicationYear()).isEqualTo(2021);
    PublicationResolveReadModel d = byId.get(deleted);
    assertThat(d.deleted()).isTrue();
    assertThat(d.title()).isEqualTo("Deleted paper");
    assertThat(d.venueName()).isEqualTo("Lancet");
    assertThat(d.publicationYear()).isEqualTo(2019);
  }

  @Test
  @DisplayName("按 PMID：含软删，PMID 与内部 ID 对应正确；不存在的 PMID 不出现")
  void should_return_rows_by_pmids_including_soft_deleted() {
    Long active = persist(publication("Active paper", "38001234", 2021));
    Long deleted = persist(publication("Deleted paper", "37000001", 2019));
    flush();
    softDelete(deleted);

    List<PublicationResolveReadModel> rows =
        adapter.findByPmids(List.of("38001234", "37000001", "1"));

    assertThat(rows)
        .extracting(PublicationResolveReadModel::pmid, PublicationResolveReadModel::id)
        .containsExactlyInAnyOrder(tuple("38001234", active), tuple("37000001", deleted));
  }

  @Test
  @DisplayName("PMID 为空、没有载体：照实返回 null，仍算存在")
  void should_return_null_pmid_and_null_venue_as_is() {
    Long id = persist(publication("No pmid", null, 2020));
    flush();

    List<PublicationResolveReadModel> rows = adapter.findByIds(List.of(id));

    assertThat(rows)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.pmid()).isNull();
              assertThat(r.venueName()).isNull();
              assertThat(r.deleted()).isFalse();
            });
  }

  @Test
  @DisplayName("撤稿：发表类型 D016441 或全文原因 RETRACTED 算撤稿；撤稿声明 D016440 不算")
  void should_flag_retracted_by_publication_type_or_fulltext_reason() {
    Long byType = persist(publication("Retracted by type", "30000001", 2018));
    Long byFulltext = persist(publication("Retracted by PMC", "30000002", 2018));
    Long normal = persist(publication("Normal", "30000003", 2018));
    Long notice = persist(publication("Retraction notice", "30000004", 2018));
    flush();
    insertType(byType, "D016441", "Retracted Publication");
    insertType(notice, "D016440", "Retraction of Publication");
    insertFulltext(byFulltext, "RETRACTED");
    insertFulltext(normal, null);

    Map<Long, PublicationResolveReadModel> byId =
        index(adapter.findByIds(List.of(byType, byFulltext, normal, notice)));

    assertThat(byId.get(byType).retracted()).isTrue();
    assertThat(byId.get(byFulltext).retracted()).isTrue();
    assertThat(byId.get(normal).retracted()).isFalse();
    assertThat(byId.get(notice).retracted()).isFalse();
  }

  @Test
  @DisplayName("空入参返回空列表")
  void should_return_empty_without_query_for_empty_input() {
    assertThat(adapter.findByIds(List.of())).isEmpty();
    assertThat(adapter.findByPmids(List.of())).isEmpty();
  }

  // ===== fixtures =====

  private static Map<Long, PublicationResolveReadModel> index(
      List<PublicationResolveReadModel> rows) {
    return rows.stream()
        .collect(Collectors.toMap(PublicationResolveReadModel::id, Function.identity()));
  }

  private void flush() {
    em.flush();
    em.clear();
  }

  /// 软删一篇文献：实体走 Hibernate `@SoftDelete` 会被过滤，测试直接改列。
  private void softDelete(Long id) {
    em.getEntityManager()
        .createNativeQuery("UPDATE cat_publication SET deleted_at = now() WHERE id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }

  /// 直接插一条 MeSH 发表类型。
  private void insertType(Long publicationId, String typeId, String value) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO cat_publication_type (id, publication_id, type_id, type_value,"
                + " vocabulary_source) VALUES (:id, :pub, :typeId, :value, 'MESH')")
        .setParameter("id", SnowflakeIdGenerator.getId())
        .setParameter("pub", publicationId)
        .setParameter("typeId", typeId)
        .setParameter("value", value)
        .executeUpdate();
  }

  /// 直接插一条全文记录；原因为空时是正常的已解析全文。
  private void insertFulltext(Long publicationId, String unavailableReason) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO cat_publication_fulltext (id, publication_id, pmcid, status,"
                + " unavailable_reason) VALUES (:id, :pub, 'PMC1', :status, :reason)")
        .setParameter("id", SnowflakeIdGenerator.getId())
        .setParameter("pub", publicationId)
        .setParameter("status", unavailableReason == null ? "PARSED" : "UNAVAILABLE")
        .setParameter("reason", unavailableReason)
        .executeUpdate();
  }

  private Long saveVenue(String title) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle(title);
    v.setProvenanceCode("OPENALEX");
    v.setCountryCode("US");
    em.persist(v);
    return v.getId();
  }

  private PublicationEntity.PublicationEntityBuilder<?, ?> publication(
      String title, String pmid, int year) {
    return PublicationEntity.builder()
        .id(SnowflakeIdGenerator.getId())
        .provenanceCode("PUBMED")
        .pmid(pmid)
        .title(title)
        .publicationYear(year)
        .venueInstanceId(SnowflakeIdGenerator.getId())
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
}
