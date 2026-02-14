package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.infra.adapter.persistence.dao.VenueDao;
import com.patra.catalog.infra.adapter.persistence.entity.VenueEntity;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueReadAdapter 集成测试。
///
/// **测试目标**：
///
/// - 仅查询 `venue_type=JOURNAL`
/// - 关键词查询策略（名称前缀 + ISSN/NLM 精确）
/// - 分页元信息与排序规则
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueReadAdapter.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("VenueReadAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueReadAdapterIT {

  @Autowired private VenueReadAdapter venueReadAdapter;

  @Autowired private VenueDao venueDao;

  /// 无关键词时应返回所有期刊并排除非期刊类型。
  @Test
  @DisplayName("无关键词应只返回 JOURNAL 类型")
  void shouldReturnOnlyJournalsWhenKeywordMissing() {
    // Given
    saveVenue("JOURNAL", "Nature", "0028-0836", "0410462");
    saveVenue("JOURNAL", "The Lancet", "0140-6736", "2985213");
    saveVenue("REPOSITORY", "PubMed Central", null, null);

    // When
    PageResult<VenueSummaryReadModel> page =
        venueReadAdapter.findVenuePage(PagingParams.of(1, 20), null);

    // Then
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).hasSize(2);
    assertThat(page.items()).extracting("displayName").containsExactly("The Lancet", "Nature");
  }

  @Nested
  @DisplayName("关键词检索策略")
  class KeywordSearchTests {

    /// 名称前缀匹配应命中 displayName LIKE q%。
    @Test
    @DisplayName("名称前缀匹配")
    void shouldMatchByDisplayNamePrefix() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462");
      saveVenue("JOURNAL", "Science", "0036-8075", "0404511");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 20), "Nat");

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("Nature");
    }

    /// ISSN-L 精确匹配应命中对应期刊。
    @Test
    @DisplayName("ISSN-L 精确匹配")
    void shouldMatchByExactIssnL() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462");
      saveVenue("JOURNAL", "Science", "0036-8075", "0404511");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 20), "0036-8075");

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("Science");
    }

    /// NLM ID 精确匹配应命中对应期刊。
    @Test
    @DisplayName("NLM ID 精确匹配")
    void shouldMatchByExactNlmId() {
      // Given
      saveVenue("JOURNAL", "Nature", "0028-0836", "0410462");
      saveVenue("JOURNAL", "Science", "0036-8075", "0404511");

      // When
      PageResult<VenueSummaryReadModel> page =
          venueReadAdapter.findVenuePage(PagingParams.of(1, 20), "0410462");

      // Then
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items()).singleElement().extracting("displayName").isEqualTo("Nature");
    }
  }

  /// 分页应返回正确元信息，并按更新时间/ID 倒序稳定输出。
  @Test
  @DisplayName("分页元信息与排序应正确")
  void shouldReturnCorrectPagingMetaAndSort() {
    // Given
    VenueEntity v1 = saveVenue("JOURNAL", "Journal-1", "1111-1111", "NLM1");
    VenueEntity v2 = saveVenue("JOURNAL", "Journal-2", "2222-2222", "NLM2");
    VenueEntity v3 = saveVenue("JOURNAL", "Journal-3", "3333-3333", "NLM3");

    Long expectedSecondPageId =
        List.of(v1.getId(), v2.getId(), v3.getId()).stream()
            .sorted((left, right) -> Long.compare(right, left))
            .toList()
            .get(1);

    // When
    PageResult<VenueSummaryReadModel> page =
        venueReadAdapter.findVenuePage(PagingParams.of(2, 1), null);

    // Then
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.pageSize()).isEqualTo(1);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.items()).singleElement().extracting("id").isEqualTo(expectedSecondPageId);
  }

  /// 保存测试用 Venue 实体。
  ///
  /// @param venueType 载体类型
  /// @param displayName 展示名称
  /// @param issnL ISSN-L
  /// @param nlmId NLM ID
  /// @return 持久化后的实体
  private VenueEntity saveVenue(String venueType, String displayName, String issnL, String nlmId) {
    VenueEntity entity = new VenueEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueType(venueType);
    entity.setDisplayName(displayName);
    entity.setIssnL(issnL);
    entity.setNlmId(nlmId);
    entity.setProvenanceCode("OPENALEX");
    entity.setCountryCode("US");
    entity.setLastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"));
    return venueDao.save(entity);
  }
}
