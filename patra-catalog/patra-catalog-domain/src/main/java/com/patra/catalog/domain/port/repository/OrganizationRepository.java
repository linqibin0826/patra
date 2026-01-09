package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import com.patra.catalog.domain.model.vo.organization.OrganizationId;
import com.patra.catalog.domain.model.vo.organization.RorId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// 机构聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// 基于 ROR (Research Organization Registry) 数据模型设计。
///
/// **聚合边界**：
///
/// - OrganizationAggregate：聚合根
/// - OrganizationName：子实体（多语言名称）
/// - ExternalId：子实体（外部标识符）
/// - OrganizationRelation：子实体（机构关系）
/// - GeoLocation：子实体（地理位置）
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合根为操作单位，保持一致性边界
///
/// **主要使用场景**：
///
/// - ROR 数据 Dump 初始化导入（批量写入）
/// - ROR REST API 增量同步（单条更新）
/// - 机构查询和关系解析
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationRepository {

  // ========== 数据存在性检查 ==========

  /// 检查是否存在任何机构数据。
  ///
  /// 用于「一次性初始化」导入前的数据存在性检查。
  /// 如果表中已有数据，导入操作应拒绝执行。
  ///
  /// @return 如果存在任何机构数据返回 true，否则返回 false
  boolean hasAnyData();

  /// 检查 ROR ID 是否已存在。
  ///
  /// @param rorId ROR ID
  /// @return 如果已存在返回 true
  boolean existsByRorId(RorId rorId);

  // ========== 基本查询 ==========

  /// 根据内部 ID 查找机构。
  ///
  /// @param id 内部 ID
  /// @return 机构聚合根，如果不存在返回 empty
  Optional<OrganizationAggregate> findById(OrganizationId id);

  /// 根据 ROR ID 查找机构。
  ///
  /// @param rorId ROR ID
  /// @return 机构聚合根，如果不存在返回 empty
  Optional<OrganizationAggregate> findByRorId(RorId rorId);

  /// 根据 ROR ID 批量查找机构。
  ///
  /// 用于导入时的批量匹配查询。
  ///
  /// @param rorIds ROR ID 集合
  /// @return ROR ID 到聚合根的映射
  Map<RorId, OrganizationAggregate> findByRorIds(Collection<RorId> rorIds);

  /// 根据 ROR ID 批量查找内部 ID。
  ///
  /// 用于关系延迟填充场景，根据 ROR ID 查找已导入机构的内部 ID。
  ///
  /// @param rorIds ROR ID 集合
  /// @return ROR ID（字符串形式）到内部 ID 的映射
  Map<String, Long> findIdsByRorIds(Collection<String> rorIds);

  // ========== 批量写入 ==========

  /// 批量插入机构聚合根。
  ///
  /// **适用场景**：ROR 数据 Dump 初始化导入
  ///
  /// **设计说明**：
  ///
  /// - 纯 INSERT 语义，用于「一次性初始化」场景
  /// - 自动生成主键 ID 并设置子表外键
  /// - 子表（names、externalIds、relations、locations）随主表一起插入
  /// - 空的子集合会被安全跳过，不会导致失败
  ///
  /// **注意**：
  ///
  /// - 不支持 Upsert（更新已存在记录）
  /// - 如果存在 ROR ID 冲突，操作会失败
  /// - 调用前应确保目标表为空或无冲突数据
  ///
  /// @param aggregates 聚合根列表
  void insertAll(List<OrganizationAggregate> aggregates);

  /// 批量查询已存在的 ROR ID。
  ///
  /// 用于导入前的去重检查。
  ///
  /// @param rorIds ROR ID 集合（字符串形式，如 "03vek6s52"）
  /// @return 数据库中已存在的 ROR ID 集合
  Set<String> findExistingRorIds(Collection<String> rorIds);

  // ========== 单条保存/更新 ==========

  /// 保存机构聚合根（新建或更新）。
  ///
  /// **适用场景**：ROR REST API 增量同步、单条编辑
  ///
  /// **设计说明**：
  ///
  /// - 如果 ID 为 null，执行 INSERT
  /// - 如果 ID 不为 null，执行 UPDATE（仅更新脏数据）
  /// - 返回持久化后的聚合根（带 ID 和版本号）
  ///
  /// @param aggregate 聚合根
  /// @return 持久化后的聚合根
  OrganizationAggregate save(OrganizationAggregate aggregate);

  // ========== 批量更新 ==========

  /// 批量更新机构聚合根。
  ///
  /// 基于子实体变更，执行精准的增量更新。
  ///
  /// **更新策略**：
  ///
  /// - **主表**：更新聚合根字段
  /// - **子表**：基于 pullChildChanges() 执行增量操作
  ///
  /// @param aggregates 聚合根列表
  void updateBatch(List<OrganizationAggregate> aggregates);

  // ========== 关系延迟填充 ==========

  /// 批量更新机构关系的内部关联 ID。
  ///
  /// 用于导入完成后，根据 ROR ID 填充关系表的 related_org_id 字段。
  /// 这是一个批量关联操作，不涉及聚合根重建。
  ///
  /// @param rorIdToOrgId ROR ID 到内部 ID 的映射
  /// @return 更新的关系记录数
  int linkRelatedOrganizations(Map<String, Long> rorIdToOrgId);

  // ========== 统计查询 ==========

  /// 统计机构总数。
  ///
  /// @return 机构总数
  long count();

  /// 按状态统计机构数量。
  ///
  /// @return 状态代码到数量的映射
  Map<String, Long> countByStatus();
}
