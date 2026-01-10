package com.patra.catalog.infra.adapter.batch.author;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.infra.adapter.persistence.AuthorRepositoryAdapter;
import com.patra.catalog.infra.adapter.persistence.dao.AuthorDao;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorEntity;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// AuthorItemWriter 集成测试。
///
/// **测试策略**：
///
/// - 使用 TestContainers + MySQL 测试数据库写入
/// - 验证解析后的数据能正确保存到数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
///
/// **测试场景**：
///
/// - 批量写入 500 条数据
/// - 验证数据完整性（normalizedKey、nameVariants、orcids）
/// - 验证子实体正确保存
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  AuthorItemWriter.class,
  AuthorRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "com.patra.catalog.infra.adapter.persistence.converter")
@ActiveProfiles("test")
@DisplayName("AuthorItemWriter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AuthorItemWriterIT {

  @Autowired private AuthorItemWriter authorItemWriter;

  @Autowired private AuthorDao authorDao;

  @Autowired private ObjectMapper objectMapper;

  private PubMedComputedAuthorParser parser;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    parser = new PubMedComputedAuthorParser(objectMapper);
  }

  @Nested
  @DisplayName("正常写入测试")
  class NormalWriteTests {

    @Test
    @DisplayName("写入 500 条作者数据 - 应该全部正确保存到数据库")
    void write500Authors_shouldSaveAllToDatabase() throws Exception {
      // Given: 生成并解析 500 条测试数据
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(500, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      List<AuthorAggregate> authors;
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        authors = stream.toList();
      }

      // When: 批量写入
      Chunk<AuthorAggregate> chunk = new Chunk<>(authors);
      authorItemWriter.write(chunk);

      // Then: 验证数据库记录数
      long count = authorDao.count();
      assertThat(count).isEqualTo(500);
    }

    @Test
    @DisplayName("写入作者 - 应该正确保存名字变体")
    void writeAuthors_shouldSaveNameVariantsCorrectly() throws Exception {
      // Given: 生成并解析测试数据
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(100, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      List<AuthorAggregate> authors;
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        authors = stream.toList();
      }

      // When: 写入
      Chunk<AuthorAggregate> chunk = new Chunk<>(authors);
      authorItemWriter.write(chunk);

      // Then: 验证名字变体
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      assertThat(savedAuthors).isNotEmpty();

      // 每个作者至少有一个名字变体
      for (AuthorEntity author : savedAuthors) {
        assertThat(author.getNameVariants()).isNotEmpty();
        author
            .getNameVariants()
            .forEach(
                variant -> {
                  assertThat(variant.getFullString()).isNotBlank();
                  assertThat(variant.getLastName()).isNotBlank();
                });
      }
    }

    @Test
    @DisplayName("写入作者 - 应该正确保存 ORCID")
    void writeAuthors_shouldSaveOrcidsCorrectly() throws Exception {
      // Given: 生成测试数据（约 30% 有 ORCID）
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(100, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      List<AuthorAggregate> authors;
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        authors = stream.toList();
      }

      // When: 写入
      Chunk<AuthorAggregate> chunk = new Chunk<>(authors);
      authorItemWriter.write(chunk);

      // Then: 验证 ORCID（约 30% 有）
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      long authorsWithOrcid = savedAuthors.stream().filter(a -> !a.getOrcids().isEmpty()).count();

      // 应该有约 20-40% 的作者有 ORCID
      assertThat(authorsWithOrcid).isBetween(20L, 40L);

      // 验证 ORCID 格式
      savedAuthors.stream()
          .flatMap(a -> a.getOrcids().stream())
          .forEach(
              orcid -> {
                assertThat(orcid.getOrcid()).matches("\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]");
              });
    }
  }

  @Nested
  @DisplayName("数据完整性测试")
  class DataIntegrityTests {

    @Test
    @DisplayName("验证 normalizedKey 唯一性 - 应该全部不同")
    void verifyNormalizedKeyUniqueness() throws Exception {
      // Given
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(100, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      List<AuthorAggregate> authors;
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        authors = stream.toList();
      }

      // When
      Chunk<AuthorAggregate> chunk = new Chunk<>(authors);
      authorItemWriter.write(chunk);

      // Then: 验证 normalizedKey 全部唯一
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      long uniqueKeys =
          savedAuthors.stream().map(AuthorEntity::getNormalizedKey).distinct().count();
      assertThat(uniqueKeys).isEqualTo(savedAuthors.size());
    }

    @Test
    @DisplayName("验证 provenanceCode - 应该全部是 PUBMED")
    void verifyProvenanceCode() throws Exception {
      // Given
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(50, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      List<AuthorAggregate> authors;
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        authors = stream.toList();
      }

      // When
      Chunk<AuthorAggregate> chunk = new Chunk<>(authors);
      authorItemWriter.write(chunk);

      // Then: 验证所有作者的 provenanceCode 都是 PUBMED
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      assertThat(savedAuthors).allMatch(a -> "PUBMED".equals(a.getProvenanceCode()));
    }

    @Test
    @DisplayName("验证 status - 应该全部是 ACTIVE")
    void verifyStatus() throws Exception {
      // Given
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(50, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      List<AuthorAggregate> authors;
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        authors = stream.toList();
      }

      // When
      Chunk<AuthorAggregate> chunk = new Chunk<>(authors);
      authorItemWriter.write(chunk);

      // Then: 验证所有作者的 status 都是 ACTIVE
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      assertThat(savedAuthors).allMatch(a -> "ACTIVE".equals(a.getStatus()));
    }

    @Test
    @DisplayName("验证中文姓名 - 应该正确处理 UTF-8")
    void verifyChineseNames() throws Exception {
      // Given: 生成包含中文姓名的数据（约 20%）
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(100, baos);
      InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

      List<AuthorAggregate> authors;
      try (Stream<AuthorAggregate> stream = parser.parse(inputStream)) {
        authors = stream.toList();
      }

      // When
      Chunk<AuthorAggregate> chunk = new Chunk<>(authors);
      authorItemWriter.write(chunk);

      // Then: 验证中文姓名正确保存
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      long chineseVariants =
          savedAuthors.stream()
              .flatMap(a -> a.getNameVariants().stream())
              .filter(v -> v.getLastName() != null && containsChinese(v.getLastName()))
              .count();

      // 应该有一些中文变体
      assertThat(chineseVariants).isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("边界情况测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("写入空 Chunk - 应该不抛出异常")
    void writeEmptyChunk_shouldNotThrowException() throws Exception {
      // Given: 空 Chunk
      Chunk<AuthorAggregate> emptyChunk = new Chunk<>();

      // When: 写入空 Chunk
      authorItemWriter.write(emptyChunk);

      // Then: 不抛出异常，数据库应该没有记录
      long count = authorDao.count();
      assertThat(count).isEqualTo(0);
    }
  }

  // ========== 辅助方法 ==========

  /// 检查字符串是否包含中文字符。
  private boolean containsChinese(String str) {
    if (str == null) return false;
    return str.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
  }
}
