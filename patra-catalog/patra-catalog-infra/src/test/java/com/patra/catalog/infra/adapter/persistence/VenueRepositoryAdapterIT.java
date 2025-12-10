package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.util.List;
import java.util.Set;
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
    @DisplayName("应该正确插入多个聚合根（含标识符）")
    void insertAll_shouldInsertAggregatesWithIdentifiers() {
      // Given
      VenueAggregate venue1 = createVenueAggregate("S1", "Journal A");
      venue1.addIdentifier(VenueIdentifierType.ISSN, "1111-1111");

      VenueAggregate venue2 = createVenueAggregate("S2", "Journal B");
      venue2.addIdentifier(VenueIdentifierType.ISSN, "2222-2222");

      // When
      repository.insertAll(List.of(venue1, venue2));

      // Then: 验证主表
      assertThat(venueMapper.selectCount(null)).isEqualTo(2);

      // Then: 验证标识符子表（每个 Venue 有 1 个 OpenAlex + 1 个 ISSN = 2）
      assertThat(venueIdentifierMapper.selectCount(null)).isEqualTo(4);
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
    @DisplayName("标识符子表应正确关联到主表")
    void insertAll_shouldSetCorrectVenueIdForIdentifiers() {
      // Given
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venue.addIdentifier(VenueIdentifierType.ISSN, "1234-5678");

      // When
      repository.insertAll(List.of(venue));

      // Then: 获取主表 ID
      var savedVenue = venueMapper.selectList(null).get(0);
      Long venueId = savedVenue.getId();
      assertThat(venueId).isNotNull();

      // Then: 验证标识符子表的外键
      var identifiers = venueIdentifierMapper.selectList(null);
      assertThat(identifiers).allMatch(i -> i.getVenueId().equals(venueId));
    }

    @Test
    @DisplayName("应该正确处理只有默认标识符的聚合根")
    void insertAll_aggregateWithDefaultIdentifier_shouldInsertCorrectly() {
      // Given: 创建没有额外标识符的聚合根
      VenueAggregate venue = VenueAggregate.fromOpenAlex("S1", VenueType.JOURNAL, "Journal A");
      venue.withIssnL("1234-S1");
      venue.withPublicationHistory(PublicationHistory.active(2000));

      // When
      repository.insertAll(List.of(venue));

      // Then: 主表有 1 条记录
      assertThat(venueMapper.selectCount(null)).isEqualTo(1);

      // Then: 标识符只有 1 个（OpenAlex ID 由 fromOpenAlex 自动添加）
      assertThat(venueIdentifierMapper.selectCount(null)).isEqualTo(1);
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
      assertThat(venueMapper.selectCount(null)).isZero();

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

  /// 创建测试用的 VenueAggregate。
  private VenueAggregate createVenueAggregate(String openalexId, String displayName) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.withIssnL("1234-" + openalexId);
    venue.withCountryCode("US");
    venue.withOaStatus(true, false);
    venue.withPublicationHistory(PublicationHistory.active(2000));
    return venue;
  }

  /// 创建测试用的 VenueAggregate（指定 ISSN-L）。
  private VenueAggregate createVenueAggregateWithIssnL(
      String openalexId, String displayName, String issnL) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    venue.withIssnL(issnL);
    venue.withCountryCode("US");
    venue.withOaStatus(true, false);
    venue.withPublicationHistory(PublicationHistory.active(2000));
    return venue;
  }

  // ========== updateBatch() 测试说明 ==========
  //
  // updateBatch() 方法内部使用 Db.saveBatch() 和 Db.updateBatchById() 进行批量操作。
  // 这些方法在 @MybatisPlusTest 切片测试中无法正常工作，原因如下：
  //
  // 1. Db.saveBatch() 使用独立的 BATCH SqlSession（ExecutorType.BATCH）
  // 2. 该 SqlSession 与切片测试使用的普通 SqlSession 不共享数据
  // 3. 因此批量插入/更新的数据在同一测试方法中不可见
  //
  // 解决方案：updateBatch() 的集成测试应在 @SpringBootTest E2E 测试中进行，
  // 确保有完整的 Spring 事务管理上下文。
  //
  // 参考：
  // - MyBatis BATCH SqlSession: https://mybatis.org/mybatis-3/sqlmap-xml.html#batch
  // - MyBatis-Plus 批量操作: https://baomidou.com/en/guides/batch-operation/
}
