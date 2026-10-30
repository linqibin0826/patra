package dev.patra.catalog.infra.publication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.patra.catalog.domain.kernel.model.enums.ProvenanceCode;
import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.publication.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.publication.model.enums.PublicationMedium;
import dev.patra.catalog.domain.publication.model.enums.PublicationStatus;
import dev.patra.catalog.domain.publication.model.vo.LanguageInfo;
import dev.patra.catalog.domain.publication.model.vo.PublicationAuthorAffiliationSnapshot;
import dev.patra.catalog.domain.publication.model.vo.PublicationAuthorSnapshot;
import dev.patra.catalog.domain.publication.model.vo.PublicationCompleteData;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import dev.patra.catalog.infra.author.persistence.AuthorOrcidReadAdapter;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 作者写链路闭环集成测试（写入 → 恢复）。
///
/// **测试目标**：
///
/// - 作者快照（含机构、ORCID 软关联）经 `insertAllWithAssociations` 写入后可由 `findById` 完整恢复
/// - 同一文献重写时按替换语义先删旧行，不撞 `uk_author_order`
/// - 同篇多个作者命中同一已消歧作者时，仅 `author_order` 最小行保留 `author_id`
///   （否则撞部分唯一索引 `uk_pub_author` 导致整批回滚）
///
/// @author linqibin
/// @since 0.6.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationRepositoryAdapter.class,
  AuthorOrcidReadAdapter.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class,
  JsonColumnCodec.class
})
@ComponentScan(
    basePackages = {
      "dev.patra.catalog.infra.*.persistence.mapper",
      "dev.patra.catalog.infra.*.persistence.converter"
    })
@ActiveProfiles("test")
@DisplayName("文献作者写链路闭环集成测试")
class PublicationAuthorRoundTripIT {

  /// ISO 7064 校验位合法的 ORCID 样例。
  private static final String ORCID_ACTIVE = "0000-0002-1825-0097";

  @Autowired private PublicationRepositoryAdapter adapter;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  /// 测试内自增 ID 游标（事务回滚，跨用例复用不冲突）。
  private long idSeq = 9_200_000L;

  @Test
  @DisplayName("写入→findById 恢复：作者快照完整闭环（含机构与 ORCID 软关联）")
  void writeAndRestore_authorSnapshots() {
    Long knownAuthorId = saveActiveAuthorWithOrcid(ORCID_ACTIVE);

    PublicationAggregate agg = buildAggregateWithVenue("90000001", "10.9000/rt-1");
    agg.attachAuthors(
        List.of(
            PublicationAuthorSnapshot.builder()
                .order(1)
                .lastName("Smith")
                .foreName("John")
                .initials("J")
                .suffix("Jr")
                .displayName("Smith John")
                .orcid(ORCID_ACTIVE)
                .firstAuthor(true)
                .equalContribution(true)
                .affiliations(
                    List.of(
                        PublicationAuthorAffiliationSnapshot.of(1, "Harvard"),
                        PublicationAuthorAffiliationSnapshot.of(2, "MIT")))
                .build(),
            PublicationAuthorSnapshot.builder()
                .order(2)
                .collectiveName("WHO Group")
                .displayName("WHO Group")
                .affiliations(List.of(PublicationAuthorAffiliationSnapshot.of(1, "WHO HQ")))
                .build()));

    adapter.insertAllWithAssociations(List.of(PublicationCompleteData.ofPublication(agg)));
    em.flush();
    em.clear();

    PublicationAggregate restored = adapter.findById(agg.getId().value()).orElseThrow();

    assertThat(restored.getAuthors()).hasSize(2);
    PublicationAuthorSnapshot first = restored.getAuthors().getFirst();
    assertThat(first.order()).isEqualTo(1);
    assertThat(first.displayName()).isEqualTo("Smith John");
    assertThat(first.lastName()).isEqualTo("Smith");
    assertThat(first.foreName()).isEqualTo("John");
    assertThat(first.initials()).isEqualTo("J");
    assertThat(first.suffix()).isEqualTo("Jr");
    assertThat(first.firstAuthor()).isTrue();
    assertThat(first.equalContribution()).isTrue();
    assertThat(first.orcid()).isEqualTo(ORCID_ACTIVE);
    assertThat(first.authorId()).isEqualTo(knownAuthorId);

    PublicationAuthorSnapshot second = restored.getAuthors().get(1);
    assertThat(second.order()).isEqualTo(2);
    assertThat(second.authorId()).isNull();
    assertThat(second.isCollective()).isTrue();
    assertThat(second.firstAuthor()).isFalse();
    assertThat(second.equalContribution()).isFalse();

    // 机构须按 pub_author_id 正确归属，不得因预分配 ID 串位而错挂到另一作者
    assertThat(first.affiliations())
        .extracting(PublicationAuthorAffiliationSnapshot::affiliationString)
        .containsExactly("Harvard", "MIT");
    assertThat(second.affiliations())
        .extracting(PublicationAuthorAffiliationSnapshot::affiliationString)
        .containsExactly("WHO HQ");
  }

