package dev.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel;
import dev.patra.catalog.domain.model.read.portal.VenueDetailReadModel.JcrRatingView;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import dev.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueDetailReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("VenueDetailReadAdapter 期刊详情查询集成测试")
class VenueDetailReadAdapterIT {

  @Autowired private VenueDetailReadAdapter adapter;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("按 ID 查询期刊详情，返回 present + 标量字段正确")
  void shouldReturnVenueDetailWhenExists() {
    Long venueId = saveJournal("Nature", "Nat");
    saveJcr(venueId, 2024, new BigDecimal("60.0"), "Q1");
    saveJcr(venueId, 2025, new BigDecimal("69.5"), "Q1");
    em.flush();
    em.clear();

    Optional<VenueDetailReadModel> result = adapter.findById(venueId);

    assertThat(result).isPresent();
    VenueDetailReadModel model = result.get();
    assertThat(model.title()).isEqualTo("Nature");
    assertThat(model.abbreviatedTitle()).isEqualTo("Nat");
    assertThat(model.venueType()).isEqualTo("JOURNAL");
  }

  @Test
  @DisplayName("最新年 JCR 影响因子写入 impactFactor 标量字段")
  void shouldUseLatestJcrImpactFactor() {
    Long venueId = saveJournal("Science", "Sci");
    saveJcr(venueId, 2024, new BigDecimal("50.0"), "Q1");
    saveJcr(venueId, 2025, new BigDecimal("56.9"), "Q1"); // 最新年应取这条
    em.flush();
    em.clear();

    VenueDetailReadModel model = adapter.findById(venueId).orElseThrow();

    assertThat(model.impactFactor()).isEqualByComparingTo("56.9");
  }

  @Test
  @DisplayName("jcrRatings 列表按年份降序，包含所有年度记录，quartile 来自 jif_quartile 字段")
  void shouldReturnJcrRatingsSortedByYearDesc() {
    Long venueId = saveJournal("Cell", "Cell");
    saveJcr(venueId, 2023, new BigDecimal("45.0"), "Q1");
    saveJcr(venueId, 2025, new BigDecimal("66.8"), "Q2");
    em.flush();
    em.clear();

    VenueDetailReadModel model = adapter.findById(venueId).orElseThrow();

    assertThat(model.jcrRatings()).hasSize(2);
    assertThat(model.jcrRatings())
        .extracting(JcrRatingView::year)
        .containsExactly(2025, 2023); // DESC
    assertThat(model.jcrRatings().get(0).quartile()).isEqualTo("Q2"); // jif_quartile 字段
  }

  @Test
  @DisplayName("ID 不存在时返回 empty")
  void shouldReturnEmptyWhenNotFound() {
    Optional<VenueDetailReadModel> result = adapter.findById(Long.MAX_VALUE);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("非 JOURNAL 类型 venue findById 返回 empty（期刊详情端点不对外暴露非期刊载体）")
  void shouldReturnEmptyForNonJournalVenue() {
    VenueEntity conference = new VenueEntity();
    conference.setId(SnowflakeIdGenerator.getId());
    conference.setVenueType("CONFERENCE");
    conference.setTitle("IEEE Conference");
    conference.setAbbreviatedTitle("IEEE Conf");
    conference.setProvenanceCode("OPENALEX");
    em.persist(conference);
    em.flush();
    em.clear();

    Optional<VenueDetailReadModel> result = adapter.findById(conference.getId());

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("软删除后 findById 返回 empty")
  void shouldReturnEmptyWhenSoftDeleted() {
    Long venueId = saveJournal("PLOS ONE", "PLOS ONE");
    em.flush();
    em.clear();

    VenueEntity venue = em.find(VenueEntity.class, venueId);
    em.remove(venue); // Hibernate @SoftDelete 自动转为 UPDATE deleted_at = now()
    em.flush();
    em.clear();

    Optional<VenueDetailReadModel> result = adapter.findById(venueId);
    assertThat(result).isEmpty();
  }

  // ===== 测试数据构建助手 =====

  private Long saveJournal(String title, String abbr) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle(title);
    v.setAbbreviatedTitle(abbr);
    v.setProvenanceCode("OPENALEX");
    em.persist(v);
    return v.getId();
  }

  private void saveJcr(Long venueId, int year, BigDecimal impactFactor, String quartile) {
    JcrRatingEntity r = new JcrRatingEntity();
    r.setId(SnowflakeIdGenerator.getId());
    r.setVenueId(venueId);
    r.setYear((short) year);
    r.setImpactFactor(impactFactor);
    r.setJifQuartile(quartile);
    em.persist(r);
  }
}
