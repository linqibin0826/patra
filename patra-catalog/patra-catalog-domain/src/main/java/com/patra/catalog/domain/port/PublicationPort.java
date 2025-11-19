package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import java.util.List;
import java.util.Optional;

/**
 * 文献聚合根仓储接口（领域层定义，基础设施层实现）。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>接口在 Domain 层定义，确保领域层独立
 *   <li>实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
 *   <li>以聚合为单位加载和保存，维护聚合一致性
 *   <li>方法返回领域对象，而非 DO（数据对象）
 * </ul>
 *
 * <p><b>事务管理</b>：
 *
 * <ul>
 *   <li>查询方法（find*）：只读事务或无事务
 *   <li>变更方法（save, delete）：由应用层（Orchestrator）管理事务
 *   <li>聚合根的一致性由事务边界保证
 * </ul>
 *
 * <p><b>性能考虑</b>：
 *
 * <ul>
 *   <li>单个查询：findById, findByPmid, findByDoi
 *   <li>批量查询：findByYear, findByVenueId（应该分页）
 *   <li>复杂查询：由应用层组装，可能涉及多个聚合
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PublicationPort {

  // ========== 单个查询（精确匹配） ==========

  /**
   * 按主键ID查询文献（加载整个聚合）。
   *
   * @param id 主键ID
   * @return 文献聚合根，如果不存在则返回 empty
   */
  Optional<PublicationAggregate> findById(Long id);

  /**
   * 按 PMID 查询文献。
   *
   * <p>业务规则：PMID 在同一数据来源中必须唯一。
   *
   * @param pmid PubMed ID（1-15位数字）
   * @return 文献聚合根，如果不存在则返回 empty
   */
  Optional<PublicationAggregate> findByPmid(String pmid);

  /**
   * 按 DOI 查询文献。
   *
   * <p>业务规则：DOI 在同一数据来源中必须唯一。
   *
   * @param doi Digital Object Identifier
   * @return 文献聚合根，如果不存在则返回 empty
   */
  Optional<PublicationAggregate> findByDoi(String doi);

  // ========== 批量查询（返回聚合列表） ==========

  /**
   * 按出版年份查询文献列表。
   *
   * <p>性能提示：高频查询，应考虑分页。
   *
   * @param year 出版年份
   * @return 文献聚合根列表（可能为空列表）
   */
  List<PublicationAggregate> findByYear(int year);

  /**
   * 按载体ID查询文献列表。
   *
   * <p>性能提示：查询某期刊的所有文献，应考虑分页。
   *
   * @param venueId 载体ID
   * @return 文献聚合根列表（可能为空列表）
   */
  List<PublicationAggregate> findByVenueId(Long venueId);

  /**
   * 按载体实例ID查询文献列表。
   *
   * <p>业务场景：查询某期刊某卷某期的所有文献。
   *
   * @param venueInstanceId 载体实例ID
   * @return 文献聚合根列表（可能为空列表）
   */
  List<PublicationAggregate> findByVenueInstanceId(Long venueInstanceId);

  /**
   * 查询开放获取文献列表（按 OA 状态筛选）。
   *
   * <p>业务场景：筛选所有 OA 文献。
   *
   * @return 开放获取文献聚合根列表（可能为空列表）
   */
  List<PublicationAggregate> findOpenAccessPublications();

  // ========== 持久化操作 ==========

  /**
   * 保存文献聚合根（新建或更新）。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>如果 ID 为 null，则新建文献（INSERT）
   *   <li>如果 ID 不为 null，则更新文献（UPDATE）
   *   <li>使用乐观锁（version）防止并发冲突
   *   <li>保存后回写 ID 和 version
   * </ul>
   *
   * @param aggregate 文献聚合根
   * @throws org.springframework.dao.OptimisticLockingFailureException 如果乐观锁冲突
   */
  void save(PublicationAggregate aggregate);

  /**
   * 删除文献聚合根（软删除）。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>软删除：设置 deleted = 1
   *   <li>不影响关联表（如 cat_abstract, cat_identifier）
   * </ul>
   *
   * @param id 主键ID
   */
  void delete(Long id);

  // ========== 统计查询 ==========

  /**
   * 按年份统计文献数量。
   *
   * @param year 出版年份
   * @return 文献数量
   */
  long countByYear(int year);

  /**
   * 按载体ID统计文献数量。
   *
   * @param venueId 载体ID
   * @return 文献数量
   */
  long countByVenueId(Long venueId);

  /**
   * 判断 PMID 是否已存在。
   *
   * @param pmid PubMed ID
   * @return true 如果已存在
   */
  boolean existsByPmid(String pmid);

  /**
   * 判断 DOI 是否已存在。
   *
   * @param doi Digital Object Identifier
   * @return true 如果已存在
   */
  boolean existsByDoi(String doi);
}
