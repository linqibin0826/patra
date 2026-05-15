package dev.linqibin.patra.catalog.infra.persistence.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `PublicationDao#findTopByVenue` 集成测试。
///
/// **测试目标**：
///
/// - 无 since 参数时按 `citation_count DESC` 返回 top N
/// - 传 since 过滤 `publication_year >= since`
/// - `limit` 控制返回条数
/// - 已软删除的 publication 被排除
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("PublicationDao#findTopByVenue 集成测试")
class PublicationDaoTopByVenueIT {

  private static final Long VENUE_ID = 1001L;

  @Autowired private PublicationDao publicationDao;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM cat_publication WHERE venue_id = ?", VENUE_ID);
    jdbc.update("DELETE FROM cat_venue WHERE id = ?", VENUE_ID);

    jdbc.update(
        "INSERT INTO cat_venue (id, venue_type, title, provenance_code, version, created_at, updated_at) "
            + "VALUES (?, 'JOURNAL', 'Test Journal', 'PUBMED', 0, NOW(6), NOW(6))",
        VENUE_ID);

    // 插入 10 篇 publication：citation_count 从 10→1 递减，publication_year 在 2020..2024 循环
    for (int i = 0; i < 10; i++) {
      jdbc.update(
          "INSERT INTO cat_publication (id, venue_id, title, provenance_code, citation_count, "
              + "publication_year, version, created_at, updated_at) "
              + "VALUES (?, ?, ?, 'PUBMED', ?, ?, 0, NOW(6), NOW(6))",
          2000L + i,
          VENUE_ID,
          "Paper " + i,
          10 - i,
          2020 + (i % 5));
    }
  }

  @Test
  @DisplayName("无 since 参数时按 citation_count 降序返回 top 5")
  void findTopByVenue_withoutSince_returnsTop5ByCitationDesc() {
    List<PublicationEntity> result = publicationDao.findTopByVenue(VENUE_ID, null, 5);

    assertThat(result).hasSize(5);
    assertThat(result.get(0).getCitationCount()).isEqualTo(10);
    assertThat(result.get(1).getCitationCount()).isEqualTo(9);
    assertThat(result.get(4).getCitationCount()).isEqualTo(6);
  }

  @Test
  @DisplayName("传 since 过滤 publication_year ≥ since")
  void findTopByVenue_withSince_filtersByYear() {
    List<PublicationEntity> result = publicationDao.findTopByVenue(VENUE_ID, 2023, 20);

    assertThat(result).allMatch(p -> p.getPublicationYear() >= 2023);
    assertThat(result).isNotEmpty();
  }

  @Test
  @DisplayName("limit 控制返回条数")
  void findTopByVenue_withLimit_respectsLimit() {
    List<PublicationEntity> result = publicationDao.findTopByVenue(VENUE_ID, null, 3);

    assertThat(result).hasSize(3);
  }

  @Test
  @DisplayName("软删除的 publication 被排除")
  void findTopByVenue_excludesSoftDeleted() {
    // 软删除 id=2000 (citation_count=10, 原 top 1)
    jdbc.update("UPDATE cat_publication SET deleted_at = NOW(6) WHERE id = ?", 2000L);

    List<PublicationEntity> result = publicationDao.findTopByVenue(VENUE_ID, null, 5);

    assertThat(result).hasSize(5);
    assertThat(result).extracting(PublicationEntity::getId).doesNotContain(2000L);
    assertThat(result.get(0).getCitationCount()).isEqualTo(9);
  }
}
