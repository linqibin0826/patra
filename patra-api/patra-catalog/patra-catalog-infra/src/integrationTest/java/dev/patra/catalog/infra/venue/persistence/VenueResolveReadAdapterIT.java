package dev.patra.catalog.infra.venue.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.catalog.domain.venue.model.read.VenueResolveReadModel;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.venue.persistence.entity.VenueEntity;
import dev.patra.catalog.infra.venue.persistence.entity.VenueIdentifierEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `VenueResolveReadAdapter` 集成测试：按期刊 ID / NLM ID 批量解析，软删照样返回，只认期刊类型，
/// 副 NLM 命中时读模型里是主 NLM。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueResolveReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("VenueResolveReadAdapter 期刊批量解析集成测试")
class VenueResolveReadAdapterIT {

  @Autowired private VenueResolveReadAdapter adapter;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("按 ID：正常与软删的期刊都返回，软删行带刊名；非期刊类型与不存在的 ID 不返回")
  void should_return_active_and_soft_deleted_journals_by_ids_and_ignore_others() {
    long active = venue("JOURNAL", "Lancet (London, England)", "Lancet", "2985213R");
    long deleted = venue("JOURNAL", "Gone Journal", "Gone J", "0000003R");
    long repository = venue("REPOSITORY", "Some repository", null, "0000009");
    flush();
    softDelete(deleted);

    Map<Long, VenueResolveReadModel> byId =
        adapter.findByIds(List.of(active, deleted, repository, 999L)).stream()
            .collect(Collectors.toMap(VenueResolveReadModel::id, Function.identity()));

    assertThat(byId).containsOnlyKeys(active, deleted);
    assertThat(byId.get(active))
        .extracting(
            VenueResolveReadModel::nlmId,
            VenueResolveReadModel::title,
            VenueResolveReadModel::abbreviatedTitle,
            VenueResolveReadModel::deleted)
        .containsExactly("2985213R", "Lancet (London, England)", "Lancet", false);
    assertThat(byId.get(deleted))
        .extracting(VenueResolveReadModel::title, VenueResolveReadModel::deleted)
        .containsExactly("Gone Journal", true);
  }

  @Test
  @DisplayName("按 NLM：主 NLM 与副 NLM 都对到同一期刊，nlmId 是主 NLM，前导零与后缀原样")
  void should_resolve_primary_and_secondary_nlm_to_the_same_journal() {
    long swiss =
        venue("JOURNAL", "Schweizerische medizinische Wochenschrift", "Swiss Med Wkly", "0404401");
    identifier(swiss, "100970884");
    long lancet = venue("JOURNAL", "Lancet (London, England)", "Lancet", "2985213R");
    flush();

    Map<String, VenueResolveReadModel> byNlm =
        adapter.findByNlmIds(List.of("0404401", "100970884", "2985213R", "9999999"));

    assertThat(byNlm).containsOnlyKeys("0404401", "100970884", "2985213R");
    assertThat(List.of(byNlm.get("0404401"), byNlm.get("100970884")))
        .extracting(VenueResolveReadModel::id, VenueResolveReadModel::nlmId)
        .containsOnly(tuple(swiss, "0404401"));
    assertThat(byNlm.get("2985213R"))
        .extracting(VenueResolveReadModel::id, VenueResolveReadModel::nlmId)
        .containsExactly(lancet, "2985213R");
  }

  @Test
  @DisplayName("按 NLM：软删期刊照样命中并标 deleted；非期刊类型带 NLM 也不命中")
  void should_include_soft_deleted_and_exclude_non_journal_by_nlm() {
    long deleted = venue("JOURNAL", "Gone Journal", "Gone J", "0000003R");
    venue("REPOSITORY", "Some repository", null, "0000009");
    flush();
    softDelete(deleted);

    Map<String, VenueResolveReadModel> byNlm = adapter.findByNlmIds(List.of("0000003R", "0000009"));

    assertThat(byNlm).containsOnlyKeys("0000003R");
    assertThat(byNlm.get("0000003R").deleted()).isTrue();
  }

  @Test
  @DisplayName("空入参返回空结果")
  void should_return_empty_for_empty_input() {
    assertThat(adapter.findByIds(List.of())).isEmpty();
    assertThat(adapter.findByNlmIds(List.of())).isEmpty();
  }

  // ===== fixtures =====

  /// 落一个载体：主 NLM 同时写进 `nlm_id` 与一行 NLM 标识符（主 NLM 一定在标识符里）。
  ///
  /// @param type 载体类型
  /// @param title 全称
  /// @param abbr 缩写，可空
  /// @param nlmId 主 NLM
  /// @return 载体 ID
  private long venue(String type, String title, String abbr, String nlmId) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType(type);
    v.setTitle(title);
    v.setAbbreviatedTitle(abbr);
    v.setProvenanceCode("PUBMED");
    v.setNlmId(nlmId);
    em.persist(v);
    identifier(v.getId(), nlmId);
    return v.getId();
  }

  /// 给载体加一行 NLM 标识符。
  ///
  /// @param venueId 载体 ID
  /// @param nlmId NLM ID
  private void identifier(long venueId, String nlmId) {
    VenueIdentifierEntity i = new VenueIdentifierEntity();
    i.setId(SnowflakeIdGenerator.getId());
    i.setVenueId(venueId);
    i.setIdentifierType("NLM");
    i.setIdentifierValue(nlmId);
    i.setIsPrimary(false);
    em.persist(i);
  }

  /// 刷到库里并清空一级缓存。
  private void flush() {
    em.flush();
    em.clear();
  }

  /// 软删一个载体：实体走 Hibernate `@SoftDelete` 会被过滤，测试直接改列。
  ///
  /// @param id 载体 ID
  private void softDelete(long id) {
    em.getEntityManager()
        .createNativeQuery("UPDATE cat_venue SET deleted_at = now() WHERE id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }
}
