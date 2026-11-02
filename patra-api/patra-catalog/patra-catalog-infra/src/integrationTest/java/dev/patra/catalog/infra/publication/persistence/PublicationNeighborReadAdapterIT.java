package dev.patra.catalog.infra.publication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.patra.catalog.domain.publication.model.enums.SubjectNeighborReason;
import dev.patra.catalog.domain.publication.model.read.SubjectNeighborReadModel;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.venue.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `PublicationNeighborReadAdapter` 集成测试：最新收录、同主题邻居（analytics PAP-170 的补位）。
///
/// 用小的显式主键：主键倒序就是收录时间倒序（雪花 ID）。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PublicationNeighborReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("PublicationNeighborReadAdapter 最新收录与同主题邻居集成测试")
class PublicationNeighborReadAdapterIT {

  private static final Instant SYNCED = Instant.parse("2026-05-01T00:00:00Z");
  private static final long X = 1_000L;

  @Autowired private PublicationNeighborReadAdapter adapter;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("最新收录：主键倒序，去掉软删与撤稿")
  void latest_ids_are_newest_first_without_deleted_or_retracted() {
    persist(10L, null);
    persist(20L, null);
    persist(30L, null);
    persist(40L, null);
    flush();
    softDelete(30L);
    retractByType(40L);

    assertThat(adapter.findLatestIds(10)).containsExactly(20L, 10L);
    assertThat(adapter.findLatestIds(1)).containsExactly(20L);
  }

