package dev.linqibin.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.enums.DisambiguationMethod;
import dev.linqibin.patra.catalog.domain.model.enums.DisambiguationStatus;
import dev.linqibin.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationAuthorAffiliationDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationAuthorAffiliationEntity;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 作者-机构归属 Dao 集成测试。
///
/// 使用 Testcontainers + PostgreSQL 17 测试 JPA Repository 的 CRUD 操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 PostgreSQL 容器
///
/// **重点测试场景**：
///
/// - 消歧状态枚举字段的正确存储和查询
/// - 消歧方法枚举字段的正确存储和查询
/// - Entity 便捷方法的正确行为
/// - 各种查询方法的正确性
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("PublicationAuthorAffiliationDao 集成测试")
class PublicationAuthorAffiliationDaoIT {

  @Autowired private PublicationAuthorAffiliationDao dao;

  /// 测试数据：文献-作者关联 ID
  private Long pubAuthorId1;
  private Long pubAuthorId2;
  private Long publicationId;
  private Long authorId1;
  private Long authorId2;

  @BeforeEach
  void setUp() {
    pubAuthorId1 = SnowflakeIdGenerator.getId();
    pubAuthorId2 = SnowflakeIdGenerator.getId();
    publicationId = SnowflakeIdGenerator.getId();
    authorId1 = SnowflakeIdGenerator.getId();
    authorId2 = SnowflakeIdGenerator.getId();
  }

  /// 创建测试用的机构归属实体。
  private PublicationAuthorAffiliationEntity createTestEntity(
      Long pubAuthorId, Long publicationId, Long authorId, int order) {
    return PublicationAuthorAffiliationEntity.builder()
        .id(SnowflakeIdGenerator.getId())
        .pubAuthorId(pubAuthorId)
        .publicationId(publicationId)
        .authorId(authorId)
        .affiliationOrder(order)
        .affiliationString("Test University, Department of Computer Science")
        .disambiguationStatus(DisambiguationStatus.PENDING)
        .build();
  }

  @Nested
  @DisplayName("枚举字段存储测试")
  class EnumFieldPersistenceTests {

    @Test
    @DisplayName("保存 PENDING 状态 - 应该正确存储枚举值")
    void save_pendingStatus_shouldPersistEnumCorrectly() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);

      // When
      var saved = dao.save(entity);

      // Then
      assertThat(saved.getDisambiguationStatus()).isEqualTo(DisambiguationStatus.PENDING);
      assertThat(saved.getDisambiguationMethod()).isNull();