  @Test
  @DisplayName("重写同一文献：替换语义不撞唯一约束（幂等重导）")
  void rewrite_samePublication_replaceSemantics() {
    PublicationAggregate agg = buildAggregateWithVenue("90000002", "10.9000/rt-2");
    agg.attachAuthors(
        List.of(
            PublicationAuthorSnapshot.builder()
                .order(1)
                .displayName("Smith John")
                .firstAuthor(true)
                .affiliations(
                    List.of(
                        PublicationAuthorAffiliationSnapshot.of(1, "Harvard"),
                        PublicationAuthorAffiliationSnapshot.of(2, "MIT")))
                .build(),
            PublicationAuthorSnapshot.builder()
                .order(2)
                .displayName("Doe Jane")
                .affiliations(List.of(PublicationAuthorAffiliationSnapshot.of(1, "Oxford")))
                .build()));

    adapter.insertAllWithAssociations(List.of(PublicationCompleteData.ofPublication(agg)));
    em.flush();
    em.clear();

    Long publicationId = agg.getId().value();
    assertThat(countAuthorRows(publicationId)).isEqualTo(2);
    assertThat(countAffiliationRows(publicationId)).isEqualTo(3);
    List<Long> staleAuthorRowIds = authorRowIds(publicationId);

    // 模拟幂等重导：主表行按上游策略先行清理，作者行仍是上一轮遗留（author_order 1/2 已占用）
    jdbc.update("DELETE FROM cat_publication WHERE id = ?", publicationId);
    assertThat(countAuthorRows(publicationId)).isEqualTo(2);

    // 次轮作者不带机构：机构行归零才能证明旧机构行被一并清除（该表无外键，删漏只会留孤儿不报错）
    agg.attachAuthors(List.of(authorSnapshot(1, "Zhang Wei", null)));

    assertThatCode(
            () ->
                adapter.insertAllWithAssociations(
                    List.of(PublicationCompleteData.ofPublication(agg))))
        .doesNotThrowAnyException();
    em.flush();
    em.clear();

    PublicationAggregate restored = adapter.findById(publicationId).orElseThrow();
    assertThat(restored.getAuthors()).hasSize(1);
    assertThat(restored.getAuthors().getFirst().displayName()).isEqualTo("Zhang Wei");
    assertThat(restored.getAuthors().getFirst().affiliations()).isEmpty();
    assertThat(countAffiliationRows(publicationId)).isZero();
    assertThat(authorRowIds(publicationId)).doesNotContainAnyElementsOf(staleAuthorRowIds);
  }

