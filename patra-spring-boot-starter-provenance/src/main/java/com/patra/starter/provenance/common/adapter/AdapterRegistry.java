package com.patra.starter.provenance.common.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源端口注册表
 *
 * <p>负责发现和查询 {@link DataSourcePort} 实现。注册表通过 Spring 的组件扫描填充, 并提供按数据源代码的快速查找功能。
 */
@Slf4j
public class AdapterRegistry {

  private final Map<String, List<DataSourcePort>> adapters = new ConcurrentHashMap<>();

  /**
   * 创建注册表并注册所有已发现的端口实现
   *
   * @param discoveredAdapters 由 Spring 提供的端口实现列表
   */
  public AdapterRegistry(List<DataSourcePort> discoveredAdapters) {
    List<DataSourcePort> safeAdapters =
        discoveredAdapters == null ? List.of() : List.copyOf(discoveredAdapters);
    safeAdapters.forEach(this::register);
  }

  /**
   * 测试是否存在请求的数据源代码对应的端口实现
   *
   * @param provenanceCode 数据源标识符(如 {@code pubmed})
   * @return 如果有匹配的端口实现可用则返回 true
   */
  public boolean supports(String provenanceCode) {
    return findAdapter(provenanceCode).isPresent();
  }

  /**
   * 返回与请求的数据源代码匹配的端口实现
   *
   * @param provenanceCode 数据源标识符(如 {@code pubmed})
   * @return 匹配的端口实现实例
   * @throws IllegalArgumentException 如果该数据源不存在对应的端口实现
   */
  public DataSourcePort getAdapter(String provenanceCode) {
    return findAdapter(provenanceCode)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "未找到数据源对应的端口实现: provenance=%s".formatted(provenanceCode)));
  }

  private Optional<DataSourcePort> findAdapter(String provenanceCode) {
    if (provenanceCode == null || provenanceCode.isBlank()) {
      return Optional.empty();
    }
    String normalizedCode = normalize(provenanceCode);
    List<DataSourcePort> candidates = adapters.get(normalizedCode);
    if (candidates == null || candidates.isEmpty()) {
      return Optional.empty();
    }
    // 返回该数据源的第一个端口实现
    // 每个数据源应该恰好有一个端口实现
    return candidates.stream().findFirst();
  }

  private void register(DataSourcePort adapter) {
    if (adapter == null) {
      return;
    }
    String normalizedCode = normalize(adapter.getProvenanceCode());
    adapters.compute(
        normalizedCode,
        (code, list) -> {
          if (list == null || list.isEmpty()) {
            return List.of(adapter);
          }
          if (list.stream().anyMatch(existing -> existing.getClass().equals(adapter.getClass()))) {
            log.warn("忽略重复的端口实现注册: {}", adapter.getClass().getName());
            return list;
          }
          return createExpandedList(list, adapter);
        });
  }

  private List<DataSourcePort> createExpandedList(
      List<DataSourcePort> existing, DataSourcePort adapter) {
    List<DataSourcePort> combined = new ArrayList<>(existing.size() + 1);
    combined.addAll(existing);
    combined.add(adapter);
    return List.copyOf(combined);
  }

  private String normalize(String provenanceCode) {
    return provenanceCode == null ? "" : provenanceCode.trim().toLowerCase(Locale.ROOT);
  }
}