      // 重新读取验证
      var found = dao.findById(saved.getId()).orElseThrow();
      assertThat(found.getDisambiguationStatus()).isEqualTo(DisambiguationStatus.PENDING);
    }

    @Test
    @DisplayName("保存所有消歧状态 - 应该正确存储各枚举值")
    void save_allDisambiguationStatuses_shouldPersistCorrectly() {
      // Given & When: 保存各种状态的实体
      var pending = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      pending.setDisambiguationStatus(DisambiguationStatus.PENDING);

      var matched = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      matched.setDisambiguationStatus(DisambiguationStatus.MATCHED);
      matched.setDisambiguationMethod(DisambiguationMethod.ROR_ID);

      var unmatched = createTestEntity(pubAuthorId1, publicationId, authorId1, 3);
      unmatched.setDisambiguationStatus(DisambiguationStatus.UNMATCHED);
      unmatched.setDisambiguationMethod(DisambiguationMethod.NAME_MATCH);

      var ambiguous = createTestEntity(pubAuthorId1, publicationId, authorId1, 4);
      ambiguous.setDisambiguationStatus(DisambiguationStatus.AMBIGUOUS);
      ambiguous.setDisambiguationMethod(DisambiguationMethod.RINGGOLD);

      dao.saveAll(List.of(pending, matched, unmatched, ambiguous));

      // Then: 验证各状态
      assertThat(dao.findById(pending.getId()).orElseThrow().getDisambiguationStatus())
          .isEqualTo(DisambiguationStatus.PENDING);
      assertThat(dao.findById(matched.getId()).orElseThrow().getDisambiguationStatus())
          .isEqualTo(DisambiguationStatus.MATCHED);
      assertThat(dao.findById(unmatched.getId()).orElseThrow().getDisambiguationStatus())
          .isEqualTo(DisambiguationStatus.UNMATCHED);
      assertThat(dao.findById(ambiguous.getId()).orElseThrow().getDisambiguationStatus())
          .isEqualTo(DisambiguationStatus.AMBIGUOUS);
    }

    @Test
    @DisplayName("保存所有消歧方法 - 应该正确存储各枚举值")
    void save_allDisambiguationMethods_shouldPersistCorrectly() {
      // Given & When: 保存各种方法的实体
      var rorId = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      rorId.setDisambiguationMethod(DisambiguationMethod.ROR_ID);

      var ringgold = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      ringgold.setDisambiguationMethod(DisambiguationMethod.RINGGOLD);

      var grid = createTestEntity(pubAuthorId1, publicationId, authorId1, 3);
      grid.setDisambiguationMethod(DisambiguationMethod.GRID);

      var nameMatch = createTestEntity(pubAuthorId1, publicationId, authorId1, 4);
      nameMatch.setDisambiguationMethod(DisambiguationMethod.NAME_MATCH);

      var manual = createTestEntity(pubAuthorId1, publicationId, authorId1, 5);
      manual.setDisambiguationMethod(DisambiguationMethod.MANUAL);

      dao.saveAll(List.of(rorId, ringgold, grid, nameMatch, manual));

      // Then: 验证各方法
      assertThat(dao.findById(rorId.getId()).orElseThrow().getDisambiguationMethod())
          .isEqualTo(DisambiguationMethod.ROR_ID);
      assertThat(dao.findById(ringgold.getId()).orElseThrow().getDisambiguationMethod())
          .isEqualTo(DisambiguationMethod.RINGGOLD);
      assertThat(dao.findById(grid.getId()).orElseThrow().getDisambiguationMethod())
          .isEqualTo(DisambiguationMethod.GRID);
      assertThat(dao.findById(nameMatch.getId()).orElseThrow().getDisambiguationMethod())
          .isEqualTo(DisambiguationMethod.NAME_MATCH);
      assertThat(dao.findById(manual.getId()).orElseThrow().getDisambiguationMethod())
          .isEqualTo(DisambiguationMethod.MANUAL);
    }
  }

  @Nested
  @DisplayName("按消歧状态查询测试")
  class FindByDisambiguationStatusTests {

    @Test
    @DisplayName("查询 PENDING 状态 - 应该返回匹配的实体")
    void findAllByDisambiguationStatus_pending_shouldReturnMatching() {
      // Given
      var pending1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var pending2 = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      var matched = createTestEntity(pubAuthorId1, publicationId, authorId1, 3);
      matched.setDisambiguationStatus(DisambiguationStatus.MATCHED);

      dao.saveAll(List.of(pending1, pending2, matched));

      // When
      List<PublicationAuthorAffiliationEntity> result =
          dao.findAllByDisambiguationStatus(DisambiguationStatus.PENDING);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(e -> e.getDisambiguationStatus() == DisambiguationStatus.PENDING);
    }

    @Test
    @DisplayName("统计各状态数量 - 应该返回正确计数")
    void countByDisambiguationStatus_shouldReturnCorrectCount() {
      // Given
      var pending1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var pending2 = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      var pending3 = createTestEntity(pubAuthorId1, publicationId, authorId1, 3);
      var matched = createTestEntity(pubAuthorId1, publicationId, authorId1, 4);
      matched.setDisambiguationStatus(DisambiguationStatus.MATCHED);

      dao.saveAll(List.of(pending1, pending2, pending3, matched));

      // When & Then
      assertThat(dao.countByDisambiguationStatus(DisambiguationStatus.PENDING)).isEqualTo(3);
      assertThat(dao.countByDisambiguationStatus(DisambiguationStatus.MATCHED)).isEqualTo(1);
      assertThat(dao.countByDisambiguationStatus(DisambiguationStatus.UNMATCHED)).isEqualTo(0);
    }

    @Test
    @DisplayName("分页查询 PENDING 状态 - 应该正确分页")
    void findByDisambiguationStatus_paged_shouldReturnCorrectPage() {
      // Given: 创建 5 个 PENDING 状态的实体
      for (int i = 1; i <= 5; i++) {
        var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, i);
        dao.save(entity);
      }

      // When: 分页查询，每页 2 条
      Page<PublicationAuthorAffiliationEntity> page1 =
          dao.findByDisambiguationStatus(DisambiguationStatus.PENDING, PageRequest.of(0, 2));
      Page<PublicationAuthorAffiliationEntity> page2 =
          dao.findByDisambiguationStatus(DisambiguationStatus.PENDING, PageRequest.of(1, 2));
      Page<PublicationAuthorAffiliationEntity> page3 =
          dao.findByDisambiguationStatus(DisambiguationStatus.PENDING, PageRequest.of(2, 2));

      // Then
      assertThat(page1.getTotalElements()).isEqualTo(5);
      assertThat(page1.getTotalPages()).isEqualTo(3);
      assertThat(page1.getContent()).hasSize(2);
      assertThat(page2.getContent()).hasSize(2);
      assertThat(page3.getContent()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("按关联 ID 查询测试")
  class FindByAssociationIdTests {

    @Test
    @DisplayName("按 pubAuthorId 查询 - 应该返回匹配的实体")
    void findAllByPubAuthorId_shouldReturnMatching() {
      // Given
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var aff2 = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      var aff3 = createTestEntity(pubAuthorId2, publicationId, authorId2, 1);

      dao.saveAll(List.of(aff1, aff2, aff3));

      // When
      List<PublicationAuthorAffiliationEntity> result = dao.findAllByPubAuthorId(pubAuthorId1);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(e -> e.getPubAuthorId().equals(pubAuthorId1));
    }

    @Test
    @DisplayName("批量按 pubAuthorId 查询 - 应该返回所有匹配的实体")
    void findAllByPubAuthorIdIn_shouldReturnAllMatching() {
      // Given
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var aff2 = createTestEntity(pubAuthorId2, publicationId, authorId2, 1);
      Long pubAuthorId3 = SnowflakeIdGenerator.getId();
      var aff3 = createTestEntity(pubAuthorId3, publicationId, authorId1, 1);

      dao.saveAll(List.of(aff1, aff2, aff3));

      // When
      List<PublicationAuthorAffiliationEntity> result =
          dao.findAllByPubAuthorIdIn(List.of(pubAuthorId1, pubAuthorId2));

      // Then
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("按 publicationId 查询 - 应该返回匹配的实体")
    void findAllByPublicationId_shouldReturnMatching() {
      // Given
      Long publicationId2 = SnowflakeIdGenerator.getId();
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var aff2 = createTestEntity(pubAuthorId2, publicationId, authorId2, 1);
      var aff3 = createTestEntity(SnowflakeIdGenerator.getId(), publicationId2, authorId1, 1);

      dao.saveAll(List.of(aff1, aff2, aff3));

      // When
      List<PublicationAuthorAffiliationEntity> result = dao.findAllByPublicationId(publicationId);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(e -> e.getPublicationId().equals(publicationId));
    }

    @Test
    @DisplayName("按 authorId 查询 - 应该返回匹配的实体")
    void findAllByAuthorId_shouldReturnMatching() {
      // Given
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var aff2 = createTestEntity(pubAuthorId2, publicationId, authorId1, 1);
      var aff3 = createTestEntity(SnowflakeIdGenerator.getId(), publicationId, authorId2, 1);

      dao.saveAll(List.of(aff1, aff2, aff3));

      // When
      List<PublicationAuthorAffiliationEntity> result = dao.findAllByAuthorId(authorId1);

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(e -> e.getAuthorId().equals(authorId1));
    }
  }

  @Nested
  @DisplayName("按标识符查询测试")
  class FindByIdentifierTests {

    @Test
    @DisplayName("按 ROR ID 查询 - 应该返回匹配的实体")
    void findAllByRorId_shouldReturnMatching() {
      // Given
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      aff1.setRorId("03vek6s52");

      var aff2 = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      aff2.setRorId("03vek6s52");

      var aff3 = createTestEntity(pubAuthorId1, publicationId, authorId1, 3);
      aff3.setRorId("other-ror");

      dao.saveAll(List.of(aff1, aff2, aff3));

      // When
      List<PublicationAuthorAffiliationEntity> result = dao.findAllByRorId("03vek6s52");

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).allMatch(e -> "03vek6s52".equals(e.getRorId()));
    }

    @Test
    @DisplayName("按 Ringgold ID 查询 - 应该返回匹配的实体")
    void findAllByRinggoldId_shouldReturnMatching() {
      // Given
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      aff1.setRinggoldId("123456");

      var aff2 = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      aff2.setRinggoldId("789012");

      dao.saveAll(List.of(aff1, aff2));

      // When
      List<PublicationAuthorAffiliationEntity> result = dao.findAllByRinggoldId("123456");

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getRinggoldId()).isEqualTo("123456");
    }
  }

  @Nested
  @DisplayName("删除操作测试")
  class DeleteTests {

    @Test
    @DisplayName("按 pubAuthorId 删除 - 应该删除所有匹配的实体")
    void deleteAllByPubAuthorId_shouldDeleteMatching() {
      // Given
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var aff2 = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      var aff3 = createTestEntity(pubAuthorId2, publicationId, authorId2, 1);

      dao.saveAll(List.of(aff1, aff2, aff3));
      assertThat(dao.count()).isEqualTo(3);

      // When
      dao.deleteAllByPubAuthorId(pubAuthorId1);

      // Then
      assertThat(dao.count()).isEqualTo(1);
      assertThat(dao.findAllByPubAuthorId(pubAuthorId1)).isEmpty();
      assertThat(dao.findAllByPubAuthorId(pubAuthorId2)).hasSize(1);
    }

    @Test
    @DisplayName("按 publicationId 删除 - 应该删除所有匹配的实体")
    void deleteAllByPublicationId_shouldDeleteMatching() {
      // Given
      Long publicationId2 = SnowflakeIdGenerator.getId();
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var aff2 = createTestEntity(pubAuthorId2, publicationId, authorId2, 1);
      var aff3 = createTestEntity(SnowflakeIdGenerator.getId(), publicationId2, authorId1, 1);

      dao.saveAll(List.of(aff1, aff2, aff3));
      assertThat(dao.count()).isEqualTo(3);

      // When
      dao.deleteAllByPublicationId(publicationId);

      // Then
      assertThat(dao.count()).isEqualTo(1);
      assertThat(dao.findAllByPublicationId(publicationId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Entity 便捷方法测试")
  class EntityConvenienceMethodTests {

    @Test
    @DisplayName("markAsMatched - 应该正确设置状态、方法、分数和时间戳")
    void markAsMatched_shouldSetAllFields() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      dao.save(entity);
      Long organizationId = SnowflakeIdGenerator.getId();
      Instant beforeMark = Instant.now();

      // When
      entity.markAsMatched(organizationId, DisambiguationMethod.ROR_ID, new BigDecimal("0.9500"));
      dao.save(entity);

      // Then: 重新读取验证
      var found = dao.findById(entity.getId()).orElseThrow();
      assertThat(found.getOrganizationId()).isEqualTo(organizationId);
      assertThat(found.getDisambiguationStatus()).isEqualTo(DisambiguationStatus.MATCHED);
      assertThat(found.getDisambiguationMethod()).isEqualTo(DisambiguationMethod.ROR_ID);
      assertThat(found.getDisambiguationScore()).isEqualByComparingTo(new BigDecimal("0.9500"));
      assertThat(found.getDisambiguatedAt()).isAfterOrEqualTo(beforeMark);
    }

    @Test
    @DisplayName("markAsUnmatched - 应该正确设置状态、方法和时间戳")
    void markAsUnmatched_shouldSetAllFields() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      dao.save(entity);
      Instant beforeMark = Instant.now();

      // When
      entity.markAsUnmatched(DisambiguationMethod.NAME_MATCH);
      dao.save(entity);

      // Then: 重新读取验证
      var found = dao.findById(entity.getId()).orElseThrow();
      assertThat(found.getDisambiguationStatus()).isEqualTo(DisambiguationStatus.UNMATCHED);
      assertThat(found.getDisambiguationMethod()).isEqualTo(DisambiguationMethod.NAME_MATCH);
      assertThat(found.getDisambiguatedAt()).isAfterOrEqualTo(beforeMark);
      assertThat(found.getOrganizationId()).isNull();
      assertThat(found.getDisambiguationScore()).isNull();
    }

    @Test
    @DisplayName("markAsAmbiguous - 应该正确设置状态、方法和时间戳")
    void markAsAmbiguous_shouldSetAllFields() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      dao.save(entity);
      Instant beforeMark = Instant.now();

      // When
      entity.markAsAmbiguous(DisambiguationMethod.RINGGOLD);
      dao.save(entity);

      // Then: 重新读取验证
      var found = dao.findById(entity.getId()).orElseThrow();
      assertThat(found.getDisambiguationStatus()).isEqualTo(DisambiguationStatus.AMBIGUOUS);
      assertThat(found.getDisambiguationMethod()).isEqualTo(DisambiguationMethod.RINGGOLD);
      assertThat(found.getDisambiguatedAt()).isAfterOrEqualTo(beforeMark);
    }

    @Test
    @DisplayName("hasIdentifiers - 有 ROR ID 时返回 true")
    void hasIdentifiers_withRorId_shouldReturnTrue() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      entity.setRorId("03vek6s52");

      // When & Then
      assertThat(entity.hasIdentifiers()).isTrue();
    }

    @Test
    @DisplayName("hasIdentifiers - 有 Ringgold ID 时返回 true")
    void hasIdentifiers_withRinggoldId_shouldReturnTrue() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      entity.setRinggoldId("123456");

      // When & Then
      assertThat(entity.hasIdentifiers()).isTrue();
    }

    @Test
    @DisplayName("hasIdentifiers - 有 GRID ID 时返回 true")
    void hasIdentifiers_withGridId_shouldReturnTrue() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      entity.setGridId("grid.12345.6");

      // When & Then
      assertThat(entity.hasIdentifiers()).isTrue();
    }

    @Test
    @DisplayName("hasIdentifiers - 无任何标识符时返回 false")
    void hasIdentifiers_withNoIdentifiers_shouldReturnFalse() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);

      // When & Then
      assertThat(entity.hasIdentifiers()).isFalse();
    }

    @Test
    @DisplayName("hasIdentifiers - 空白标识符时返回 false")
    void hasIdentifiers_withBlankIdentifiers_shouldReturnFalse() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      entity.setRorId("   ");
      entity.setRinggoldId("");

      // When & Then
      assertThat(entity.hasIdentifiers()).isFalse();
    }

    @Test
    @DisplayName("isPending - PENDING 状态返回 true")
    void isPending_pendingStatus_shouldReturnTrue() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);

      // When & Then
      assertThat(entity.isPending()).isTrue();
      assertThat(entity.isMatched()).isFalse();
    }

    @Test
    @DisplayName("isMatched - MATCHED 状态返回 true")
    void isMatched_matchedStatus_shouldReturnTrue() {
      // Given
      var entity = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      entity.setDisambiguationStatus(DisambiguationStatus.MATCHED);

      // When & Then
      assertThat(entity.isMatched()).isTrue();
      assertThat(entity.isPending()).isFalse();
    }
  }

  @Nested
  @DisplayName("唯一约束测试")
  class UniqueConstraintTests {

    @Test
    @DisplayName("同一 pubAuthorId 不同 affiliationOrder - 应该保存成功")
    void save_samePubAuthorDifferentOrder_shouldSucceed() {
      // Given
      var aff1 = createTestEntity(pubAuthorId1, publicationId, authorId1, 1);
      var aff2 = createTestEntity(pubAuthorId1, publicationId, authorId1, 2);
      var aff3 = createTestEntity(pubAuthorId1, publicationId, authorId1, 3);

      // When
      dao.saveAll(List.of(aff1, aff2, aff3));

      // Then
      assertThat(dao.findAllByPubAuthorId(pubAuthorId1)).hasSize(3);
    }
  }
}
