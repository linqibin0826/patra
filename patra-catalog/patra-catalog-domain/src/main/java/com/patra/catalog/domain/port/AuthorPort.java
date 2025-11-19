package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.catalog.domain.model.vo.common.DedupKey;
import java.util.List;
import java.util.Optional;

/**
 * 作者聚合根仓储接口(领域层定义,基础设施层实现)。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>接口在Domain层定义,确保领域层独立
 *   <li>实现在Infrastructure层,遵循依赖倒置原则(DIP)
 *   <li>提供去重查询、相似度匹配等专门方法
 *   <li>支持按标识符(ORCID/ResearcherID/ScopusID)查询
 * </ul>
 *
 * <p><b>去重策略</b>：
 *
 * <ul>
 *   <li>优先级1：ORCID(准确率99%+,覆盖率30%)
 *   <li>优先级2：去重键(基于姓名+邮箱/机构)
 *   <li>优先级3：模糊匹配(姓名+组织名称)
 *   <li>Repository 提供 findSimilarAuthors() 辅助应用层去重
 * </ul>
 *
 * <p><b>性能考虑</b>：
 *
 * <ul>
 *   <li>ORCID 和去重键建立唯一索引
 *   <li>姓名、邮箱、机构名称建立普通索引
 *   <li>模糊查询可能较慢,应用层应缓存结果
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface AuthorPort {

  // ========== 聚合根查询 ==========

  /**
   * 按主键ID查询作者。
   *
   * @param id 主键ID
   * @return 作者聚合根,如果不存在则返回empty
   */
  Optional<AuthorAggregate> findById(Long id);

  /**
   * 按 ORCID 查询作者。
   *
   * <p>业务规则：ORCID 全局唯一。
   *
   * @param orcid ORCID 标识符(格式：XXXX-XXXX-XXXX-XXXX)
   * @return 作者聚合根,如果不存在则返回empty
   */
  Optional<AuthorAggregate> findByOrcid(Orcid orcid);

  /**
   * 按去重键查询作者。
   *
   * <p>业务规则：去重键用于识别可能的重复作者。
   *
   * @param dedupKey 去重键(MD5哈希)
   * @return 作者聚合根,如果不存在则返回empty
   */
  Optional<AuthorAggregate> findByDedupKey(DedupKey dedupKey);

  /**
   * 按邮箱查询作者。
   *
   * @param email 邮箱地址
   * @return 作者聚合根,如果不存在则返回empty
   */
  Optional<AuthorAggregate> findByEmail(String email);

  /**
   * 按 Researcher ID 查询作者。
   *
   * @param researcherId Researcher ID(ResearcherID/Publons)
   * @return 作者聚合根,如果不存在则返回empty
   */
  Optional<AuthorAggregate> findByResearcherId(String researcherId);

  /**
   * 按 Scopus ID 查询作者。
   *
   * @param scopusId Scopus 作者 ID
   * @return 作者聚合根,如果不存在则返回empty
   */
  Optional<AuthorAggregate> findByScopusId(String scopusId);

  // ========== 模糊查询 ==========

  /**
   * 按姓名模糊查询作者列表。
   *
   * <p>支持姓氏或全名模糊匹配。
   *
   * @param namePattern 姓名模糊匹配(支持%通配符)
   * @return 作者聚合根列表(可能为空列表)
   */
  List<AuthorAggregate> findByNameLike(String namePattern);

  /**
   * 按机构名称查询作者列表。
   *
   * @param organizationName 机构名称(精确匹配)
   * @return 作者聚合根列表(可能为空列表)
   */
  List<AuthorAggregate> findByOrganizationName(String organizationName);

  /**
   * 按机构名称模糊查询作者列表。
   *
   * @param organizationPattern 机构名称模糊匹配(支持%通配符)
   * @return 作者聚合根列表(可能为空列表)
   */
  List<AuthorAggregate> findByOrganizationNameLike(String organizationPattern);

  // ========== 去重支持 ==========

  /**
   * 查找与给定作者相似的作者列表(去重辅助方法)。
   *
   * <p>相似度判断规则：
   *
   * <ul>
   *   <li>姓氏相同 + 缩写相同
   *   <li>姓氏相同 + 机构名称相似
   *   <li>姓氏相同 + 邮箱域名相同
   * </ul>
   *
   * <p>应用层应进一步验证并决定是否合并。
   *
   * @param lastName 姓氏
   * @param initials 缩写
   * @param email 邮箱地址
   * @param organizationName 机构名称
   * @return 相似作者列表(可能为空列表,排除自身)
   */
  List<AuthorAggregate> findSimilarAuthors(
      String lastName, String initials, String email, String organizationName);

  /**
   * 按有效状态查询作者列表。
   *
   * <p>业务规则：valid=false 表示该作者已被合并到其他作者记录。
   *
   * @param valid 有效状态(true=有效,false=已合并)
   * @return 作者聚合根列表(可能为空列表)
   */
  List<AuthorAggregate> findByValidStatus(boolean valid);

  /**
   * 查询所有无效作者(已合并的重复作者)。
   *
   * @return 无效作者列表(可能为空列表)
   */
  default List<AuthorAggregate> findInvalidAuthors() {
    return findByValidStatus(false);
  }

  /**
   * 按同等贡献标志查询作者列表。
   *
   * <p>业务规则：用于查找标记为同等贡献的作者(共同第一作者)。
   *
   * @param equalContribution 同等贡献标志
   * @return 作者聚合根列表(可能为空列表)
   */
  List<AuthorAggregate> findByEqualContribution(boolean equalContribution);

  // ========== 持久化操作 ==========

  /**
   * 保存作者聚合根(新建或更新)。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>如果ID为null,则新建作者(INSERT)
   *   <li>如果ID不为null,则更新作者(UPDATE)
   *   <li>ORCID 全局唯一,如果冲突则抛出异常
   *   <li>使用乐观锁(version)防止并发冲突
   *   <li>保存后回写ID和version
   * </ul>
   *
   * @param aggregate 作者聚合根
   * @throws org.springframework.dao.DuplicateKeyException 如果ORCID重复
   * @throws org.springframework.dao.OptimisticLockingFailureException 如果乐观锁冲突
   */
  void save(AuthorAggregate aggregate);

  /**
   * 删除作者聚合根(软删除)。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>软删除：设置deleted = 1
   *   <li>不删除关联数据(publication_author),保持历史记录
   * </ul>
   *
   * @param id 主键ID
   */
  void delete(Long id);

  // ========== 统计查询 ==========

  /**
   * 判断 ORCID 是否已存在。
   *
   * @param orcid ORCID 标识符
   * @return true 如果已存在
   */
  boolean existsByOrcid(Orcid orcid);

  /**
   * 判断去重键是否已存在。
   *
   * @param dedupKey 去重键
   * @return true 如果已存在
   */
  boolean existsByDedupKey(DedupKey dedupKey);

  /**
   * 判断邮箱是否已存在。
   *
   * @param email 邮箱地址
   * @return true 如果已存在
   */
  boolean existsByEmail(String email);

  /**
   * 统计某机构的作者数量。
   *
   * @param organizationName 机构名称
   * @return 作者数量
   */
  long countByOrganizationName(String organizationName);

  /**
   * 统计有效作者数量。
   *
   * @param valid 有效状态
   * @return 作者数量
   */
  long countByValidStatus(boolean valid);

  /**
   * 统计拥有 ORCID 的作者数量。
   *
   * @return 拥有 ORCID 的作者数量
   */
  long countAuthorsWithOrcid();
}
