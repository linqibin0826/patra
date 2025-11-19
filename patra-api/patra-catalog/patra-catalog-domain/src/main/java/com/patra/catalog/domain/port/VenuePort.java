package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenueInstance;
import java.util.List;
import java.util.Optional;

/**
 * 载体聚合根仓储接口（领域层定义，基础设施层实现）。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>接口在Domain层定义，确保领域层独立
 *   <li>实现在Infrastructure层，遵循依赖倒置原则（DIP）
 *   <li>Venue和VenueInstance分开管理（避免性能问题）
 *   <li>提供关联查询方法满足业务需求
 * </ul>
 *
 * <p><b>聚合管理策略</b>：
 *
 * <ul>
 *   <li>VenueAggregate不直接持有instances集合
 *   <li>通过Repository按需加载instances（分页或按条件查询）
 *   <li>卷期唯一性通过数据库唯一索引保证
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface VenuePort {

  // ========== Venue聚合根查询 ==========

  /**
   * 按主键ID查询载体。
   *
   * @param id 主键ID
   * @return 载体聚合根，如果不存在则返回empty
   */
  Optional<VenueAggregate> findById(Long id);

  /**
   * 按ISSN查询期刊。
   *
   * <p>业务规则：ISSN在期刊中必须唯一。
   *
   * @param issn ISSN号码（格式：XXXX-XXXX）
   * @return 期刊聚合根，如果不存在则返回empty
   */
  Optional<VenueAggregate> findByIssn(String issn);

  /**
   * 按ISBN查询书籍。
   *
   * <p>业务规则：ISBN在书籍中必须唯一。
   *
   * @param isbn ISBN号码
   * @return 书籍聚合根，如果不存在则返回empty
   */
  Optional<VenueAggregate> findByIsbn(String isbn);

  /**
   * 按NLM唯一标识符查询期刊。
   *
   * @param nlmUniqueId NLM唯一标识符
   * @return 期刊聚合根，如果不存在则返回empty
   */
  Optional<VenueAggregate> findByNlmUniqueId(String nlmUniqueId);

  /**
   * 按名称模糊查询载体列表。
   *
   * @param titlePattern 名称模糊匹配（支持%通配符）
   * @return 载体聚合根列表（可能为空列表）
   */
  List<VenueAggregate> findByTitleLike(String titlePattern);

  // ========== VenueInstance查询 ==========

  /**
   * 按载体ID查询所有实例。
   *
   * <p>性能提示：可能返回大量数据，建议分页查询。
   *
   * @param venueId 载体ID
   * @return 实例列表（可能为空列表）
   */
  List<VenueInstance> findInstancesByVenueId(Long venueId);

  /**
   * 按载体ID和年份查询实例。
   *
   * @param venueId 载体ID
   * @param publicationYear 出版年份
   * @return 实例列表（可能为空列表）
   */
  List<VenueInstance> findInstancesByVenueIdAndYear(Long venueId, int publicationYear);

  /**
   * 按载体ID、卷号、期号查询实例（期刊专用）。
   *
   * <p>业务规则：同一载体的volume+issue组合必须唯一。
   *
   * @param venueId 载体ID
   * @param volume 卷号
   * @param issue 期号
   * @return 实例，如果不存在则返回empty
   */
  Optional<VenueInstance> findInstanceByVolumeIssue(
      Long venueId, String volume, String issue);

  /**
   * 获取载体的最新实例（按publication_year降序）。
   *
   * @param venueId 载体ID
   * @return 最新实例，如果没有实例则返回empty
   */
  Optional<VenueInstance> findLatestInstanceByVenueId(Long venueId);

  // ========== 持久化操作 ==========

  /**
   * 保存载体聚合根（新建或更新）。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>如果ID为null，则新建载体（INSERT）
   *   <li>如果ID不为null，则更新载体（UPDATE）
   *   <li>使用乐观锁（version）防止并发冲突
   *   <li>保存后回写ID和version
   * </ul>
   *
   * @param aggregate 载体聚合根
   * @throws org.springframework.dao.OptimisticLockingFailureException 如果乐观锁冲突
   */
  void save(VenueAggregate aggregate);

  /**
   * 保存载体实例（新建或更新）。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>实例必须关联到已存在的载体
   *   <li>卷期组合唯一性通过数据库唯一索引保证
   *   <li>保存后回写ID
   * </ul>
   *
   * @param instance 载体实例
   */
  void saveInstance(VenueInstance instance);

  /**
   * 删除载体聚合根（软删除）。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>软删除：设置deleted = 1
   *   <li>级联软删除所有关联的instances
   * </ul>
   *
   * @param id 主键ID
   */
  void delete(Long id);

  /**
   * 删除载体实例（软删除）。
   *
   * @param instanceId 实例ID
   */
  void deleteInstance(Long instanceId);

  // ========== 统计查询 ==========

  /**
   * 判断ISSN是否已存在。
   *
   * @param issn ISSN号码
   * @return true 如果已存在
   */
  boolean existsByIssn(String issn);

  /**
   * 判断ISBN是否已存在。
   *
   * @param isbn ISBN号码
   * @return true 如果已存在
   */
  boolean existsByIsbn(String isbn);

  /**
   * 统计载体的实例数量。
   *
   * @param venueId 载体ID
   * @return 实例数量
   */
  long countInstancesByVenueId(Long venueId);

  /**
   * 判断卷期组合是否已存在（期刊专用）。
   *
   * @param venueId 载体ID
   * @param volume 卷号
   * @param issue 期号
   * @return true 如果已存在
   */
  boolean existsInstanceByVolumeIssue(Long venueId, String volume, String issue);
}
