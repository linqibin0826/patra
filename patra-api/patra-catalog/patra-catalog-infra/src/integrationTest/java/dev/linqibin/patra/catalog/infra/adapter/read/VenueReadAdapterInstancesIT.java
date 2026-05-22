package dev.linqibin.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueInstanceSummaryReadModel;
import dev.linqibin.patra.catalog.infra.config.CatalogPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueInstanceDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueInstanceEntity;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueReadAdapter 实例列表查询集成测试。
///
/// **测试目标**：
///
/// - 分页查询正确（总数、页数、每页条数）
/// - 年份过滤生效
/// - publicationCount 统计正确
/// - 排序规则：publicationYear DESC、volume DESC、issue DESC
@DataJpaTest
@ContextConfiguration(initializers = CatalogPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueReadAdapter.class,
  VenueReadModelMapperImpl.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("VenueReadAdapter 实例列表查询集成测试")
class VenueReadAdapterInstancesIT {

  @Autowired private VenueReadAdapter venueReadAdapter;

  @Autowired private VenueDao venueDao;
  @Autowired private VenueInstanceDao venueInstanceDao;
  @Autowired private PublicationDao publicationDao;

  private int pmidSeq = 10000000;
  private Long venueId;
  private Long instance2024Id;
  private Long instance2023Id;
  private Long instance2022Id;

  @BeforeEach
  void setUp() {
    // 创建 Venue
    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Test Journal");
    venue.setIssnL("1234-5678");
    venue.setProvenanceCode("OPENALEX");
    venue.setCountryCode("US");
    venue.setLastSyncedAt(Instant.parse("2026-01-01T00:00:00Z"));
    venueDao.save(venue);
    venueId = venue.getId();

    // 创建 3 个实例（不同年份）
    instance2024Id = saveInstance(venueId, "45", "3", 2024, 3, 15);
    instance2023Id = saveInstance(venueId, "44", "12", 2023, 12, 1);
    instance2022Id = saveInstance(venueId, "43", "6", 2022, 6, null);

    // 为每个实例创建不同数量的文献
    savePublications(instance2024Id, venueId, 5);
    savePublications(instance2023Id, venueId, 3);
    savePublications(instance2022Id, venueId, 1);
  }

  /// 查询所有实例应返回正确的分页结果和文献数量。
  @Test
  @DisplayName("无过滤条件应返回所有实例并包含正确的 publicationCount")
  void shouldReturnAllInstancesWithCorrectPubCount() {
    // When
    PageResult<VenueInstanceSummaryReadModel> result =
        venueReadAdapter.findVenueInstances(venueId, PagingParams.of(1, 20), null);

    // Then
    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).hasSize(3);

    // 验证排序：publicationYear DESC
    assertThat(result.items())
        .extracting(VenueInstanceSummaryReadModel::publicationYear)
        .containsExactly(2024, 2023, 2022);

    // 验证 publicationCount
    assertThat(result.items())
        .extracting(VenueInstanceSummaryReadModel::publicationCount)
        .containsExactly(5L, 3L, 1L);
  }

  /// 分页应返回正确的元信息。
  @Test
  @DisplayName("分页 page=1 size=2 应返回 2 条记录且 totalPages=2")
  void shouldReturnCorrectPagination() {
    // When
    PageResult<VenueInstanceSummaryReadModel> page1 =
        venueReadAdapter.findVenueInstances(venueId, PagingParams.of(1, 2), null);

    // Then
    assertThat(page1.page()).isEqualTo(1);
    assertThat(page1.pageSize()).isEqualTo(2);
    assertThat(page1.total()).isEqualTo(3);
    assertThat(page1.totalPages()).isEqualTo(2);
    assertThat(page1.items()).hasSize(2);

    // 第一页应包含 2024 和 2023 年实例
    assertThat(page1.items())
        .extracting(VenueInstanceSummaryReadModel::publicationYear)
        .containsExactly(2024, 2023);

    // 验证第二页
    PageResult<VenueInstanceSummaryReadModel> page2 =
        venueReadAdapter.findVenueInstances(venueId, PagingParams.of(2, 2), null);

    assertThat(page2.items()).hasSize(1);
    assertThat(page2.items().getFirst().publicationYear()).isEqualTo(2022);
  }

  /// 年份过滤应只返回匹配年份的实例。
  @Test
  @DisplayName("year=2023 应只返回 2023 年的实例")
  void shouldFilterByYear() {
    // When
    PageResult<VenueInstanceSummaryReadModel> result =
        venueReadAdapter.findVenueInstances(venueId, PagingParams.of(1, 20), 2023);

    // Then
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.publicationYear()).isEqualTo(2023);
              assertThat(item.volume()).isEqualTo("44");
              assertThat(item.issue()).isEqualTo("12");
              assertThat(item.publicationCount()).isEqualTo(3);
            });
  }

  /// 不存在的年份应返回空结果。
  @Test
  @DisplayName("不存在的年份应返回空分页结果")
  void shouldReturnEmptyForNonExistentYear() {
    // When
    PageResult<VenueInstanceSummaryReadModel> result =
        venueReadAdapter.findVenueInstances(venueId, PagingParams.of(1, 20), 2020);

    // Then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  /// 实例字段映射应正确。
  @Test
  @DisplayName("实例字段映射应完整正确")
  void shouldMapAllFieldsCorrectly() {
    // When
    PageResult<VenueInstanceSummaryReadModel> result =
        venueReadAdapter.findVenueInstances(venueId, PagingParams.of(1, 20), 2024);

    // Then
    assertThat(result.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.id()).isEqualTo(instance2024Id);
              assertThat(item.volume()).isEqualTo("45");
              assertThat(item.issue()).isEqualTo("3");
              assertThat(item.publicationYear()).isEqualTo(2024);
              assertThat(item.publicationMonth()).isEqualTo(3);
              assertThat(item.publicationDay()).isEqualTo(15);
              assertThat(item.publicationCount()).isEqualTo(5);
            });
  }

  /// 无关联文献的实例 publicationCount 应为 0。
  @Test
  @DisplayName("无关联文献的实例 publicationCount 应为 0")
  void shouldReturnZeroPubCountForEmptyInstance() {
    // Given - 创建无文献的实例
    saveInstance(venueId, "46", "1", 2025, 1, 1);

    // When
    PageResult<VenueInstanceSummaryReadModel> result =
        venueReadAdapter.findVenueInstances(venueId, PagingParams.of(1, 20), 2025);

    // Then
    assertThat(result.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.publicationCount()).isZero();
            });
  }

  @Nested
  @DisplayName("排序规则")
  class SortingTests {

    /// 同一年份的实例应按 volume DESC、issue DESC 排序。
    @Test
    @DisplayName("同年份应按 volume DESC、issue DESC 排序")
    void shouldSortByVolumeAndIssueDescWithinSameYear() {
      // Given - 同一年份创建多个实例
      saveInstance(venueId, "10", "1", 2025, 1, null);
      saveInstance(venueId, "10", "3", 2025, 3, null);
      saveInstance(venueId, "10", "2", 2025, 2, null);
      saveInstance(venueId, "9", "12", 2025, 12, null);

      // When
      PageResult<VenueInstanceSummaryReadModel> result =
          venueReadAdapter.findVenueInstances(venueId, PagingParams.of(1, 20), 2025);

      // Then - volume DESC 优先，然后 issue DESC
      assertThat(result.items()).hasSize(4);
      assertThat(result.items())
          .extracting(VenueInstanceSummaryReadModel::volume)
          .containsExactly("9", "10", "10", "10");
      assertThat(result.items())
          .extracting(VenueInstanceSummaryReadModel::issue)
          .containsExactly("12", "3", "2", "1");
    }
  }

  /// 保存测试用 VenueInstance 实体。
  private Long saveInstance(
      Long venueId, String volume, String issue, int year, Integer month, Integer day) {
    VenueInstanceEntity entity = new VenueInstanceEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueId(venueId);
    entity.setVolume(volume);
    entity.setIssue(issue);
    entity.setPublicationYear(year);
    entity.setPublicationMonth(month);
    entity.setPublicationDay(day);
    venueInstanceDao.save(entity);
    return entity.getId();
  }

  /// 为指定实例批量创建测试文献。
  private void savePublications(Long instanceId, Long venueId, int count) {
    for (int i = 0; i < count; i++) {
      PublicationEntity pub = new PublicationEntity();
      pub.setId(SnowflakeIdGenerator.getId());
      pub.setVenueInstanceId(instanceId);
      pub.setVenueId(venueId);
      pub.setProvenanceCode("PUBMED");
      pub.setTitle("Test Publication " + i);
      pub.setPublicationStatus("PUBLISHED");
      pub.setPublicationYear(2024);
      pub.setAuthorsComplete(true);
      pub.setIsOa(false);
      pub.setPmid(String.valueOf(pmidSeq++));
      publicationDao.save(pub);
    }
  }
}
