package com.patra.catalog.infra.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenueMetrics;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.entity.VenueMetricsDO;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMetricsMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueImportItemWriter 集成测试。
///
/// 使用 Testcontainers + MySQL 8 测试 Upsert 操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立
/// - TestContainers：自动启动和停止 MySQL 容器
///
/// **重点测试场景**：
///
/// - 新增场景：全部为新记录
/// - 更新场景：全部为已存在记录
/// - 混合场景：部分新增部分更新
/// - 子表处理：标识符和年度指标
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueImportItemWriter.class, TestMybatisPlusAutoConfiguration.class})
@MapperScan("com.patra.catalog.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("VenueImportItemWriter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueImportItemWriterIT {

  @Autowired private VenueImportItemWriter writer;

  @Autowired private VenueMapper venueMapper;
  @Autowired private VenueIdentifierMapper identifierMapper;
  @Autowired private VenueMetricsMapper metricsMapper;

  /// 创建测试用的 VenueAggregate。
  ///
  /// 注意：issnL 使用 openalexId 后缀生成，确保唯一性（数据库有 uk_issn_l 唯一索引）。
  private VenueAggregate createVenueAggregate(String openalexId, String displayName) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    // 使用 openalexId 生成唯一的 issnL，避免唯一索引冲突
    venue.withIssnL("1234-" + openalexId);
    venue.withCountryCode("US");
    venue.withOaStatus(true, false, false);
    return venue;
  }

  /// 创建带年度指标的 VenueAggregate。
  private VenueAggregate createVenueWithMetrics(String openalexId, String displayName) {
    VenueAggregate venue = createVenueAggregate(openalexId, displayName);
    venue.setYearlyMetrics(
        List.of(VenueMetrics.create(2024, 100, 500), VenueMetrics.create(2023, 90, 400)));
    return venue;
  }

  /// 创建带标识符的 VenueAggregate。
  private VenueAggregate createVenueWithIdentifiers(String openalexId, String displayName) {
    VenueAggregate venue = createVenueAggregate(openalexId, displayName);
    venue.addIdentifier(VenueIdentifierType.ISSN, "1234-5678", true);
    venue.addIdentifier(VenueIdentifierType.ISSN, "5678-1234", false);
    return venue;
  }

  @Nested
  @DisplayName("新增场景测试")
  class InsertTest {

    @Test
    @DisplayName("全部为新记录 - 应该正确插入主表和子表")
    void write_allNew_shouldInsert() throws Exception {
      // Given
      VenueAggregate venue1 = createVenueAggregate("S1", "Journal A");
      VenueAggregate venue2 = createVenueAggregate("S2", "Journal B");

      // When
      writer.write(new Chunk<>(List.of(venue1, venue2)));

      // Then: 验证主表
      long venueCount = venueMapper.selectCount(null);
      assertThat(venueCount).isEqualTo(2);

      List<VenueDO> venues = venueMapper.selectList(null);
      assertThat(venues).extracting(VenueDO::getOpenalexId).containsExactlyInAnyOrder("S1", "S2");
      assertThat(venues)
          .extracting(VenueDO::getDisplayName)
          .containsExactlyInAnyOrder("Journal A", "Journal B");

      // Then: 验证标识符子表（每个 Venue 有 1 个 OpenAlex 标识符）
      long identifierCount = identifierMapper.selectCount(null);
      assertThat(identifierCount).isEqualTo(2);
    }

    @Test
    @DisplayName("空 Chunk - 不应该执行任何操作")
    void write_emptyChunk_shouldDoNothing() throws Exception {
      // When
      writer.write(new Chunk<>());

      // Then
      long venueCount = venueMapper.selectCount(null);
      assertThat(venueCount).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("更新场景测试")
  class UpdateTest {

    @Test
    @DisplayName("全部为已存在记录 - 应该更新记录")
    void write_allExisting_shouldUpdate() throws Exception {
      // Given: 先插入记录
      VenueAggregate venue1 = createVenueAggregate("S1", "Journal A Original");
      VenueAggregate venue2 = createVenueAggregate("S2", "Journal B Original");
      writer.write(new Chunk<>(List.of(venue1, venue2)));

      // When: 更新记录
      VenueAggregate updated1 = createVenueAggregate("S1", "Journal A Updated");
      VenueAggregate updated2 = createVenueAggregate("S2", "Journal B Updated");
      writer.write(new Chunk<>(List.of(updated1, updated2)));

      // Then: 验证记录数不变
      long venueCount = venueMapper.selectCount(null);
      assertThat(venueCount).isEqualTo(2);

      // Then: 验证名称已更新
      List<VenueDO> venues = venueMapper.selectList(null);
      assertThat(venues)
          .extracting(VenueDO::getDisplayName)
          .containsExactlyInAnyOrder("Journal A Updated", "Journal B Updated");
    }
  }

  @Nested
  @DisplayName("混合场景测试")
  class MixedTest {

    @Test
    @DisplayName("部分新增部分更新 - 应该分别处理")
    void write_mixedNewAndExisting_shouldHandleBoth() throws Exception {
      // Given: 先插入 S1
      VenueAggregate venue1 = createVenueAggregate("S1", "Journal A Original");
      writer.write(new Chunk<>(List.of(venue1)));

      // When: S1 更新，S2 新增
      VenueAggregate updated1 = createVenueAggregate("S1", "Journal A Updated");
      VenueAggregate newVenue2 = createVenueAggregate("S2", "Journal B New");
      writer.write(new Chunk<>(List.of(updated1, newVenue2)));

      // Then: 验证记录数
      long venueCount = venueMapper.selectCount(null);
      assertThat(venueCount).isEqualTo(2);

      // Then: 验证内容
      VenueDO s1 =
          venueMapper.selectOne(Wrappers.<VenueDO>lambdaQuery().eq(VenueDO::getOpenalexId, "S1"));
      assertThat(s1.getDisplayName()).isEqualTo("Journal A Updated");

      VenueDO s2 =
          venueMapper.selectOne(Wrappers.<VenueDO>lambdaQuery().eq(VenueDO::getOpenalexId, "S2"));
      assertThat(s2.getDisplayName()).isEqualTo("Journal B New");
    }
  }

  @Nested
  @DisplayName("子表处理测试")
  class ChildTableTest {

    @Test
    @DisplayName("新增记录 - 应该插入标识符")
    void write_newWithIdentifiers_shouldInsertIdentifiers() throws Exception {
      // Given
      VenueAggregate venue = createVenueWithIdentifiers("S1", "Journal A");

      // When
      writer.write(new Chunk<>(List.of(venue)));

      // Then: 应该插入标识符（OpenAlex + 2 ISSN = 3）
      long identifierCount = identifierMapper.selectCount(null);
      assertThat(identifierCount).isEqualTo(3);

      List<VenueIdentifierDO> identifiers = identifierMapper.selectList(null);
      assertThat(identifiers)
          .extracting(VenueIdentifierDO::getIdentifierType)
          .containsExactlyInAnyOrder("OPENALEX", "ISSN", "ISSN");
    }

    @Test
    @DisplayName("新增记录 - 应该插入年度指标")
    void write_newWithMetrics_shouldInsertMetrics() throws Exception {
      // Given
      VenueAggregate venue = createVenueWithMetrics("S1", "Journal A");

      // When
      writer.write(new Chunk<>(List.of(venue)));

      // Then: 应该插入年度指标
      long metricsCount = metricsMapper.selectCount(null);
      assertThat(metricsCount).isEqualTo(2);

      List<VenueMetricsDO> metrics = metricsMapper.selectList(null);
      assertThat(metrics)
          .extracting(VenueMetricsDO::getYear)
          .containsExactlyInAnyOrder((short) 2024, (short) 2023);
      assertThat(metrics)
          .extracting(VenueMetricsDO::getWorksCount)
          .containsExactlyInAnyOrder(100, 90);
    }

    @Test
    @DisplayName("更新记录 - 应该先删后插子表")
    void write_existingWithChildData_shouldDeleteThenInsert() throws Exception {
      // Given: 先插入记录
      VenueAggregate venue = createVenueWithMetrics("S1", "Journal A");
      venue.addIdentifier(VenueIdentifierType.ISSN, "1111-2222", true);
      writer.write(new Chunk<>(List.of(venue)));

      // When: 更新记录（指标和标识符都变化）
      VenueAggregate updatedVenue = createVenueAggregate("S1", "Journal A Updated");
      updatedVenue.setYearlyMetrics(List.of(VenueMetrics.create(2025, 200, 1000)));
      updatedVenue.addIdentifier(VenueIdentifierType.ISSN, "3333-4444", true);
      writer.write(new Chunk<>(List.of(updatedVenue)));

      // Then: 验证标识符（旧的被删除，新的被插入）
      List<VenueIdentifierDO> identifiers = identifierMapper.selectList(null);
      // 应该有 OpenAlex + 1 个新 ISSN = 2
      assertThat(identifiers).hasSize(2);
      assertThat(identifiers)
          .extracting(VenueIdentifierDO::getIdentifierValue)
          .contains("3333-4444");
      assertThat(identifiers)
          .extracting(VenueIdentifierDO::getIdentifierValue)
          .doesNotContain("1111-2222");

      // Then: 验证年度指标（旧的被删除，新的被插入）
      List<VenueMetricsDO> metrics = metricsMapper.selectList(null);
      assertThat(metrics).hasSize(1);
      assertThat(metrics.get(0).getYear()).isEqualTo((short) 2025);
      assertThat(metrics.get(0).getWorksCount()).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("子表关联正确性测试")
  class RelationKeyTest {

    @Test
    @DisplayName("子表应该正确关联到主表")
    void write_shouldSetCorrectVenueId() throws Exception {
      // Given
      VenueAggregate venue = createVenueWithMetrics("S1", "Journal A");

      // When
      writer.write(new Chunk<>(List.of(venue)));

      // Then: 获取主表 ID
      VenueDO savedVenue = venueMapper.selectList(null).get(0);
      Long venueId = savedVenue.getId();
      assertThat(venueId).isNotNull();

      // Then: 验证子表的外键
      List<VenueIdentifierDO> identifiers = identifierMapper.selectList(null);
      assertThat(identifiers).allMatch(i -> i.getVenueId().equals(venueId));

      List<VenueMetricsDO> metrics = metricsMapper.selectList(null);
      assertThat(metrics).allMatch(m -> m.getVenueId().equals(venueId));
    }
  }
}
