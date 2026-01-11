package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.enums.MediaType;
import com.patra.catalog.domain.model.enums.OaStatus;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationDao;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
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

/// PublicationRepositoryAdapter 集成测试（JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试文献仓储操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：findById、findByPmid、findByDoi、save、delete 等方法
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "com.patra.catalog.infra.adapter.persistence.converter")
@ActiveProfiles("test")
@DisplayName("PublicationRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PublicationRepositoryAdapterIT {

  @Autowired private PublicationRepositoryAdapter repository;

  @Autowired private PublicationDao jpaRepository;

  // ========== 工厂方法 ==========

  private PublicationAggregate createPublication(
      String pmid, String doi, Long venueId, Long venueInstanceId, String title, int year) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        PublicationIdentifiers.of(pmid, doi),
        venueId != null ? VenueId.of(venueId) : null,
        VenueInstanceId.of(venueInstanceId),
        title,
        null, // originalTitle
        LanguageInfo.of("English", "en-US"),
        PublicationStatus.PPUBLISH,
        MediaType.PRINT,
        year,
        true, // authorsComplete
        10, // numberOfReferences
        null // conflictOfInterest
        );
  }

  private PublicationAggregate createPublicationWithPmidOnly(
      String pmid, Long venueInstanceId, String title, int year) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        PublicationIdentifiers.ofPmid(pmid),
        null, // venueId
        VenueInstanceId.of(venueInstanceId),
        title,
        null,
        LanguageInfo.of("English", "en-US"),
        PublicationStatus.PPUBLISH,
        MediaType.PRINT,
        year,
        true,
        0,
        null);
  }

  @Nested
  @DisplayName("findById 测试")
  class FindByIdTests {

    @Test
    @DisplayName("存在的 ID - 应该返回聚合根")
    void findById_exists_shouldReturnAggregate() {
      // Given
      var publication =
          createPublication(
              "12345678", "10.1000/test.123", 1001L, 2001L, "Test Publication Title", 2024);
      repository.save(publication);

      // When
      Optional<PublicationAggregate> found = repository.findById(publication.getId().value());

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getPmid()).isEqualTo("12345678");
      assertThat(found.get().getDoi()).isEqualTo("10.1000/test.123");
      assertThat(found.get().getTitle()).isEqualTo("Test Publication Title");
    }

    @Test
    @DisplayName("不存在的 ID - 应该返回 empty")
    void findById_notExists_shouldReturnEmpty() {
      // When
      Optional<PublicationAggregate> found = repository.findById(999999L);

      // Then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("null ID - 应该返回 empty")
    void findById_null_shouldReturnEmpty() {
      // When
      Optional<PublicationAggregate> found = repository.findById(null);

      // Then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByPmid 测试")
  class FindByPmidTests {

    @Test
    @DisplayName("存在的 PMID - 应该返回聚合根")
    void findByPmid_exists_shouldReturnAggregate() {
      // Given
      var publication =
          createPublicationWithPmidOnly("87654321", 2001L, "PMID Test Publication", 2024);
      repository.save(publication);

      // When
      Optional<PublicationAggregate> found = repository.findByPmid("87654321");

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getPmid()).isEqualTo("87654321");
    }

    @Test
    @DisplayName("不存在的 PMID - 应该返回 empty")
    void findByPmid_notExists_shouldReturnEmpty() {
      // When
      Optional<PublicationAggregate> found = repository.findByPmid("99999999");

      // Then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("null PMID - 应该返回 empty")
    void findByPmid_null_shouldReturnEmpty() {
      // When
      Optional<PublicationAggregate> found = repository.findByPmid(null);

      // Then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByDoi 测试")
  class FindByDoiTests {

    @Test
    @DisplayName("存在的 DOI - 应该返回聚合根")
    void findByDoi_exists_shouldReturnAggregate() {
      // Given
      var publication =
          createPublication(
              "11111111", "10.1234/unique.doi", 1001L, 2001L, "DOI Test Publication", 2024);
      repository.save(publication);

      // When
      Optional<PublicationAggregate> found = repository.findByDoi("10.1234/unique.doi");

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getDoi()).isEqualTo("10.1234/unique.doi");
    }

    @Test
    @DisplayName("不存在的 DOI - 应该返回 empty")
    void findByDoi_notExists_shouldReturnEmpty() {
      // When
      Optional<PublicationAggregate> found = repository.findByDoi("10.9999/notexist");

      // Then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByPmidOrDoi 测试")
  class FindByPmidOrDoiTests {

    @Test
    @DisplayName("PMID 匹配 - 应该返回聚合根")
    void findByPmidOrDoi_pmidMatch_shouldReturnAggregate() {
      // Given
      var publication =
          createPublication("22222222", "10.2222/test", 1001L, 2001L, "Test Publication", 2024);
      repository.save(publication);

      // When
      Optional<PublicationAggregate> found =
          repository.findByPmidOrDoi("22222222", "10.9999/different");

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getPmid()).isEqualTo("22222222");
    }

    @Test
    @DisplayName("DOI 匹配 - 应该返回聚合根")
    void findByPmidOrDoi_doiMatch_shouldReturnAggregate() {
      // Given
      var publication =
          createPublication("33333333", "10.3333/test", 1001L, 2001L, "Test Publication", 2024);
      repository.save(publication);

      // When
      Optional<PublicationAggregate> found = repository.findByPmidOrDoi("99999999", "10.3333/test");

      // Then
      assertThat(found).isPresent();
      assertThat(found.get().getDoi()).isEqualTo("10.3333/test");
    }

    @Test
    @DisplayName("两者都为 null - 应该返回 empty")
    void findByPmidOrDoi_bothNull_shouldReturnEmpty() {
      // When
      Optional<PublicationAggregate> found = repository.findByPmidOrDoi(null, null);

      // Then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("existsByPmid/existsByDoi 测试")
  class ExistsTests {

    @Test
    @DisplayName("存在的 PMID - 应该返回 true")
    void existsByPmid_exists_shouldReturnTrue() {
      // Given
      var publication =
          createPublicationWithPmidOnly("44444444", 2001L, "Exists Test Publication", 2024);
      repository.save(publication);

      // When & Then
      assertThat(repository.existsByPmid("44444444")).isTrue();
      assertThat(repository.existsByPmid("99999999")).isFalse();
    }

    @Test
    @DisplayName("存在的 DOI - 应该返回 true")
    void existsByDoi_exists_shouldReturnTrue() {
      // Given
      var publication =
          createPublication("55555555", "10.5555/exists", 1001L, 2001L, "Exists Test", 2024);
      repository.save(publication);

      // When & Then
      assertThat(repository.existsByDoi("10.5555/exists")).isTrue();
      assertThat(repository.existsByDoi("10.9999/notexists")).isFalse();
    }
  }

  @Nested
  @DisplayName("findByVenueId/findByVenueInstanceId 测试")
  class FindByVenueTests {

    @Test
    @DisplayName("按载体 ID 查找 - 应该返回关联文献")
    void findByVenueId_shouldReturnPublications() {
      // Given
      repository.save(
          createPublication("66666661", "10.6666/a", 1001L, 2001L, "Publication A", 2024));
      repository.save(
          createPublication("66666662", "10.6666/b", 1001L, 2002L, "Publication B", 2024));
      repository.save(
          createPublication("66666663", "10.6666/c", 1002L, 2003L, "Publication C", 2024));

      // When
      List<PublicationAggregate> found = repository.findByVenueId(1001L);

      // Then
      assertThat(found).hasSize(2);
    }

    @Test
    @DisplayName("按载体实例 ID 查找 - 应该返回关联文献")
    void findByVenueInstanceId_shouldReturnPublications() {
      // Given
      repository.save(
          createPublication("77777771", "10.7777/a", 1001L, 2001L, "Publication A", 2024));
      repository.save(
          createPublication("77777772", "10.7777/b", 1001L, 2001L, "Publication B", 2024));
      repository.save(
          createPublication("77777773", "10.7777/c", 1001L, 2002L, "Publication C", 2024));

      // When
      List<PublicationAggregate> found = repository.findByVenueInstanceId(2001L);

      // Then
      assertThat(found).hasSize(2);
    }

    @Test
    @DisplayName("统计载体文献数量")
    void countByVenueId_shouldReturnCount() {
      // Given
      repository.save(
          createPublication("88888881", "10.8888/a", 1001L, 2001L, "Publication A", 2024));
      repository.save(
          createPublication("88888882", "10.8888/b", 1001L, 2002L, "Publication B", 2024));

      // When & Then
      assertThat(repository.countByVenueId(1001L)).isEqualTo(2);
      assertThat(repository.countByVenueId(9999L)).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("save 测试")
  class SaveTests {

    @Test
    @DisplayName("新建文献 - 应该分配 ID")
    void save_newPublication_shouldAssignId() {
      // Given
      var publication =
          createPublication("99999991", "10.9999/new", 1001L, 2001L, "New Publication", 2024);
      assertThat(publication.getId()).isNull();

      // When
      repository.save(publication);

      // Then
      assertThat(publication.getId()).isNotNull();
      assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("更新 OA 状态 - 应该保存变更")
    void save_updateOaStatus_shouldPersist() {
      // Given
      var publication =
          createPublication("10101010", "10.1010/oa", 1001L, 2001L, "OA Test Publication", 2024);
      repository.save(publication);
      assertThat(publication.getIsOa()).isFalse();

      // When
      publication.updateOaStatus(true, OaStatus.GOLD);
      repository.save(publication);

      // Then
      var found = repository.findById(publication.getId().value()).orElseThrow();
      assertThat(found.getIsOa()).isTrue();
      assertThat(found.getOaStatus()).isEqualTo(OaStatus.GOLD);
    }

    @Test
    @DisplayName("更新被引次数 - 应该保存变更")
    void save_incrementCitationCount_shouldPersist() {
      // Given
      var publication =
          createPublication(
              "11112222", "10.1111/citation", 1001L, 2001L, "Citation Test Publication", 2024);
      repository.save(publication);
      assertThat(publication.getCitationCount()).isEqualTo(0);

      // When
      publication.incrementCitationCount(5);
      repository.save(publication);

      // Then
      var found = repository.findById(publication.getId().value()).orElseThrow();
      assertThat(found.getCitationCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("null 聚合根 - 应该抛出异常")
    void save_null_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> repository.save(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("聚合根不能为 null");
    }
  }

  @Nested
  @DisplayName("insertAll 测试")
  class InsertAllTests {

    @Test
    @DisplayName("批量插入 - 应该全部成功并回填 ID")
    void insertAll_shouldInsertAllAndBackfillIds() {
      // Given
      List<PublicationAggregate> publications =
          List.of(
              createPublication("20202021", "10.2020/a", 1001L, 2001L, "Batch A", 2024),
              createPublication("20202022", "10.2020/b", 1001L, 2001L, "Batch B", 2024),
              createPublication("20202023", "10.2020/c", 1001L, 2001L, "Batch C", 2024));

      // When
      repository.insertAll(publications);

      // Then
      assertThat(jpaRepository.count()).isEqualTo(3);
      for (PublicationAggregate publication : publications) {
        assertThat(publication.getId()).isNotNull();
      }
    }

    @Test
    @DisplayName("空列表 - 应该不执行任何操作")
    void insertAll_empty_shouldDoNothing() {
      // When
      repository.insertAll(List.of());

      // Then
      assertThat(jpaRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("null - 应该不执行任何操作")
    void insertAll_null_shouldDoNothing() {
      // When
      repository.insertAll(null);

      // Then
      assertThat(jpaRepository.count()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("deleteById 测试")
  class DeleteByIdTests {

    @Test
    @DisplayName("存在的 ID - 应该删除并返回 true")
    void deleteById_exists_shouldDeleteAndReturnTrue() {
      // Given
      var publication =
          createPublication("30303030", "10.3030/delete", 1001L, 2001L, "Delete Test", 2024);
      repository.save(publication);
      assertThat(jpaRepository.count()).isEqualTo(1);

      // When
      boolean deleted = repository.deleteById(publication.getId().value());

      // Then
      assertThat(deleted).isTrue();
      assertThat(jpaRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("不存在的 ID - 应该返回 false")
    void deleteById_notExists_shouldReturnFalse() {
      // When
      boolean deleted = repository.deleteById(999999L);

      // Then
      assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("null ID - 应该返回 false")
    void deleteById_null_shouldReturnFalse() {
      // When
      boolean deleted = repository.deleteById(null);

      // Then
      assertThat(deleted).isFalse();
    }
  }

  @Nested
  @DisplayName("deleteByVenueId/deleteByVenueInstanceId 测试")
  class DeleteByVenueTests {

    @Test
    @DisplayName("根据载体 ID 删除 - 应该删除所有关联文献")
    void deleteByVenueId_shouldDeleteAllRelated() {
      // Given
      repository.save(
          createPublication("40404041", "10.4040/a", 1001L, 2001L, "Publication A", 2024));
      repository.save(
          createPublication("40404042", "10.4040/b", 1001L, 2002L, "Publication B", 2024));
      repository.save(
          createPublication("40404043", "10.4040/c", 1002L, 2003L, "Publication C", 2024));
      assertThat(jpaRepository.count()).isEqualTo(3);

      // When
      int deleted = repository.deleteByVenueId(1001L);

      // Then
      assertThat(deleted).isEqualTo(2);
      assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("根据载体实例 ID 删除 - 应该删除所有关联文献")
    void deleteByVenueInstanceId_shouldDeleteAllRelated() {
      // Given
      repository.save(
          createPublication("50505051", "10.5050/a", 1001L, 2001L, "Publication A", 2024));
      repository.save(
          createPublication("50505052", "10.5050/b", 1001L, 2001L, "Publication B", 2024));
      repository.save(
          createPublication("50505053", "10.5050/c", 1001L, 2002L, "Publication C", 2024));
      assertThat(jpaRepository.count()).isEqualTo(3);

      // When
      int deleted = repository.deleteByVenueInstanceId(2001L);

      // Then
      assertThat(deleted).isEqualTo(2);
      assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("null venueId - 应该返回 0")
    void deleteByVenueId_null_shouldReturnZero() {
      // When
      int deleted = repository.deleteByVenueId(null);

      // Then
      assertThat(deleted).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("语言信息测试")
  class LanguageInfoTests {

    @Test
    @DisplayName("保存和恢复语言信息 - 应该正确重建值对象")
    void save_withLanguageInfo_shouldRebuildCorrectly() {
      // Given
      var publication =
          PublicationAggregate.create(
              ProvenanceCode.PUBMED,
              PublicationIdentifiers.ofPmid("60606060"),
              VenueId.of(1001L),
              VenueInstanceId.of(2001L),
              "中文文献标题",
              "Chinese Publication Title",
              LanguageInfo.of("Chinese", "zh-CN"),
              PublicationStatus.PPUBLISH,
              MediaType.ELECTRONIC,
              2024,
              true,
              5,
              null);
      repository.save(publication);

      // When
      var found = repository.findById(publication.getId().value()).orElseThrow();

      // Then
      assertThat(found.getLanguageInfo()).isNotNull();
      assertThat(found.getLanguageInfo().raw()).isEqualTo("Chinese");
      assertThat(found.getLanguageInfo().code()).isEqualTo("zh-CN");
      // language_base 由数据库生成列计算
    }
  }
}
