package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// MeSH 主题词聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合根为操作单位，保持一致性边界
///
/// **主要使用场景**：
///
/// NLM MeSH XML 数据初始化导入（批量写入）
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorRepository {

  /// 检查是否存在任何 MeSH 主题词数据。
  ///
  /// 用于「一次性初始化」导入前的数据存在性检查。
  /// 如果表中已有数据，导入操作应拒绝执行。
  ///
  /// @return 如果存在任何主题词数据返回 true，否则返回 false
  boolean hasAnyData();

  /// 批量插入主题词聚合根（包含所有子实体）。
  ///
  /// **适用场景**：NLM MeSH XML 数据初始化导入
  ///
  /// **设计说明**：
  ///
  /// - 纯 INSERT 语义，用于「一次性初始化」场景
  /// - 自动生成主键 ID 并设置子表外键
  /// - 子表（TreeNumber、Concept、ConceptRelation、EntryTerm、EntryCombination）随主表一起插入
  /// - 空的子集合会被安全跳过，不会导致失败
  ///
  /// **注意**：
  ///
  /// - 不支持 Upsert（更新已存在记录）
  /// - 如果存在主键冲突，操作会失败
  /// - 调用前应确保目标表为空或无冲突数据
  ///
  /// @param aggregates 聚合根列表（不能为 null，可以为空）
  void insertAll(List<MeshDescriptorAggregate> aggregates);

  /// 按名称批量查询主题词，返回名称到 UI 的映射。
  ///
  /// **使用场景**：为期刊 MeSH 主题词补充 UI 字段
  ///
  /// **匹配规则**：精确匹配（大小写敏感）
  ///
  /// @param names 主题词名称集合（如 "Cardiology", "Medicine"）
  /// @return 名称 → UI 映射表（如 "Cardiology" → "D002309"），未匹配的名称不在返回结果中
  Map<String, String> findAllByNameIn(Collection<String> names);
}
