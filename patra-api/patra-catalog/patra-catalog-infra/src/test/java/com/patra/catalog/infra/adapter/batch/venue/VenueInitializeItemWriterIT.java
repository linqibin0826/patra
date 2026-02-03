package com.patra.catalog.infra.adapter.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.port.registry.DictionaryResolverPort;
import com.patra.catalog.infra.adapter.persistence.VenueRepositoryAdapter;
import com.patra.catalog.infra.adapter.persistence.dao.VenueDao;
import com.patra.catalog.infra.adapter.persistence.dao.VenueIdentifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.VenuePublicationStatsDao;
import com.patra.catalog.infra.adapter.persistence.entity.VenueEntity;
import com.patra.catalog.infra.adapter.persistence.entity.VenueIdentifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.VenuePublicationStatsEntity;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// VenueInitializeItemWriter 集成测试（纯 JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试批量写入操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立
/// - TestContainers：自动启动和停止 MySQL 容器
///
/// **DDD 嵌入式值对象设计**：
///
/// 聚合根（VenueAggregate）已包含所有嵌入式值对象，随聚合根一起保存为 JSON：
/// - publicationProfile → cat_venue.publication_profile
/// - citationMetrics → cat_venue.citation_metrics
/// - openAccess → cat_venue.open_access
/// - affiliatedSocieties → cat_venue.affiliated_societies
///
/// **重点测试场景**：
///
/// - 新增场景：全部为新记录（纯 INSERT 语义）
/// - 子表处理：标识符和年度指标的插入
/// - 子表关联：外键正确性验证
///
/// **注意**：
///
/// 本测试类仅覆盖纯 INSERT 场景。不支持 Upsert（更新已存在记录）。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueInitializeItemWriter.class,
  VenueRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "com.patra.catalog.infra.adapter.persistence.converter")
