package dev.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationAuthorEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationTypeEntity;
import dev.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/// 按 ID 批量取文献卡片的集成测试：100 个 ID 只发一条 SQL（PAP-109 AC「无 N+1」）。
///
/// 单独成类：监视 `JdbcClient` 会换一个 Spring 测试上下文，不和其他读适配器 IT 共用。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PublicationSearchReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("PublicationSearchReadAdapter 批量卡片集成测试")
class PublicationCardReadIT {

  private static final Instant SYNCED = Instant.parse("2026-05-01T00:00:00Z");

  @Autowired private PublicationSearchReadAdapter adapter;
  @Autowired private TestEntityManager em;
  @MockitoSpyBean private JdbcClient jdbcClient;

  @Test
  @DisplayName("100 篇带载体、作者、类型、摘要的文献 + 1 篇软删：返回 100 张卡，只发 1 条 SQL")
  void should_load_100_cards_with_a_single_statement() {
    Long venueId = saveVenue("N Engl J Med");
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      Long id = persist(publication("Paper " + i).venueId(venueId));
      saveAuthor(id, "Perkovic Vlado", 1);
      saveAuthor(id, "Tuttle Katherine", 2);
      saveType(id, "Journal Article", 1);
      saveType(id, "Randomized Controlled Trial", 2);
      saveAbstract(id, "BACKGROUND: abstract " + i);
      ids.add(id);
    }
    Long deleted = persist(publication("Deleted paper"));
    em.flush();
    em.clear();
    softDelete(deleted);
    ids.add(deleted);

    clearInvocations(jdbcClient);
    List<PortalPaperReadModel> cards = adapter.findCardsByIds(ids);

    verify(jdbcClient, times(1)).sql(anyString());
    assertThat(cards).hasSize(100);
    assertThat(cards).extracting(PortalPaperReadModel::id).doesNotContain(deleted);
    PortalPaperReadModel any = cards.get(0);
    assertThat(any.authors()).containsExactly("Perkovic Vlado", "Tuttle Katherine");
    assertThat(any.studyType()).isEqualTo("Journal Article");
    assertThat(any.venueName()).isEqualTo("N Engl J Med");
    assertThat(any.abstractSnippet()).startsWith("BACKGROUND: abstract");
  }

  @Test
  @DisplayName("不存在的 ID 跳过")
  void should_skip_missing_ids() {
    Long id = persist(publication("Only paper"));
    em.flush();
    em.clear();

    assertThat(adapter.findCardsByIds(List.of(id, 999_999_999L)))
        .extracting(PortalPaperReadModel::id)
        .containsExactly(id);
  }

  @Test
  @DisplayName("空入参返回空列表")
  void should_return_empty_for_empty_input() {
    assertThat(adapter.findCardsByIds(List.of())).isEmpty();
  }

  // ===== fixtures =====

  private void softDelete(Long id) {
    em.getEntityManager()
        .createNativeQuery("UPDATE cat_publication SET deleted_at = now() WHERE id = :id")
        .setParameter("id", id)
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

  private PublicationEntity.PublicationEntityBuilder<?, ?> publication(String title) {
    return PublicationEntity.builder()
        .id(SnowflakeIdGenerator.getId())
        .provenanceCode("PUBMED")
        .title(title)
        .publicationYear(2026)
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

  private void saveAuthor(Long publicationId, String displayName, int order) {
    em.persist(
        PublicationAuthorEntity.builder()
            .id(SnowflakeIdGenerator.getId())
            .publicationId(publicationId)
            .displayName(displayName)
            .authorOrder(order)
            .build());
  }

  private void saveType(Long publicationId, String typeValue, int order) {
    em.persist(
        PublicationTypeEntity.builder()
            .id(SnowflakeIdGenerator.getId())
            .publicationId(publicationId)
            .typeValue(typeValue)
            .typeOrder(order)
            .build());
  }

  private void saveAbstract(Long publicationId, String plainText) {
    PublicationAbstractEntity a = new PublicationAbstractEntity();
    a.setId(SnowflakeIdGenerator.getId());
    a.setPublicationId(publicationId);
    a.setPlainText(plainText);
    em.persist(a);
  }
}