  @Test
  @DisplayName("同篇两作者同 ORCID：仅 order 最小行保留 authorId（去重不变量）")
  void duplicateOrcidWithinPublication_onlyFirstKeepsAuthorId() {
    Long knownAuthorId = saveActiveAuthorWithOrcid(ORCID_ACTIVE);

    PublicationAggregate agg = buildAggregateWithVenue("90000003", "10.9000/rt-3");
    agg.attachAuthors(
        List.of(
            authorSnapshot(1, "Smith John", ORCID_ACTIVE),
            authorSnapshot(2, "Smith J", ORCID_ACTIVE)));

    assertThatCode(
            () ->
                adapter.insertAllWithAssociations(
                    List.of(PublicationCompleteData.ofPublication(agg))))
        .doesNotThrowAnyException();
    em.flush();
    em.clear();

    PublicationAggregate restored = adapter.findById(agg.getId().value()).orElseThrow();
    assertThat(restored.getAuthors()).hasSize(2);
    assertThat(restored.getAuthors().getFirst().authorId()).isEqualTo(knownAuthorId);
    assertThat(restored.getAuthors().get(1).authorId()).isNull();
    // ORCID 存档位不受去重影响，两行均保留原文
    assertThat(restored.getAuthors().getFirst().orcid()).isEqualTo(ORCID_ACTIVE);
    assertThat(restored.getAuthors().get(1).orcid()).isEqualTo(ORCID_ACTIVE);
  }

  // ========== 测试助手 ==========

  /// 构造带载体的文献聚合根。
  ///
  /// @param pmid PMID
  /// @param doi DOI
  /// @return 文献聚合根
  private PublicationAggregate buildAggregateWithVenue(String pmid, String doi) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        doi,
        VenueId.of(1001L),
        VenueInstanceId.of(2001L),
        "Author Round Trip " + pmid,
        null,
        LanguageInfo.of("English", "en-US"),
        PublicationStatus.PPUBLISH,
        PublicationMedium.PRINT,
        2024,
        true,
        0,
        null);
  }

  /// 构造无机构的作者快照。
  ///
  /// @param order 作者顺序
  /// @param displayName 展示名
  /// @param orcid ORCID（可空）
  /// @return 作者快照
  private PublicationAuthorSnapshot authorSnapshot(int order, String displayName, String orcid) {
    return PublicationAuthorSnapshot.builder()
        .order(order)
        .displayName(displayName)
        .orcid(orcid)
        .firstAuthor(order == 1)
        .build();
  }

  /// 插入一个 ACTIVE 作者及其 ORCID 子行。
  ///
  /// @param orcid ORCID 标识符
  /// @return 新建作者的 ID
  private Long saveActiveAuthorWithOrcid(String orcid) {
    long authorId = ++idSeq;
    jdbc.update(
        "INSERT INTO cat_author (id, normalized_key, display_name, status, provenance_code, "
            + "version, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'PUBMED', 0, NOW(), NOW())",
        authorId,
        "TEST+" + authorId,
        "Test Author " + authorId);
    jdbc.update(
        "INSERT INTO cat_author_orcid (id, author_id, orcid, is_primary, version, "
            + "created_at, updated_at) VALUES (?, ?, ?, true, 0, NOW(), NOW())",
        ++idSeq,
        authorId,
        orcid);
    return authorId;
  }

  /// 查询指定文献的作者行 ID（按作者顺序升序）。
  ///
  /// @param publicationId 文献 ID
  /// @return 作者行 ID 列表
  private List<Long> authorRowIds(Long publicationId) {
    return jdbc.queryForList(
        "SELECT id FROM cat_publication_author WHERE publication_id = ? ORDER BY author_order",
        Long.class,
        publicationId);
  }

  /// 统计指定文献的作者行数。
  ///
  /// @param publicationId 文献 ID
  /// @return 行数
  private int countAuthorRows(Long publicationId) {
    return queryCount(
        "SELECT COUNT(*) FROM cat_publication_author WHERE publication_id = ?", publicationId);
  }

  /// 统计指定文献的作者机构行数。
  ///
  /// @param publicationId 文献 ID
  /// @return 行数
  private int countAffiliationRows(Long publicationId) {
    return queryCount(
        "SELECT COUNT(*) FROM cat_publication_author_affiliation WHERE publication_id = ?",
        publicationId);
  }

  /// 执行计数查询。
  ///
  /// @param sql 计数 SQL
  /// @param publicationId 文献 ID
  /// @return 行数
  private int queryCount(String sql, Long publicationId) {
    Map<String, Object> row = jdbc.queryForMap(sql, publicationId);
    return ((Number) row.values().iterator().next()).intValue();
  }
}
