package com.patra.starter.provenance.common.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源提供者注册表
 *
 * <p>负责发现和查询 {@link DataSourceProvider} 实现。注册表通过 Spring 的组件扫描填充, 并提供按数据源代码的快速查找功能。
 */
@Slf4j
public class ProviderRegistry {

  private final Map<String, List<DataSourceProvider>> providers = new ConcurrentHashMap<>();

  /**
   * 创建注册表并注册所有已发现的提供者实现
   *
   * @param discoveredProviders 由 Spring 提供的提供者实现列表
   */
  public ProviderRegistry(List<DataSourceProvider> discoveredProviders) {
    List<DataSourceProvider> safeProviders =
        discoveredProviders == null ? List.of() : List.copyOf(discoveredProviders);
    safeProviders.forEach(this::register);
  }

  /**
   * 测试是否存在请求的数据源代码对应的提供者实现
   *
   * @param provenanceCode 数据源标识符(如 {@code pubmed})
   * @return 如果有匹配的提供者实现可用则返回 true
   */
  public boolean supports(String provenanceCode) {
    return findProvider(provenanceCode).isPresent();
  }

  /**
   * 返回与请求的数据源代码匹配的提供者实现
   *
   * @param provenanceCode 数据源标识符(如 {@code pubmed})
   * @return 匹配的提供者实现实例
   * @throws IllegalArgumentException 如果该数据源不存在对应的提供者实现
   */
  public DataSourceProvider getProvider(String provenanceCode) {
    return findProvider(provenanceCode)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "未找到数据源对应的提供者实现: provenance=%s".formatted(provenanceCode)));
  }

  private Optional<DataSourceProvider> findProvider(String provenanceCode) {
    if (provenanceCode == null || provenanceCode.isBlank()) {
      return Optional.empty();
    }
    String normalizedCode = normalize(provenanceCode);
    List<DataSourceProvider> candidates = providers.get(normalizedCode);
    if (candidates == null || candidates.isEmpty()) {
      return Optional.empty();
    }
    // 返回该数据源的第一个提供者实现
    // 每个数据源应该恰好有一个提供者实现
    return candidates.stream().findFirst();
  }

  private void register(DataSourceProvider provider) {
    if (provider == null) {
      return;
    }
    String normalizedCode = normalize(provider.getProvenanceCode());
    providers.compute(
        normalizedCode,
        (code, list) -> {
          if (list == null || list.isEmpty()) {
            return List.of(provider);
          }
          if (list.stream().anyMatch(existing -> existing.getClass().equals(provider.getClass()))) {
            log.warn("忽略重复的提供者实现注册: {}", provider.getClass().getName());
            return list;
          }
          return createExpandedList(list, provider);
        });
  }

  private List<DataSourceProvider> createExpandedList(
      List<DataSourceProvider> existing, DataSourceProvider provider) {
    List<DataSourceProvider> combined = new ArrayList<>(existing.size() + 1);
    combined.addAll(existing);
    combined.add(provider);
    return List.copyOf(combined);
  }

  private String normalize(String provenanceCode) {
    return provenanceCode == null ? "" : provenanceCode.trim().toLowerCase(Locale.ROOT);
  }
}
