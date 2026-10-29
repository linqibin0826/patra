package dev.patra.catalog.infra.author.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `AuthorOrcidReadAdapter` 集成测试：ORCID → 作者 ID，只认 ACTIVE 且未软删的作者。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuthorOrcidReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("AuthorOrcidReadAdapter ORCID 读端集成测试")
class AuthorOrcidReadAdapterIT {

  @Autowired private AuthorOrcidReadAdapter adapter;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("全部命中：每个 ORCID 映射到作者 ID")
  void should_map_every_orcid_when_all_hit() {
    long smith = saveAuthor("ACTIVE", "0000-0001-0000-0001");
    long doe = saveAuthor("ACTIVE", "0000-0001-0000-0002");

    Map<String, Long> result =
        adapter.findAuthorIdsByOrcidIn(List.of("0000-0001-0000-0001", "0000-0001-0000-0002"));

    assertThat(result)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("0000-0001-0000-0001", smith, "0000-0001-0000-0002", doe));
  }

  @Test
  @DisplayName("部分命中：未命中、非 ACTIVE、已软删的作者都不出现")
  void should_leave_out_missing_inactive_and_deleted_authors() {
    long smith = saveAuthor("ACTIVE", "0000-0001-0000-0001");
    saveAuthor("MERGED", "0000-0001-0000-0002");
    long deleted = saveAuthor("ACTIVE", "0000-0001-0000-0003");
    jdbc.update("UPDATE cat_author SET deleted_at = now() WHERE id = ?", deleted);

    Map<String, Long> result =
        adapter.findAuthorIdsByOrcidIn(
            List.of(
                "0000-0001-0000-0001",
                "0000-0001-0000-0002",
                "0000-0001-0000-0003",
                "0000-0001-0000-0009"));

    assertThat(result).containsExactlyEntriesOf(Map.of("0000-0001-0000-0001", smith));
  }

  @Test
  @DisplayName("空输入返回空 Map")
  void should_return_empty_map_when_input_is_empty() {
    assertThat(adapter.findAuthorIdsByOrcidIn(List.of())).isEmpty();
  }

  /// 写一个带 ORCID 的作者。
  ///
  /// @param status 作者状态
  /// @param orcid ORCID
  /// @return 作者 ID
  private long saveAuthor(String status, String orcid) {
    long id = SnowflakeIdGenerator.getId();
    jdbc.update(
        "INSERT INTO cat_author (id, normalized_key, status, provenance_code)"
            + " VALUES (?, ?, ?, 'PUBMED')",
        id,
        "key-" + id,
        status);
    jdbc.update(
        "INSERT INTO cat_author_orcid (id, author_id, orcid) VALUES (?, ?, ?)",
        SnowflakeIdGenerator.getId(),
        id,
        orcid);
    return id;
  }
}
