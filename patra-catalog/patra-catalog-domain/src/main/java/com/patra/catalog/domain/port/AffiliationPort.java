package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.AffiliationAggregate;
import com.patra.catalog.domain.model.enums.AffiliationType;
import com.patra.catalog.domain.model.vo.affiliation.GridId;
import com.patra.catalog.domain.model.vo.affiliation.RorId;
import com.patra.catalog.domain.model.vo.common.DedupKey;
import java.util.List;
import java.util.Optional;

/**
 * 机构聚合根仓储接口(领域层定义,基础设施层实现)。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>接口在Domain层定义,确保领域层独立
 *   <li>实现在Infrastructure层,遵循依赖倒置原则(DIP)
 *   <li>提供去重查询、相似度匹配等专门方法
 *   <li>支持按标识符(ROR/GRID/ISNI)查询
 * </ul>
 *
 * <p><b>去重策略</b>：
 *
 * <ul>
 *   <li>优先级1：ROR ID(准确率99%,覆盖率60%)
 *   <li>优先级2：GRID ID(准确率95%,覆盖率75%)
 *   <li>优先级3：去重键(基于名称+地理位置)
 *   <li>Repository 提供 findSimilarAffiliations() 辅助应用层去重
 * </ul>
 *
 * <p><b>性能考虑</b>：
 *
 * <ul>
 *   <li>ROR ID 和 GRID ID 建立唯一索引
 *   <li>名称、国家、去重键建立普通索引
 *   <li>模糊查询可能较慢,应用层应缓存结果
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface AffiliationPort {

  // ========== 聚合根查询 ==========

  /**
   * 按主键ID查询机构。
   *
   * @param id 主键ID
   * @return 机构聚合根,如果不存在则返回empty
   */
  Optional<AffiliationAggregate> findById(Long id);

  /**
   * 按 ROR ID 查询机构。
   *
   * <p>业务规则：ROR ID 全局唯一。
   *
   * @param rorId ROR 标识符
   * @return 机构聚合根,如果不存在则返回empty
   */
  Optional<AffiliationAggregate> findByRorId(RorId rorId);

  /**
   * 按 GRID ID 查询机构。
   *
   * <p>业务规则：GRID ID 全局唯一。
   *
   * @param gridId GRID 标识符
   * @return 机构聚合根,如果不存在则返回empty
   */
  Optional<AffiliationAggregate> findByGridId(GridId gridId);

  /**
   * 按去重键查询机构。
   *
   * @param dedupKey 去重键(MD5哈希)
   * @return 机构聚合根,如果不存在则返回empty
   */
  Optional<AffiliationAggregate> findByDedupKey(DedupKey dedupKey);

  /**
   * 按 ISNI 查询机构。
   *
   * @param isni ISNI 标识符
   * @return 机构聚合根,如果不存在则返回empty
   */
  Optional<AffiliationAggregate> findByIsni(String isni);

  /**
   * 按 Ringgold ID 查询机构。
   *
   * @param ringgoldId Ringgold ID
   * @return 机构聚合根,如果不存在则返回empty
   */
  Optional<AffiliationAggregate> findByRinggoldId(String ringgoldId);

  /**
   * 按名称精确查询机构。
   *
   * @param name 机构名称(标准化后)
   * @return 机构聚合根,如果不存在则返回empty
   */
  Optional<AffiliationAggregate> findByName(String name);

  // ========== 模糊查询 ==========

  /**
   * 按名称模糊查询机构列表。
   *
   * @param namePattern 名称模糊匹配(支持%通配符)
   * @return 机构聚合根列表(可能为空列表)
   */
  List<AffiliationAggregate> findByNameLike(String namePattern);

  /**
   * 按国家查询机构列表。
   *
   * @param country 国家(ISO 3166-1 alpha-3,如"USA","CHN")
   * @return 机构聚合根列表(可能为空列表)
   */
  List<AffiliationAggregate> findByCountry(String country);

  /**
   * 按城市查询机构列表。
   *
   * @param city 城市名称
   * @return 机构聚合根列表(可能为空列表)
   */
  List<AffiliationAggregate> findByCity(String city);

  /**
   * 按国家和城市查询机构列表。
   *
   * @param country 国家代码
   * @param city 城市名称
   * @return 机构聚合根列表(可能为空列表)
   */
  List<AffiliationAggregate> findByCountryAndCity(String country, String city);

  /**
   * 按机构类型查询机构列表。
   *
   * @param affiliationType 机构类型
   * @return 机构聚合根列表(可能为空列表)
   */
  List<AffiliationAggregate> findByAffiliationType(AffiliationType affiliationType);

  /**
   * 按上级机构查询下属机构列表。
   *
   * @param parentAffiliationName 上级机构名称
   * @return 机构聚合根列表(可能为空列表)
   */
  List<AffiliationAggregate> findByParentAffiliation(String parentAffiliationName);

  // ========== 去重支持 ==========

  /**
   * 查找与给定机构相似的机构列表(去重辅助方法)。
   *
   * <p>相似度判断规则：
   *
   * <ul>
   *   <li>名称相似 + 国家相同
   *   <li>名称相似 + 城市相同
   *   <li>名称包含或被包含关系
   * </ul>
   *
   * <p>应用层应进一步验证并决定是否合并。
   *
   * @param name 机构名称
   * @param city 城市
   * @param country 国家
   * @return 相似机构列表(可能为空列表,排除自身)
   */
  List<AffiliationAggregate> findSimilarAffiliations(String name, String city, String country);

  // ========== 持久化操作 ==========

  /**
   * 保存机构聚合根(新建或更新)。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>如果ID为null,则新建机构(INSERT)
   *   <li>如果ID不为null,则更新机构(UPDATE)
   *   <li>ROR ID 全局唯一,如果冲突则抛出异常
   *   <li>GRID ID 全局唯一,如果冲突则抛出异常
   *   <li>使用乐观锁(version)防止并发冲突
   *   <li>保存后回写ID和version
   * </ul>
   *
   * @param aggregate 机构聚合根
   * @throws org.springframework.dao.DuplicateKeyException 如果 ROR ID 或 GRID ID 重复
   * @throws org.springframework.dao.OptimisticLockingFailureException 如果乐观锁冲突
   */
  void save(AffiliationAggregate aggregate);

  /**
   * 删除机构聚合根(软删除)。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>软删除：设置deleted = 1
   *   <li>不删除关联数据(author_affiliation),保持历史记录
   * </ul>
   *
   * @param id 主键ID
   */
  void delete(Long id);

  // ========== 统计查询 ==========

  /**
   * 判断 ROR ID 是否已存在。
   *
   * @param rorId ROR 标识符
   * @return true 如果已存在
   */
  boolean existsByRorId(RorId rorId);

  /**
   * 判断 GRID ID 是否已存在。
   *
   * @param gridId GRID 标识符
   * @return true 如果已存在
   */
  boolean existsByGridId(GridId gridId);

  /**
   * 判断去重键是否已存在。
   *
   * @param dedupKey 去重键
   * @return true 如果已存在
   */
  boolean existsByDedupKey(DedupKey dedupKey);

  /**
   * 判断机构名称是否已存在。
   *
   * @param name 机构名称
   * @return true 如果已存在
   */
  boolean existsByName(String name);

  /**
   * 统计某国家的机构数量。
   *
   * @param country 国家代码
   * @return 机构数量
   */
  long countByCountry(String country);

  /**
   * 统计某类型的机构数量。
   *
   * @param affiliationType 机构类型
   * @return 机构数量
   */
  long countByAffiliationType(AffiliationType affiliationType);

  /**
   * 统计拥有 ROR ID 的机构数量。
   *
   * @return 拥有 ROR ID 的机构数量
   */
  long countAffiliationsWithRorId();

  /**
   * 统计拥有 GRID ID 的机构数量。
   *
   * @return 拥有 GRID ID 的机构数量
   */
  long countAffiliationsWithGridId();
}