  @Test
  @DisplayName("同主题：多个主要主题词之间轮流取，词内按收录时间新的在前，meshTerm 是主题词原文")
  void neighbors_round_robin_across_major_descriptors() {
    descriptor("D000001", "Sepsis");
    descriptor("D000002", "Shock, Septic");
    persist(X, null);
    persist(101L, null);
    persist(102L, null);
    persist(103L, null);
    persist(201L, null);
    persist(202L, null);
    flush();
    heading(X, "D000001", true, 1);
    heading(X, "D000002", true, 2);
    for (long id : List.of(101L, 102L, 103L)) {
      heading(id, "D000001", true, 1);
    }
    for (long id : List.of(201L, 202L)) {
      heading(id, "D000002", true, 1);
    }

    List<SubjectNeighborReadModel> rows = adapter.findSubjectNeighbors(X, 4);

    assertThat(rows)
        .extracting(SubjectNeighborReadModel::id, SubjectNeighborReadModel::meshTerm)
        .containsExactly(
            tuple(103L, "Sepsis"),
            tuple(202L, "Shock, Septic"),
            tuple(102L, "Sepsis"),
            tuple(201L, "Shock, Septic"));
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.reason()).isEqualTo(SubjectNeighborReason.SAME_MESH);
              assertThat(r.venueName()).isNull();
            });
    assertThat(rows.getFirst().meshDescriptorUi()).isEqualTo("D000001");
  }

  @Test
  @DisplayName("主要主题：major 标在副主题词上也算，两边都按这个算；对方只是一般主题词不算")
  void major_on_qualifier_counts_on_both_sides() {
    descriptor("D000003", "Pneumonia");
    persist(X, null);
    persist(301L, null);
    persist(302L, null);
    persist(303L, null);
    flush();
    qualifier(heading(X, "D000003", false, 1), true);
    qualifier(heading(301L, "D000003", false, 1), true);
    qualifier(heading(302L, "D000003", false, 1), false);
    heading(303L, "D000003", false, 1);

    assertThat(adapter.findSubjectNeighbors(X, 10))
        .extracting(SubjectNeighborReadModel::id)
        .containsExactly(301L);
  }

  @Test
  @DisplayName("同期刊补在同主题之后，同一篇只出现一次（以同主题为准）")
  void venue_fills_after_mesh_and_skips_duplicates() {
    descriptor("D000001", "Sepsis");
    Long venue = saveVenue("Chest");
    persist(X, venue);
    persist(401L, venue);
    persist(402L, venue);
    flush();
    heading(X, "D000001", true, 1);
    heading(402L, "D000001", true, 1);

    List<SubjectNeighborReadModel> rows = adapter.findSubjectNeighbors(X, 10);

    assertThat(rows)
        .extracting(
            SubjectNeighborReadModel::id,
            SubjectNeighborReadModel::reason,
            SubjectNeighborReadModel::meshTerm,
            SubjectNeighborReadModel::venueName)
        .containsExactly(
            tuple(402L, SubjectNeighborReason.SAME_MESH, "Sepsis", null),
            tuple(401L, SubjectNeighborReason.SAME_VENUE, null, "Chest"));
  }

  @Test
  @DisplayName("去掉自己、软删、撤稿（发表类型与全文原因两种）")
  void excludes_self_deleted_and_retracted() {
    descriptor("D000001", "Sepsis");
    Long venue = saveVenue("Chest");
    persist(X, venue);
    persist(501L, venue);
    persist(502L, venue);
    persist(503L, venue);
    persist(504L, venue);
    flush();
    heading(X, "D000001", true, 1);
    for (long id : List.of(501L, 502L, 503L)) {
      heading(id, "D000001", true, 1);
    }
    softDelete(501L);
    retractByType(502L);
    retractByFulltext(504L);

    assertThat(adapter.findSubjectNeighbors(X, 10))
        .extracting(SubjectNeighborReadModel::id)
        .containsExactly(503L);
  }

  @Test
  @DisplayName("X 不存在或已软删：没有邻居")
  void missing_or_deleted_publication_has_no_neighbors() {
    descriptor("D000001", "Sepsis");
    Long venue = saveVenue("Chest");
    persist(X, venue);
    persist(601L, venue);
    flush();
    heading(X, "D000001", true, 1);
    heading(601L, "D000001", true, 1);

    assertThat(adapter.findSubjectNeighbors(999_999L, 10)).isEmpty();
    softDelete(X);
    assertThat(adapter.findSubjectNeighbors(X, 10)).isEmpty();
  }

  // ===== fixtures =====

  private void flush() {
    em.flush();
    em.clear();
  }

  private void persist(long id, Long venueId) {
    em.persist(
        PublicationEntity.builder()
            .id(id)
            .provenanceCode("PUBMED")
            .title("Paper " + id)
            .publicationYear(2024)
            .venueId(venueId)
            .venueInstanceId(SnowflakeIdGenerator.getId())
            .isOa(false)
            .authorsComplete(true)
            .citationCount(0)
            .lastSyncedAt(SYNCED)
            .build());
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

  private void descriptor(String ui, String name) {
    native_(
        "INSERT INTO cat_mesh_descriptor (id, ui, name) VALUES (?1, ?2, ?3)",
        SnowflakeIdGenerator.getId(),
        ui,
        name);
  }

  private long heading(long publicationId, String ui, boolean major, int order) {
    long id = SnowflakeIdGenerator.getId();
    native_(
        "INSERT INTO cat_publication_mesh_heading (id, publication_id, descriptor_ui,"
            + " is_major_topic, heading_order) VALUES (?1, ?2, ?3, ?4, ?5)",
        id,
        publicationId,
        ui,
        major,
        order);
    return id;
  }

  private void qualifier(long headingId, boolean major) {
    native_(
        "INSERT INTO cat_publication_mesh_qualifier (id, publication_mesh_heading_id,"
            + " qualifier_ui, is_major_topic, qualifier_order) VALUES (?1, ?2, 'Q000188', ?3, 1)",
        SnowflakeIdGenerator.getId(),
        headingId,
        major);
  }

  private void softDelete(long id) {
    native_("UPDATE cat_publication SET deleted_at = now() WHERE id = ?1", id);
  }

  private void retractByType(long id) {
    native_(
        "INSERT INTO cat_publication_type (id, publication_id, type_id, type_value,"
            + " vocabulary_source) VALUES (?1, ?2, 'D016441', 'Retracted Publication', 'MESH')",
        SnowflakeIdGenerator.getId(),
        id);
  }

  private void retractByFulltext(long id) {
    native_(
        "INSERT INTO cat_publication_fulltext (id, publication_id, pmcid, status,"
            + " unavailable_reason) VALUES (?1, ?2, 'PMC1', 'UNAVAILABLE', 'RETRACTED')",
        SnowflakeIdGenerator.getId(),
        id);
  }

  private void native_(String sql, Object... params) {
    var query = em.getEntityManager().createNativeQuery(sql);
    for (int i = 0; i < params.length; i++) {
      query.setParameter(i + 1, params[i]);
    }
    query.executeUpdate();
  }
}
