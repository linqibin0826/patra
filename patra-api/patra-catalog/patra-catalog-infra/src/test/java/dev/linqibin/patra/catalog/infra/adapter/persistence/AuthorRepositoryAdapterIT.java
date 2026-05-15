package dev.linqibin.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.patra.catalog.domain.model.aggregate.AuthorAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.author.AuthorNameVariant;
import dev.linqibin.patra.catalog.domain.model.vo.author.Orcid;
import dev.linqibin.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.AuthorDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.AuthorEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
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
/// - 测试覆盖：save(), saveBatch(), findByOrcid(), findByNormalizedKey(), hasAnyData() 等场景
///
/// **重点测试场景**：
///
/// - 名字变体（AuthorNameVariant）作为子实体的级联保存
/// - ORCID 作为子实体的级联保存
/// - normalizedKey 分组查询（同一格式下可能有多个作者）
/// - 批量保存功能
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuthorRepositoryAdapter.class, JpaAuditingConfig.class, JacksonAutoConfiguration.class})
@ComponentScan(basePackages = "dev.linqibin.patra.catalog.infra.persistence.converter")
@ActiveProfiles("test")
@DisplayName("AuthorRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AuthorRepositoryAdapterIT {

  @Autowired private AuthorRepositoryAdapter authorRepository;

  @Autowired private AuthorDao jpaRepository;

  @Nested
  @DisplayName("save() 方法测试")
  class SaveTests {

    @Test
    @DisplayName("保存单个作者 - 应该正确插入到数据库")
    void save_singleAuthor_shouldInsertSuccessfully() {
      // Given: 创建作者聚合根
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Smith+J");
      author.addNameVariant(AuthorNameVariant.of("Smith", "John", "J", "Smith,John,J"));
      author.addOrcid(Orcid.of("0000-0002-1825-0097"));

      // When: 保存作者
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证返回的聚合根
      assertThat(saved).isNotNull();
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getNormalizedKey()).isEqualTo("Smith+J");
      assertThat(saved.getDisplayName()).isEqualTo("John Smith");
      assertThat(saved.getNameVariants()).hasSize(1);
      assertThat(saved.getNameVariants().getFirst().lastName()).isEqualTo("Smith");
      assertThat(saved.getOrcids()).hasSize(1);
      assertThat(saved.getOrcids().getFirst().value()).isEqualTo("0000-0002-1825-0097");

      // Then: 验证数据库中的记录
      Optional<AuthorEntity> entity = jpaRepository.findById(saved.getId().value());
      assertThat(entity).isPresent();
      assertThat(entity.get().getNormalizedKey()).isEqualTo("Smith+J");
      assertThat(entity.get().getNameVariants()).hasSize(1);
      assertThat(entity.get().getOrcids()).hasSize(1);
    }

    @Test
    @DisplayName("保存作者含多个名字变体 - 应该正确保存所有变体")
    void save_authorWithMultipleNameVariants_shouldSaveAllVariants() {
      // Given: 创建含多个名字变体的作者
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Li+M");
      author.addNameVariant(AuthorNameVariant.of("Li", "Ming", "M", "Li,Ming,M"));
      author.addNameVariant(AuthorNameVariant.of("李", "明", null, "李,明"));

      // When: 保存作者
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证所有名字变体
      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getNameVariants()).hasSize(2);

      // Then: 验证数据库记录
      AuthorEntity entity = jpaRepository.findById(saved.getId().value()).orElseThrow();
      assertThat(entity.getNameVariants()).hasSize(2);
    }

    @Test
    @DisplayName("保存作者含多个 ORCID - 应该正确保存")
    void save_authorWithMultipleOrcids_shouldSaveAll() {
      // Given: 创建含多个 ORCID 的作者（极少数情况）
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Johnson+MK");
      author.addNameVariant(AuthorNameVariant.of("Johnson", "Mary", "MK", "Johnson,Mary,MK"));
      author.addOrcid(Orcid.of("0000-0001-5109-3700"));
      author.addOrcid(Orcid.of("0000-0002-1825-0097"));

      // When: 保存作者
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证
      assertThat(saved.getOrcids()).hasSize(2);
      assertThat(saved.hasOrcid()).isTrue();

      // Then: 验证数据库
      AuthorEntity entity = jpaRepository.findById(saved.getId().value()).orElseThrow();
      assertThat(entity.getOrcids()).hasSize(2);
    }

    @Test
    @DisplayName("保存仅有姓氏和缩写的作者 - PubMed 常见格式")
    void save_authorWithLastNameAndInitials_pubMedFormat() {
      // Given: PubMed 格式的作者姓名（仅姓氏和缩写）
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Johnson+MK");
      author.addNameVariant(AuthorNameVariant.parse("Johnson,MK"));

      // When: 保存作者
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证
      assertThat(saved.getNameVariants()).hasSize(1);
      AuthorNameVariant variant = saved.getNameVariants().getFirst();
      assertThat(variant.lastName()).isEqualTo("Johnson");
      assertThat(variant.initials()).isEqualTo("MK");
      assertThat(variant.foreName()).isNull();
    }
  }

  @Nested
  @DisplayName("saveBatch() 方法测试")
  class SaveBatchTests {

    @Test
    @DisplayName("批量保存作者 - 应该正确批量插入")
    void saveBatch_multipleAuthors_shouldInsertAllSuccessfully() {
      // Given: 创建多个作者
      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("Smith+J");
      author1.addNameVariant(AuthorNameVariant.of("Smith", "John", "J", "Smith,John,J"));
      author1.addOrcid(Orcid.of("0000-0002-1825-0097"));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("Brown+A");
      author2.addNameVariant(AuthorNameVariant.of("Brown", "Alice", "A", "Brown,Alice,A"));
      author2.addOrcid(Orcid.of("0000-0001-5109-3700"));

      AuthorAggregate author3 = AuthorAggregate.fromPubMedComputed("Wilson+B");
      author3.addNameVariant(AuthorNameVariant.of("Wilson", "Bob", "B", "Wilson,Bob,B"));

      List<AuthorAggregate> authors = List.of(author1, author2, author3);

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
        AuthorAggregate author = AuthorAggregate.fromPubMedComputed("LastName" + i + "+F" + i);
        author.addNameVariant(
            AuthorNameVariant.of(
                "LastName" + i,
                "FirstName" + i,
                "F" + i,
                "LastName" + i + ",FirstName" + i + ",F" + i));
        authors.add(author);
      }

      // When: 批量保存
      authorRepository.saveBatch(authors);

      // Then: 验证数据库记录数
      long count = jpaRepository.count();
      assertThat(count).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("findByNormalizedKey() 方法测试")
  class FindByNormalizedKeyTests {

    @Test
    @DisplayName("根据 normalizedKey 查询 - 存在时返回作者列表")
    void findByNormalizedKey_exists_shouldReturnAuthorList() {
      // Given: 保存一个作者
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Smith+J");
      author.addNameVariant(AuthorNameVariant.of("Smith", "John", "J", "Smith,John,J"));
      authorRepository.save(author);

      // When: 根据 normalizedKey 查询
      List<AuthorAggregate> found = authorRepository.findByNormalizedKey("Smith+J");

      // Then: 验证
      assertThat(found).hasSize(1);
      assertThat(found.getFirst().getNormalizedKey()).isEqualTo("Smith+J");
      assertThat(found.getFirst().getNameVariants()).hasSize(1);
    }

    @Test
    @DisplayName("根据 normalizedKey 查询 - 不存在时返回空列表")
    void findByNormalizedKey_notExists_shouldReturnEmptyList() {
      // When: 查询不存在的 normalizedKey
      List<AuthorAggregate> found = authorRepository.findByNormalizedKey("Unknown+X");

      // Then: 验证
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("同一 normalizedKey 下多个作者 - 应该返回所有匹配的作者")
    void findByNormalizedKey_multipleAuthors_shouldReturnAll() {
      // Given: 保存多个相同 normalizedKey 的作者（模拟 PubMed 数据中的情况）
      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("Smith+R");
      author1.addNameVariant(AuthorNameVariant.of("Smith", "Richard", "R", "Smith,Richard,R"));
      author1.addOrcid(Orcid.of("0000-0002-1825-0097"));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("Smith+R");
      author2.addNameVariant(AuthorNameVariant.of("Smith", "Robert", "R", "Smith,Robert,R"));
      author2.addOrcid(Orcid.of("0000-0001-5109-3700"));

      AuthorAggregate author3 = AuthorAggregate.fromPubMedComputed("Smith+R");
      author3.addNameVariant(AuthorNameVariant.of("Smith", "Roger", "R", "Smith,Roger,R"));

      authorRepository.saveBatch(List.of(author1, author2, author3));

      // When: 根据 normalizedKey 查询
      List<AuthorAggregate> found = authorRepository.findByNormalizedKey("Smith+R");

      // Then: 验证返回所有匹配的作者
      assertThat(found).hasSize(3);
      assertThat(found)
          .extracting(a -> a.getNameVariants().getFirst().foreName())
          .containsExactlyInAnyOrder("Richard", "Robert", "Roger");
    }
  }

  @Nested
  @DisplayName("findByOrcid() 方法测试")
  class FindByOrcidTests {

    @Test
    @DisplayName("根据 ORCID 查询 - 存在时返回作者")
    void findByOrcid_exists_shouldReturnAuthor() {
      // Given: 保存一个有 ORCID 的作者
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Smith+J");
      author.addNameVariant(AuthorNameVariant.of("Smith", "John", "J", "Smith,John,J"));
      author.addOrcid(Orcid.of("0000-0002-1825-0097"));
      authorRepository.save(author);

      // When: 根据 ORCID 查询
      Optional<AuthorAggregate> found = authorRepository.findByOrcid("0000-0002-1825-0097");

      // Then: 验证
      assertThat(found).isPresent();
      assertThat(found.get().getNormalizedKey()).isEqualTo("Smith+J");
      assertThat(found.get().hasOrcid()).isTrue();
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
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Test+A");
      author.addNameVariant(AuthorNameVariant.of("Test", "Author", "A", "Test,Author,A"));
      authorRepository.save(author);

      // When & Then
      assertThat(authorRepository.hasAnyData()).isTrue();
    }
  }

  @Nested
  @DisplayName("子实体（名字变体 + ORCID）测试")
  class ChildEntityTests {

    @Test
    @DisplayName("完整作者信息 - 应该正确保存和读取所有子实体")
    void save_fullAuthorInfo_shouldPersistAllChildEntities() {
      // Given: 包含所有信息的作者
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Smith+JK");
      author.addNameVariant(AuthorNameVariant.of("Smith", "John", "JK", "Smith,John,JK"));
      author.addNameVariant(AuthorNameVariant.of("Smith", "J. Kevin", "JK", "Smith,J. Kevin,JK"));
      author.addOrcid(Orcid.of("0000-0002-1825-0097"));

      // When: 保存
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证聚合根
      assertThat(saved.getNormalizedKey()).isEqualTo("Smith+JK");
      assertThat(saved.getDisplayName()).isEqualTo("John Smith");
      assertThat(saved.getNameVariants()).hasSize(2);
      assertThat(saved.getOrcids()).hasSize(1);

      // Then: 重新从数据库读取验证
      List<AuthorAggregate> reloaded = authorRepository.findByNormalizedKey("Smith+JK");
      assertThat(reloaded).hasSize(1);
      assertThat(reloaded.getFirst().getNameVariants()).hasSize(2);
      assertThat(reloaded.getFirst().getOrcids()).hasSize(1);
    }

    @Test
    @DisplayName("中文姓名 - 应该正确处理 UTF-8")
    void save_chineseAuthorName_shouldHandleUtf8() {
      // Given: 中文姓名
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Wang+XM");
      author.addNameVariant(AuthorNameVariant.of("Wang", "Xiaoming", "XM", "Wang,Xiaoming,XM"));
      author.addNameVariant(AuthorNameVariant.of("王", "小明", null, "王,小明"));

      // When: 保存
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证中文姓名
      assertThat(saved.getNameVariants()).hasSize(2);

      // Then: 重新读取验证
      List<AuthorAggregate> reloadedList = authorRepository.findByNormalizedKey("Wang+XM");
      assertThat(reloadedList).hasSize(1);
      boolean hasChineseVariant =
          reloadedList.getFirst().getNameVariants().stream()
              .anyMatch(v -> "王".equals(v.lastName()));
      assertThat(hasChineseVariant).isTrue();
    }

    @Test
    @DisplayName("PubMed 格式名字解析 - 应该正确解析")
    void save_pubMedFormatName_shouldParseCorrectly() {
      // Given: 使用 PubMed 格式的名字变体
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      author.addNameVariant(AuthorNameVariant.parse("Lu,Zhiyong,Z"));
      author.addNameVariant(AuthorNameVariant.parse("Lu,Z"));

      // When: 保存
      AuthorAggregate saved = authorRepository.save(author);

      // Then: 验证解析结果
      assertThat(saved.getNameVariants()).hasSize(2);

      AuthorNameVariant fullVariant =
          saved.getNameVariants().stream()
              .filter(v -> v.foreName() != null)
              .findFirst()
              .orElseThrow();
      assertThat(fullVariant.lastName()).isEqualTo("Lu");
      assertThat(fullVariant.foreName()).isEqualTo("Zhiyong");
      assertThat(fullVariant.initials()).isEqualTo("Z");

      AuthorNameVariant shortVariant =
          saved.getNameVariants().stream()
              .filter(v -> v.foreName() == null)
              .findFirst()
              .orElseThrow();
      assertThat(shortVariant.lastName()).isEqualTo("Lu");
      assertThat(shortVariant.initials()).isEqualTo("Z");
    }
  }

  @Nested
  @DisplayName("existsByNormalizedKey() 方法测试")
  class ExistsByNormalizedKeyTests {

    @Test
    @DisplayName("存在时返回 true")
    void existsByNormalizedKey_exists_shouldReturnTrue() {
      // Given: 保存作者
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Test+A");
      author.addNameVariant(AuthorNameVariant.of("Test", "Author", "A", "Test,Author,A"));
      authorRepository.save(author);

      // When & Then
      assertThat(authorRepository.existsByNormalizedKey("Test+A")).isTrue();
    }

    @Test
    @DisplayName("不存在时返回 false")
    void existsByNormalizedKey_notExists_shouldReturnFalse() {
      // When & Then
      assertThat(authorRepository.existsByNormalizedKey("Unknown+X")).isFalse();
    }
  }

  @Nested
  @DisplayName("existsByOrcid() 方法测试")
  class ExistsByOrcidTests {

    @Test
    @DisplayName("存在时返回 true")
    void existsByOrcid_exists_shouldReturnTrue() {
      // Given: 保存有 ORCID 的作者
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Test+A");
      author.addNameVariant(AuthorNameVariant.of("Test", "Author", "A", "Test,Author,A"));
      author.addOrcid(Orcid.of("0000-0002-1825-0097"));
      authorRepository.save(author);

      // When & Then
      assertThat(authorRepository.existsByOrcid("0000-0002-1825-0097")).isTrue();
    }

    @Test
    @DisplayName("不存在时返回 false")
    void existsByOrcid_notExists_shouldReturnFalse() {
      // When & Then
      assertThat(authorRepository.existsByOrcid("0000-0000-0000-0000")).isFalse();
    }
  }

  @Nested
  @DisplayName("findByNormalizedKeys() 批量查询测试")
  class FindByNormalizedKeysTests {

    @Test
    @DisplayName("批量查询 - 应该返回匹配的作者")
    void findByNormalizedKeys_shouldReturnMatchingAuthors() {
      // Given: 保存多个作者
      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("Smith+J");
      author1.addNameVariant(AuthorNameVariant.of("Smith", "John", "J", "Smith,John,J"));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("Brown+A");
      author2.addNameVariant(AuthorNameVariant.of("Brown", "Alice", "A", "Brown,Alice,A"));

      AuthorAggregate author3 = AuthorAggregate.fromPubMedComputed("Wilson+B");
      author3.addNameVariant(AuthorNameVariant.of("Wilson", "Bob", "B", "Wilson,Bob,B"));

      authorRepository.saveBatch(List.of(author1, author2, author3));

      // When: 批量查询部分作者
      List<AuthorAggregate> found =
          authorRepository.findByNormalizedKeys(List.of("Smith+J", "Wilson+B", "Unknown+X"));

      // Then: 验证只返回存在的作者
      assertThat(found).hasSize(2);
      assertThat(found)
          .extracting(AuthorAggregate::getNormalizedKey)
          .containsExactlyInAnyOrder("Smith+J", "Wilson+B");
    }
  }
}
