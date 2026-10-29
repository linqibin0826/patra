package dev.patra.catalog.infra.mesh.persistence.dao;

import dev.patra.catalog.infra.mesh.persistence.MeshDescriptorSuggestRow;
import dev.patra.catalog.infra.mesh.persistence.MeshDescriptorSummaryRow;
import dev.patra.catalog.infra.mesh.persistence.entity.MeshDescriptorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// MeSH 主题词 JPA Repository。
///
/// **职责**：管理 MeshDescriptorEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorDao extends JpaRepository<MeshDescriptorEntity, Long> {

  /// 判断是否存在任何数据。
  ///
  /// @return true 如果存在数据
  @Query("SELECT COUNT(d) > 0 FROM MeshDescriptorEntity d")
  boolean hasAnyData();

  /// 按 UI 查询主题词。
  ///
  /// @param ui MeSH UI
  /// @return 主题词实体（如果存在）
  Optional<MeshDescriptorEntity> findByUi(String ui);

  /// 按 UI 列表批量查询主题词。
  ///
  /// @param uis UI 列表
  /// @return 主题词实体列表
  List<MeshDescriptorEntity> findAllByUiIn(Collection<String> uis);

  /// 按名称列表批量查询主题词。
  ///
  /// @param names 主题词名称列表
  /// @return 主题词实体列表
  List<MeshDescriptorEntity> findAllByNameIn(Collection<String> names);

  /// 主题词建议：主题词名与非排列入口词按前缀 / 词首命中，只给主题类且 active 的主题词，
  /// 经入口词命中的返回主题词本身并带上入口词；每个主题词只出现一次。
  ///
  /// @param q 归一化后的查询原文（精确比较用）
  /// @param prefix 前缀模式 `q%`（已转义）
  /// @param wordStart 词首模式 `% q%`（已转义）
  /// @param limit 最多返回条数
  /// @return 候选投影
  @Query(value = MeshDescriptorSuggestSql.SUGGEST, nativeQuery = true)
  List<MeshDescriptorSuggestRow> suggest(
      @Param("q") String q,
      @Param("prefix") String prefix,
      @Param("wordStart") String wordStart,
      @Param("limit") int limit);

  /// 按 UI 查主题词摘要（名称与全部树号），不限 class。
  ///
  /// @param ui 主题词 UI
  /// @return 摘要投影；不存在为空
  @Query(value = MeshDescriptorSuggestSql.FIND_SUMMARY_BY_UI, nativeQuery = true)
  Optional<MeshDescriptorSummaryRow> findSummaryByUi(@Param("ui") String ui);
}
