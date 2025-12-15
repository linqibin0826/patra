package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.vo.author.AuthorName;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.jpa.AuthorJpaRepository;
import com.patra.catalog.infra.persistence.jpa.entity.AuthorEntity;
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

/// 作者仓储实现集成测试（JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试 CRUD 操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：save(), saveBatch(), findByOrcid(), hasAnyData() 等场景
///
/// **重点测试场景**：
///
/// - 嵌入式值对象（AuthorName）的正确映射
/// - ORCID 唯一性约束
/// - 批量保存功能
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuthorRepositoryAdapter.class, JpaAuditingConfig.class})
@ComponentScan(basePackages = "com.patra.catalog.infra.persistence.jpa.converter")
@ActiveProfiles("test")
@DisplayName("AuthorRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AuthorRepositoryAdapterIT {

  @Autowired private AuthorRepositoryAdapter authorRepository;

  @Autowired private AuthorJpaRepository jpaRepository;

  @Nested
  @DisplayName("save() 方法测试")
  class SaveTests {

    @Test
    @DisplayName("保存单个作者 - 应该正确插入到数据库")
    void save_singleAuthor_shouldInsertSuccessfully() {
      // Given: 创建作者聚合根
      AuthorAggregate author =
          AuthorAggregate.create(
              AuthorName.of("Smith", "John", "J.", null), Orcid.of("0000-0002-1825-0097"));

      // When: 保存作者
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证返回的聚合根
      assertThat(saved).isNotNull();
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getName().lastName()).isEqualTo("Smith");
      assertThat(saved.getName().foreName()).isEqualTo("John");
      assertThat(saved.getOrcid().value()).isEqualTo("0000-0002-1825-0097");

      // Then: 验证数据库中的记录
      Optional<AuthorEntity> entity = jpaRepository.findById(saved.getId().value());
      assertThat(entity).isPresent();
      assertThat(entity.get().getName().getLastName()).isEqualTo("Smith");
      assertThat(entity.get().getOrcid()).isEqualTo("0000-0002-1825-0097");
    }

    @Test
    @DisplayName("保存作者含机构和邮箱 - 应该正确保存所有字段")
    void save_authorWithOrgAndEmail_shouldSaveAllFields() {
      // Given: 创建含机构和邮箱的作者
      AuthorAggregate author =
          AuthorAggregate.create(AuthorName.of("李", "明", null, null), "北京大学", "liming@pku.edu.cn");

      // When: 保存作者
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证所有字段
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getName().lastName()).isEqualTo("李");
      assertThat(saved.getName().foreName()).isEqualTo("明");
      assertThat(saved.getOrganizationName()).isEqualTo("北京大学");
      assertThat(saved.getEmail()).isEqualTo("liming@pku.edu.cn");

      // Then: 验证数据库记录
      AuthorEntity entity = jpaRepository.findById(saved.getId().value()).orElseThrow();
      assertThat(entity.getOrganizationName()).isEqualTo("北京大学");
      assertThat(entity.getEmail()).isEqualTo("liming@pku.edu.cn");
    }

    @Test
    @DisplayName("保存仅有姓氏和缩写的作者 - PubMed 常见格式")
    void save_authorWithLastNameAndInitials_pubMedFormat() {
      // Given: PubMed 格式的作者姓名（仅姓氏和缩写）
      AuthorAggregate author = AuthorAggregate.create(AuthorName.withInitials("Johnson", "M.K."));

      // When: 保存作者
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证
      assertThat(saved.getName().lastName()).isEqualTo("Johnson");
      assertThat(saved.getName().initials()).isEqualTo("M.K.");
      assertThat(saved.getName().foreName()).isNull();
    }
  }

  @Nested
  @DisplayName("saveBatch() 方法测试")
  class SaveBatchTests {

    @Test
    @DisplayName("批量保存作者 - 应该正确批量插入")
    void saveBatch_multipleAuthors_shouldInsertAllSuccessfully() {
      // Given: 创建多个作者
      List<AuthorAggregate> authors =
          List.of(
              AuthorAggregate.create(
                  AuthorName.of("Smith", "John"), Orcid.of("0000-0002-1825-0097")),
              AuthorAggregate.create(
                  AuthorName.of("Brown", "Alice"), Orcid.of("0000-0001-5109-3700")),
              AuthorAggregate.create(AuthorName.of("Wilson", "Bob")));

      // When: 批量保存
      authorRepository.saveBatch(authors);

      // Then: 验证数据库记录数
      long count = jpaRepository.count();
      assertThat(count).isEqualTo(3);

      // Then: 验证 ORCID 索引查询
      assertThat(jpaRepository.findByOrcid("0000-0002-1825-0097")).isPresent();
      assertThat(jpaRepository.findByOrcid("0000-0001-5109-3700")).isPresent();
    }

    @Test
    @DisplayName("空列表 - 应该不抛出异常，直接返回")
    void saveBatch_emptyList_shouldReturnWithoutError() {
      // Given: 空列表
      List<AuthorAggregate> emptyList = List.of();

      // When: 批量保存空列表
      authorRepository.saveBatch(emptyList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = jpaRepository.count();
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("大批量作者（100条）- 应该正确处理")
    void saveBatch_largeNumberOfAuthors_shouldInsertAllSuccessfully() {
      // Given: 创建 100 个作者
      List<AuthorAggregate> authors = new ArrayList<>();
      for (int i = 1; i <= 100; i++) {
        authors.add(
            AuthorAggregate.create(
                AuthorName.of("LastName" + i, "FirstName" + i, "F" + i + ".", null)));
      }

      // When: 批量保存
      authorRepository.saveBatch(authors);

      // Then: 验证数据库记录数
      long count = jpaRepository.count();
      assertThat(count).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("findByOrcid() 方法测试")
  class FindByOrcidTests {

    @Test
    @DisplayName("根据 ORCID 查询 - 存在时返回作者")
    void findByOrcid_exists_shouldReturnAuthor() {
      // Given: 保存一个有 ORCID 的作者
      AuthorAggregate author =
          AuthorAggregate.create(AuthorName.of("Smith", "John"), Orcid.of("0000-0002-1825-0097"));
      authorRepository.save(author);

      // When: 根据 ORCID 查询
      Optional<AuthorAggregate> found = authorRepository.findByOrcid("0000-0002-1825-0097");

      // Then: 验证
      assertThat(found).isPresent();
      assertThat(found.get().getName().lastName()).isEqualTo("Smith");
      assertThat(found.get().getOrcid().value()).isEqualTo("0000-0002-1825-0097");
    }

    @Test
    @DisplayName("根据 ORCID 查询 - 不存在时返回空")
    void findByOrcid_notExists_shouldReturnEmpty() {
      // When: 查询不存在的 ORCID
      Optional<AuthorAggregate> found = authorRepository.findByOrcid("0000-0000-0000-0000");

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
      assertThat(authorRepository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      // Given: 插入一条数据
      AuthorAggregate author = AuthorAggregate.create(AuthorName.of("Test", "Author"));
      authorRepository.save(author);

      // When & Then
      assertThat(authorRepository.hasAnyData()).isTrue();
    }
  }

  @Nested
  @DisplayName("嵌入式值对象（AuthorName）测试")
  class EmbeddedValueObjectTests {

    @Test
    @DisplayName("完整姓名信息 - 应该正确保存和读取所有字段")
    void save_fullAuthorName_shouldPersistAllFields() {
      // Given: 包含所有姓名字段的作者
      AuthorAggregate author =
          AuthorAggregate.create(AuthorName.of("Smith", "John", "J.K.", "Jr."));

      // When: 保存
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证所有字段
      assertThat(saved.getName().lastName()).isEqualTo("Smith");
      assertThat(saved.getName().foreName()).isEqualTo("John");
      assertThat(saved.getName().initials()).isEqualTo("J.K.");
      assertThat(saved.getName().suffix()).isEqualTo("Jr.");

      // Then: 验证数据库
      AuthorEntity entity = jpaRepository.findById(saved.getId().value()).orElseThrow();
      assertThat(entity.getName().getLastName()).isEqualTo("Smith");
      assertThat(entity.getName().getForeName()).isEqualTo("John");
      assertThat(entity.getName().getInitials()).isEqualTo("J.K.");
      assertThat(entity.getName().getSuffix()).isEqualTo("Jr.");
    }

    @Test
    @DisplayName("中文姓名 - 应该正确处理 UTF-8")
    void save_chineseAuthorName_shouldHandleUtf8() {
      // Given: 中文姓名
      AuthorAggregate author =
          AuthorAggregate.create(AuthorName.of("王", "小明"), "中国科学院", "wangxm@cas.cn");

      // When: 保存
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证中文姓名
      assertThat(saved.getName().lastName()).isEqualTo("王");
      assertThat(saved.getName().foreName()).isEqualTo("小明");
      assertThat(saved.getOrganizationName()).isEqualTo("中国科学院");
    }
  }
}
