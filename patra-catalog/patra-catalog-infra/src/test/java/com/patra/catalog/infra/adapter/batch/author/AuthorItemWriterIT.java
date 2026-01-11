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
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
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

  @Nested
  @DisplayName("ORCID 去重测试")
  class OrcidDeduplicationTests {

    @Test
    @DisplayName("批次内 ORCID 重复 - 应该合并名字变体并只保存一条记录")
    void batchInternalDuplicate_shouldMergeAndSaveOnce() throws Exception {
      // Given: 两个作者有相同的 ORCID（模拟 PubMed 数据源的消歧误差）
      String sharedOrcid = "0000-0001-7223-7726";

      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("SARMA+R");
      author1.withNameVariants(
          List.of(com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse("Sarma,Rup,R")));
      author1.addOrcid(com.patra.catalog.domain.model.vo.author.Orcid.of(sharedOrcid));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("SARMA+R");
      author2.withNameVariants(
          List.of(
              com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse(
                  "Sarma,Rup Jyoti,RJ")));
      author2.addOrcid(com.patra.catalog.domain.model.vo.author.Orcid.of(sharedOrcid));

      // When: 写入包含重复 ORCID 的批次
      Chunk<AuthorAggregate> chunk = new Chunk<>(List.of(author1, author2));
      authorItemWriter.write(chunk);

      // Then: 只有一条记录，但包含两个名字变体
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      assertThat(savedAuthors).hasSize(1);

      AuthorEntity savedAuthor = savedAuthors.getFirst();
      assertThat(savedAuthor.getNameVariants()).hasSize(2);
      assertThat(savedAuthor.getOrcids()).hasSize(1);
      assertThat(savedAuthor.getOrcids().iterator().next().getOrcid()).isEqualTo(sharedOrcid);
    }

    @Test
    @DisplayName("跨批次 ORCID 重复 - 第二批次应合并名字变体到已存在的作者")
    void crossBatchDuplicate_shouldMergeNameVariantsToExistingAuthor() throws Exception {
      // Given: 第一批次写入一个有 ORCID 的作者
      String existingOrcid = "0000-0002-0688-2193";

      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("SMITH+R");
      author1.withNameVariants(
          List.of(
              com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse("Smith,Raymond,R")));
      author1.addOrcid(com.patra.catalog.domain.model.vo.author.Orcid.of(existingOrcid));

      Chunk<AuthorAggregate> firstBatch = new Chunk<>(List.of(author1));
      authorItemWriter.write(firstBatch);

      // 验证第一批次写入成功
      assertThat(authorDao.count()).isEqualTo(1);
      AuthorEntity firstSaved = authorDao.findAll().getFirst();
      assertThat(firstSaved.getNameVariants()).hasSize(1);

      // When: 第二批次尝试写入相同 ORCID 的不同作者（名字变体不同）
      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("SMITH+R");
      author2.withNameVariants(
          List.of(
              com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse(
                  "Smith,Ray Alexander,RA")));
      author2.addOrcid(com.patra.catalog.domain.model.vo.author.Orcid.of(existingOrcid));

      // 添加一个新作者（无 ORCID）确保不影响正常写入
      AuthorAggregate author3 = AuthorAggregate.fromPubMedComputed("JONES+M");
      author3.withNameVariants(
          List.of(
              com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse("Jones,Mary,M")));

      Chunk<AuthorAggregate> secondBatch = new Chunk<>(List.of(author2, author3));
      authorItemWriter.write(secondBatch);

      // Then: 只有 2 条记录（SMITH+R 被合并，JONES+M 是新增）
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      assertThat(savedAuthors).hasSize(2);

      // 验证 SMITH+R 的名字变体被合并（从 1 个变为 2 个）
      AuthorEntity smithAuthor =
          savedAuthors.stream()
              .filter(a -> a.getNormalizedKey().equals("SMITH+R"))
              .findFirst()
              .orElseThrow();
      assertThat(smithAuthor.getNameVariants()).hasSize(2);
      assertThat(smithAuthor.getNameVariants())
          .extracting(v -> v.getFullString())
          .containsExactlyInAnyOrder("Smith,Raymond,R", "Smith,Ray Alexander,RA");

      // 验证新作者正常写入
      boolean jonesExists =
          savedAuthors.stream().anyMatch(a -> a.getNormalizedKey().equals("JONES+M"));
      assertThat(jonesExists).isTrue();
    }

    @Test
    @DisplayName("跨批次 ORCID 重复 - 重复的名字变体应该被去重")
    void crossBatchDuplicate_shouldDeduplicateSameNameVariants() throws Exception {
      // Given: 第一批次写入一个有 ORCID 的作者
      String existingOrcid = "0000-0003-1234-5678";

      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("WANG+L");
      author1.withNameVariants(
          List.of(com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse("Wang,Lei,L")));
      author1.addOrcid(com.patra.catalog.domain.model.vo.author.Orcid.of(existingOrcid));

      Chunk<AuthorAggregate> firstBatch = new Chunk<>(List.of(author1));
      authorItemWriter.write(firstBatch);

      // When: 第二批次尝试写入相同 ORCID 且有重复名字变体的作者
      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("WANG+L");
      author2.withNameVariants(
          List.of(
              com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse("Wang,Lei,L"), // 重复
              com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse(
                  "Wang,Lei Ming,LM"))); // 新变体
      author2.addOrcid(com.patra.catalog.domain.model.vo.author.Orcid.of(existingOrcid));

      Chunk<AuthorAggregate> secondBatch = new Chunk<>(List.of(author2));
      authorItemWriter.write(secondBatch);

      // Then: 只有 1 条记录，名字变体去重后应该有 2 个
      List<AuthorEntity> savedAuthors = authorDao.findAll();
      assertThat(savedAuthors).hasSize(1);

      AuthorEntity wangAuthor = savedAuthors.getFirst();
      assertThat(wangAuthor.getNameVariants()).hasSize(2);
      assertThat(wangAuthor.getNameVariants())
          .extracting(v -> v.getFullString())
          .containsExactlyInAnyOrder("Wang,Lei,L", "Wang,Lei Ming,LM");
    }

    @Test
    @DisplayName("无 ORCID 的作者 - 应该正常写入，不参与去重")
    void authorsWithoutOrcid_shouldWriteNormally() throws Exception {
      // Given: 两个无 ORCID 的作者
      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("ZHANG+W");
      author1.withNameVariants(
          List.of(com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse("Zhang,Wei,W")));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("LI+X");
      author2.withNameVariants(
          List.of(
              com.patra.catalog.domain.model.vo.author.AuthorNameVariant.parse("Li,Xiaoming,X")));

      // When: 写入
      Chunk<AuthorAggregate> chunk = new Chunk<>(List.of(author1, author2));
      authorItemWriter.write(chunk);

      // Then: 两条记录都应该写入
      assertThat(authorDao.count()).isEqualTo(2);
    }
  }

  // ========== 辅助方法 ==========

  /// 检查字符串是否包含中文字符。
  private boolean containsChinese(String str) {
    if (str == null) return false;
    return str.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
  }
}
