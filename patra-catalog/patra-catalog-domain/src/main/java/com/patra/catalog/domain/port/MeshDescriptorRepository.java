package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshQualifier;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import java.util.List;

/// MeSH 主题词聚合根仓储接口(领域层定义,基础设施层实现)。
///
/// **设计原则**：
///
/// - 接口在Domain层定义,确保领域层独立
///   - 实现在Infrastructure层,遵循依赖倒置原则(DIP)
///   - 聚合内实体(TreeNumber/EntryTerm/Concept)分开加载
///   - 提供树形查询、全文检索等专门方法
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorRepository {

  /// 批量保存限定词。
  ///
  /// 实现说明：
  ///
  /// - 直接批量插入到 cat_mesh_qualifier 表
  ///   - 限定词是独立的主数据，不依赖其他实体
  ///   - 用于导入 qual2025.xml 中的限定词数据
  ///   - 必须先于主题词导入
  ///
  /// @param qualifiers 限定词实体列表
  void saveQualifiersBatch(List<MeshQualifier> qualifiers);

  /// 批量保存主题词聚合根。
  ///
  /// 实现说明：
  ///
  /// - 保存完整的聚合根（包括所有子实体：TreeNumber、EntryTerm、Concept）
  ///   - 使用 MyBatis-Plus 的 saveBatch 方法进行批量插入
  ///   - 子实体通过 descriptor_id 外键关联
  ///   - 事务由调用方（Application 层）管理
  ///
  /// @param descriptors 主题词聚合根列表
  void saveBatch(List<MeshDescriptorAggregate> descriptors);

  /// 批量保存树形编号实体。
  ///
  /// 实现说明：
  ///
  /// - 直接批量插入到 cat_mesh_tree_number 表
  ///   - 需要确保 descriptor_id 已存在（引用完整性）
  ///   - 用于性能优化场景（分开导入主表和子表）
  ///
  /// @param treeNumbers 树形编号实体列表
  void saveTreeNumbersBatch(List<MeshTreeNumber> treeNumbers);

  /// 批量保存入口术语实体。
  ///
  /// 实现说明：
  ///
  /// - 直接批量插入到 cat_mesh_entry_term 表
  ///   - 需要确保 descriptor_id 已存在（引用完整性）
  ///   - 用于性能优化场景（分开导入主表和子表）
  ///
  /// @param entryTerms 入口术语实体列表
  void saveEntryTermsBatch(List<MeshEntryTerm> entryTerms);

  /// 批量保存概念实体。
  ///
  /// 实现说明：
  ///
  /// - 直接批量插入到 cat_mesh_concept 表
  ///   - 需要确保 descriptor_id 已存在（引用完整性）
  ///   - 用于性能优化场景（分开导入主表和子表）
  ///
  /// @param concepts 概念实体列表
  void saveConceptsBatch(List<MeshConcept> concepts);
}