@ActiveProfiles("test")
@DisplayName("VenueInitializeItemWriter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueInitializeItemWriterIT {

  @Autowired private VenueInitializeItemWriter writer;

  @Autowired private VenueDao venueDao;
  @Autowired private VenueIdentifierDao identifierDao;
  @Autowired private VenuePublicationStatsDao metricsRepository;

  /// DictionaryResolverPort Mock（国家编码验证，测试中不需要真实调用）
  @MockitoBean private DictionaryResolverPort dictionaryResolverPort;

  /// 创建测试用的 VenueParseResult（无年度指标）。
  ///
  /// **DDD 嵌入式值对象设计**：聚合根直接包含所有值对象。
  ///
  /// @param openalexId OpenAlex ID（如 S1、S2）
  /// @param displayName 显示名称
  /// @param issnL ISSN-L（符合 XXXX-XXXX 格式）
  private VenueParseResult createParseResult(String openalexId, String displayName, String issnL) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));
    return new VenueParseResult(venue, List.of());
  }

  /// 创建带年度指标的 VenueParseResult。
  private VenueParseResult createParseResultWithMetrics(
      String openalexId, String displayName, String issnL) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));
    // 嵌入引用指标
    venue.withCitationMetrics(CitationMetrics.ofBasic(190, 900));
    List<VenuePublicationStats> metrics =
        List.of(
            VenuePublicationStats.create(2024, 100, 500),
            VenuePublicationStats.create(2023, 90, 400));
    return new VenueParseResult(venue, metrics);
  }

  /// 创建带标识符的 VenueParseResult（无年度指标）。
  private VenueParseResult createParseResultWithIdentifiers(
      String openalexId, String displayName, String issnL) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));
    venue.addIdentifier(new VenueIdentifier(VenueIdentifierType.ISSN, "1234-5678"));
    venue.addIdentifier(new VenueIdentifier(VenueIdentifierType.ISSN, "5678-1234"));
    return new VenueParseResult(venue, List.of());
  }

  @Nested
  @DisplayName("新增场景测试")
  class InsertTest {

    @Test
    @DisplayName("全部为新记录 - 应该正确插入主表和子表")
    void write_allNew_shouldInsert() throws Exception {
      // Given
      VenueParseResult result1 = createParseResult("S1", "Journal A", "1111-1111");
      VenueParseResult result2 = createParseResult("S2", "Journal B", "2222-2222");

      // When
      writer.write(
          new org.springframework.batch.infrastructure.item.Chunk<>(List.of(result1, result2)));

      // Then: 验证主表
      long venueCount = venueDao.count();
      assertThat(venueCount).isEqualTo(2);

      List<VenueEntity> venues = venueDao.findAll();
      assertThat(venues)
          .extracting(VenueEntity::getDisplayName)
          .containsExactlyInAnyOrder("Journal A", "Journal B");

      // Then: 验证标识符子表（每个 Venue 有 OpenAlex + ISSN-L 共 2 个标识符）
      long identifierCount = identifierDao.count();
      assertThat(identifierCount).isEqualTo(4);

      // 验证 OpenAlex ID 标识符存在
      List<VenueIdentifierEntity> identifiers = identifierDao.findAll();
      assertThat(identifiers)
          .filteredOn(id -> id.getIdentifierType().equals(VenueIdentifierType.OPENALEX.name()))
          .extracting(VenueIdentifierEntity::getIdentifierValue)
          .containsExactlyInAnyOrder("S1", "S2");
    }

    @Test
    @DisplayName("空 Chunk - 不应该执行任何操作")
    void write_emptyChunk_shouldDoNothing() throws Exception {
      // When
      writer.write(new org.springframework.batch.infrastructure.item.Chunk<>());

      // Then
      long venueCount = venueDao.count();
      assertThat(venueCount).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("子表处理测试")
  class ChildTableTest {

    @Test
    @DisplayName("新增记录 - 应该插入标识符")
    void write_newWithIdentifiers_shouldInsertIdentifiers() throws Exception {
      // Given
      VenueParseResult result = createParseResultWithIdentifiers("S1", "Journal A", "3333-3333");

      // When
      writer.write(new org.springframework.batch.infrastructure.item.Chunk<>(List.of(result)));

      // Then: 应该插入标识符（OpenAlex + ISSN-L + 2 ISSN = 4）
      long identifierCount = identifierDao.count();
      assertThat(identifierCount).isEqualTo(4);

      List<VenueIdentifierEntity> identifiers = identifierDao.findAll();
      assertThat(identifiers)
          .extracting(VenueIdentifierEntity::getIdentifierType)
          .containsExactlyInAnyOrder("OPENALEX", "ISSN_L", "ISSN", "ISSN");
    }

    @Test
    @DisplayName("新增记录 - 应该插入年度指标")
    void write_newWithMetrics_shouldInsertMetrics() throws Exception {
      // Given
      VenueParseResult result = createParseResultWithMetrics("S1", "Journal A", "4444-4444");

      // When
      writer.write(new org.springframework.batch.infrastructure.item.Chunk<>(List.of(result)));

      // Then: 应该插入年度指标
      long metricsCount = metricsRepository.count();
      assertThat(metricsCount).isEqualTo(2);

      List<VenuePublicationStatsEntity> metrics = metricsRepository.findAll();
      assertThat(metrics)
          .extracting(VenuePublicationStatsEntity::getYear)
          .containsExactlyInAnyOrder((short) 2024, (short) 2023);
      assertThat(metrics)
          .extracting(VenuePublicationStatsEntity::getWorksCount)
          .containsExactlyInAnyOrder(100, 90);
    }
  }

  @Nested
  @DisplayName("子表关联正确性测试")
  class RelationKeyTest {

    @Test
    @DisplayName("子表应该正确关联到主表")
    void write_shouldSetCorrectVenueId() throws Exception {
      // Given
      VenueParseResult result = createParseResultWithMetrics("S1", "Journal A", "5555-5555");

      // When
      writer.write(new org.springframework.batch.infrastructure.item.Chunk<>(List.of(result)));

      // Then: 获取主表 ID
      VenueEntity savedVenue = venueDao.findAll().get(0);
      Long venueId = savedVenue.getId();
      assertThat(venueId).isNotNull();

      // Then: 验证子表的外键
      List<VenueIdentifierEntity> identifiers = identifierDao.findAll();
      assertThat(identifiers).allMatch(i -> i.getVenueId().equals(venueId));

      List<VenuePublicationStatsEntity> metrics = metricsRepository.findAll();
      assertThat(metrics).allMatch(m -> m.getVenueId().equals(venueId));
    }
  }
}
