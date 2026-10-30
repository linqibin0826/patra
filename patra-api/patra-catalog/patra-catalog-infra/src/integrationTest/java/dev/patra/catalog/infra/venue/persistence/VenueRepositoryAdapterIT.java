package dev.patra.catalog.infra.venue.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.kernel.model.enums.ProvenanceCode;
import dev.patra.catalog.domain.venue.model.aggregate.VenueAggregate;
import dev.patra.catalog.domain.venue.model.enums.VenueIdentifierType;
import dev.patra.catalog.domain.venue.model.vo.CitationMetrics;
import dev.patra.catalog.domain.venue.model.vo.OpenAccessInfo;
import dev.patra.catalog.domain.venue.model.vo.ProvenanceInfo;
import dev.patra.catalog.domain.venue.model.vo.PublicationHistory;
import dev.patra.catalog.domain.venue.model.vo.PublicationProfile;
import dev.patra.catalog.domain.venue.model.vo.Society;
import dev.patra.catalog.domain.venue.model.vo.VenueIdentifier;
import dev.patra.catalog.domain.venue.model.vo.VenueLanguages;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.venue.persistence.dao.VenueDao;
import dev.patra.catalog.infra.venue.persistence.dao.VenueIdentifierDao;
import dev.patra.catalog.infra.venue.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueRepositoryAdapter 集成测试（纯 JPA 版本）。
///
/// 使用 Testcontainers + PostgreSQL 17 测试 Venue 仓储操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 PostgreSQL 容器
/// - 测试覆盖：hasAnyData、insertAll（聚合根批量插入）、findExistingIssnLs、findByIssnLs、findByNlmIds、updateBatch
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class,
  JsonColumnCodec.class
})
@ComponentScan(
    basePackages = {
      "dev.patra.catalog.infra.*.persistence.mapper",
      "dev.patra.catalog.infra.*.persistence.converter"
    })
@ActiveProfiles("test")
@DisplayName("VenueRepositoryAdapter 集成测试（JPA）")
class VenueRepositoryAdapterIT {

  @Autowired private VenueRepositoryAdapter repository;

  @Autowired private VenueDao venueDao;
  @Autowired private VenueIdentifierDao identifierDao;
  @Autowired private TestEntityManager em;

  // ========== hasAnyData() 测试 ==========

  @Nested
  @DisplayName("hasAnyData() 测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      long count = venueDao.count();
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
      assertThat(venueDao.count()).isEqualTo(2);

      // Then: 验证标识符子表（每个 Venue 有 1 个 NLM + 1 个 ISSN-L + 1 个 ISSN = 3）
      assertThat(identifierDao.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("空列表不应抛出异常")
    void insertAll_emptyList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.insertAll(List.of())).doesNotThrowAnyException();

      // 验证没有数据插入
      assertThat(venueDao.count()).isZero();
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
      var savedVenue = venueDao.findAll().get(0);
      Long venueId = savedVenue.getId();
      assertThat(venueId).isNotNull();

      // Then: 验证标识符子表的外键
      var identifiers = identifierDao.findAll();
      assertThat(identifiers).allMatch(i -> i.getVenueId().equals(venueId));
    }

    @Test
    @DisplayName("应该正确处理只有 NLM ID 标识符的聚合根")
    void insertAll_aggregateWithSingleIdentifier_shouldInsertCorrectly() {
      // Given: 创建只有 NLM ID 标识符的聚合根
      VenueAggregate venue = VenueAggregate.fromPubMed("Journal A", "NLM001", null);

      // When
      repository.insertAll(List.of(venue));

      // Then: 主表有 1 条记录
      assertThat(venueDao.count()).isEqualTo(1);

      // Then: 标识符只有 1 个（NLM ID）
      assertThat(identifierDao.count()).isEqualTo(1);
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
      assertThat(venueDao.count()).isZero();

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
      assertThat(result.get("1111-1111").getTitle()).isEqualTo("Journal A");
    }

