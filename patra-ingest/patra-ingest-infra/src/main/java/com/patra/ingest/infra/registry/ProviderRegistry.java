package com.patra.ingest.infra.registry;

import com.patra.common.model.DataType;
import com.patra.starter.provenance.common.provider.DataSourceProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DataSourceProvider注册表（二维索引）
 *
 * <p>ProviderRegistry负责管理所有DataSourceProvider实例的注册和查找。
 *
 * <p><strong>核心功能</strong>：
 *
 * <ul>
 *   <li>二维索引：(provenanceCode, dataType) → Provider
 *   <li>一对多支持：一个Provider可以支持多个DataType
 *   <li>自动发现和注册Provider（Spring依赖注入）
 *   <li>O(1)时间复杂度的查找
 *   <li>线程安全设计
 * </ul>
 *
 * <p><strong>索引结构</strong>：
 *
 * <pre>
 * 主索引：Map<ProviderKey, DataSourceProvider>
 *   ProviderKey = (provenanceCode, dataType)
 *   示例：("pubmed", LITERATURE) → PubmedProvider
 *        ("pubmed", CITATION) → PubmedProvider
 *        ("doaj", JOURNAL) → DoajProvider
 *
 * 辅助索引1：Map<String, Set<DataType>>
 *   provenanceCode → 支持的DataType集合
 *   示例："pubmed" → {LITERATURE, CITATION, AUTHOR}
 *
 * 辅助索引2：Map<DataType, List<DataSourceProvider>>
 *   dataType → 支持该类型的所有Provider
 *   示例：LITERATURE → [PubmedProvider, CrossrefProvider]
 * </pre>
 *
 * <p><strong>使用示例</strong>：
 *
 * <pre>{@code
 * // Spring自动注入
 * @Component
 * public class DataSourceAdapter implements DataSourcePort {
 *     private final ProviderRegistry providerRegistry;
 *
 *     public DataSourceAdapter(ProviderRegistry providerRegistry) {
 *         this.providerRegistry = providerRegistry;
 *     }
 *
 *     public <T> DataFetchResult<T> fetchData(...) {
 *         // 二维查找
 *         DataSourceProvider provider =
 *             providerRegistry.getProvider(provenanceCode, dataType);
 *
 *         // 委托Provider处理
 *         return provider.fetchData(request, dataType, targetClass);
 *     }
 * }
 * }</pre>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
@Component
@Slf4j
public class ProviderRegistry {

  /** 主索引：(provenanceCode, dataType) → Provider */
  private final Map<ProviderKey, DataSourceProvider> providersByType;

  /** 辅助索引1：provenanceCode → Set<DataType> */
  private final Map<String, Set<DataType>> typesByProvenance;

  /** 辅助索引2：dataType → List<Provider> */
  private final Map<DataType, List<DataSourceProvider>> providersByDataType;

  /**
   * 构造函数（Spring自动注入所有DataSourceProvider实现）
   *
   * @param discoveredProviders Spring发现的所有Provider实例
   */
  public ProviderRegistry(List<DataSourceProvider> discoveredProviders) {
    this.providersByType = new ConcurrentHashMap<>();
    this.typesByProvenance = new ConcurrentHashMap<>();
    this.providersByDataType = new ConcurrentHashMap<>();

    if (discoveredProviders != null && !discoveredProviders.isEmpty()) {
      discoveredProviders.forEach(this::register);
      logRegistrationSummary();
    } else {
      log.warn("未发现任何DataSourceProvider实现");
    }
  }

  /**
   * 注册单个Provider
   *
   * <p>将Provider注册到三个索引中
   *
   * @param provider Provider实例
   */
  private void register(DataSourceProvider provider) {
    String provenanceCode = normalizeProvenanceCode(provider.getProvenanceCode());
    Set<DataType> supportedTypes = provider.getSupportedDataTypes();

    // 检测重复的provenanceCode
    if (typesByProvenance.containsKey(provenanceCode)) {
      log.warn(
          "检测到重复的provenanceCode: {}, existing={}, new={}",
          provenanceCode,
          typesByProvenance.get(provenanceCode),
          supportedTypes);
      log.warn("保留第一个注册的Provider，忽略后续注册");
      return;
    }

    // 注册到主索引
    for (DataType dataType : supportedTypes) {
      ProviderKey key = new ProviderKey(provenanceCode, dataType);
      providersByType.put(key, provider);
    }

    // 注册到辅助索引1
    typesByProvenance.put(provenanceCode, Set.copyOf(supportedTypes));

    // 注册到辅助索引2
    for (DataType dataType : supportedTypes) {
      providersByDataType
          .computeIfAbsent(dataType, k -> new CopyOnWriteArrayList<>())
          .add(provider);
    }

    log.debug(
        "注册Provider: code={}, types={}, impl={}",
        provenanceCode,
        supportedTypes,
        provider.getClass().getSimpleName());
  }

