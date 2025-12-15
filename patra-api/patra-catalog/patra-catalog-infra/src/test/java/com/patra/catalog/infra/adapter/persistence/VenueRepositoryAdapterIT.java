package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.converter.VenueIndexingHistoryConverter;
import com.patra.catalog.infra.persistence.converter.VenueMeshConverter;
import com.patra.catalog.infra.persistence.converter.VenuePublicationStatsConverter;
import com.patra.catalog.infra.persistence.converter.VenueRelationConverter;
import com.patra.catalog.infra.persistence.jpa.VenueIdentifierJpaRepository;
import com.patra.catalog.infra.persistence.jpa.VenueJpaRepository;
import com.patra.catalog.infra.persistence.mapper.VenueIndexingHistoryMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMeshMapper;
import com.patra.catalog.infra.persistence.mapper.VenuePublicationStatsMapper;
import com.patra.catalog.infra.persistence.mapper.VenueRelationMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// VenueRepositoryAdapter 集成测试（JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试 Venue 仓储操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：hasAnyData、insertAll（聚合根批量插入）、findExistingIssnLs、findByIssnLs、findByNlmIds、updateBatch
///
/// **混合架构说明**：
///
/// - 核心聚合（VenueAggregate、VenueIdentifier）使用 JPA
/// - 补充数据（VenuePublicationStats、VenueMesh 等）暂保留 MyBatis，本测试使用 Mock
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueRepositoryAdapter.class, JpaAuditingConfig.class, JacksonAutoConfiguration.class})
@ComponentScan(basePackages = "com.patra.catalog.infra.persistence.jpa.converter")
@ActiveProfiles("test")
@DisplayName("VenueRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueRepositoryAdapterIT {

  @Autowired private VenueRepositoryAdapter repository;

  @Autowired private VenueJpaRepository venueJpaRepository;
  @Autowired private VenueIdentifierJpaRepository identifierJpaRepository;

  // ========== MyBatis 组件 Mock（补充数据功能暂不测试） ==========
  @MockitoBean private VenuePublicationStatsMapper publicationStatsMapper;
  @MockitoBean private VenueMeshMapper meshMapper;
  @MockitoBean private VenueRelationMapper relationMapper;
  @MockitoBean private VenueIndexingHistoryMapper indexingHistoryMapper;
  @MockitoBean private VenuePublicationStatsConverter publicationStatsConverter;
  @MockitoBean private VenueMeshConverter meshConverter;
  @MockitoBean private VenueRelationConverter relationConverter;
  @MockitoBean private VenueIndexingHistoryConverter indexingHistoryConverter;

  // ========== hasAnyData() 测试 ==========

  @Nested
  @DisplayName("hasAnyData() 测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      long count = venueJpaRepository.count();
      assertThat(count).isEqualTo(0);

      // When & Then
      assertThat(repository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      // Given: 通过 insertAll 插入数据
      VenueAggregate venue = createVenueAggregate("S1", "Journal A", 1);
      repository.insertAll(List.of(venue));

      // When & Then
      assertThat(repository.hasAnyData()).isTrue();
    }
  }

  // ========== insertAll() 测试 ==========

  @Nested
  @DisplayName("insertAll() 测试")
  class InsertAllTests {

    @Test
    @DisplayName("应该正确插入多个聚合根（含标识符）")
    void insertAll_shouldInsertAggregatesWithIdentifiers() {
      // Given
      VenueAggregate venue1 = createVenueAggregate("S1", "Journal A", 1);
      venue1.addIdentifier(VenueIdentifierType.ISSN, "1111-1111");

      VenueAggregate venue2 = createVenueAggregate("S2", "Journal B", 2);
      venue2.addIdentifier(VenueIdentifierType.ISSN, "2222-2222");

      // When
      repository.insertAll(List.of(venue1, venue2));

      // Then: 验证主表
      assertThat(venueJpaRepository.count()).isEqualTo(2);

      // Then: 验证标识符子表（每个 Venue 有 1 个 OpenAlex + 1 个 ISSN-L + 1 个 ISSN = 3）
      assertThat(identifierJpaRepository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("空列表不应抛出异常")
    void insertAll_emptyList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.insertAll(List.of())).doesNotThrowAnyException();

      // 验证没有数据插入
      assertThat(venueJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("标识符子表应正确关联到主表")
    void insertAll_shouldSetCorrectVenueIdForIdentifiers() {
      // Given
      VenueAggregate venue = createVenueAggregate("S1", "Journal A", 1);
      venue.addIdentifier(VenueIdentifierType.ISSN, "1234-5678");

      // When
      repository.insertAll(List.of(venue));

      // Then: 获取主表 ID
      var savedVenue = venueJpaRepository.findAll().get(0);
      Long venueId = savedVenue.getId();
      assertThat(venueId).isNotNull();

      // Then: 验证标识符子表的外键
      var identifiers = identifierJpaRepository.findAll();
      assertThat(identifiers).allMatch(i -> i.getVenueId().equals(venueId));
    }

    @Test
    @DisplayName("应该正确处理只有默认标识符的聚合根")
    void insertAll_aggregateWithDefaultIdentifier_shouldInsertCorrectly() {
      // Given: 创建只有 OpenAlex ID 标识符的聚合根（fromOpenAlex 会自动添加）
      VenueAggregate venue = VenueAggregate.fromOpenAlex("S1", VenueType.JOURNAL, "Journal A");

      // When
      repository.insertAll(List.of(venue));

      // Then: 主表有 1 条记录
      assertThat(venueJpaRepository.count()).isEqualTo(1);

      // Then: 标识符只有 1 个（OpenAlex ID 由 fromOpenAlex 自动添加）
      assertThat(identifierJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确回填聚合根 ID")
    void insertAll_shouldBackfillAggregateId() {
      // Given
      VenueAggregate venue = createVenueAggregate("S1", "Journal A", 1);
      assertThat(venue.getId()).isNull();

      // When
      repository.insertAll(List.of(venue));

      // Then: ID 应该被回填
      assertThat(venue.getId()).isNotNull();
      assertThat(venue.getId().value()).isPositive();
    }
  }

  // ========== findExistingIssnLs() 测试 ==========

  @Nested
  @DisplayName("findExistingIssnLs() 测试")
  class FindExistingIssnLsTests {

    @Test
    @DisplayName("空集合输入 - 应该返回空集合")
    void findExistingIssnLs_emptyInput_shouldReturnEmptySet() {
      // When
      Set<String> result = repository.findExistingIssnLs(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 输入 - 应该返回空集合")
    void findExistingIssnLs_nullInput_shouldReturnEmptySet() {
      // When
      Set<String> result = repository.findExistingIssnLs(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("数据库为空 - 应该返回空集合")
    void findExistingIssnLs_emptyDatabase_shouldReturnEmptySet() {
      // Given: 数据库为空
      assertThat(venueJpaRepository.count()).isZero();

      // When
      Set<String> result = repository.findExistingIssnLs(Set.of("1234-5678", "2345-6789"));

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("部分匹配 - 应该只返回存在的 ISSN-L")
    void findExistingIssnLs_partialMatch_shouldReturnOnlyExisting() {
      // Given: 插入两条记录
      VenueAggregate venue1 = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      VenueAggregate venue2 = createVenueAggregateWithIssnL("S2", "Journal B", "2222-2222");
      repository.insertAll(List.of(venue1, venue2));

      // When: 查询 3 个 ISSN-L，其中 2 个存在
      Set<String> result =
          repository.findExistingIssnLs(Set.of("1111-1111", "2222-2222", "3333-3333"));

      // Then: 只返回存在的 2 个
      assertThat(result).containsExactlyInAnyOrder("1111-1111", "2222-2222");
    }

    @Test
    @DisplayName("全匹配 - 应该返回所有查询的 ISSN-L")
    void findExistingIssnLs_allMatch_shouldReturnAll() {
      // Given
      VenueAggregate venue1 = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      VenueAggregate venue2 = createVenueAggregateWithIssnL("S2", "Journal B", "2222-2222");
      repository.insertAll(List.of(venue1, venue2));

      // When
      Set<String> result = repository.findExistingIssnLs(Set.of("1111-1111", "2222-2222"));

      // Then
      assertThat(result).containsExactlyInAnyOrder("1111-1111", "2222-2222");
    }

    @Test
    @DisplayName("无匹配 - 应该返回空集合")
    void findExistingIssnLs_noMatch_shouldReturnEmptySet() {
      // Given: 插入数据
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      repository.insertAll(List.of(venue));

      // When: 查询不存在的 ISSN-L
      Set<String> result = repository.findExistingIssnLs(Set.of("9999-9999", "8888-8888"));

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ========== findByIssnLs() 测试 ==========

  @Nested
  @DisplayName("findByIssnLs() 测试")
  class FindByIssnLsTests {

    @Test
    @DisplayName("空集合输入 - 应该返回空 Map")
    void findByIssnLs_emptyInput_shouldReturnEmptyMap() {
      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 输入 - 应该返回空 Map")
    void findByIssnLs_nullInput_shouldReturnEmptyMap() {
      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("部分匹配 - 应该只返回存在的聚合根")
    void findByIssnLs_partialMatch_shouldReturnOnlyExisting() {
      // Given
      VenueAggregate venue1 = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      VenueAggregate venue2 = createVenueAggregateWithIssnL("S2", "Journal B", "2222-2222");
      repository.insertAll(List.of(venue1, venue2));

      // When
      Map<String, VenueAggregate> result =
          repository.findByIssnLs(Set.of("1111-1111", "3333-3333"));

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("1111-1111").getDisplayName()).isEqualTo("Journal A");
    }

    @Test
    @DisplayName("应该正确重建聚合根（含标识符）")
    void findByIssnLs_shouldReconstructAggregateWithIdentifiers() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      venue.addIdentifier(VenueIdentifierType.ISSN, "1234-5678");
      venue.addIdentifier(VenueIdentifierType.NLM, "NLM123");
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      assertThat(result).hasSize(1);
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getIdentifiers()).hasSize(4); // OpenAlex + ISSN-L + ISSN + NLM
      assertThat(found.getIdentifier(VenueIdentifierType.ISSN_L)).contains("1111-1111");
      assertThat(found.getIdentifier(VenueIdentifierType.NLM)).contains("NLM123");
    }
  }

  // ========== findByNlmIds() 测试 ==========

  @Nested
  @DisplayName("findByNlmIds() 测试")
  class FindByNlmIdsTests {

    @Test
    @DisplayName("空集合输入 - 应该返回空 Map")
    void findByNlmIds_emptyInput_shouldReturnEmptyMap() {
      // When
      Map<String, VenueAggregate> result = repository.findByNlmIds(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("部分匹配 - 应该只返回存在的聚合根")
    void findByNlmIds_partialMatch_shouldReturnOnlyExisting() {
      // Given
      VenueAggregate venue1 = createVenueAggregateWithNlmId("S1", "Journal A", "NLM001");
      VenueAggregate venue2 = createVenueAggregateWithNlmId("S2", "Journal B", "NLM002");
      repository.insertAll(List.of(venue1, venue2));

      // When
      Map<String, VenueAggregate> result = repository.findByNlmIds(Set.of("NLM001", "NLM999"));

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("NLM001").getDisplayName()).isEqualTo("Journal A");
    }
  }

  // ========== findByIssns() 测试 ==========

  @Nested
  @DisplayName("findByIssns() 测试")
  class FindByIssnsTests {

    @Test
    @DisplayName("空集合输入 - 应该返回空 Map")
    void findByIssns_emptyInput_shouldReturnEmptyMap() {
      // When
      Map<String, VenueAggregate> result = repository.findByIssns(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该同时匹配 ISSN 和 ISSN-L")
    void findByIssns_shouldMatchBothIssnAndIssnL() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      venue.addIdentifier(VenueIdentifierType.ISSN, "2222-2222");
      repository.insertAll(List.of(venue));

      // When: 用 ISSN 查找
      Map<String, VenueAggregate> resultByIssn = repository.findByIssns(Set.of("2222-2222"));

      // Then
      assertThat(resultByIssn).hasSize(1);
      assertThat(resultByIssn.get("2222-2222").getDisplayName()).isEqualTo("Journal A");

      // When: 用 ISSN-L 查找
      Map<String, VenueAggregate> resultByIssnL = repository.findByIssns(Set.of("1111-1111"));

      // Then
      assertThat(resultByIssnL).hasSize(1);
      assertThat(resultByIssnL.get("1111-1111").getDisplayName()).isEqualTo("Journal A");
    }
  }

  // ========== updateBatch() 测试 ==========

  @Nested
  @DisplayName("updateBatch() 测试")
  class UpdateBatchTests {

    @Test
    @DisplayName("空列表 - 应该不抛出异常")
    void updateBatch_emptyList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.updateBatch(List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null - 应该不抛出异常")
    void updateBatch_null_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.updateBatch(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("更新嵌入式值对象 - 应该正确持久化")
    void updateBatch_withEmbeddedValueObjects_shouldPersist() {
      // Given: 插入聚合根
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      repository.insertAll(List.of(venue));

      // 重新查询以获取托管实体
      Map<String, VenueAggregate> found = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate retrievedVenue = found.get("1111-1111");

      // When: 更新嵌入式值对象
      retrievedVenue.withPublicationProfile(
          PublicationProfile.builder()
              .abbreviatedTitle("J A")
              .alternateTitles(List.of("Alternate Title"))
              .countryCode("US")
              .frequency("Monthly")
              .publicationHistory(PublicationHistory.active(1990))
              .build());
      retrievedVenue.withCitationMetrics(
          CitationMetrics.of(1000, 50000, 100, 200, new BigDecimal("5.123")));
      retrievedVenue.withOpenAccess(OpenAccessInfo.of(true, true, "gold", 3000, List.of()));

      repository.updateBatch(List.of(retrievedVenue));

      // Then: 验证更新
      Map<String, VenueAggregate> updatedResult = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate updatedVenue = updatedResult.get("1111-1111");

      assertThat(updatedVenue.getPublicationProfile()).isNotNull();
      assertThat(updatedVenue.getPublicationProfile().abbreviatedTitle()).isEqualTo("J A");
      assertThat(updatedVenue.getCitationMetrics()).isNotNull();
      assertThat(updatedVenue.getCitationMetrics().worksCount()).isEqualTo(1000);
      assertThat(updatedVenue.getOpenAccess()).isNotNull();
      assertThat(updatedVenue.getOpenAccess().isOa()).isTrue();
    }

    @Test
    @DisplayName("新增标识符 - 应该正确持久化")
    void updateBatch_addIdentifier_shouldPersist() {
      // Given: 插入聚合根
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      repository.insertAll(List.of(venue));

      int initialCount = (int) identifierJpaRepository.count();

      // 重新查询
      Map<String, VenueAggregate> found = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate retrievedVenue = found.get("1111-1111");

      // When: 添加新标识符
      retrievedVenue.addIdentifier(VenueIdentifierType.NLM, "NLM123");

      repository.updateBatch(List.of(retrievedVenue));

      // Then: 验证标识符被添加
      assertThat(identifierJpaRepository.count()).isEqualTo(initialCount + 1);

      Map<String, VenueAggregate> updatedResult = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate updatedVenue = updatedResult.get("1111-1111");
      assertThat(updatedVenue.getIdentifier(VenueIdentifierType.NLM)).contains("NLM123");
    }

    @Test
    @DisplayName("删除标识符 - 应该正确持久化")
    void updateBatch_removeIdentifier_shouldPersist() {
      // Given: 插入带多个标识符的聚合根
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      venue.addIdentifier(VenueIdentifierType.NLM, "NLM123");
      repository.insertAll(List.of(venue));

      int initialCount = (int) identifierJpaRepository.count();

      // 重新查询
      Map<String, VenueAggregate> found = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate retrievedVenue = found.get("1111-1111");

      // When: 删除 NLM 标识符
      retrievedVenue.removeIdentifier(VenueIdentifierType.NLM, "NLM123");

      repository.updateBatch(List.of(retrievedVenue));

      // Then: 验证标识符被删除
      assertThat(identifierJpaRepository.count()).isEqualTo(initialCount - 1);

      Map<String, VenueAggregate> updatedResult = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate updatedVenue = updatedResult.get("1111-1111");
      assertThat(updatedVenue.getIdentifier(VenueIdentifierType.NLM)).isEmpty();
    }
  }

  // ========== JSON 嵌入式值对象测试 ==========

  @Nested
  @DisplayName("JSON 嵌入式值对象测试")
  class JsonEmbeddedValueObjectTests {

    @Test
    @DisplayName("PublicationProfile - 应该正确持久化和重建")
    void publicationProfile_shouldPersistAndReconstruct() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      venue.withPublicationProfile(
          PublicationProfile.builder()
              .abbreviatedTitle("J A")
              .alternateTitles(List.of("Journal Alpha", "J. A."))
              .languages(VenueLanguages.ofSingleLanguage("eng"))
              .countryCode("USA")
              .frequency("Monthly")
              .publicationHistory(PublicationHistory.active(1990))
              .build());
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getPublicationProfile()).isNotNull();
      assertThat(found.getPublicationProfile().abbreviatedTitle()).isEqualTo("J A");
      assertThat(found.getPublicationProfile().alternateTitles())
          .containsExactly("Journal Alpha", "J. A.");
      assertThat(found.getPublicationProfile().countryCode()).isEqualTo("USA");
    }

    @Test
    @DisplayName("CitationMetrics - 应该正确持久化和重建")
    void citationMetrics_shouldPersistAndReconstruct() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      venue.withCitationMetrics(
          CitationMetrics.of(
              500, // worksCount
              25000, // citedByCount
              75, // hIndex
              120, // i10Index
              new BigDecimal("3.456") // twoYearMeanCitedness
              ));
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getCitationMetrics()).isNotNull();
      assertThat(found.getCitationMetrics().worksCount()).isEqualTo(500);
      assertThat(found.getCitationMetrics().hIndex()).isEqualTo(75);
      assertThat(found.getCitationMetrics().twoYearMeanCitedness())
          .isEqualByComparingTo(new BigDecimal("3.456"));
    }

    @Test
    @DisplayName("OpenAccessInfo - 应该正确持久化和重建")
    void openAccessInfo_shouldPersistAndReconstruct() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      venue.withOpenAccess(
          OpenAccessInfo.of(
              false, // isOa
              true, // isInDoaj
              "hybrid", // oaType
              2500, // apcUsd
              List.of() // apcPrices
              ));
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getOpenAccess()).isNotNull();
      assertThat(found.getOpenAccess().isOa()).isFalse();
      assertThat(found.getOpenAccess().oaType()).isEqualTo("hybrid");
    }

    @Test
    @DisplayName("AffiliatedSocieties - 应该正确持久化和重建")
    void affiliatedSocieties_shouldPersistAndReconstruct() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      venue.withAffiliatedSocieties(
          List.of(
              Society.of("https://www.som.org", "Society of Medicine"),
              Society.of("https://www.mrc.org.uk", "Medical Research Council")));
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getAffiliatedSocieties()).hasSize(2);
      assertThat(found.getAffiliatedSocieties().get(0).organization())
          .isEqualTo("Society of Medicine");
      assertThat(found.getAffiliatedSocieties().get(1).organization())
          .isEqualTo("Medical Research Council");
    }

    @Test
    @DisplayName("ProvenanceInfo - 应该正确持久化和重建")
    void provenanceInfo_shouldPersistAndReconstruct() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("S1", "Journal A", "1111-1111");
      LocalDate now = LocalDate.now();
      venue.withProvenance(
          ProvenanceInfo.of(
              ProvenanceCode.OPENALEX.name(),
              now.minusDays(30), // sourceCreatedDate
              now.minusDays(1), // sourceUpdatedDate
              Instant.now() // lastSyncedAt
              ));
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getProvenance()).isNotNull();
      assertThat(found.getProvenance().code()).isEqualTo(ProvenanceCode.OPENALEX.name());
      assertThat(found.getProvenance().sourceCreatedDate()).isNotNull();
      assertThat(found.getProvenance().lastSyncedAt()).isNotNull();
    }
  }

  // ========== 工厂方法 ==========

  /// 创建测试用的 VenueAggregate。
  ///
  /// @param suffix 数字后缀（1-9），用于生成唯一的 ISSN-L（格式：0001-000X）
  private VenueAggregate createVenueAggregate(String openalexId, String displayName, int suffix) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    // 生成符合格式的 ISSN-L：0001-000X
    String issnL = String.format("0001-00%02d", suffix);
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));
    return venue;
  }

  /// 创建测试用的 VenueAggregate（指定 ISSN-L）。
  private VenueAggregate createVenueAggregateWithIssnL(
      String openalexId, String displayName, String issnL) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));
    return venue;
  }

  /// 创建测试用的 VenueAggregate（指定 NLM ID）。
  private VenueAggregate createVenueAggregateWithNlmId(
      String openalexId, String displayName, String nlmId) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.addIdentifier(new VenueIdentifier(VenueIdentifierType.NLM, nlmId));
    return venue;
  }
}
