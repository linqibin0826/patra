package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenueMetrics;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueRepositoryAdapter 集成测试。
///
/// 使用 Testcontainers + MySQL 8 测试 Venue 仓储操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：hasAnyData、insertAll（聚合根批量插入）
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueRepositoryAdapter.class, TestMybatisPlusAutoConfiguration.class})
@ComponentScan(basePackages = "com.patra.catalog.infra.persistence.converter")
@MapperScan("com.patra.catalog.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("VenueRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueRepositoryAdapterIT {

  @Autowired private VenueRepositoryAdapter repository;

  @Autowired private VenueMapper venueMapper;
  @Autowired private VenueIdentifierMapper venueIdentifierMapper;
  @Autowired private VenueMetricsMapper venueMetricsMapper;

  // ========== hasAnyData() 测试 ==========

  @Nested
  @DisplayName("hasAnyData() 测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      long count = venueMapper.selectCount(null);
      assertThat(count).isEqualTo(0);

      // When & Then
      assertThat(repository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      // Given: 通过 insertAll 插入数据
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
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
    @DisplayName("应该正确插入多个聚合根（含子表）")
    void insertAll_shouldInsertAggregatesWithChildren() {
      // Given
      VenueAggregate venue1 = createVenueAggregate("S1", "Journal A");
      venue1.setYearlyMetrics(List.of(VenueMetrics.create(2024, 100, 500)));

      VenueAggregate venue2 = createVenueAggregate("S2", "Journal B");
      venue2.setYearlyMetrics(List.of(VenueMetrics.create(2023, 50, 200)));

      // When
      repository.insertAll(List.of(venue1, venue2));

      // Then: 验证主表
      assertThat(venueMapper.selectCount(null)).isEqualTo(2);

      // Then: 验证标识符子表（每个 Venue 有 1 个 OpenAlex 标识符）
      assertThat(venueIdentifierMapper.selectCount(null)).isEqualTo(2);

      // Then: 验证年度指标子表
      assertThat(venueMetricsMapper.selectCount(null)).isEqualTo(2);
    }

    @Test
    @DisplayName("空列表不应抛出异常")
    void insertAll_emptyList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.insertAll(List.of())).doesNotThrowAnyException();

      // 验证没有数据插入
      assertThat(venueMapper.selectCount(null)).isZero();
    }

    @Test
    @DisplayName("子表应正确关联到主表")
    void insertAll_shouldSetCorrectVenueId() {
      // Given
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venue.addIdentifier(VenueIdentifierType.ISSN, "1234-5678", true);
      venue.setYearlyMetrics(List.of(VenueMetrics.create(2024, 100, 500)));

      // When
      repository.insertAll(List.of(venue));

      // Then: 获取主表 ID
      var savedVenue = venueMapper.selectList(null).get(0);
      Long venueId = savedVenue.getId();
      assertThat(venueId).isNotNull();

      // Then: 验证标识符子表的外键
      var identifiers = venueIdentifierMapper.selectList(null);
      assertThat(identifiers).allMatch(i -> i.getVenueId().equals(venueId));

      // Then: 验证年度指标子表的外键
      var metrics = venueMetricsMapper.selectList(null);
      assertThat(metrics).allMatch(m -> m.getVenueId().equals(venueId));
    }

    @Test
    @DisplayName("应该正确处理没有子表数据的聚合根")
    void insertAll_aggregateWithoutChildren_shouldInsertOnlyMain() {
      // Given: 创建没有额外标识符和指标的聚合根
      VenueAggregate venue = VenueAggregate.fromOpenAlex("S1", VenueType.JOURNAL, "Journal A");
      venue.withIssnL("1234-S1");

      // When
      repository.insertAll(List.of(venue));

      // Then: 主表有 1 条记录
      assertThat(venueMapper.selectCount(null)).isEqualTo(1);

      // Then: 标识符只有 1 个（OpenAlex ID 由 fromOpenAlex 自动添加）
      assertThat(venueIdentifierMapper.selectCount(null)).isEqualTo(1);

      // Then: 年度指标为空
      assertThat(venueMetricsMapper.selectCount(null)).isZero();
    }
  }

  /// 创建测试用的 VenueAggregate。
  private VenueAggregate createVenueAggregate(String openalexId, String displayName) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.withIssnL("1234-" + openalexId);
    venue.withCountryCode("US");
    venue.withOaStatus(true, false, false);
    return venue;
  }
}
