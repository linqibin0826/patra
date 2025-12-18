package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.AffiliationAggregate;
import com.patra.catalog.domain.model.enums.AffiliationType;
import com.patra.catalog.domain.model.vo.affiliation.GridId;
import com.patra.catalog.domain.model.vo.affiliation.RorId;
import com.patra.catalog.infra.adapter.persistence.dao.AffiliationDao;
import com.patra.catalog.infra.adapter.persistence.entity.AffiliationEntity;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 机构仓储实现集成测试（JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试 CRUD 操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：save(), saveBatch(), findByRorId(), findByGridId(), hasAnyData() 等场景
///
/// **重点测试场景**：
///
/// - ROR ID 和 GRID ID 唯一性约束
/// - 机构类型枚举（AffiliationType）的正确映射
/// - 批量保存功能
/// - 地理位置信息
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AffiliationRepositoryAdapter.class, JpaAuditingConfig.class})
@ComponentScan(basePackages = "com.patra.catalog.infra.adapter.persistence.converter")
@ActiveProfiles("test")
@DisplayName("AffiliationRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AffiliationRepositoryAdapterIT {

  @Autowired private AffiliationRepositoryAdapter affiliationRepository;

  @Autowired private AffiliationDao jpaRepository;

  @Nested
  @DisplayName("save() 方法测试")
  class SaveTests {

    @Test
    @DisplayName("保存单个机构 - 应该正确插入到数据库")
    void save_singleAffiliation_shouldInsertSuccessfully() {
      // Given: 创建机构聚合根
      AffiliationAggregate affiliation = AffiliationAggregate.create("Harvard University");

      // When: 保存机构
      AffiliationAggregate saved = affiliationRepository.save(affiliation);

      // Then: 验证返回的聚合根
      assertThat(saved).isNotNull();
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getName()).isEqualTo("Harvard University");

      // Then: 验证数据库中的记录
      Optional<AffiliationEntity> entity = jpaRepository.findById(saved.getId().value());
      assertThat(entity).isPresent();
      assertThat(entity.get().getName()).isEqualTo("Harvard University");
    }

    @Test
    @DisplayName("保存机构含 ROR ID - 应该正确保存标识符")
    void save_affiliationWithRorId_shouldSaveIdentifier() {
      // Given: 创建含 ROR ID 的机构
      AffiliationAggregate affiliation =
          AffiliationAggregate.create("MIT", RorId.of("https://ror.org/042nb2s44"));

      // When: 保存机构
      AffiliationAggregate saved = affiliationRepository.save(affiliation);

      // Then: 验证 ROR ID
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getRorId()).isNotNull();
      assertThat(saved.getRorId().value()).isEqualTo("https://ror.org/042nb2s44");

      // Then: 验证数据库记录
      AffiliationEntity entity = jpaRepository.findById(saved.getId().value()).orElseThrow();
      assertThat(entity.getRorId()).isEqualTo("https://ror.org/042nb2s44");
    }

    @Test
    @DisplayName("保存机构含地理位置 - 应该正确保存位置信息")
    void save_affiliationWithGeographicLocation_shouldSaveLocationInfo() {
      // Given: 创建含地理位置的机构
      AffiliationAggregate affiliation =
          AffiliationAggregate.create("Stanford University", "Stanford", "USA");

      // When: 保存机构
      AffiliationAggregate saved = affiliationRepository.save(affiliation);

      // Then: 验证地理位置
      assertThat(saved.getCity()).isEqualTo("Stanford");
      assertThat(saved.getCountry()).isEqualTo("USA");

      // Then: 验证数据库记录
      AffiliationEntity entity = jpaRepository.findById(saved.getId().value()).orElseThrow();
      assertThat(entity.getCity()).isEqualTo("Stanford");
      assertThat(entity.getCountry()).isEqualTo("USA");
    }

    @Test
    @DisplayName("保存机构含完整信息 - 应该正确保存所有字段")
    void save_affiliationWithFullInfo_shouldSaveAllFields() {
      // Given: 创建完整信息的机构（使用 restore 方法设置包级字段）
      AffiliationAggregate affiliation =
          AffiliationAggregate.restore(
              null, // id
              "Peking University", // name
              null, // originalName
              "School of Medicine", // department
              null, // division
              null, // section
              "Beijing", // city
              "Beijing", // stateProvince
              "CHN", // country
              "100871", // postalCode
              RorId.of("https://ror.org/02v51f717"), // rorId
              null, // gridId
              null, // isni
              null, // ringgoldId
              null, // parentAffiliation
              AffiliationType.EDUCATION, // affiliationType
              null, // dedupKey
              null); // version

      // When: 保存机构
      AffiliationAggregate saved = affiliationRepository.save(affiliation);

      // Then: 验证所有字段
      assertThat(saved.getName()).isEqualTo("Peking University");
      assertThat(saved.getCity()).isEqualTo("Beijing");
      assertThat(saved.getCountry()).isEqualTo("CHN");
      assertThat(saved.getPostalCode()).isEqualTo("100871");
      assertThat(saved.getDepartment()).isEqualTo("School of Medicine");
      assertThat(saved.getRorId().value()).isEqualTo("https://ror.org/02v51f717");
      assertThat(saved.getAffiliationType()).isEqualTo(AffiliationType.EDUCATION);
    }
  }

  @Nested
  @DisplayName("saveBatch() 方法测试")
  class SaveBatchTests {

    @Test
    @DisplayName("批量保存机构 - 应该正确批量插入")
    void saveBatch_multipleAffiliations_shouldInsertAllSuccessfully() {
      // Given: 创建多个机构
      List<AffiliationAggregate> affiliations =
          List.of(
              AffiliationAggregate.create(
                  "Harvard University", RorId.of("https://ror.org/03vek6s52")),
              AffiliationAggregate.create("MIT", RorId.of("https://ror.org/042nb2s44")),
              AffiliationAggregate.create("Stanford University"));

      // When: 批量保存
      affiliationRepository.saveBatch(affiliations);

      // Then: 验证数据库记录数
      long count = jpaRepository.count();
      assertThat(count).isEqualTo(3);

      // Then: 验证 ROR ID 索引查询
      assertThat(jpaRepository.findByRorId("https://ror.org/03vek6s52")).isPresent();
      assertThat(jpaRepository.findByRorId("https://ror.org/042nb2s44")).isPresent();
    }

    @Test
    @DisplayName("空列表 - 应该不抛出异常，直接返回")
    void saveBatch_emptyList_shouldReturnWithoutError() {
      // Given: 空列表
      List<AffiliationAggregate> emptyList = List.of();

      // When: 批量保存空列表
      affiliationRepository.saveBatch(emptyList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = jpaRepository.count();
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("大批量机构（100条）- 应该正确处理")
    void saveBatch_largeNumberOfAffiliations_shouldInsertAllSuccessfully() {
      // Given: 创建 100 个机构
      List<AffiliationAggregate> affiliations = new ArrayList<>();
      for (int i = 1; i <= 100; i++) {
        affiliations.add(AffiliationAggregate.create("University " + i));
      }

      // When: 批量保存
      affiliationRepository.saveBatch(affiliations);

      // Then: 验证数据库记录数
      long count = jpaRepository.count();
      assertThat(count).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("findByRorId() 方法测试")
  class FindByRorIdTests {

    @Test
    @DisplayName("根据 ROR ID 查询 - 存在时返回机构")
    void findByRorId_exists_shouldReturnAffiliation() {
      // Given: 保存一个有 ROR ID 的机构
      AffiliationAggregate affiliation =
          AffiliationAggregate.create("Harvard University", RorId.of("https://ror.org/03vek6s52"));
      affiliationRepository.save(affiliation);

      // When: 根据 ROR ID 查询
      Optional<AffiliationAggregate> found =
          affiliationRepository.findByRorId("https://ror.org/03vek6s52");

      // Then: 验证
      assertThat(found).isPresent();
      assertThat(found.get().getName()).isEqualTo("Harvard University");
      assertThat(found.get().getRorId().value()).isEqualTo("https://ror.org/03vek6s52");
    }

    @Test
    @DisplayName("根据 ROR ID 查询 - 不存在时返回空")
    void findByRorId_notExists_shouldReturnEmpty() {
      // When: 查询不存在的 ROR ID
      Optional<AffiliationAggregate> found =
          affiliationRepository.findByRorId("https://ror.org/000000000");

      // Then: 验证
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByGridId() 方法测试")
  class FindByGridIdTests {

    @Test
    @DisplayName("根据 GRID ID 查询 - 存在时返回机构")
    void findByGridId_exists_shouldReturnAffiliation() {
      // Given: 保存一个有 GRID ID 的机构
      AffiliationAggregate affiliation = AffiliationAggregate.create("Oxford University");
      affiliation.setGridId(GridId.of("grid.4991.5"));
      affiliationRepository.save(affiliation);

      // When: 根据 GRID ID 查询
      Optional<AffiliationAggregate> found = affiliationRepository.findByGridId("grid.4991.5");

      // Then: 验证
      assertThat(found).isPresent();
      assertThat(found.get().getName()).isEqualTo("Oxford University");
      assertThat(found.get().getGridId().value()).isEqualTo("grid.4991.5");
    }

    @Test
    @DisplayName("根据 GRID ID 查询 - 不存在时返回空")
    void findByGridId_notExists_shouldReturnEmpty() {
      // When: 查询不存在的 GRID ID
      Optional<AffiliationAggregate> found = affiliationRepository.findByGridId("grid.0000.0");

      // Then: 验证
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("hasAnyData() 方法测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      assertThat(jpaRepository.count()).isEqualTo(0);

      // When & Then
      assertThat(affiliationRepository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      // Given: 插入一条数据
      AffiliationAggregate affiliation = AffiliationAggregate.create("Test University");
      affiliationRepository.save(affiliation);

      // When & Then
      assertThat(affiliationRepository.hasAnyData()).isTrue();
    }
  }

  @Nested
  @DisplayName("枚举类型（AffiliationType）测试")
  class AffiliationTypeTests {

    @Test
    @DisplayName("保存含机构类型 - 应该正确保存和读取枚举")
    void save_affiliationWithType_shouldPersistEnumCorrectly() {
      // Given: 创建含机构类型的机构（使用 restore 方法）
      AffiliationAggregate affiliation =
          AffiliationAggregate.restore(
              null,
              "Harvard Medical School",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              AffiliationType.HEALTHCARE,
              null,
              null);

      // When: 保存
      AffiliationAggregate saved = affiliationRepository.save(affiliation);

      // Then: 验证枚举
      assertThat(saved.getAffiliationType()).isEqualTo(AffiliationType.HEALTHCARE);

      // Then: 验证数据库存储的是 code 值
      AffiliationEntity entity = jpaRepository.findById(saved.getId().value()).orElseThrow();
      assertThat(entity.getAffiliationType()).isEqualTo(AffiliationType.HEALTHCARE);
    }

    @Test
    @DisplayName("各种机构类型 - 应该全部正确映射")
    void save_variousAffiliationTypes_shouldMapCorrectly() {
      // Given: 不同类型的机构
      List<AffiliationType> types =
          List.of(
              AffiliationType.EDUCATION,
              AffiliationType.HEALTHCARE,
              AffiliationType.COMPANY,
              AffiliationType.GOVERNMENT,
              AffiliationType.NONPROFIT);

      for (AffiliationType type : types) {
        // 使用 restore 方法设置机构类型
        AffiliationAggregate affiliation =
            AffiliationAggregate.restore(
                null,
                "Org " + type.name(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                type,
                null,
                null);

        // When: 保存
        AffiliationAggregate saved = affiliationRepository.save(affiliation);

        // Then: 验证
        assertThat(saved.getAffiliationType()).isEqualTo(type);
      }
    }
  }

  @Nested
  @DisplayName("中文支持测试")
  class ChineseSupportTests {

    @Test
    @DisplayName("中文机构名称 - 应该正确处理 UTF-8")
    void save_chineseAffiliationName_shouldHandleUtf8() {
      // Given: 中文机构名称（使用 restore 方法设置部门字段）
      AffiliationAggregate affiliation =
          AffiliationAggregate.restore(
              null, // id
              "北京大学", // name
              null, // originalName
              "医学部", // department
              null, // division
              null, // section
              "北京", // city
              null, // stateProvince
              "CHN", // country
              null, // postalCode
              null, // rorId
              null, // gridId
              null, // isni
              null, // ringgoldId
              null, // parentAffiliation
              AffiliationType.EDUCATION, // affiliationType
              null, // dedupKey
              null); // version

      // When: 保存
      AffiliationAggregate saved = affiliationRepository.save(affiliation);

      // Then: 验证中文
      assertThat(saved.getName()).isEqualTo("北京大学");
      assertThat(saved.getCity()).isEqualTo("北京");
      assertThat(saved.getDepartment()).isEqualTo("医学部");
    }
  }
}
