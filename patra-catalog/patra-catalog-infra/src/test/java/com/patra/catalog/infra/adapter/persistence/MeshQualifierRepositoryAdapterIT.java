package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.converter.MeshQualifierConverter;
import com.patra.catalog.infra.persistence.entity.MeshQualifierDO;
import com.patra.catalog.infra.persistence.mapper.MeshQualifierMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// MeSH 限定词仓储实现集成测试。
///
/// 使用 Testcontainers + MySQL 8 测试批量保存操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
///   - TestContainers：自动启动和停止 MySQL 容器
///   - 测试覆盖：saveBatch() 和 hasAnyData() 的各种场景
///
/// **重点测试场景**：
///
/// - saveBatch()：空列表/单条/批量/大批量插入
/// - hasAnyData()：空表返回 false，有数据返回 true
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MeshQualifierRepositoryAdapter.class,
  MeshQualifierConverter.class,
  TestMybatisPlusAutoConfiguration.class
})
@MapperScan("com.patra.catalog.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("MeshQualifierRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshQualifierRepositoryAdapterIT {

  @Autowired private MeshQualifierRepositoryAdapter meshQualifierRepository;

  @Autowired private MeshQualifierMapper meshQualifierMapper;

  @Nested
  @DisplayName("saveBatch() 方法测试")
  class SaveBatchTests {

    @Test
    @DisplayName("空列表 - 应该不抛出异常，直接返回")
    void saveBatch_emptyList_shouldReturnWithoutError() {
      // Given: 空列表
      List<MeshQualifierAggregate> emptyList = List.of();

      // When: 批量保存空列表
      meshQualifierRepository.saveBatch(emptyList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("null列表 - 应该不抛出异常，直接返回")
    void saveBatch_nullList_shouldReturnWithoutError() {
      // Given: null列表
      List<MeshQualifierAggregate> nullList = null;

      // When: 批量保存null列表
      meshQualifierRepository.saveBatch(nullList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("单个限定词 - 应该正确插入到数据库")
    void saveBatch_singleQualifier_shouldInsertSuccessfully() {
      // Given: 单个限定词
      MeshQualifierAggregate qualifier =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
              .withAnnotation("Used with organs, animals, and diseases for immunologic")
              .withDateCreated(LocalDate.of(1999, 1, 1))
              .withDateRevised(LocalDate.of(2025, 1, 1))
              .withDateEstablished(LocalDate.of(1999, 1, 1))
              .withActiveStatus(true)
              .withMeshVersion("2025");

      // When: 批量保存单个限定词
      meshQualifierRepository.saveBatch(List.of(qualifier));

      // Then: 数据库应该有1条记录
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(1);

      // Then: 验证保存的数据
      List<MeshQualifierDO> savedQualifiers = meshQualifierMapper.selectList(null);
      assertThat(savedQualifiers).hasSize(1);

      MeshQualifierDO saved = savedQualifiers.get(0);
      assertThat(saved.getId()).isNotNull(); // 应该自动分配ID
      assertThat(saved.getUi()).isEqualTo("Q000001");
      assertThat(saved.getName()).isEqualTo("immunology");
      assertThat(saved.getAbbreviation()).isEqualTo("IM");
      assertThat(saved.getAnnotation())
          .isEqualTo("Used with organs, animals, and diseases for immunologic");
      assertThat(saved.getActiveStatus()).isTrue();
      assertThat(saved.getMeshVersion()).isEqualTo("2025");
    }

    @Test
    @DisplayName("批量限定词 - 应该正确批量插入")
    void saveBatch_multipleQualifiers_shouldInsertAllSuccessfully() {
      // Given: 创建5个限定词（模拟真实场景）
      List<MeshQualifierAggregate> qualifiers =
          List.of(
              MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(2), "genetics", "GE")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(3), "diagnosis", "DI")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(4), "therapy", "TH")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(5), "pharmacology", "PD")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"));

      // When: 批量保存
      meshQualifierRepository.saveBatch(qualifiers);

      // Then: 数据库应该有5条记录
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(5);

      // Then: 验证所有记录都正确保存
      List<MeshQualifierDO> savedQualifiers = meshQualifierMapper.selectList(null);
      assertThat(savedQualifiers).hasSize(5);

      // 验证UI的唯一性
      assertThat(savedQualifiers)
          .extracting(MeshQualifierDO::getUi)
          .containsExactlyInAnyOrder("Q000001", "Q000002", "Q000003", "Q000004", "Q000005");

      // 验证名称
      assertThat(savedQualifiers)
          .extracting(MeshQualifierDO::getName)
          .containsExactlyInAnyOrder(
              "immunology", "genetics", "diagnosis", "therapy", "pharmacology");

      // 验证所有记录都有ID
      assertThat(savedQualifiers).allMatch(q -> q.getId() != null);
    }

    @Test
    @DisplayName("大批量限定词 - 应该正确处理接近真实数量的数据（80条）")
    void saveBatch_largeNumberOfQualifiers_shouldInsertAllSuccessfully() {
      // Given: 创建80个限定词（模拟真实的MeSH限定词数量）
      List<MeshQualifierAggregate> qualifiers = new ArrayList<>();
      for (int i = 1; i <= 80; i++) {
        qualifiers.add(
            MeshQualifierAggregate.create(
                    MeshUI.qualifierOf(i), "qualifier_" + i, "Q" + String.format("%02d", i))
                .withAnnotation("Test annotation for qualifier " + i)
                .withDateCreated(LocalDate.of(2025, 1, 1))
                .withActiveStatus(true)
                .withMeshVersion("2025"));
      }

      // When: 批量保存80个限定词
      meshQualifierRepository.saveBatch(qualifiers);

      // Then: 数据库应该有80条记录
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(80);

      // Then: 验证第一个和最后一个
      List<MeshQualifierDO> savedQualifiers = meshQualifierMapper.selectList(null);
      assertThat(savedQualifiers).hasSize(80);
      assertThat(savedQualifiers).allMatch(q -> q.getId() != null);
      assertThat(savedQualifiers).allMatch(q -> q.getMeshVersion().equals("2025"));
    }

    @Test
    @DisplayName("包含可选字段为null的限定词 - 应该正确保存")
    void saveBatch_qualifiersWithNullOptionalFields_shouldInsertSuccessfully() {
      // Given: 只设置必填字段的限定词
      MeshQualifierAggregate minimalQualifier =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM");
      // 不设置可选字段

      // When: 批量保存
      meshQualifierRepository.saveBatch(List.of(minimalQualifier));

      // Then: 数据库应该有1条记录
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(1);

      // Then: 验证可选字段
      MeshQualifierDO saved = meshQualifierMapper.selectList(null).get(0);
      assertThat(saved.getUi()).isEqualTo("Q000001");
      assertThat(saved.getName()).isEqualTo("immunology");
      assertThat(saved.getAbbreviation()).isEqualTo("IM");
      assertThat(saved.getAnnotation()).isNull();
      assertThat(saved.getDateCreated()).isNull();
      // activeStatus 使用数据库默认值(DEFAULT 1)
      assertThat(saved.getActiveStatus()).isTrue();
      assertThat(saved.getMeshVersion()).isNull();
    }

    @Test
    @DisplayName("包含已废弃的限定词 - 应该正确保存activeStatus为false")
    void saveBatch_deprecatedQualifier_shouldSaveWithInactiveStatus() {
      // Given: 已废弃的限定词
      MeshQualifierAggregate deprecatedQualifier =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "old qualifier", "OQ")
              .withActiveStatus(false)
              .withMeshVersion("2024");

      // When: 批量保存
      meshQualifierRepository.saveBatch(List.of(deprecatedQualifier));

      // Then: 验证activeStatus为false
      MeshQualifierDO saved = meshQualifierMapper.selectList(null).get(0);
      assertThat(saved.getActiveStatus()).isFalse();
      assertThat(saved.getMeshVersion()).isEqualTo("2024");
    }

    @Test
    @DisplayName("混合新旧限定词 - 应该正确处理不同状态的限定词")
    void saveBatch_mixedActiveAndInactiveQualifiers_shouldInsertAll() {
      // Given: 混合新旧限定词
      List<MeshQualifierAggregate> qualifiers =
          List.of(
              MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "active qualifier", "AQ")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(2), "deprecated qualifier", "DQ")
                  .withActiveStatus(false)
                  .withMeshVersion("2024"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(3), "another active", "AA")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"));

      // When: 批量保存
      meshQualifierRepository.saveBatch(qualifiers);

      // Then: 数据库应该有3条记录
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(3);

      // Then: 验证不同状态
      List<MeshQualifierDO> savedQualifiers = meshQualifierMapper.selectList(null);
      long activeCount = savedQualifiers.stream().filter(MeshQualifierDO::getActiveStatus).count();
      long inactiveCount = savedQualifiers.stream().filter(q -> !q.getActiveStatus()).count();

      assertThat(activeCount).isEqualTo(2);
      assertThat(inactiveCount).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("hasAnyData() 方法测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      long count = meshQualifierMapper.selectCount(null);
      assertThat(count).isEqualTo(0);

      // When & Then
      assertThat(meshQualifierRepository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有单条数据 - 应该返回 true")
    void hasAnyData_withSingleRecord_shouldReturnTrue() {
      // Given: 插入单条数据
      MeshQualifierAggregate qualifier =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
              .withActiveStatus(true)
              .withMeshVersion("2025");
      meshQualifierRepository.saveBatch(List.of(qualifier));

      // When & Then
      assertThat(meshQualifierRepository.hasAnyData()).isTrue();
    }

    @Test
    @DisplayName("有多条数据 - 应该返回 true")
    void hasAnyData_withMultipleRecords_shouldReturnTrue() {
      // Given: 插入多条数据
      List<MeshQualifierAggregate> qualifiers =
          List.of(
              MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(2), "genetics", "GE")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"),
              MeshQualifierAggregate.create(MeshUI.qualifierOf(3), "diagnosis", "DI")
                  .withActiveStatus(true)
                  .withMeshVersion("2025"));
      meshQualifierRepository.saveBatch(qualifiers);

      // When & Then
      assertThat(meshQualifierRepository.hasAnyData()).isTrue();
    }

    @Test
    @DisplayName("有大量数据（80条）- 应该返回 true")
    void hasAnyData_withLargeDataSet_shouldReturnTrue() {
      // Given: 插入80条数据
      List<MeshQualifierAggregate> qualifiers = new ArrayList<>();
      for (int i = 1; i <= 80; i++) {
        qualifiers.add(
            MeshQualifierAggregate.create(
                    MeshUI.qualifierOf(i), "qualifier_" + i, "Q" + String.format("%02d", i))
                .withActiveStatus(true)
                .withMeshVersion("2025"));
      }
      meshQualifierRepository.saveBatch(qualifiers);

      // When & Then
      assertThat(meshQualifierRepository.hasAnyData()).isTrue();
    }
  }
}
