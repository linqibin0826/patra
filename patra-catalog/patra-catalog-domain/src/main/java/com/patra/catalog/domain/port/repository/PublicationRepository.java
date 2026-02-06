package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationAlternativeAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import com.patra.catalog.domain.model.vo.publication.PublicationDate;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import com.patra.catalog.domain.model.vo.publication.PublicationMetadata;
import com.patra.catalog.domain.model.vo.publication.PublicationOaLocation;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 文献聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **聚合边界**：
///
/// - PublicationAggregate：聚合根
/// - PublicationIdentifier：值对象集合（保护标识符唯一性不变量）
/// - PublicationAbstract：嵌入式值对象（与主文献 1:1 关系）
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合为单位加载和保存，维护聚合一致性
/// - 方法返回领域对象，而非 DO（数据对象）
///
/// **补充数据管理**：
///
/// 本接口同时管理与文献关联的补充数据（不属于聚合边界，但通过 Repository 统一访问）：
///
/// - PublicationDate：日期信息（投稿、接收、发表等）
/// - PublicationMetadata：索引状态、质量评分、数据溯源
/// - PublicationAlternativeAbstract：翻译摘要
/// - PublicationOaLocation：开放获取位置
///
/// **主要使用场景**：
///
/// - PubMed Baseline 批量导入（批量写入）
/// - OpenAlex Works 数据富化（批量更新 OA 位置）
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

  /// 批量插入文献及其关联数据。
  ///
  /// 用于 PubMed Baseline 等批量导入场景，一次性写入：
  /// - 文献聚合根（ID 会被回填）
  /// - MeSH 标引、关键词、资助信息、出版类型
  /// - 补充 MeSH 概念、翻译摘要、日期信息
  /// - 元数据、研究者、人物主题
  ///
  /// **事务保证**：
  ///
  /// 所有数据在同一事务内写入，保证一致性。
  ///
  /// **性能优化**：
  ///
  /// - 批量插入减少数据库往返次数
  /// - 研究者去重（优先 ORCID 匹配，其次 dedupKey 匹配）
  ///
  /// @param data 完整文献数据列表
  void insertAllWithAssociations(List<PublicationCompleteData> data);

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

  // ========== 标识符管理（聚合边界内，独立表） ==========

  /// 批量替换标识符（删除旧数据后插入新数据）。
  ///
  /// 用于 PubMed Baseline 导入时保存完整的标识符列表。
  /// 标识符包括 PMID、DOI、PMC、PII、ArXiv 等。
  ///
  /// **语义**：对于每个 publicationId，先删除该文献的所有现有标识符，
  /// 然后插入新的标识符列表（replace 语义）。
  ///
  /// @param identifiersByPublicationId Map，key 为 publicationId，value 为要设置的标识符列表
  void replaceIdentifiersBatch(Map<Long, List<PublicationIdentifier>> identifiersByPublicationId);

  // ========== 摘要管理（聚合边界内，独立表） ==========

  /// 批量替换摘要（删除旧数据后插入新数据）。
  ///
  /// 用于 PubMed Baseline 导入时保存文献摘要。
  /// 摘要与主文献为 1:1 关系，存储在独立表中。
  ///
  /// **语义**：对于每个 publicationId，先删除该文献的现有摘要，
  /// 然后插入新的摘要（replace 语义）。
  ///
  /// @param abstractsByPublicationId Map，key 为 publicationId，value 为要设置的摘要
  void replaceAbstractsBatch(Map<Long, PublicationAbstract> abstractsByPublicationId);

  // ========== 补充数据管理（聚合边界外） ==========

  /// 批量替换日期信息（删除旧数据后插入新数据）。
  ///
  /// 用于 PubMed Baseline 导入时保存文献的各类日期信息。
  /// 日期类型包括投稿日期、接收日期、发表日期、修订日期等。
  ///
  /// @param datesByPublicationId Map，key 为 publicationId，value 为要设置的日期列表
  void replaceDatesBatch(Map<Long, List<PublicationDate>> datesByPublicationId);

  /// 批量替换元数据（删除旧数据后插入新数据）。
  ///
  /// 用于 PubMed Baseline 导入时保存文献元数据。
  /// 元数据包括索引状态、质量评分、数据溯源、审核状态等。
  /// 元数据与主文献为 1:1 关系。
  ///
  /// @param metadataByPublicationId Map，key 为 publicationId，value 为要设置的元数据
  void replaceMetadataBatch(Map<Long, PublicationMetadata> metadataByPublicationId);

  /// 批量替换翻译摘要（删除旧数据后插入新数据）。
  ///
  /// 用于保存文献摘要的多语言翻译版本。
  /// 一个文献可有多个不同语言的翻译摘要。
  ///
  /// @param abstractsByPublicationId Map，key 为 publicationId，value 为要设置的翻译摘要列表
  void replaceAlternativeAbstractsBatch(
      Map<Long, List<PublicationAlternativeAbstract>> abstractsByPublicationId);

  /// 批量替换开放获取位置（删除旧数据后插入新数据）。
  ///
  /// 用于 OpenAlex Works 数据富化时保存 OA 位置信息。
  /// 一个文献可有多个 OA 位置（不同来源）。
  ///
  /// @param locationsByPublicationId Map，key 为 publicationId，value 为要设置的 OA 位置列表
  void replaceOaLocationsBatch(Map<Long, List<PublicationOaLocation>> locationsByPublicationId);
}
