package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueEnrichmentReadAdapter 集成测试（JPA + Testcontainers MySQL）。
///
/// **测试策略**：
/// - `@DataJpaTest` 仅装配 JPA 相关 bean；通过 `@Import` 显式加入被测 Adapter
/// - 每个测试方法独立，`@Transactional` 自动回滚保证隔离
/// - 测试数据直接用 `VenueDao.save(VenueEntity)` / `JcrRatingDao.save(...)` 构造，
///   不走聚合根路径，避免引入 `VenueRepositoryAdapter` 依赖
///
/// **覆盖场景**：
/// - `findNeedingLetPubEnrichment`：NOT EXISTS 过滤、keyset 前进、citedByCount 下限、
///   ISSN-L 为空跳过、limit 生效、cover key 投影（6 个测试）
/// - `findNeedingScopusEnrichment`：NOT EXISTS 对接 `cat_venue_scopus_rating` 表（2 个代表性测试，验证关键差异）
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueEnrichmentReadAdapter.class, JpaAuditingConfig.class, JacksonAutoConfiguration.class})
@ComponentScan(basePackages = "com.patra.catalog.infra.persistence.converter")
@ActiveProfiles("test")
@DisplayName("VenueEnrichmentReadAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueEnrichmentReadAdapterIT {

  @Autowired VenueEnrichmentReadAdapter adapter;
  @Autowired VenueDao venueDao;
  @Autowired JcrRatingDao jcrRatingDao;
  @Autowired ScopusRatingDao scopusRatingDao;

  // ========== findNeedingLetPubEnrichment ==========

  @Nested
  @DisplayName("findNeedingLetPubEnrichment 测试")
  class FindNeedingLetPubEnrichmentTests {

    @Test
    @DisplayName("NOT EXISTS 过滤 - 排除已有目标年份 JCR 评级的期刊")
    void returnsVenuesMissingTargetYear() {
      VenueEntity v1 = persistJournal("1000-0001", 100);
      VenueEntity v2 = persistJournal("1000-0002", 200);
      VenueEntity v3 = persistJournal("1000-0003", 300);
      persistJcrRating(v2.getId(), (short) 2025);

      List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50);

      assertThat(result).extracting(VenueSnapshot::id).containsExactly(v1.getId(), v3.getId());
    }

    @Test
    @DisplayName("keyset 游标 - 只返回 id > lastId 的 venue")
    void keysetAdvancesBeyondLastId() {
      VenueEntity v1 = persistJournal("2000-0001", 0);
      VenueEntity v2 = persistJournal("2000-0002", 0);

      List<VenueSnapshot> result =
          adapter.findNeedingLetPubEnrichment((short) 2025, 0, v1.getId(), 50);

      assertThat(result).extracting(VenueSnapshot::id).containsExactly(v2.getId());
    }

    @Test
    @DisplayName("citedByCount 下限 - 被引次数不足的 venue 被过滤")
    void respectsMinCitedByCount() {
      persistJournal("3000-0001", 50);
      VenueEntity v2 = persistJournal("3000-0002", 150);

      List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment((short) 2025, 100, 0L, 50);

      assertThat(result).extracting(VenueSnapshot::id).containsExactly(v2.getId());
    }

    @Test
    @DisplayName("ISSN-L 为空 - 该 venue 被跳过")
    void skipsVenuesWithoutIssnL() {
      persistJournal(null, 500);
      VenueEntity v2 = persistJournal("4000-0002", 500);

      List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50);

      assertThat(result).extracting(VenueSnapshot::id).containsExactly(v2.getId());
    }

    @Test
    @DisplayName("limit 生效 - 返回不超过指定数量")
    void respectsLimit() {
      for (int i = 0; i < 10; i++) persistJournal(String.format("5000-%04d", i), 0);

      List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 3);

      assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("cover key 投影 - 已存在封面的 venue 在 snapshot 里带回 existingCoverKey")
    void projectsExistingCoverKey() {
      VenueEntity v1 = persistJournalWithCover("8000-0001", "catalog/venue-cover/111.jpg");
      VenueEntity v2 = persistJournal("8000-0002", 0); // 无封面

      List<VenueSnapshot> result = adapter.findNeedingLetPubEnrichment((short) 2025, 0, 0L, 50);

      assertThat(result)
          .filteredOn(s -> s.id().equals(v1.getId()))
          .singleElement()
          .extracting(VenueSnapshot::existingCoverKey)
          .isEqualTo("catalog/venue-cover/111.jpg");
      assertThat(result)
          .filteredOn(s -> s.id().equals(v2.getId()))
          .singleElement()
          .extracting(VenueSnapshot::existingCoverKey)
          .isNull();
    }
  }

  // ========== findNeedingScopusEnrichment ==========

  @Nested
  @DisplayName("findNeedingScopusEnrichment 测试")
  class FindNeedingScopusEnrichmentTests {

    @Test
    @DisplayName("NOT EXISTS 对接 Scopus 表 - 排除已有目标年份 Scopus 评级的期刊")
    void returnsVenuesMissingScopusTargetYear() {
      VenueEntity v1 = persistJournal("7000-0001", 100);
      VenueEntity v2 = persistJournal("7000-0002", 200);
      persistScopusRating(v2.getId(), (short) 2025);

      List<VenueSnapshot> result = adapter.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50);

      assertThat(result).extracting(VenueSnapshot::id).containsExactly(v1.getId());
    }

    @Test
    @DisplayName("与 JCR 表无关 - Scopus 查询不受 JCR 评级影响")
    void scopusQueryIgnoresJcrRatings() {
      VenueEntity v1 = persistJournal("7100-0001", 100);
      persistJcrRating(v1.getId(), (short) 2025); // JCR 已存在，但 Scopus 不应过滤

      List<VenueSnapshot> result = adapter.findNeedingScopusEnrichment((short) 2025, 0, 0L, 50);

      assertThat(result).extracting(VenueSnapshot::id).containsExactly(v1.getId());
    }
  }

  // ========== 辅助方法 ==========

  private VenueEntity persistJournal(String issnL, int citedByCount) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle("Test Journal " + (issnL == null ? "no-issn" : issnL));
    v.setProvenanceCode(ProvenanceCode.OPENALEX.getCode());
    v.setIssnL(issnL);
    v.setCitedByCount(citedByCount);
    return venueDao.save(v);
  }

  private VenueEntity persistJournalWithCover(String issnL, String coverKey) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle("Test Journal " + issnL);
    v.setProvenanceCode(ProvenanceCode.OPENALEX.getCode());
    v.setIssnL(issnL);
    v.setCitedByCount(0);
    v.setImageObjectKey(coverKey);
    return venueDao.save(v);
  }

  private void persistJcrRating(Long venueId, short year) {
    JcrRatingEntity r = new JcrRatingEntity();
    r.setId(SnowflakeIdGenerator.getId());
    r.setVenueId(venueId);
    r.setYear(year);
    jcrRatingDao.save(r);
  }

  private void persistScopusRating(Long venueId, short year) {
    ScopusRatingEntity r = new ScopusRatingEntity();
    r.setId(SnowflakeIdGenerator.getId());
    r.setVenueId(venueId);
    r.setYear(year);
    scopusRatingDao.save(r);
  }
}
