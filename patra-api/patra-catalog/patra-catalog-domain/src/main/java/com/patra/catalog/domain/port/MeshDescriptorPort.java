package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.util.List;
import java.util.Optional;

/**
 * MeSH 主题词聚合根仓储接口(领域层定义,基础设施层实现)。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>接口在Domain层定义,确保领域层独立
 *   <li>实现在Infrastructure层,遵循依赖倒置原则(DIP)
 *   <li>聚合内实体(TreeNumber/EntryTerm/Concept)分开加载
 *   <li>提供树形查询、全文检索等专门方法
 * </ul>
 *
 * <p><b>聚合管理策略</b>：
 *
 * <ul>
 *   <li>MeshDescriptorAggregate 持有聚合内实体集合
 *   <li>Repository 按需加载聚合内实体(避免N+1问题)
 *   <li>树形编号支持层次查询(如查询某分支下的所有主题词)
 *   <li>入口术语支持全文检索(同义词模糊匹配)
 * </ul>
 *
 * <p><b>性能考虑</b>：
 *
 * <ul>
 *   <li>一个主题词平均: 2.3个树形位置 + 7-8个入口术语 + 5-6个概念
 *   <li>聚合内实体总量适中,可以一次性加载
 *   <li>如果性能问题,可以提供按需加载方法
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface MeshDescriptorPort {

  // ========== 聚合根查询 ==========

  /**
   * 按主键ID查询主题词。
   *
   * @param id 主键ID
   * @return 主题词聚合根,如果不存在则返回empty
   */
  Optional<MeshDescriptorAggregate> findById(Long id);

  /**
   * 按 MeSH UI 查询主题词。
   *
   * <p>业务规则：MeSH UI 全局唯一。
   *
   * @param ui MeSH 唯一标识符(格式：D000001-D999999)
   * @return 主题词聚合根,如果不存在则返回empty
   */
  Optional<MeshDescriptorAggregate> findByUi(MeshUI ui);

  /**
   * 按名称精确查询主题词。
   *
   * @param name 主题词名称
   * @return 主题词聚合根,如果不存在则返回empty
   */
  Optional<MeshDescriptorAggregate> findByName(String name);

  /**
   * 按名称模糊查询主题词列表。
   *
   * @param namePattern 名称模糊匹配(支持%通配符)
   * @return 主题词聚合根列表(可能为空列表)
   */
  List<MeshDescriptorAggregate> findByNameLike(String namePattern);

  /**
   * 按版本查询有效主题词列表。
   *
   * @param meshVersion MeSH 版本年份(如"2025")
   * @return 主题词聚合根列表(可能为空列表)
   */
  List<MeshDescriptorAggregate> findByVersion(String meshVersion);

  /**
   * 按激活状态查询主题词列表。
   *
   * @param activeStatus 激活状态(true=有效,false=已废弃)
   * @return 主题词聚合根列表(可能为空列表)
   */
  List<MeshDescriptorAggregate> findByActiveStatus(boolean activeStatus);

  // ========== 树形编号查询 ==========

  /**
   * 按树形编号前缀查询主题词列表(层次查询)。
   *
   * <p>示例：查询 C04 分支下的所有主题词(肿瘤相关)。
   *
   * @param treeNumberPrefix 树形编号前缀(如 "C04", "C04.557")
   * @return 主题词聚合根列表(可能为空列表)
   */
  List<MeshDescriptorAggregate> findByTreeNumberPrefix(String treeNumberPrefix);

  /**
   * 按树形编号精确查询主题词。
   *
   * @param treeNumber 树形编号(如 "C04.557.337.428")
   * @return 主题词聚合根,如果不存在则返回empty
   */
  Optional<MeshDescriptorAggregate> findByTreeNumber(String treeNumber);

  /**
   * 按层级深度查询主题词列表。
   *
   * @param treeLevel 层级深度(1-10)
   * @return 主题词聚合根列表(可能为空列表)
   */
  List<MeshDescriptorAggregate> findByTreeLevel(int treeLevel);

  // ========== 入口术语查询 ==========

  /**
   * 按入口术语全文检索主题词列表。
   *
   * <p>支持同义词模糊匹配,如 "A-23187" → "Calcimycin"。
   *
   * @param termKeyword 术语关键词
   * @return 主题词聚合根列表(可能为空列表)
   */
  List<MeshDescriptorAggregate> findByEntryTermFullText(String termKeyword);

  /**
   * 按入口术语精确查询主题词。
   *
   * @param term 入口术语
   * @return 主题词聚合根,如果不存在则返回empty
   */
  Optional<MeshDescriptorAggregate> findByEntryTerm(String term);

  // ========== 概念查询 ==========

  /**
   * 按注册号查询主题词(化学物质专用)。
   *
   * <p>支持 CAS 号、EC 号等注册号查询。
   *
   * @param registryNumber 注册号(如 "50-78-2")
   * @return 主题词聚合根,如果不存在则返回empty
   */
  Optional<MeshDescriptorAggregate> findByRegistryNumber(String registryNumber);

  /**
   * 按概念 UI 查询主题词。
   *
   * @param conceptUi 概念 UI(格式：M000001-M999999)
   * @return 主题词聚合根,如果不存在则返回empty
   */
  Optional<MeshDescriptorAggregate> findByConceptUi(MeshUI conceptUi);

  // ========== 聚合内实体查询 ==========

  /**
   * 查询主题词的所有树形编号。
   *
   * @param descriptorId 主题词ID
   * @return 树形编号列表(可能为空列表)
   */
  List<MeshTreeNumber> findTreeNumbersByDescriptorId(Long descriptorId);

  /**
   * 查询主题词的所有入口术语。
   *
   * @param descriptorId 主题词ID
   * @return 入口术语列表(可能为空列表)
   */
  List<MeshEntryTerm> findEntryTermsByDescriptorId(Long descriptorId);

  /**
   * 查询主题词的所有概念。
   *
   * @param descriptorId 主题词ID
   * @return 概念列表(可能为空列表)
   */
  List<MeshConcept> findConceptsByDescriptorId(Long descriptorId);

  // ========== 持久化操作 ==========

  /**
   * 保存主题词聚合根(新建或更新)。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>如果ID为null,则新建主题词(INSERT)
   *   <li>如果ID不为null,则更新主题词(UPDATE)
   *   <li>级联保存聚合内实体(TreeNumber/EntryTerm/Concept)
   *   <li>使用乐观锁(version)防止并发冲突
   *   <li>保存后回写ID和version
   * </ul>
   *
   * @param aggregate 主题词聚合根
   * @throws org.springframework.dao.OptimisticLockingFailureException 如果乐观锁冲突
   */
  void save(MeshDescriptorAggregate aggregate);

  /**
   * 删除主题词聚合根(软删除)。
   *
   * <p>业务规则：
   *
   * <ul>
   *   <li>软删除：设置deleted = 1
   *   <li>级联软删除所有关联的聚合内实体
   * </ul>
   *
   * @param id 主键ID
   */
  void delete(Long id);

  // ========== 统计查询 ==========

  /**
   * 判断 MeSH UI 是否已存在。
   *
   * @param ui MeSH 唯一标识符
   * @return true 如果已存在
   */
  boolean existsByUi(MeshUI ui);

  /**
   * 判断主题词名称是否已存在。
   *
   * @param name 主题词名称
   * @return true 如果已存在
   */
  boolean existsByName(String name);

  /**
   * 统计某版本的有效主题词数量。
   *
   * @param meshVersion MeSH 版本年份
   * @return 主题词数量
   */
  long countByVersion(String meshVersion);

  /**
   * 统计某分支下的主题词数量。
   *
   * @param treeNumberPrefix 树形编号前缀
   * @return 主题词数量
   */
  long countByTreeNumberPrefix(String treeNumberPrefix);
}
