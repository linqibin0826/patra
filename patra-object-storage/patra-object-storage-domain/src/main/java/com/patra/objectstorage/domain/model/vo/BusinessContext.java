package com.patra.objectstorage.domain.model.vo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// 描述与存储载荷关联的上游业务上下文。
///
/// 业务上下文封装了文件上传的来源服务、业务类型和业务标识等关键信息,以及可选的关联元数据。 这些信息用于建立存储文件与业务实体之间的关联,支持数据追溯和查询。
///
/// @param serviceName 生产者服务名称(例如 patra-ingest)
/// @param businessType 逻辑业务分类(例如 publication_batch)
/// @param businessId 调用方提供的唯一标识符
/// @param correlationData 可选的结构化元数据,用于下游搜索
public record BusinessContext(
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData) {

  /// 规范构造器,强制执行业务上下文的验证规则。
  ///
  /// 验证规则:
  ///
  /// - 服务名称、业务类型和业务ID不能为空
  ///   - 关联数据Map会被清理和不可变包装
  ///
  /// @throws IllegalArgumentException 如果必需字段为空
  public BusinessContext {
    if (serviceName == null || serviceName.isBlank()) {
      throw new IllegalArgumentException("服务名称不能为空");
    }
    if (businessType == null || businessType.isBlank()) {
      throw new IllegalArgumentException("业务类型不能为空");
    }
    if (businessId == null || businessId.isBlank()) {
      throw new IllegalArgumentException("业务ID不能为空");
    }
    correlationData = sanitize(correlationData);
  }

  /// 清理关联数据Map,确保不可变性。
  ///
  /// @param source 源Map对象
  /// @return 不可变的Map副本
  private static Map<String, Object> sanitize(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> copy = new LinkedHashMap<>(source.size());
    source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "关联数据键不能为null"), value));
    return Collections.unmodifiableMap(copy);
  }
}
