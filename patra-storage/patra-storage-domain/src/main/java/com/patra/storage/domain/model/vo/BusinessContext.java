package com.patra.storage.domain.model.vo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 描述与存储载荷关联的上游业务上下文。
 *
 * <p>业务上下文封装了文件上传的来源服务、业务类型和业务标识等关键信息,以及可选的关联元数据。 这些信息用于建立存储文件与业务实体之间的关联,支持数据追溯和查询。
 *
 * @param serviceName 生产者服务名称(例如 patra-ingest)
 * @param businessType 逻辑业务分类(例如 literature_batch)
 * @param businessId 调用方提供的唯一标识符
 * @param correlationData 可选的结构化元数据,用于下游搜索
 */
public record BusinessContext(
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData) {

  /**
   * 创建新的上下文,确保关键标识符存在。
   *
   * @throws IllegalArgumentException 如果服务名称、业务类型或业务ID为空
   */
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

  private static Map<String, Object> sanitize(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> copy = new LinkedHashMap<>(source.size());
    source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "关联数据键不能为null"), value));
    return Collections.unmodifiableMap(copy);
  }
}
