package com.patra.catalog.domain.port.lookup;

import java.util.Collection;
import java.util.Map;

/// MeSH 数据查找端口（领域层定义，基础设施层实现）。
///
/// **设计说明**：
///
/// - 提供按 UI（唯一标识符）批量查找 MeSH 数据的能力
/// - 用于文献导入时将 MeSH UI 映射为内部 ID
/// - 批量查找优化性能，避免逐条查询
///
/// **使用场景**：
///
/// Spring Batch Processor 在处理文献的 MeSH 标引时，
/// 需要将 XML 中的 UI（如 "D000001"）映射为数据库 ID。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshLookupPort {

  /// 按 UI 批量查找 MeSH 主题词，返回 UI 到内部 ID 的映射。
  ///
  /// @param descriptorUis MeSH 主题词 UI 集合（如 "D000001", "D018352"）
  /// @return UI → 内部 ID 映射表，未找到的 UI 不在返回结果中
  Map<String, Long> findDescriptorIdsByUi(Collection<String> descriptorUis);

  /// 按 UI 批量查找 MeSH 限定词，返回 UI 到内部 ID 的映射。
  ///
  /// @param qualifierUis MeSH 限定词 UI 集合（如 "Q000379", "Q000175"）
  /// @return UI → 内部 ID 映射表，未找到的 UI 不在返回结果中
  Map<String, Long> findQualifierIdsByUi(Collection<String> qualifierUis);
}
