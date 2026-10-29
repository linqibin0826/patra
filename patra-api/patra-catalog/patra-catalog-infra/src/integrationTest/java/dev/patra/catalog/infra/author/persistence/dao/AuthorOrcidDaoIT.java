package dev.patra.catalog.infra.author.persistence.dao;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
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

/// `AuthorOrcidDao#findAuthorIdsByOrcidIn` 集成测试。
///
/// **测试目标**：
///
/// - 批量投影只命中 `status = 'ACTIVE'` 的作者
/// - 已软删除（`deleted_at` 非空）的作者被 Hibernate `@SoftDelete` 自动过滤
/// - 库中不存在的 ORCID 不产生任何行（写链路据此判定"无法软关联"）
///
/// **测试数据**：ORCID 样例均为 ISO 7064 校验位合法值。
///
/// @author linqibin
/// @since 0.6.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfiguration.class)
@ActiveProfiles("test")
@DisplayName("AuthorOrcidDao#findAuthorIdsByOrcidIn 集成测试")
class AuthorOrcidDaoIT {

  /// ACTIVE 且未软删的作者 ORCID（唯一应命中的样例）。
  private static final String ORCID_ACTIVE = "0000-0002-1825-0097";

  /// MERGED 状态作者的 ORCID。
  private static final String ORCID_MERGED = "0000-0002-1694-233X";

  /// 已软删除作者的 ORCID。
  private static final String ORCID_SOFT_DELETED = "0000-0001-5109-3700";

  /// 库中不存在的 ORCID。
  private static final String ORCID_ABSENT = "0000-0003-0000-0003";

  @Autowired private AuthorOrcidDao dao;
  @Autowired private JdbcTemplate jdbc;

  /// 测试内自增 ID 游标（事务回滚，跨用例复用不冲突）。
  private long idSeq;

  @BeforeEach
  void setUp() {
    idSeq = 9_100_000L;
    jdbc.update(
        "DELETE FROM cat_author_orcid WHERE orcid IN (?, ?, ?, ?)",
        ORCID_ACTIVE,
        ORCID_MERGED,
        ORCID_SOFT_DELETED,
        ORCID_ABSENT);
    jdbc.update("DELETE FROM cat_author WHERE id BETWEEN 9100000 AND 9100010");
  }

  @Test
  @DisplayName("批查投影：仅返回 ACTIVE 未删作者的 orcid→authorId")
  void findAuthorIdsByOrcidIn_shouldOnlyHitActiveAuthors() {
    Long activeId = saveAuthorWithOrcid("ACTIVE", false, ORCID_ACTIVE);
    saveAuthorWithOrcid("MERGED", false, ORCID_MERGED);
    saveAuthorWithOrcid("ACTIVE", true, ORCID_SOFT_DELETED);

    List<OrcidAuthorIdView> views =
        dao.findAuthorIdsByOrcidIn(
            List.of(ORCID_ACTIVE, ORCID_MERGED, ORCID_SOFT_DELETED, ORCID_ABSENT));

    assertThat(views).hasSize(1);
    assertThat(views.getFirst().getOrcid()).isEqualTo(ORCID_ACTIVE);
    assertThat(views.getFirst().getAuthorId()).isEqualTo(activeId);
  }

  @Test
  @DisplayName("批查投影：空入参返回空列表")
  void findAuthorIdsByOrcidIn_withEmptyInput_shouldReturnEmpty() {
    saveAuthorWithOrcid("ACTIVE", false, ORCID_ACTIVE);

    // 空集合下 Hibernate 将 IN () 折叠为 1=0，不会退化成全表扫描
    assertThat(dao.findAuthorIdsByOrcidIn(List.of())).isEmpty();
  }

  /// 插入一个作者及其 ORCID 子行。
  ///
  /// @param status 作者状态（ACTIVE / MERGED / INACTIVE）
  /// @param softDeleted 是否标记为已软删除
  /// @param orcid ORCID 标识符
  /// @return 新建作者的 ID
  private Long saveAuthorWithOrcid(String status, boolean softDeleted, String orcid) {
    long authorId = ++idSeq;
    jdbc.update(
        "INSERT INTO cat_author (id, normalized_key, display_name, status, provenance_code, "
            + "version, created_at, updated_at) VALUES (?, ?, ?, ?, 'PUBMED', 0, NOW(), NOW())",
        authorId,
        "TEST+" + authorId,
        "Test Author " + authorId,
        status);
    if (softDeleted) {
      jdbc.update("UPDATE cat_author SET deleted_at = NOW() WHERE id = ?", authorId);
    }
    jdbc.update(
        "INSERT INTO cat_author_orcid (id, author_id, orcid, is_primary, version, "
            + "created_at, updated_at) VALUES (?, ?, ?, true, 0, NOW(), NOW())",
        ++idSeq,
        authorId,
        orcid);
    return authorId;
  }
}