    @Test
    @DisplayName("应该正确重建聚合根（含标识符）")
    void findByIssnLs_shouldReconstructAggregateWithIdentifiers() {
      // Given
      VenueAggregate venue = createVenueAggregateWithIssnL("NLM001", "Journal A", "1111-1111");
      venue.addIdentifier(VenueIdentifierType.ISSN, "1234-5678");
      venue.addIdentifier(VenueIdentifierType.OPENALEX, "S123");
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      assertThat(result).hasSize(1);
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getIdentifiers()).hasSize(4); // NLM + ISSN-L + ISSN + OPENALEX
      assertThat(found.getIdentifier(VenueIdentifierType.ISSN_L)).contains("1111-1111");
      assertThat(found.getIdentifier(VenueIdentifierType.NLM)).contains("NLM001");
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
      assertThat(result.get("NLM001").getTitle()).isEqualTo("Journal A");
    }

    @Test
    @DisplayName("同一期刊的主、副 NLM 一起查时两个都返回，指向同一期刊")
    void should_return_every_requested_nlm_of_a_merged_venue() {
      repository.insertAll(List.of(VenueAggregate.fromPubMed("J", "0404401", "0036-7672")));
      em.flush();
      em.clear();
      VenueAggregate venue = repository.findByNlmIds(Set.of("0404401")).get("0404401");
      venue.addIdentifier(VenueIdentifier.forNlm("100970884"));
      repository.updateBatch(List.of(venue));
      em.flush();
      em.clear();

      Map<String, VenueAggregate> result = repository.findByNlmIds(Set.of("0404401", "100970884"));

      assertThat(result).containsOnlyKeys("0404401", "100970884");
      assertThat(result.get("100970884").getId()).isEqualTo(result.get("0404401").getId());
      assertThat(result.get("100970884").getNlmId()).isEqualTo("0404401");
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
      assertThat(resultByIssn.get("2222-2222").getTitle()).isEqualTo("Journal A");

      // When: 用 ISSN-L 查找
      Map<String, VenueAggregate> resultByIssnL = repository.findByIssns(Set.of("1111-1111"));

      // Then
      assertThat(resultByIssnL).hasSize(1);
      assertThat(resultByIssnL.get("1111-1111").getTitle()).isEqualTo("Journal A");
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

      int initialCount = (int) identifierDao.count();

      // 重新查询
      Map<String, VenueAggregate> found = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate retrievedVenue = found.get("1111-1111");

      // When: 添加新标识符
      retrievedVenue.addIdentifier(VenueIdentifierType.NLM, "NLM123");

      repository.updateBatch(List.of(retrievedVenue));

      // Then: 验证标识符被添加
      assertThat(identifierDao.count()).isEqualTo(initialCount + 1);

      Map<String, VenueAggregate> updatedResult = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate updatedVenue = updatedResult.get("1111-1111");
      assertThat(updatedVenue.getIdentifier(VenueIdentifierType.NLM)).contains("NLM123");
    }

    @Test
    @DisplayName("删除标识符 - 应该正确持久化")
    void updateBatch_removeIdentifier_shouldPersist() {
      // Given: 插入带多个标识符的聚合根
      VenueAggregate venue = createVenueAggregateWithIssnL("NLM001", "Journal A", "1111-1111");
      venue.addIdentifier(VenueIdentifierType.OPENALEX, "S123");
      repository.insertAll(List.of(venue));

      int initialCount = (int) identifierDao.count();

      // 重新查询
      Map<String, VenueAggregate> found = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate retrievedVenue = found.get("1111-1111");

      // When: 删除 OPENALEX 标识符
      retrievedVenue.removeIdentifier(VenueIdentifierType.OPENALEX, "S123");

      repository.updateBatch(List.of(retrievedVenue));

      // Then: 验证标识符被删除
      assertThat(identifierDao.count()).isEqualTo(initialCount - 1);

      Map<String, VenueAggregate> updatedResult = repository.findByIssnLs(Set.of("1111-1111"));
      VenueAggregate updatedVenue = updatedResult.get("1111-1111");
      assertThat(updatedVenue.getIdentifier(VenueIdentifierType.OPENALEX)).isEmpty();
    }
  }

  // ========== NLM 全局唯一（V12）测试 ==========

  @Nested
  @DisplayName("NLM 全局唯一（V12）")
  class NlmUniquenessTests {

    @Test
    @DisplayName("主 NLM 与已有期刊相同的期刊被 uk_venue_nlm_id 拒绝")
    void should_reject_second_venue_with_same_primary_nlm() {
      repository.insertAll(List.of(VenueAggregate.fromPubMed("Journal A", "0404401", "1111-1111")));
      em.flush();

      assertThatThrownBy(
              () -> {
                repository.insertAll(
                    List.of(VenueAggregate.fromPubMed("Journal B", "0404401", "2222-2222")));
                em.flush();
              })
          .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
          .hasStackTraceContaining("uk_venue_nlm_id");
    }

    @Test
    @DisplayName("把别的期刊已有的 NLM 加成副 NLM 被 uk_venue_identifier_nlm 拒绝")
    void should_reject_nlm_identifier_already_owned_by_another_venue() {
      repository.insertAll(
          List.of(
              VenueAggregate.fromPubMed("Journal A", "0404401", "1111-1111"),
              VenueAggregate.fromPubMed("Journal B", "100970884", "2222-2222")));
      em.flush();
      em.clear();
      VenueAggregate b = repository.findByNlmIds(Set.of("100970884")).get("100970884");
      b.addIdentifier(VenueIdentifier.forNlm("0404401"));

      assertThatThrownBy(
              () -> {
                repository.updateBatch(List.of(b));
                em.flush();
              })
          .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class)
          .hasStackTraceContaining("uk_venue_identifier_nlm");
    }

    @Test
    @DisplayName("多个没有 NLM 的期刊可以共存")
    void should_allow_many_venues_without_nlm() {
      repository.insertAll(
          List.of(
              VenueAggregate.fromPubMed("Journal A", null, "1111-1111"),
              VenueAggregate.fromPubMed("Journal B", null, "2222-2222")));
      em.flush();

      assertThat(venueDao.findAll())
          .extracting(VenueEntity::getNlmId)
          .hasSize(2)
          .containsOnlyNulls();
    }
  }

  // ========== findSoftDeletedNlmIds() 测试 ==========

  @Nested
  @DisplayName("findSoftDeletedNlmIds()")
  class FindSoftDeletedNlmIdsTests {

    @Test
    @DisplayName("只返回软删期刊的主 NLM 与副 NLM；JPA 查询看不到软删期刊")
    void should_find_primary_and_secondary_nlm_of_soft_deleted_venues_only() {
      repository.insertAll(
          List.of(
              VenueAggregate.fromPubMed("Active", "0404401", "1111-1111"),
              VenueAggregate.fromPubMed("Gone", "0000003R", "3333-3333")));
      em.flush();
      em.clear();
      VenueAggregate gone = repository.findByNlmIds(Set.of("0000003R")).get("0000003R");
      gone.addIdentifier(VenueIdentifier.forNlm("0000005"));
      repository.updateBatch(List.of(gone));
      em.flush();
      em.getEntityManager()
          .createNativeQuery("UPDATE cat_venue SET deleted_at = now() WHERE nlm_id = '0000003R'")
          .executeUpdate();
      em.clear();

      assertThat(
              repository.findSoftDeletedNlmIds(
                  List.of("0404401", "0000003R", "0000005", "9999999")))
          .containsExactlyInAnyOrder("0000003R", "0000005");
      assertThat(repository.findByNlmIds(Set.of("0000003R"))).isEmpty();
      assertThat(repository.findSoftDeletedNlmIds(List.of())).isEmpty();
    }
  }

  // ========== 主 NLM 稳定测试 ==========

  @Nested
  @DisplayName("主 NLM 稳定")
  class PrimaryNlmStabilityTests {

    @Test
    @DisplayName("没有主 NLM 的期刊在导入里第一次拿到 NLM：它成为主 NLM，并写进 nlm_id 列")
    void should_write_primary_nlm_when_a_venue_without_nlm_gains_one() {
      repository.insertAll(List.of(VenueAggregate.fromPubMed("Journal A", null, "1111-1111")));
      em.flush();
      em.clear();
      VenueAggregate venue = repository.findByIssnLs(Set.of("1111-1111")).get("1111-1111");
      venue.addIdentifier(VenueIdentifier.forNlm("0404401"));

      repository.updateBatch(List.of(venue));
      em.flush();
      em.clear();

      assertThat(venueDao.findById(venue.getId().value()))
          .get()
          .extracting(VenueEntity::getNlmId)
          .isEqualTo("0404401");
      assertThat(repository.findByNlmIds(Set.of("0404401")).get("0404401").getNlmId())
          .isEqualTo("0404401");
    }

    @Test
    @DisplayName("标识符表里副 NLM 排在前面时，反复读出再更新，主 NLM 不变")
    void should_keep_primary_nlm_when_another_nlm_comes_first_in_identifiers() {
      em.getEntityManager()
          .createNativeQuery(
              "INSERT INTO cat_venue (id, venue_type, title, provenance_code, nlm_id)"
                  + " VALUES (1001, 'JOURNAL', 'Swiss medical weekly', 'PUBMED', '100970884')")
          .executeUpdate();
      em.getEntityManager()
          .createNativeQuery(
              "INSERT INTO cat_venue_identifier (id, venue_id, identifier_type, identifier_value)"
                  + " VALUES (2001, 1001, 'NLM', '0404401'), (2002, 1001, 'NLM', '100970884')")
          .executeUpdate();

      for (int round = 0; round < 2; round++) {
        VenueAggregate venue = repository.findByNlmIds(Set.of("100970884")).get("100970884");
        repository.updateBatch(List.of(venue));
        em.flush();
        em.clear();

        assertThat(venueDao.findById(1001L))
            .get()
            .extracting(VenueEntity::getNlmId)
            .isEqualTo("100970884");
      }
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
              .countryCode("US")
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
      assertThat(found.getPublicationProfile().countryCode()).isEqualTo("US");
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
      venue.withProvenance(
          ProvenanceInfo.of(
              ProvenanceCode.OPENALEX, Instant.now() // lastSyncedAt
              ));
      repository.insertAll(List.of(venue));

      // When
      Map<String, VenueAggregate> result = repository.findByIssnLs(Set.of("1111-1111"));

      // Then
      VenueAggregate found = result.get("1111-1111");
      assertThat(found.getProvenance()).isNotNull();
      assertThat(found.getProvenance().code()).isEqualTo(ProvenanceCode.OPENALEX);
      assertThat(found.getProvenance().lastSyncedAt()).isNotNull();
    }
  }

  // ========== 工厂方法 ==========

  /// 创建测试用的 VenueAggregate。
  ///
  /// @param nlmId NLM 唯一标识符
  /// @param title 期刊标题
  /// @param suffix 数字后缀（1-9），用于生成唯一的 ISSN-L（格式：0001-000X）
  private VenueAggregate createVenueAggregate(String nlmId, String title, int suffix) {
    String issnL = String.format("0001-00%02d", suffix);
    return VenueAggregate.fromPubMed(title, nlmId, issnL);
  }

  /// 创建测试用的 VenueAggregate（指定 ISSN-L）。
  private VenueAggregate createVenueAggregateWithIssnL(String nlmId, String title, String issnL) {
    return VenueAggregate.fromPubMed(title, nlmId, issnL);
  }

  /// 创建测试用的 VenueAggregate（指定 NLM ID）。
  private VenueAggregate createVenueAggregateWithNlmId(
      String ignoredParam, String title, String nlmId) {
    return VenueAggregate.fromPubMed(title, nlmId, null);
  }
}
