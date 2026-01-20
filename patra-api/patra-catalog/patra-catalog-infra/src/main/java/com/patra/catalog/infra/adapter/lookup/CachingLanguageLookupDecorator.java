package com.patra.catalog.infra.adapter.lookup;

import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/// 语言查找缓存装饰器。
///
/// 使用装饰器模式为任意 LanguageLookupPort 实现添加缓存能力。
///
/// **缓存策略**：
///
/// - 结果缓存：缓存解析结果（包括 "unknown"，即 Negative Cache）
/// - 增量查询：批量查询时只向 delegate 请求未缓存的代码
/// - 线程安全：使用 ConcurrentHashMap 支持并发访问
///
/// **注意**：此类不是 Spring Bean，需要在使用时手动创建实例。
/// 缓存生命周期由调用方控制（如 Step 级别、Request 级别等）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class CachingLanguageLookupDecorator implements LanguageLookupPort {

  private final LanguageLookupPort delegate;

  /// ISO 639-3 代码 → BCP 47 代码的缓存。
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  /// 构造函数。
  ///
  /// @param delegate 被装饰的 LanguageLookupPort 实现
  public CachingLanguageLookupDecorator(LanguageLookupPort delegate) {
    this.delegate = delegate;
  }

  @Override
  public Map<String, String> resolve(Set<String> iso639Codes) {
    if (iso639Codes == null || iso639Codes.isEmpty()) {
      return Map.of();
    }

    Map<String, String> result = new HashMap<>();
    Set<String> uncachedCodes = new HashSet<>();

    // 1. 从缓存中获取已有结果
    for (String code : iso639Codes) {
      String cached = cache.get(code);
      if (cached != null) {
        result.put(code, cached);
      } else {
        uncachedCodes.add(code);
      }
    }

    // 2. 如果有未缓存的代码，查询 delegate
    if (!uncachedCodes.isEmpty()) {
      Map<String, String> delegateResult = delegate.resolve(uncachedCodes);

      // 3. 将结果存入缓存并合并到返回值（防御性 null 检查）
      if (delegateResult != null) {
        for (Map.Entry<String, String> entry : delegateResult.entrySet()) {
          cache.put(entry.getKey(), entry.getValue());
          result.put(entry.getKey(), entry.getValue());
        }
      }
    }

    return result;
  }

  /// 获取缓存统计信息。
  ///
  /// @return 缓存统计描述
  public String getStats() {
    long knownCount = cache.values().stream().filter(v -> !UNKNOWN_LANGUAGE.equals(v)).count();
    long unknownCount = cache.size() - knownCount;
    return "LanguageLookupCache[cached=%d, known=%d, unknown=%d]"
        .formatted(cache.size(), knownCount, unknownCount);
  }

  /// 清空缓存。
  public void clear() {
    cache.clear();
    log.debug("LanguageLookup 缓存已清空");
  }
}
