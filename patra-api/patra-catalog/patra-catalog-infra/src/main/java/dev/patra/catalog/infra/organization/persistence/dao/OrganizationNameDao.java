package dev.patra.catalog.infra.organization.persistence.dao;

import dev.patra.catalog.infra.organization.persistence.OrganizationSuggestRow;
import dev.patra.catalog.infra.organization.persistence.entity.OrganizationNameEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 机构名称 JPA Repository。
///
/// **职责**：管理 OrganizationNameEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationNameDao extends JpaRepository<OrganizationNameEntity, Long> {

  /// 按机构 ID 查询所有名称。
  ///
  /// @param orgId 机构 ID
  /// @return 名称实体列表
  List<OrganizationNameEntity> findAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量查询名称。
  ///
  /// @param orgIds 机构 ID 列表
  /// @return 名称实体列表
  List<OrganizationNameEntity> findAllByOrgIdIn(Collection<Long> orgIds);

  /// 按机构 ID 删除所有名称。
  ///
  /// @param orgId 机构 ID
  void deleteAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量删除名称。
  ///
  /// @param orgIds 机构 ID 列表
  void deleteAllByOrgIdIn(Collection<Long> orgIds);

  /// 子串模式的机构建议（查询含中日韩文字）：名称表与别名表任意位置子串命中。
  ///
  /// 排序：命中方式 → 中国优先 → 医疗优先 → 名称类型 → 命中名更短 → 英文显示名。
  /// 只返回 active 且未软删的机构，每家机构只出现一次。
  ///
  /// @param q 归一化后的查询原文（精确比较用）
  /// @param contains 子串模式 `%q%`（已转义）
  /// @param prefix 前缀模式 `q%`（已转义，打分用）
  /// @param wordStart 词首模式 `% q%`（已转义，打分用）
  /// @param limit 最多返回条数
  /// @return 候选投影
  @Query(value = OrganizationSuggestSql.SUGGEST_BY_SUBSTRING, nativeQuery = true)
  List<OrganizationSuggestRow> suggestBySubstring(
      @Param("q") String q,
      @Param("contains") String contains,
      @Param("prefix") String prefix,
      @Param("wordStart") String wordStart,
      @Param("limit") int limit);

  /// 只有 2 个码点的子串模式建议：条件写成 `lower(列) LIKE`，让规划器用不了 trigram 索引，
  /// 稳定走顺序扫描（凑不出三元组的模式在通用计划下会扫完整个索引）。参数同 [#suggestBySubstring]。
  ///
  /// @param q 归一化后的查询原文（精确比较用）
  /// @param contains 子串模式 `%q%`（已转义）
  /// @param prefix 前缀模式 `q%`（已转义，打分用）
  /// @param wordStart 词首模式 `% q%`（已转义，打分用）
  /// @param limit 最多返回条数
  /// @return 候选投影
  @Query(value = OrganizationSuggestSql.SUGGEST_BY_SHORT_SUBSTRING, nativeQuery = true)
  List<OrganizationSuggestRow> suggestByShortSubstring(
      @Param("q") String q,
      @Param("contains") String contains,
      @Param("prefix") String prefix,
      @Param("wordStart") String wordStart,
      @Param("limit") int limit);

  /// 词首模式的机构建议（拉丁文字）：整串前缀或某个词的开头命中，不匹配词中间。
  ///
  /// @param q 归一化后的查询原文（精确比较用）
  /// @param prefix 前缀模式 `q%`（已转义）
  /// @param wordStart 词首模式 `% q%`（已转义）
  /// @param limit 最多返回条数
  /// @return 候选投影
  @Query(value = OrganizationSuggestSql.SUGGEST_BY_WORD_PREFIX, nativeQuery = true)
  List<OrganizationSuggestRow> suggestByWordPrefix(
      @Param("q") String q,
      @Param("prefix") String prefix,
      @Param("wordStart") String wordStart,
      @Param("limit") int limit);
}
