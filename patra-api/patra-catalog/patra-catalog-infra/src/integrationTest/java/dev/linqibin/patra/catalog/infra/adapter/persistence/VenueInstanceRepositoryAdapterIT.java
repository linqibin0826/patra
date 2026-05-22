package dev.linqibin.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueId;
import dev.linqibin.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueInstanceDao;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// VenueInstanceRepositoryAdapter 集成测试（JPA 版本）。
///
/// 使用 Testcontainers + PostgreSQL 17 测试载体实例仓储操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 PostgreSQL 容器
/// - 测试覆盖：findById、findJournalInstance、save、delete 等方法
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueInstanceRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "dev.linqibin.patra.catalog.infra.persistence.converter")
@ActiveProfiles("test")
@DisplayName("VenueInstanceRepositoryAdapter 集成测试（JPA）")
class VenueInstanceRepositoryAdapterIT {

  @Autowired private VenueInstanceRepositoryAdapter repository;

  @Autowired private VenueInstanceDao jpaRepository;

  // ========== 期刊实例工厂方法 ==========

  private VenueInstanceAggregate createJournalInstance(
      VenueId venueId, String volume, String issue, int year) {
    return VenueInstanceAggregate.forJournal(venueId, volume, issue, year, null, null);
  }

  private VenueInstanceAggregate createJournalInstance(
      VenueId venueId, String volume, String issue, int year, int month) {
    return VenueInstanceAggregate.forJournal(venueId, volume, issue, year, month, null);
  }

  // ========== 书籍实例工厂方法 ==========

  private VenueInstanceAggregate createBookInstance(VenueId venueId, String edition, int year) {
    return VenueInstanceAggregate.forBook(venueId, edition, year);
  }

  // ========== 会议实例工厂方法 ==========

  private VenueInstanceAggregate createConferenceInstance(
      VenueId venueId, String name, LocalDate start, LocalDate end, String location, int year) {
    return VenueInstanceAggregate.forConference(venueId, name, start, end, location, year);
  }

  @Nested
  @DisplayName("findById 测试")
  class FindByIdTests {

    @Test
    @DisplayName("存在的 ID - 应该返回聚合根")
    void findById_exists_shouldReturnAggregate() {
      // Given
      var instance = createJournalInstance(VenueId.of(1001L), "45", "3", 2024);
      repository.save(instance);

      // When
      Optional<VenueInstanceAggregate> found = repository.findById(instance.getId().value());

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getVenueId()).isEqualTo(VenueId.of(1001L));
      assertThat(found.get().getVolume()).isEqualTo("45");
      assertThat(found.get().getIssue()).isEqualTo("3");
    }