  /**
   * 获取指定数据源和数据类型的Provider（二维查找）
   *
   * @param provenanceCode 数据源代码
   * @param dataType 数据类型
   * @return Provider实例
   * @throws ProviderNotFoundException 如果Provider不存在
   */
  public DataSourceProvider getProvider(String provenanceCode, DataType dataType) {
    String normalizedCode = normalizeProvenanceCode(provenanceCode);
    ProviderKey key = new ProviderKey(normalizedCode, dataType);

    DataSourceProvider provider = providersByType.get(key);

    if (provider == null) {
      throw new ProviderNotFoundException(
          String.format("未找到Provider: provenanceCode=%s, dataType=%s", provenanceCode, dataType));
    }

    return provider;
  }

  /**
   * 查找指定数据源和数据类型的Provider（返回Optional）
   *
   * @param provenanceCode 数据源代码
   * @param dataType 数据类型
   * @return Provider实例（如果存在）
   */
  public Optional<DataSourceProvider> findProvider(String provenanceCode, DataType dataType) {
    String normalizedCode = normalizeProvenanceCode(provenanceCode);
    ProviderKey key = new ProviderKey(normalizedCode, dataType);
    return Optional.ofNullable(providersByType.get(key));
  }

  /**
   * 判断是否支持指定的数据源和数据类型
   *
   * @param provenanceCode 数据源代码
   * @param dataType 数据类型
   * @return 如果支持则返回true
   */
  public boolean supports(String provenanceCode, DataType dataType) {
    String normalizedCode = normalizeProvenanceCode(provenanceCode);
    ProviderKey key = new ProviderKey(normalizedCode, dataType);
    return providersByType.containsKey(key);
  }

  /**
   * 获取指定数据源支持的所有数据类型
   *
   * @param provenanceCode 数据源代码
   * @return 数据类型集合（不可变，如果数据源不存在则返回空集合）
   */
  public Set<DataType> getSupportedTypes(String provenanceCode) {
    String normalizedCode = normalizeProvenanceCode(provenanceCode);
    Set<DataType> types = typesByProvenance.get(normalizedCode);
    return types != null ? Set.copyOf(types) : Set.of();
  }

  /**
   * 获取支持指定数据类型的所有Provider
   *
   * @param dataType 数据类型
   * @return Provider列表（不可变，如果没有则返回空列表）
   */
  public List<DataSourceProvider> getProvidersByDataType(DataType dataType) {
    List<DataSourceProvider> providers = providersByDataType.get(dataType);
    return providers != null ? List.copyOf(providers) : List.of();
  }

  /**
   * 获取所有注册的Provider
   *
   * @return Provider列表（不可变，去重后）
   */
  public List<DataSourceProvider> getAllProviders() {
    return providersByType.values().stream().distinct().collect(Collectors.toUnmodifiableList());
  }

  /**
   * 标准化数据源代码（转小写，去空格）
   *
   * @param provenanceCode 原始代码
   * @return 标准化后的代码
   */
  private String normalizeProvenanceCode(String provenanceCode) {
    return provenanceCode != null ? provenanceCode.toLowerCase().trim() : "";
  }

  /** 记录注册统计信息 */
  private void logRegistrationSummary() {
    int providerCount = typesByProvenance.size();
    int combinationCount = providersByType.size();

    log.info(
        "Provider注册完成: 共注册{}个Provider, {}个(provenanceCode, dataType)组合",
        providerCount,
        combinationCount);

    // 详细日志
    typesByProvenance.forEach(
        (code, types) -> {
          log.debug("  - {}: {}", code, types);
        });
  }

  /** Provider索引键（provenanceCode + dataType） */
  private record ProviderKey(String provenanceCode, DataType dataType) {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ProviderKey that = (ProviderKey) o;
      return Objects.equals(provenanceCode, that.provenanceCode) && dataType == that.dataType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(provenanceCode, dataType);
    }
  }
}
