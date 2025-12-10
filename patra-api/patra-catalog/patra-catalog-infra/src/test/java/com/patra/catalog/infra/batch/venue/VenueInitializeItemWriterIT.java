package com.patra.catalog.infra.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.infra.adapter.persistence.VenueRepositoryAdapter;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.infra.persistence.mapper.VenuePublicationStatsMapper;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueInitializeItemWriter 集成测试。
///
/// 使用 Testcontainers + MySQL 8 测试批量写入操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立
/// - TestContainers：自动启动和停止 MySQL 容器
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
@MybatisPlusTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueInitializeItemWriter.class,
  VenueRepositoryAdapter.class,
  TestMybatisPlusAutoConfiguration.class
})
@ComponentScan(basePackages = "com.patra.catalog.infra.persistence.converter")
@MapperScan("com.patra.catalog.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("VenueInitializeItemWriter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueInitializeItemWriterIT {

  @Autowired private VenueInitializeItemWriter writer;

  @Autowired private VenueMapper venueMapper;
  @Autowired private VenueIdentifierMapper identifierMapper;
  @Autowired private VenuePublicationStatsMapper metricsMapper;

  /// 创建测试用的 VenueParseResult（无年度指标）。
  ///
  /// 注意：issnL 使用 openalexId 后缀生成，确保唯一性（数据库有 uk_issn_l 唯一索引）。
  private VenueParseResult createParseResult(String openalexId, String displayName) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    // 使用 openalexId 生成唯一的 issnL，避免唯一索引冲突
    venue.withIssnL("1234-" + openalexId);
    venue.withCountryCode("US");
    venue.withOaStatus(true, false);
    venue.withPublicationHistory(PublicationHistory.active(2000));
    return new VenueParseResult(venue, List.of());
  }

  /// 创建带年度指标的 VenueParseResult。
  private VenueParseResult createParseResultWithMetrics(String openalexId, String displayName) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.withIssnL("1234-" + openalexId);
    venue.withCountryCode("US");
    venue.withOaStatus(true, false);
    venue.withPublicationHistory(PublicationHistory.active(2000));
    List<VenuePublicationStats> metrics =
        List.of(
            VenuePublicationStats.create(2024, 100, 500),
            VenuePublicationStats.create(2023, 90, 400));
    return new VenueParseResult(venue, metrics);
  }

  /// 创建带标识符的 VenueParseResult（无年度指标）。
  private VenueParseResult createParseResultWithIdentifiers(String openalexId, String displayName) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.withIssnL("1234-" + openalexId);
    venue.withCountryCode("US");
    venue.withOaStatus(true, false);
    venue.withPublicationHistory(PublicationHistory.active(2000));
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
      VenueParseResult result1 = createParseResult("S1", "Journal A");
      VenueParseResult result2 = createParseResult("S2", "Journal B");

      // When
      writer.write(new Chunk<>(List.of(result1, result2)));

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
  @DisplayName("子表处理测试")
  class ChildTableTest {

    @Test
    @DisplayName("新增记录 - 应该插入标识符")
    void write_newWithIdentifiers_shouldInsertIdentifiers() throws Exception {
      // Given
      VenueParseResult result = createParseResultWithIdentifiers("S1", "Journal A");

      // When
      writer.write(new Chunk<>(List.of(result)));

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
      VenueParseResult result = createParseResultWithMetrics("S1", "Journal A");

      // When
      writer.write(new Chunk<>(List.of(result)));

      // Then: 应该插入年度指标
      long metricsCount = metricsMapper.selectCount(null);
      assertThat(metricsCount).isEqualTo(2);

      List<VenuePublicationStatsDO> metrics = metricsMapper.selectList(null);
      assertThat(metrics)
          .extracting(VenuePublicationStatsDO::getYear)
          .containsExactlyInAnyOrder((short) 2024, (short) 2023);
      assertThat(metrics)
          .extracting(VenuePublicationStatsDO::getWorksCount)
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
      VenueParseResult result = createParseResultWithMetrics("S1", "Journal A");

      // When
      writer.write(new Chunk<>(List.of(result)));

      // Then: 获取主表 ID
      VenueDO savedVenue = venueMapper.selectList(null).get(0);
      Long venueId = savedVenue.getId();
      assertThat(venueId).isNotNull();

      // Then: 验证子表的外键
      List<VenueIdentifierDO> identifiers = identifierMapper.selectList(null);
      assertThat(identifiers).allMatch(i -> i.getVenueId().equals(venueId));

      List<VenuePublicationStatsDO> metrics = metricsMapper.selectList(null);
      assertThat(metrics).allMatch(m -> m.getVenueId().equals(venueId));
    }
  }
}
