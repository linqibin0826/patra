package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.InvestigatorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// 研究者 JPA Repository。
///
/// **职责**：
///
/// - 提供研究者实体的 CRUD 操作
/// - 支持按 ORCID 和去重键（dedupKey）查询
/// - 支持批量查询以优化批处理导入性能
///
/// **去重匹配优先级**：
///
/// 1. ORCID 精确匹配（全局唯一标识）
/// 2. dedupKey 匹配（姓名 + ORCID 组合哈希）
/// 3. 无匹配时创建新记录
///
/// @author linqibin
/// @since 0.1.0
public interface InvestigatorDao extends JpaRepository<InvestigatorEntity, Long> {

  // ========== ORCID 查询 ==========

  /// 根据 ORCID 查询研究者。
  ///
  /// ORCID 是全局唯一标识，最多返回一条记录。
  ///
  /// @param orcid ORCID 标识符（格式: 0000-0001-2345-6789）
  /// @return 研究者实体（可选）
  Optional<InvestigatorEntity> findByOrcid(String orcid);

  /// 批量根据 ORCID 查询研究者。
  ///
  /// 用于批量导入时的去重检查，一次查询替代 N 次单条查询。
  ///
  /// @param orcids ORCID 集合
  /// @return 匹配的研究者实体列表
  List<InvestigatorEntity> findByOrcidIn(Collection<String> orcids);

  // ========== 去重键查询 ==========

  /// 根据去重键查询研究者。
  ///
  /// dedupKey 是姓名 + ORCID 的 MD5 哈希，用于无 ORCID 时的去重。
  ///
  /// @param dedupKey 去重键
  /// @return 研究者实体（可选）
  Optional<InvestigatorEntity> findByDedupKey(String dedupKey);

  /// 批量根据去重键查询研究者。
  ///
  /// 用于批量导入时的去重检查。
  ///
  /// @param dedupKeys 去重键集合
  /// @return 匹配的研究者实体列表
  List<InvestigatorEntity> findByDedupKeyIn(Collection<String> dedupKeys);
}
