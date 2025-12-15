package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.aggregate.VenueRatingAggregate;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.jpa.VenueRatingJpaRepository;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import java.math.BigDecimal;
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

/// VenueRatingRepositoryAdapter 集成测试（JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试载体评级仓储操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：findById、findByVenueId、save、delete 等方法
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  VenueRatingRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "com.patra.catalog.infra.persistence.jpa.converter")
@ActiveProfiles("test")
@DisplayName("VenueRatingRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueRatingRepositoryAdapterIT {

  @Autowired private VenueRatingRepositoryAdapter repository;

  @Autowired private VenueRatingJpaRepository jpaRepository;

  // ========== findById() 测试 ==========

  @Nested
  @DisplayName("findById() 测试")
  class FindByIdTests {

    @Test
    @DisplayName("ID 不存在 - 应该返回 empty")
    void findById_notFound_shouldReturnEmpty() {
      // When
      var result = repository.findById(VenueRatingId.of(99999L));

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null ID - 应该返回 empty")
    void findById_nullId_shouldReturnEmpty() {
      // When
      var result = repository.findById(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ID 存在 - 应该返回聚合根")
    void findById_found_shouldReturnAggregate() {
      // Given: 插入一条记录
      var rating = createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.778"));
      var saved = repository.save(rating);

      // When
      var result = repository.findById(saved.getId());

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getVenueId()).isEqualTo(VenueId.of(1001L));
      assertThat(result.get().getYear()).isEqualTo(2024);
      assertThat(result.get().getRatingSystem()).isEqualTo(RatingSystem.JCR);
      assertThat(result.get().getQuartile()).isEqualTo("Q1");
      assertThat(result.get().getImpactScore()).isEqualByComparingTo(new BigDecimal("42.778"));
    }
  }

  // ========== findByVenueIdAndYearAndRatingSystem() 测试 ==========

  @Nested
  @DisplayName("findByVenueIdAndYearAndRatingSystem() 测试")
  class FindByBusinessKeyTests {

    @Test
    @DisplayName("业务唯一键存在 - 应该返回聚合根")
    void findByBusinessKey_found_shouldReturnAggregate() {
      // Given
      var rating = createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.778"));
      repository.save(rating);

      // When
      var result = repository.findByVenueIdAndYearAndRatingSystem(1001L, 2024, RatingSystem.JCR);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getVenueId()).isEqualTo(VenueId.of(1001L));
      assertThat(result.get().getYear()).isEqualTo(2024);
      assertThat(result.get().getRatingSystem()).isEqualTo(RatingSystem.JCR);
    }

    @Test
    @DisplayName("业务唯一键不存在 - 应该返回 empty")
    void findByBusinessKey_notFound_shouldReturnEmpty() {
      // When
      var result = repository.findByVenueIdAndYearAndRatingSystem(9999L, 2024, RatingSystem.JCR);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null venueId - 应该返回 empty")
    void findByBusinessKey_nullVenueId_shouldReturnEmpty() {
      // When
      var result = repository.findByVenueIdAndYearAndRatingSystem(null, 2024, RatingSystem.JCR);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null ratingSystem - 应该返回 empty")
    void findByBusinessKey_nullRatingSystem_shouldReturnEmpty() {
      // When
      var result = repository.findByVenueIdAndYearAndRatingSystem(1001L, 2024, null);

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ========== findByVenueId() 测试 ==========

  @Nested
  @DisplayName("findByVenueId() 测试")
  class FindByVenueIdTests {

    @Test
    @DisplayName("有评级数据 - 应该返回所有评级")
    void findByVenueId_found_shouldReturnAllRatings() {
      // Given: 同一 Venue 的多条评级
      repository.save(createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.0")));
      repository.save(createJcrRating(VenueId.of(1001L), 2023, "Q2", new BigDecimal("38.0")));
      repository.save(createCasRating(VenueId.of(1001L), 2024, "1区", new BigDecimal("45.0")));

      // When
      List<VenueRatingAggregate> result = repository.findByVenueId(1001L);

      // Then
      assertThat(result).hasSize(3);
      assertThat(result).allMatch(r -> r.getVenueId().equals(VenueId.of(1001L)));
    }

    @Test
    @DisplayName("无评级数据 - 应该返回空列表")
    void findByVenueId_notFound_shouldReturnEmptyList() {
      // When
      List<VenueRatingAggregate> result = repository.findByVenueId(9999L);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null venueId - 应该返回空列表")
    void findByVenueId_nullVenueId_shouldReturnEmptyList() {
      // When
      List<VenueRatingAggregate> result = repository.findByVenueId(null);

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ========== findByVenueIdAndRatingSystem() 测试 ==========

  @Nested
  @DisplayName("findByVenueIdAndRatingSystem() 测试")
  class FindByVenueIdAndRatingSystemTests {

    @Test
    @DisplayName("有匹配数据 - 应该返回指定评价体系的评级")
    void findByVenueIdAndRatingSystem_found_shouldReturnMatchingRatings() {
      // Given
      repository.save(createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.0")));
      repository.save(createJcrRating(VenueId.of(1001L), 2023, "Q2", new BigDecimal("38.0")));
      repository.save(createCasRating(VenueId.of(1001L), 2024, "1区", new BigDecimal("45.0")));

      // When
      List<VenueRatingAggregate> result =
          repository.findByVenueIdAndRatingSystem(1001L, RatingSystem.JCR);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(r -> r.getRatingSystem().isJcr());
    }

    @Test
    @DisplayName("无匹配数据 - 应该返回空列表")
    void findByVenueIdAndRatingSystem_notFound_shouldReturnEmptyList() {
      // When
      List<VenueRatingAggregate> result =
          repository.findByVenueIdAndRatingSystem(9999L, RatingSystem.JCR);

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ========== findByVenueIdAndYear() 测试 ==========

  @Nested
  @DisplayName("findByVenueIdAndYear() 测试")
  class FindByVenueIdAndYearTests {

    @Test
    @DisplayName("有匹配数据 - 应该返回指定年份的评级")
    void findByVenueIdAndYear_found_shouldReturnMatchingRatings() {
      // Given
      repository.save(createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.0")));
      repository.save(createCasRating(VenueId.of(1001L), 2024, "1区", new BigDecimal("45.0")));
      repository.save(createJcrRating(VenueId.of(1001L), 2023, "Q2", new BigDecimal("38.0")));

      // When
      List<VenueRatingAggregate> result = repository.findByVenueIdAndYear(1001L, 2024);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(r -> r.getYear() == 2024);
    }

    @Test
    @DisplayName("无匹配数据 - 应该返回空列表")
    void findByVenueIdAndYear_notFound_shouldReturnEmptyList() {
      // When
      List<VenueRatingAggregate> result = repository.findByVenueIdAndYear(1001L, 2020);

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ========== findByVenueIds() 测试 ==========

  @Nested
  @DisplayName("findByVenueIds() 测试")
  class FindByVenueIdsTests {

    @Test
    @DisplayName("有匹配数据 - 应该返回按 venueId 分组的评级")
    void findByVenueIds_found_shouldReturnGroupedRatings() {
      // Given
      repository.save(createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.0")));
      repository.save(createJcrRating(VenueId.of(1002L), 2024, "Q2", new BigDecimal("35.0")));
      repository.save(createJcrRating(VenueId.of(1003L), 2024, "Q3", new BigDecimal("25.0")));

      // When
      Map<Long, List<VenueRatingAggregate>> result =
          repository.findByVenueIds(Set.of(1001L, 1002L, 9999L));

      // Then
      assertThat(result).hasSize(2);
      assertThat(result.get(1001L)).hasSize(1);
      assertThat(result.get(1002L)).hasSize(1);
      assertThat(result.get(9999L)).isNull();
    }

    @Test
    @DisplayName("空集合 - 应该返回空 Map")
    void findByVenueIds_emptyInput_shouldReturnEmptyMap() {
      // When
      Map<Long, List<VenueRatingAggregate>> result = repository.findByVenueIds(Set.of());

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null - 应该返回空 Map")
    void findByVenueIds_nullInput_shouldReturnEmptyMap() {
      // When
      Map<Long, List<VenueRatingAggregate>> result = repository.findByVenueIds(null);

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ========== save() 测试 ==========

  @Nested
  @DisplayName("save() 测试")
  class SaveTests {

    @Test
    @DisplayName("新建聚合根 - 应该插入并分配 ID")
    void save_newAggregate_shouldInsertAndAssignId() {
      // Given
      var rating = createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.778"));
      assertThat(rating.isTransient()).isTrue();

      // When
      VenueRatingAggregate saved = repository.save(rating);

      // Then
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.isTransient()).isFalse();
      assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("更新聚合根 - 应该更新数据")
    void save_dirtyAggregate_shouldUpdateAndClearDirty() {
      // Given: 先插入
      var rating = createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.778"));
      VenueRatingAggregate saved = repository.save(rating);

      // Given: 修改保存后的聚合根
      saved.updateQuartileAndScore("Q2", new BigDecimal("45.0"));
      assertThat(saved.isDirty()).isTrue();

      // When
      VenueRatingAggregate updated = repository.save(saved);

      // Then: 返回的新聚合根应该是干净的
      assertThat(updated.isDirty()).isFalse();

      // 验证数据库中的值
      var found = repository.findById(updated.getId()).orElseThrow();
      assertThat(found.getQuartile()).isEqualTo("Q2");
      assertThat(found.getImpactScore()).isEqualByComparingTo(new BigDecimal("45.0"));
    }

    @Test
    @DisplayName("null 聚合根 - 应该抛出异常")
    void save_nullAggregate_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> repository.save(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("聚合根不能为 null");
    }
  }

  // ========== saveAll() 测试 ==========

  @Nested
  @DisplayName("saveAll() 测试")
  class SaveAllTests {

    @Test
    @DisplayName("批量保存多个聚合根 - 应该全部插入并分配 ID")
    void saveAll_multipleAggregates_shouldInsertAll() {
      // Given
      var ratings =
          List.of(
              createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.0")),
              createJcrRating(VenueId.of(1002L), 2024, "Q2", new BigDecimal("35.0")),
              createCasRating(VenueId.of(1003L), 2024, "1区", new BigDecimal("45.0")));

      // When
      repository.saveAll(ratings);

      // Then
      assertThat(jpaRepository.count()).isEqualTo(3);
      assertThat(ratings).allMatch(r -> r.getId() != null);
      assertThat(ratings).allMatch(r -> !r.isTransient());
    }

    @Test
    @DisplayName("空列表 - 不应该抛出异常")
    void saveAll_emptyList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.saveAll(List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null 列表 - 不应该抛出异常")
    void saveAll_nullList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.saveAll(null)).doesNotThrowAnyException();
    }
  }

  // ========== deleteById() 测试 ==========

  @Nested
  @DisplayName("deleteById() 测试")
  class DeleteByIdTests {

    @Test
    @DisplayName("ID 存在 - 应该删除记录")
    void deleteById_found_shouldDelete() {
      // Given
      var rating = createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.778"));
      VenueRatingAggregate saved = repository.save(rating);
      assertThat(jpaRepository.count()).isEqualTo(1);

      // When
      repository.deleteById(saved.getId());

      // Then
      assertThat(jpaRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("ID 不存在 - 不应该抛出异常")
    void deleteById_notFound_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.deleteById(VenueRatingId.of(99999L)))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null ID - 不应该抛出异常")
    void deleteById_nullId_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.deleteById(null)).doesNotThrowAnyException();
    }
  }

  // ========== deleteByVenueId() 测试 ==========

  @Nested
  @DisplayName("deleteByVenueId() 测试")
  class DeleteByVenueIdTests {

    @Test
    @DisplayName("有匹配数据 - 应该删除所有匹配记录")
    void deleteByVenueId_found_shouldDeleteAll() {
      // Given
      repository.save(createJcrRating(VenueId.of(1001L), 2024, "Q1", new BigDecimal("42.0")));
      repository.save(createJcrRating(VenueId.of(1001L), 2023, "Q2", new BigDecimal("38.0")));
      repository.save(createJcrRating(VenueId.of(1002L), 2024, "Q3", new BigDecimal("25.0")));
      assertThat(jpaRepository.count()).isEqualTo(3);

      // When
      repository.deleteByVenueId(1001L);

      // Then
      assertThat(jpaRepository.count()).isEqualTo(1);
      assertThat(repository.findByVenueId(1001L)).isEmpty();
      assertThat(repository.findByVenueId(1002L)).hasSize(1);
    }

    @Test
    @DisplayName("无匹配数据 - 不应该抛出异常")
    void deleteByVenueId_notFound_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.deleteByVenueId(99999L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null venueId - 不应该抛出异常")
    void deleteByVenueId_nullVenueId_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.deleteByVenueId(null)).doesNotThrowAnyException();
    }
  }

  // ========== 辅助方法 ==========

  /// 创建 JCR 评级聚合根。
  private VenueRatingAggregate createJcrRating(
      VenueId venueId, int year, String quartile, BigDecimal impactFactor) {
    return VenueRatingAggregate.create(venueId, year, RatingSystem.JCR, quartile, impactFactor);
  }

  /// 创建中科院分区评级聚合根。
  private VenueRatingAggregate createCasRating(
      VenueId venueId, int year, String partition, BigDecimal compositeIf) {
    return VenueRatingAggregate.create(venueId, year, RatingSystem.CAS, partition, compositeIf);
  }
}
