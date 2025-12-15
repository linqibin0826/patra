package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import java.util.List;
import java.util.Optional;

/// 文献聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合为单位加载和保存，维护聚合一致性
/// - 方法返回领域对象，而非 DO（数据对象）
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationRepository {

  /// 根据 ID 查找文献。
  ///
  /// @param id 文献 ID
  /// @return 文献聚合根
  Optional<PublicationAggregate> findById(Long id);

  /// 根据 PMID 查找文献。
  ///
  /// @param pmid PubMed ID
  /// @return 文献聚合根
  Optional<PublicationAggregate> findByPmid(String pmid);

  /// 根据 DOI 查找文献。
  ///
  /// @param doi Digital Object Identifier
  /// @return 文献聚合根
  Optional<PublicationAggregate> findByDoi(String doi);

  /// 根据 PMID 或 DOI 查找文献（任一匹配即可）。
  ///
  /// 用于导入时的去重检查。
  ///
  /// @param pmid PubMed ID（可为 null）
  /// @param doi Digital Object Identifier（可为 null）
  /// @return 文献聚合根
  Optional<PublicationAggregate> findByPmidOrDoi(String pmid, String doi);

  /// 检查 PMID 是否已存在。
  ///
  /// @param pmid PubMed ID
  /// @return true 如果存在
  boolean existsByPmid(String pmid);

  /// 检查 DOI 是否已存在。
  ///
  /// @param doi Digital Object Identifier
  /// @return true 如果存在
  boolean existsByDoi(String doi);

  /// 根据载体实例 ID 查找文献列表。
  ///
  /// @param venueInstanceId 载体实例 ID
  /// @return 文献列表
  List<PublicationAggregate> findByVenueInstanceId(Long venueInstanceId);

  /// 根据载体 ID 查找文献列表。
  ///
  /// @param venueId 载体 ID
  /// @return 文献列表
  List<PublicationAggregate> findByVenueId(Long venueId);

  /// 统计指定载体的文献数量。
  ///
  /// @param venueId 载体 ID
  /// @return 文献数量
  long countByVenueId(Long venueId);

  /// 保存文献聚合根（新增或更新）。
  ///
  /// @param aggregate 文献聚合根
  void save(PublicationAggregate aggregate);

  /// 批量插入文献（仅用于新增）。
  ///
  /// @param aggregates 文献聚合根列表
  void insertAll(List<PublicationAggregate> aggregates);

  /// 批量更新文献。
  ///
  /// @param aggregates 文献聚合根列表
  void updateBatch(List<PublicationAggregate> aggregates);

  /// 根据 ID 删除文献。
  ///
  /// @param id 文献 ID
  /// @return true 如果删除成功
  boolean deleteById(Long id);

  /// 根据载体实例 ID 删除所有关联文献。
  ///
  /// @param venueInstanceId 载体实例 ID
  /// @return 删除的记录数
  int deleteByVenueInstanceId(Long venueInstanceId);

  /// 根据载体 ID 删除所有关联文献。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的记录数
  int deleteByVenueId(Long venueId);
}