    @Test
    @DisplayName("不存在的 ID - 应该返回 empty")
    void findById_notExists_shouldReturnEmpty() {
      // When
      Optional<VenueInstanceAggregate> found = repository.findById(999999L);

      // Then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("null ID - 应该返回 empty")
    void findById_null_shouldReturnEmpty() {
      // When
      Optional<VenueInstanceAggregate> found = repository.findById(null);

      // Then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByVenueIds 测试")
  class FindByVenueIdsTests {

    @Test
    @DisplayName("批量查询 - 应该返回按 venueId 分组的结果")
    void findByVenueIds_shouldReturnGroupedResult() {
      // Given
      repository.save(createJournalInstance(VenueId.of(1001L), "45", "1", 2024));
      repository.save(createJournalInstance(VenueId.of(1001L), "45", "2", 2024));
      repository.save(createJournalInstance(VenueId.of(1002L), "10", "1", 2024));

      // When
      Map<Long, List<VenueInstanceAggregate>> result =
          repository.findByVenueIds(Set.of(1001L, 1002L, 1003L));

      // Then
      assertThat(result).hasSize(2);
      assertThat(result.get(1001L)).hasSize(2);
      assertThat(result.get(1002L)).hasSize(1);
      assertThat(result.get(1003L)).isNull();
    }

    @Test
    @DisplayName("空集合 - 应该返回空 Map")
    void findByVenueIds_empty_shouldReturnEmptyMap() {
      // When
      Map<Long, List<VenueInstanceAggregate>> result = repository.findByVenueIds(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null - 应该返回空 Map")
    void findByVenueIds_null_shouldReturnEmptyMap() {
      // When
      Map<Long, List<VenueInstanceAggregate>> result = repository.findByVenueIds(null);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findJournalInstance 测试")
  class FindJournalInstanceTests {

    @Test
    @DisplayName("精确匹配卷期 - 应该返回实例")
    void findJournalInstance_exactMatch_shouldReturn() {
      // Given
      repository.save(createJournalInstance(VenueId.of(1001L), "45", "3", 2024));

      // When
      Optional<VenueInstanceAggregate> found =
          repository.findJournalInstance(1001L, "45", "3", 2024);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getVolume()).isEqualTo("45");
      assertThat(found.get().getIssue()).isEqualTo("3");
    }

    @Test
    @DisplayName("只有卷号没有期号 - 应该正确匹配")
    void findJournalInstance_volumeOnly_shouldMatch() {
      // Given
      repository.save(createJournalInstance(VenueId.of(1001L), "2024", null, 2024));

      // When
      Optional<VenueInstanceAggregate> found =
          repository.findJournalInstance(1001L, "2024", null, 2024);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getVolume()).isEqualTo("2024");
      assertThat(found.get().getIssue()).isNull();
    }

    @Test
    @DisplayName("不存在的实例 - 应该返回 empty")
    void findJournalInstance_notExists_shouldReturnEmpty() {
      // When
      Optional<VenueInstanceAggregate> found =
          repository.findJournalInstance(1001L, "99", "99", 2024);

      // Then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("findBookInstance 测试")
  class FindBookInstanceTests {

    @Test
    @DisplayName("精确匹配版次 - 应该返回实例")
    void findBookInstance_exactMatch_shouldReturn() {
      // Given
      repository.save(createBookInstance(VenueId.of(2001L), "3rd Edition", 2024));

      // When
      Optional<VenueInstanceAggregate> found =
          repository.findBookInstance(2001L, "3rd Edition", 2024);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getEdition()).isEqualTo("3rd Edition");
    }

    @Test
    @DisplayName("版次为 null - 应该正确匹配")
    void findBookInstance_nullEdition_shouldMatch() {
      // Given
      repository.save(createBookInstance(VenueId.of(2001L), null, 2024));

      // When
      Optional<VenueInstanceAggregate> found = repository.findBookInstance(2001L, null, 2024);

      // Then
      assertThat(found).isPresent();
    }
  }

  @Nested
  @DisplayName("findConferenceInstance 测试")
  class FindConferenceInstanceTests {

    @Test
    @DisplayName("精确匹配会议名称 - 应该返回实例")
    void findConferenceInstance_exactMatch_shouldReturn() {
      // Given
      repository.save(
          createConferenceInstance(
              VenueId.of(3001L),
              "AAAI 2024",
              LocalDate.of(2024, 2, 20),
              LocalDate.of(2024, 2, 27),
              "Vancouver, Canada",
              2024));

      // When
      Optional<VenueInstanceAggregate> found =
          repository.findConferenceInstance(3001L, "AAAI 2024", 2024);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getConferenceName()).isEqualTo("AAAI 2024");
      assertThat(found.get().getConferenceLocation()).isEqualTo("Vancouver, Canada");
    }
  }

  @Nested
  @DisplayName("save 测试")
  class SaveTests {

    @Test
    @DisplayName("新建实例 - 应该分配 ID")
    void save_newInstance_shouldAssignId() {
      // Given
      var instance = createJournalInstance(VenueId.of(1001L), "45", "3", 2024);
      assertThat(instance.getId()).isNull();

      // When
      repository.save(instance);

      // Then
      assertThat(instance.getId()).isNotNull();
      assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("带完整出版日期 - 应该保存所有字段")
    void save_withFullDate_shouldSaveAllFields() {
      // Given
      var instance = createJournalInstance(VenueId.of(1001L), "45", "3", 2024, 6);

      // When
      repository.save(instance);

      // Then
      var found = repository.findById(instance.getId().value()).orElseThrow();
      assertThat(found.getPublicationYear()).isEqualTo(2024);
      assertThat(found.getPublicationMonth()).isEqualTo(6);
    }

    @Test
    @DisplayName("null 实例 - 应该抛出异常")
    void save_null_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> repository.save(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("实例不能为 null");
    }
  }

  @Nested
  @DisplayName("insertAll 测试")
  class InsertAllTests {

    @Test
    @DisplayName("批量插入 - 应该全部成功并回填 ID")
    void insertAll_shouldInsertAllAndBackfillIds() {
      // Given
      List<VenueInstanceAggregate> instances =
          List.of(
              createJournalInstance(VenueId.of(1001L), "45", "1", 2024),
              createJournalInstance(VenueId.of(1001L), "45", "2", 2024),
              createJournalInstance(VenueId.of(1001L), "45", "3", 2024));

      // When
      repository.insertAll(instances);

      // Then
      assertThat(jpaRepository.count()).isEqualTo(3);
      for (VenueInstanceAggregate instance : instances) {
        assertThat(instance.getId()).isNotNull();
      }
    }

    @Test
    @DisplayName("空列表 - 应该不执行任何操作")
    void insertAll_empty_shouldDoNothing() {
      // When
      repository.insertAll(List.of());

      // Then
      assertThat(jpaRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("null - 应该不执行任何操作")
    void insertAll_null_shouldDoNothing() {
      // When
      repository.insertAll(null);

      // Then
      assertThat(jpaRepository.count()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("deleteById 测试")
  class DeleteByIdTests {

    @Test
    @DisplayName("存在的 ID - 应该删除并返回 true")
    void deleteById_exists_shouldDeleteAndReturnTrue() {
      // Given
      var instance = createJournalInstance(VenueId.of(1001L), "45", "3", 2024);
      repository.save(instance);
      assertThat(jpaRepository.count()).isEqualTo(1);

      // When
      boolean deleted = repository.deleteById(instance.getId().value());

      // Then
      assertThat(deleted).isTrue();
      assertThat(jpaRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("不存在的 ID - 应该返回 false")
    void deleteById_notExists_shouldReturnFalse() {
      // When
      boolean deleted = repository.deleteById(999999L);

      // Then
      assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("null ID - 应该返回 false")
    void deleteById_null_shouldReturnFalse() {
      // When
      boolean deleted = repository.deleteById(null);

      // Then
      assertThat(deleted).isFalse();
    }
  }

  @Nested
  @DisplayName("deleteByVenueId 测试")
  class DeleteByVenueIdTests {

    @Test
    @DisplayName("存在关联实例 - 应该删除全部并返回数量")
    void deleteByVenueId_exists_shouldDeleteAllAndReturnCount() {
      // Given
      repository.save(createJournalInstance(VenueId.of(1001L), "45", "1", 2024));
      repository.save(createJournalInstance(VenueId.of(1001L), "45", "2", 2024));
      repository.save(createJournalInstance(VenueId.of(1002L), "10", "1", 2024));
      assertThat(jpaRepository.count()).isEqualTo(3);

      // When
      int deleted = repository.deleteByVenueId(1001L);

      // Then
      assertThat(deleted).isEqualTo(2);
      assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("不存在关联实例 - 应该返回 0")
    void deleteByVenueId_notExists_shouldReturnZero() {
      // When
      int deleted = repository.deleteByVenueId(999999L);

      // Then
      assertThat(deleted).isEqualTo(0);
    }

    @Test
    @DisplayName("null venueId - 应该返回 0")
    void deleteByVenueId_null_shouldReturnZero() {
      // When
      int deleted = repository.deleteByVenueId(null);

      // Then
      assertThat(deleted).isEqualTo(0);
    }
  }
}
