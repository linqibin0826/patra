package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.jpa.MeshConceptJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshConceptRelationJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshDescriptorJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshEntryCombinationJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshEntryTermJpaRepository;
import com.patra.catalog.infra.persistence.jpa.MeshTreeNumberJpaRepository;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import java.util.List;
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

/// MeSH 主题词仓储实现集成测试（JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试批量保存操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：hasAnyData、insertAll（聚合根批量插入）
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MeshDescriptorRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "com.patra.catalog.infra.persistence.jpa.converter")
@ActiveProfiles("test")
@DisplayName("MeshDescriptorRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshDescriptorRepositoryAdapterIT {

  @Autowired private MeshDescriptorRepositoryAdapter repository;

  @Autowired private MeshDescriptorJpaRepository descriptorJpaRepository;
  @Autowired private MeshTreeNumberJpaRepository treeNumberJpaRepository;
  @Autowired private MeshConceptJpaRepository conceptJpaRepository;
  @Autowired private MeshConceptRelationJpaRepository conceptRelationJpaRepository;
  @Autowired private MeshEntryTermJpaRepository entryTermJpaRepository;
  @Autowired private MeshEntryCombinationJpaRepository entryCombinationJpaRepository;

  // ========== hasAnyData() 测试 ==========

  @Nested
  @DisplayName("hasAnyData() 测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      long count = descriptorJpaRepository.count();
      assertThat(count).isEqualTo(0);

      // When & Then
      assertThat(repository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      // Given: 通过 insertAll 插入数据
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Test Descriptor");
      repository.insertAll(List.of(descriptor));

      // When & Then
      assertThat(repository.hasAnyData()).isTrue();
    }
  }

  // ========== insertAll() 测试 ==========

  @Nested
  @DisplayName("insertAll() 测试")
  class InsertAllTests {

    @Test
    @DisplayName("空列表不应抛出异常")
    void insertAll_emptyList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.insertAll(List.of())).doesNotThrowAnyException();

      // 验证没有数据插入
      assertThat(descriptorJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("应该正确插入单个聚合根（仅主表）")
    void insertAll_singleDescriptor_shouldInsertMain() {
      // Given
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Test Descriptor");

      // When
      repository.insertAll(List.of(descriptor));

      // Then: 主表有 1 条记录
      assertThat(descriptorJpaRepository.count()).isEqualTo(1);

      // Then: 子表为空（没有添加子实体）
      assertThat(treeNumberJpaRepository.count()).isZero();
      assertThat(conceptJpaRepository.count()).isZero();
      assertThat(entryTermJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("应该正确插入聚合根及所有子表")
    void insertAll_withChildren_shouldInsertAllTables() {
      // Given: 创建带子实体的聚合根
      MeshDescriptorAggregate descriptor = createDescriptorWithChildren();

      // When
      repository.insertAll(List.of(descriptor));

      // Then: 验证主表
      assertThat(descriptorJpaRepository.count()).isEqualTo(1);

      // Then: 验证子表
      assertThat(treeNumberJpaRepository.count()).isEqualTo(2);
      assertThat(conceptJpaRepository.count()).isEqualTo(1);
      assertThat(entryTermJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确插入多个聚合根")
    void insertAll_multipleDescriptors_shouldInsertAll() {
      // Given
      MeshDescriptorAggregate descriptor1 = createDescriptor("D000001", "Descriptor 1");
      MeshDescriptorAggregate descriptor2 = createDescriptor("D000002", "Descriptor 2");
      MeshDescriptorAggregate descriptor3 = createDescriptor("D000003", "Descriptor 3");

      // When
      repository.insertAll(List.of(descriptor1, descriptor2, descriptor3));

      // Then: 主表有 3 条记录
      assertThat(descriptorJpaRepository.count()).isEqualTo(3);

      // Then: 验证 UI 唯一性
      var saved = descriptorJpaRepository.findAll();
      assertThat(saved)
          .extracting(d -> d.getUi())
          .containsExactlyInAnyOrder("D000001", "D000002", "D000003");
    }

    @Test
    @DisplayName("子表应正确关联到主表（通过 descriptorUi）")
    void insertAll_shouldSetCorrectDescriptorUi() {
      // Given
      MeshDescriptorAggregate descriptor = createDescriptorWithChildren();

      // When
      repository.insertAll(List.of(descriptor));

      // Then: 获取主表 descriptorUi
      var savedDescriptor = descriptorJpaRepository.findAll().get(0);
      String descriptorUi = savedDescriptor.getUi();
      assertThat(descriptorUi).isEqualTo("D000001");

      // Then: 验证树形编号的外键
      var treeNumbers = treeNumberJpaRepository.findAll();
      assertThat(treeNumbers).allMatch(tn -> tn.getDescriptorUi().equals(descriptorUi));

      // Then: 验证概念的外键
      var concepts = conceptJpaRepository.findAll();
      assertThat(concepts).allMatch(c -> c.getDescriptorUi().equals(descriptorUi));

      // Then: 验证入口术语的外键
      var entryTerms = entryTermJpaRepository.findAll();
      assertThat(entryTerms).allMatch(et -> et.getDescriptorUi().equals(descriptorUi));
    }
  }

  /// 创建测试用的 MeshDescriptorAggregate（仅主表）。
  private MeshDescriptorAggregate createDescriptor(String ui, String name) {
    return MeshDescriptorAggregate.create(MeshUI.of(ui), name, DescriptorClass.TOPICAL, "2025");
  }

  /// 创建带子实体的 MeshDescriptorAggregate。
  private MeshDescriptorAggregate createDescriptorWithChildren() {
    MeshUI descriptorUi = MeshUI.of("D000001");

    MeshDescriptorAggregate descriptor =
        MeshDescriptorAggregate.create(
            descriptorUi, "Test Descriptor", DescriptorClass.TOPICAL, "2025");

    // 添加树形编号
    descriptor.addTreeNumber(MeshTreeNumber.create(descriptorUi, "C04.557.337", true));
    descriptor.addTreeNumber(MeshTreeNumber.create(descriptorUi, "C08.381.540", false));

    // 添加概念
    MeshConcept concept =
        MeshConcept.create(descriptorUi, MeshUI.conceptOf(1), "Test Concept", true);
    descriptor.addConcept(concept);

    // 添加入口术语
    MeshEntryTerm entryTerm =
        MeshEntryTerm.create(
            descriptorUi,
            MeshUI.of("T000001"),
            "Test Term",
            LexicalTag.NON,
            true,
            true,
            true,
            false);
    descriptor.addEntryTerm(entryTerm);

    return descriptor;
  }
}
