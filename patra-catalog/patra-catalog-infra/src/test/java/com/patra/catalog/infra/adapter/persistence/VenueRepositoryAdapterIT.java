package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.port.VenueRepository.VenueData;
import com.patra.catalog.domain.port.VenueRepository.VenueIdentifierData;
import com.patra.catalog.domain.port.VenueRepository.VenueMetricsData;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMetricsMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.util.List;
import java.util.Optional;
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
/// - 测试覆盖：CRUD、批量操作、标识符查询、指标查询
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

  // ========== 测试数据 ==========

  private static final String OPENALEX_ID = "S1234567890";
  private static final String DISPLAY_NAME = "Nature";
  private static final String ISSN_L = "0028-0836";
  private static final String ISSN = "1476-4687";
  private static final String NLM_ID = "0410462";

  /// 创建测试用的 VenueData。
  private VenueData createTestVenueData() {
    return new VenueData(
        null, // id
        "JOURNAL", // venueType
        DISPLAY_NAME,
        "Nat.", // abbreviatedTitle
        "https://nature.com", // homepageUrl
        OPENALEX_ID,
        ISSN_L,
        null, // hostOrganizationId
        null, // hostOrganizationName
        "GB", // countryCode
        true, // isOa
        true, // isInDoaj
        true, // isCore
        1500, // worksCount
        25000, // citedByCount
        100, // hIndex
        50, // i10Index
        "OPENALEX", // provenanceCode
        null // version
        );
  }

  // ========== save() 测试 ==========

  @Nested
  @DisplayName("save() 测试")
  class SaveTests {

    @Test
    @DisplayName("应该正确保存新的载体并返回包含 ID 的数据")
    void saveShouldInsertAndReturnWithId() {
      // Given
      VenueData venue = createTestVenueData();
      assertThat(venue.id()).isNull();

      // When
      VenueData saved = repository.save(venue);

      // Then
      assertThat(saved.id()).isNotNull().isPositive();
      assertThat(saved.displayName()).isEqualTo(DISPLAY_NAME);

      // 验证数据库记录
      long count = venueMapper.selectCount(null);
      assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("保存时应该正确写入所有字段")
    void saveShouldPersistAllFields() {
      // Given
      VenueData venue = createTestVenueData();

      // When
      VenueData saved = repository.save(venue);

      // Then - 通过 findById 验证
      Optional<VenueData> found = repository.findById(saved.id());
      assertThat(found).isPresent();

      VenueData loaded = found.get();
      assertThat(loaded.openalexId()).isEqualTo(OPENALEX_ID);
      assertThat(loaded.displayName()).isEqualTo(DISPLAY_NAME);
      assertThat(loaded.venueType()).isEqualTo("JOURNAL");
      assertThat(loaded.issnL()).isEqualTo(ISSN_L);
      assertThat(loaded.countryCode()).isEqualTo("GB");
      assertThat(loaded.isOa()).isTrue();
      assertThat(loaded.isInDoaj()).isTrue();
      assertThat(loaded.isCore()).isTrue();
    }
  }

  // ========== findById() 测试 ==========

  @Nested
  @DisplayName("findById() 测试")
  class FindByIdTests {

    @Test
    @DisplayName("存在时应该返回载体")
    void findByIdShouldReturnWhenExists() {
      // Given
      VenueData venue = createTestVenueData();
      VenueData saved = repository.save(venue);

      // When
      Optional<VenueData> found = repository.findById(saved.id());

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().id()).isEqualTo(saved.id());
    }

    @Test
    @DisplayName("不存在时应该返回空")
    void findByIdShouldReturnEmptyWhenNotExists() {
      // When
      Optional<VenueData> found = repository.findById(999999L);

      // Then
      assertThat(found).isEmpty();
    }
  }

  // ========== findByOpenalexId() 测试 ==========

  @Nested
  @DisplayName("findByOpenalexId() 测试")
  class FindByOpenalexIdTests {

    @Test
    @DisplayName("存在时应该返回载体")
    void findByOpenalexIdShouldReturnWhenExists() {
      // Given
      VenueData venue = createTestVenueData();
      repository.save(venue);

      // When
      Optional<VenueData> found = repository.findByOpenalexId(OPENALEX_ID);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().openalexId()).isEqualTo(OPENALEX_ID);
    }

    @Test
    @DisplayName("不存在时应该返回空")
    void findByOpenalexIdShouldReturnEmptyWhenNotExists() {
      // When
      Optional<VenueData> found = repository.findByOpenalexId("S9999999999");

      // Then
      assertThat(found).isEmpty();
    }
  }

  // ========== findByIssnL() 测试 ==========

  @Nested
  @DisplayName("findByIssnL() 测试")
  class FindByIssnLTests {

    @Test
    @DisplayName("存在时应该返回载体")
    void findByIssnLShouldReturnWhenExists() {
      // Given
      VenueData venue = createTestVenueData();
      repository.save(venue);

      // When
      Optional<VenueData> found = repository.findByIssnL(ISSN_L);

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().issnL()).isEqualTo(ISSN_L);
    }

    @Test
    @DisplayName("不存在时应该返回空")
    void findByIssnLShouldReturnEmptyWhenNotExists() {
      // When
      Optional<VenueData> found = repository.findByIssnL("9999-9999");

      // Then
      assertThat(found).isEmpty();
    }
  }

  // ========== saveAll() 测试 ==========

  @Nested
  @DisplayName("saveAll() 测试")
  class SaveAllTests {

    @Test
    @DisplayName("应该批量保存多个载体")
    void saveAllShouldInsertMultiple() {
      // Given
      VenueData venue1 =
          new VenueData(
              null,
              "JOURNAL",
              "Journal A",
              null,
              null,
              "S0000000001",
              null,
              null,
              null,
              null,
              true,
              true,
              true,
              100,
              1000,
              10,
              5,
              "OPENALEX",
              null);
      VenueData venue2 =
          new VenueData(
              null,
              "REPOSITORY",
              "Repository B",
              null,
              null,
              "S0000000002",
              null,
              null,
              null,
              null,
              false,
              false,
              false,
              50,
              500,
              5,
              2,
              "OPENALEX",
              null);
      VenueData venue3 =
          new VenueData(
              null,
              "CONFERENCE",
              "Conference C",
              null,
              null,
              "S0000000003",
              null,
              null,
              null,
              null,
              false,
              false,
              false,
              30,
              300,
              3,
              1,
              "OPENALEX",
              null);

      // When
      List<VenueData> saved = repository.saveAll(List.of(venue1, venue2, venue3));

      // Then
      assertThat(saved).hasSize(3);
      assertThat(saved).allMatch(v -> v.id() != null && v.id() > 0);

      // 验证数据库记录
      long count = venueMapper.selectCount(null);
      assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("空列表应该返回空结果")
    void saveAllShouldReturnEmptyForEmptyList() {
      // When
      List<VenueData> saved = repository.saveAll(List.of());

      // Then
      assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("null 列表应该抛出 NullPointerException")
    void saveAllShouldThrowForNullList() {
      // When & Then
      assertThatNullPointerException().isThrownBy(() -> repository.saveAll(null));
    }
  }

  // ========== saveIdentifiers() 测试 ==========

  @Nested
  @DisplayName("saveIdentifiers() 测试")
  class SaveIdentifiersTests {

    @Test
    @DisplayName("应该正确保存标识符")
    void saveIdentifiersShouldInsert() {
      // Given - 先保存载体
      VenueData venue = createTestVenueData();
      VenueData saved = repository.save(venue);
      Long venueId = saved.id();

      // 创建标识符
      VenueIdentifierData issn = new VenueIdentifierData(null, venueId, "ISSN", ISSN, true);
      VenueIdentifierData nlm = new VenueIdentifierData(null, venueId, "NLM", NLM_ID, false);

      // When
      repository.saveIdentifiers(List.of(issn, nlm));

      // Then
      List<VenueIdentifierData> identifiers = repository.findIdentifiersByVenueId(venueId);
      assertThat(identifiers).hasSize(2);

      // 验证 ISSN
      Optional<VenueIdentifierData> savedIssn =
          identifiers.stream().filter(i -> "ISSN".equals(i.identifierType())).findFirst();
      assertThat(savedIssn).isPresent();
      assertThat(savedIssn.get().identifierValue()).isEqualTo(ISSN);
      assertThat(savedIssn.get().isPrimary()).isTrue();

      // 验证 NLM
      Optional<VenueIdentifierData> savedNlm =
          identifiers.stream().filter(i -> "NLM".equals(i.identifierType())).findFirst();
      assertThat(savedNlm).isPresent();
      assertThat(savedNlm.get().identifierValue()).isEqualTo(NLM_ID);
    }

    @Test
    @DisplayName("空列表应该不抛出异常")
    void saveIdentifiersShouldNotThrowForEmptyList() {
      // When & Then - 不应抛出异常
      repository.saveIdentifiers(List.of());

      long count = venueIdentifierMapper.selectCount(null);
      assertThat(count).isZero();
    }
  }

  // ========== saveMetrics() 测试 ==========

  @Nested
  @DisplayName("saveMetrics() 测试")
  class SaveMetricsTests {

    @Test
    @DisplayName("应该正确保存年度指标")
    void saveMetricsShouldInsert() {
      // Given - 先保存载体
      VenueData venue = createTestVenueData();
      VenueData saved = repository.save(venue);
      Long venueId = saved.id();

      // 创建年度指标
      VenueMetricsData m2024 = new VenueMetricsData(null, venueId, 2024, 1500, 25000, 800);
      VenueMetricsData m2023 = new VenueMetricsData(null, venueId, 2023, 1400, 22000, 700);

      // When
      repository.saveMetrics(List.of(m2024, m2023));

      // Then
      List<VenueMetricsData> metrics = repository.findMetricsByVenueId(venueId);
      assertThat(metrics).hasSize(2);

      // 验证 2024 年指标
      Optional<VenueMetricsData> saved2024 =
          metrics.stream().filter(m -> m.year() == 2024).findFirst();
      assertThat(saved2024).isPresent();
      assertThat(saved2024.get().worksCount()).isEqualTo(1500);
      assertThat(saved2024.get().citedByCount()).isEqualTo(25000);
      assertThat(saved2024.get().oaWorksCount()).isEqualTo(800);
    }

    @Test
    @DisplayName("空列表应该不抛出异常")
    void saveMetricsShouldNotThrowForEmptyList() {
      // When & Then - 不应抛出异常
      repository.saveMetrics(List.of());

      long count = venueMetricsMapper.selectCount(null);
      assertThat(count).isZero();
    }
  }

  // ========== findVenueIdByIdentifier() 测试 ==========

  @Nested
  @DisplayName("findVenueIdByIdentifier() 测试")
  class FindVenueIdByIdentifierTests {

    @Test
    @DisplayName("应该通过标识符查找载体 ID")
    void findVenueIdByIdentifierShouldReturnId() {
      // Given
      VenueData venue = createTestVenueData();
      VenueData saved = repository.save(venue);
      Long venueId = saved.id();
      repository.saveIdentifiers(
          List.of(new VenueIdentifierData(null, venueId, "ISSN", ISSN, true)));

      // When
      Optional<Long> foundId = repository.findVenueIdByIdentifier("ISSN", ISSN);

      // Then
      assertThat(foundId).contains(venueId);
    }

    @Test
    @DisplayName("不存在时应该返回空")
    void findVenueIdByIdentifierShouldReturnEmptyWhenNotExists() {
      // When
      Optional<Long> foundId = repository.findVenueIdByIdentifier("ISSN", "9999-9999");

      // Then
      assertThat(foundId).isEmpty();
    }
  }

  // ========== findMetricsByVenueIdAndYear() 测试 ==========

  @Nested
  @DisplayName("findMetricsByVenueIdAndYear() 测试")
  class FindMetricsByVenueIdAndYearTests {

    @Test
    @DisplayName("应该返回指定年份的指标")
    void findMetricsByVenueIdAndYearShouldReturnForYear() {
      // Given
      VenueData venue = createTestVenueData();
      VenueData saved = repository.save(venue);
      Long venueId = saved.id();
      repository.saveMetrics(
          List.of(
              new VenueMetricsData(null, venueId, 2024, 1500, 25000, null),
              new VenueMetricsData(null, venueId, 2023, 1400, 22000, null)));

      // When
      Optional<VenueMetricsData> metrics = repository.findMetricsByVenueIdAndYear(venueId, 2024);

      // Then
      assertThat(metrics).isPresent();
      assertThat(metrics.get().year()).isEqualTo(2024);
      assertThat(metrics.get().worksCount()).isEqualTo(1500);
    }

    @Test
    @DisplayName("年份不存在时应该返回空")
    void findMetricsByVenueIdAndYearShouldReturnEmptyWhenYearNotExists() {
      // Given
      VenueData venue = createTestVenueData();
      VenueData saved = repository.save(venue);
      Long venueId = saved.id();
      repository.saveMetrics(List.of(new VenueMetricsData(null, venueId, 2024, 1500, 25000, null)));

      // When
      Optional<VenueMetricsData> metrics = repository.findMetricsByVenueIdAndYear(venueId, 2020);

      // Then
      assertThat(metrics).isEmpty();
    }
  }

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
    @DisplayName("有单条数据 - 应该返回 true")
    void hasAnyData_withSingleRecord_shouldReturnTrue() {
      // Given: 插入单条数据
      VenueData venue = createTestVenueData();
      repository.save(venue);

      // When & Then
      assertThat(repository.hasAnyData()).isTrue();
    }

    @Test
    @DisplayName("有多条数据 - 应该返回 true")
    void hasAnyData_withMultipleRecords_shouldReturnTrue() {
      // Given: 插入多条数据
      VenueData venue1 =
          new VenueData(
              null,
              "JOURNAL",
              "Journal A",
              null,
              null,
              "S0000000001",
              null,
              null,
              null,
              null,
              true,
              true,
              true,
              100,
              1000,
              10,
              5,
              "OPENALEX",
              null);
      VenueData venue2 =
          new VenueData(
              null,
              "REPOSITORY",
              "Repository B",
              null,
              null,
              "S0000000002",
              null,
              null,
              null,
              null,
              false,
              false,
              false,
              50,
              500,
              5,
              2,
              "OPENALEX",
              null);
      VenueData venue3 =
          new VenueData(
              null,
              "CONFERENCE",
              "Conference C",
              null,
              null,
              "S0000000003",
              null,
              null,
              null,
              null,
              false,
              false,
              false,
              30,
              300,
              3,
              1,
              "OPENALEX",
              null);
      repository.saveAll(List.of(venue1, venue2, venue3));

      // When & Then
      assertThat(repository.hasAnyData()).isTrue();
    }
  }
}
