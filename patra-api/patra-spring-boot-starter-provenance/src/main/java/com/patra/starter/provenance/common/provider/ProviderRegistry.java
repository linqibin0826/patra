package com.patra.starter.provenance.common.provider;

import com.patra.common.model.DataType;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

/// ProvenanceDataProvider注册表（二维索引）
///
/// ProviderRegistry负责管理所有ProvenanceDataProvider实例的注册和查找。
///
/// **核心功能**：
///
/// - 二维索引：(provenanceCode, dataType) → Provider
///   - 一对多支持：一个Provider可以支持多个DataType
///   - 自动发现和注册Provider（Spring依赖注入）
///   - O(1)时间复杂度的查找
///   - 线程安全设计
///
/// **索引结构**：
///
/// ```
///
/// 主索引：Map<ProviderKey, ProvenanceDataProvider>
///   ProviderKey = (provenanceCode, dataType)
///   示例：("pubmed", PUBLICATION) → PubmedProvider
///        ("pubmed", CITATION) → PubmedProvider
///        ("doaj", JOURNAL) → DoajProvider
///
/// 辅助索引1：Map<String, Set<DataType>>
///   provenanceCode → 支持的DataType集合
///   示例："pubmed" → {PUBLICATION, CITATION, AUTHOR}
///
/// 辅助索引2：Map<DataType, List<ProvenanceDataProvider>>
///   dataType → 支持该类型的所有Provider
///   示例：PUBLICATION → [PubmedProvider, CrossrefProvider]
///
/// ```
///
/// **使用示例**：
///
/// ```java
/// // Spring自动注入
/// @Component
/// public class ProvenanceDataAdapter implements ProvenanceDataPort {
///     private final ProviderRegistry providerRegistry;
///
///     public ProvenanceDataAdapter(ProviderRegistry providerRegistry) {
///         this.providerRegistry = providerRegistry;
///
///     public <T> DataFetchResult<T> fetchData(...) {
///         // 二维查找
///         ProvenanceDataProvider provider =
///             providerRegistry.getProvider(provenanceCode, dataType);
///
///         // 委托Provider处理
///         return provider.fetchData(request, dataType, targetClass);
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class ProviderRegistry {

  /// 主索引：(provenanceCode, dataType) → Provider
  private final Map<ProviderKey, ProvenanceDataProvider> providersByType;

  /// 辅助索引1：provenanceCode → Set<DataType>
  private final Map<ProvenanceCode, Set<DataType>> typesByProvenance;

  /// 辅助索引2：dataType → List<Provider>
  private final Map<DataType, List<ProvenanceDataProvider>> providersByDataType;

  /// 构造函数（Spring自动注入所有ProvenanceDataProvider实现）
  ///
  /// @param discoveredProviders Spring发现的所有Provider实例
  public ProviderRegistry(List<ProvenanceDataProvider> discoveredProviders) {
    this.providersByType = new ConcurrentHashMap<>();
    this.typesByProvenance = new ConcurrentHashMap<>();
    this.providersByDataType = new ConcurrentHashMap<>();

    if (discoveredProviders != null && !discoveredProviders.isEmpty()) {
      discoveredProviders.forEach(this::register);
      logRegistrationSummary();
    } else {
      log.warn("未发现任何ProvenanceDataProvider实现");
    }
  }

  /// 注册单个Provider
  ///
  /// 将Provider注册到三个索引中
  ///
  /// @param provider Provider实例
  private void register(ProvenanceDataProvider provider) {
    ProvenanceCode provenanceCode = provider.getProvenanceCode();
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

  /// 获取指定数据源和数据类型的Provider（二维查找）
  ///
  /// @param provenanceCode 数据源代码
  /// @param dataType 数据类型
  /// @return Provider实例
  /// @throws ProviderNotFoundException 如果Provider不存在
  public ProvenanceDataProvider getProvider(ProvenanceCode provenanceCode, DataType dataType) {
    ProviderKey key = new ProviderKey(provenanceCode, dataType);

    ProvenanceDataProvider provider = providersByType.get(key);

    if (provider == null) {
      throw new ProviderNotFoundException(
          String.format("未找到Provider: provenanceCode=%s, dataType=%s", provenanceCode, dataType));
    }

    return provider;
  }

  /// 查找指定数据源和数据类型的Provider（返回Optional）
  ///
  /// @param provenanceCode 数据源代码
  /// @param dataType 数据类型
  /// @return Provider实例（如果存在）
  public Optional<ProvenanceDataProvider> findProvider(
      ProvenanceCode provenanceCode, DataType dataType) {
    ProviderKey key = new ProviderKey(provenanceCode, dataType);
    return Optional.ofNullable(providersByType.get(key));
  }

  /// 判断是否支持指定的数据源和数据类型
  ///
  /// @param provenanceCode 数据源代码
  /// @param dataType 数据类型
  /// @return 如果支持则返回true
  public boolean supports(ProvenanceCode provenanceCode, DataType dataType) {
    ProviderKey key = new ProviderKey(provenanceCode, dataType);
    return providersByType.containsKey(key);
  }

  /// 获取指定数据源支持的所有数据类型
  ///
  /// @param provenanceCode 数据源代码
  /// @return 数据类型集合（不可变，如果数据源不存在则返回空集合）
  public Set<DataType> getSupportedTypes(ProvenanceCode provenanceCode) {
    Set<DataType> types = typesByProvenance.get(provenanceCode);
    return types != null ? Set.copyOf(types) : Set.of();
  }

  /// 获取支持指定数据类型的所有Provider
  ///
  /// @param dataType 数据类型
  /// @return Provider列表（不可变，如果没有则返回空列表）
  public List<ProvenanceDataProvider> getProvidersByDataType(DataType dataType) {
    List<ProvenanceDataProvider> providers = providersByDataType.get(dataType);
    return providers != null ? List.copyOf(providers) : List.of();
  }

  /// 获取所有注册的Provider
  ///
  /// @return Provider列表（不可变，去重后）
  public List<ProvenanceDataProvider> getAllProviders() {
    return providersByType.values().stream().distinct().toList();
  }

  /// 记录注册统计信息
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

  /// Provider索引键（provenanceCode + dataType）
  private record ProviderKey(ProvenanceCode provenanceCode, DataType dataType) {}
}
