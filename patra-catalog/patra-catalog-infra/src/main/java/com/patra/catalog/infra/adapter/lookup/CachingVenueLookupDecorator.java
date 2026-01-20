package com.patra.catalog.infra.adapter.lookup;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/// Venue 查找缓存包装器。
///
/// 为 `VenueLookupPort` 提供内存缓存能力，直接访问 `VenueRepository` 以支持二级索引预热。
///
/// **缓存策略**：
///
/// - 双索引：NLM ID + ISSN
/// - Negative Cache：记录未找到的标识符，避免重复查询
/// - 二级预热：一次查询自动缓存该 Venue 的所有标识符（需要访问完整 VenueAggregate）
///
/// **设计说明**：
///
/// 此类不是传统装饰器模式，因为二级索引预热需要获取完整的 `VenueAggregate`（包含所有 ISSN），
/// 而 `VenueLookupPort` 接口只返回 `VenueId`。因此直接依赖 `VenueRepository`。
///
/// **注意**：此类不是 Spring Bean，需要在使用时手动创建实例。
/// 缓存生命周期由调用方控制（如 Step 级别、Request 级别等）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class CachingVenueLookupDecorator implements VenueLookupPort {

  private final VenueRepository venueRepository;

  /// NLM ID → VenueId 缓存
  private final Map<String, VenueId> nlmIdCache = new ConcurrentHashMap<>();

  /// ISSN → VenueId 缓存（包括 Print、Electronic、Linking）
  private final Map<String, VenueId> issnCache = new ConcurrentHashMap<>();

  /// 已查询但未找到的 NLM ID（避免重复查询）
  private final Set<String> notFoundNlmIds = ConcurrentHashMap.newKeySet();

  /// 已查询但未找到的 ISSN（避免重复查询）
  private final Set<String> notFoundIssns = ConcurrentHashMap.newKeySet();

  /// 构造函数。
  ///
  /// @param venueRepository Venue 仓储（用于查询和二级索引预热）
  public CachingVenueLookupDecorator(VenueRepository venueRepository) {
    this.venueRepository = venueRepository;
  }

  @Override
  public Optional<VenueId> findByNlmId(String nlmId) {
    return findWithCache(nlmId, nlmIdCache, notFoundNlmIds, venueRepository::findByNlmIds);
  }

  @Override
  public Optional<VenueId> findByIssn(String issn) {
    return findWithCache(issn, issnCache, notFoundIssns, venueRepository::findByIssns);
  }

  @Override
  public Optional<VenueId> findByPriority(String nlmId, Collection<String> issns) {
    // 1. 优先 NLM ID
    Optional<VenueId> result = findByNlmId(nlmId);
    if (result.isPresent()) {
      return result;
    }

    // 2. 按顺序尝试 ISSN
    if (issns != null) {
      for (String issn : issns) {
        result = findByIssn(issn);
        if (result.isPresent()) {
          return result;
        }
      }
    }

    return Optional.empty();
  }

  /// 预热缓存（批量加载 NLM ID 对应的 Venue）。
  ///
  /// 可选操作，用于 Step 初始化时预加载常用 Venue。
  ///
  /// @param nlmIds NLM ID 集合
  public void warmup(Collection<String> nlmIds) {
    if (nlmIds == null || nlmIds.isEmpty()) {
      return;
    }

    log.info("预热 VenueLookup 缓存，加载 {} 个 NLM ID", nlmIds.size());
    Map<String, VenueAggregate> venues = venueRepository.findByNlmIds(nlmIds);

    for (Map.Entry<String, VenueAggregate> entry : venues.entrySet()) {
      VenueAggregate venue = entry.getValue();
      VenueId venueId = VenueId.of(venue.getId().value());
      nlmIdCache.put(entry.getKey(), venueId);
      cacheVenueIssns(venue);
    }

    log.info("VenueLookup 缓存预热完成，缓存 {} 个 Venue", venues.size());
  }

  /// 获取缓存统计信息。
  ///
  /// @return 缓存统计描述
  public String getStats() {
    return "VenueLookupCache[nlmIdCache=%d, issnCache=%d, notFoundNlmIds=%d, notFoundIssns=%d]"
        .formatted(
            nlmIdCache.size(), issnCache.size(), notFoundNlmIds.size(), notFoundIssns.size());
  }

  /// 清空缓存。
  public void clear() {
    nlmIdCache.clear();
    issnCache.clear();
    notFoundNlmIds.clear();
    notFoundIssns.clear();
    log.debug("VenueLookup 缓存已清空");
  }

  /// 通用缓存查找逻辑。
  ///
  /// @param key 查找键（NLM ID 或 ISSN）
  /// @param cache 缓存 Map
  /// @param notFoundSet 未找到记录集合
  /// @param repositoryQuery 仓储查询函数（用于查询和二级索引预热）
  /// @return VenueId，如果不存在返回 empty
  private Optional<VenueId> findWithCache(
      String key,
      Map<String, VenueId> cache,
      Set<String> notFoundSet,
      Function<Collection<String>, Map<String, VenueAggregate>> repositoryQuery) {

    if (key == null || key.isBlank()) {
      return Optional.empty();
    }

    // 缓存命中
    VenueId cached = cache.get(key);
    if (cached != null) {
      return Optional.of(cached);
    }

    // 已确认不存在
    if (notFoundSet.contains(key)) {
      return Optional.empty();
    }

    // 查询数据库（通过仓储获取完整 VenueAggregate 以便缓存 ISSN）
    Map<String, VenueAggregate> result = repositoryQuery.apply(Set.of(key));
    VenueAggregate venue = result.get(key);

    if (venue != null) {
      VenueId venueId = VenueId.of(venue.getId().value());
      cache.put(key, venueId);
      // 同时缓存该 Venue 的所有 ISSN（二级索引预热）
      cacheVenueIssns(venue);
      return Optional.of(venueId);
    } else {
      notFoundSet.add(key);
      return Optional.empty();
    }
  }

  /// 缓存 Venue 的所有 ISSN。
  private void cacheVenueIssns(VenueAggregate venue) {
    VenueId venueId = VenueId.of(venue.getId().value());
    venue
        .getIdentifiers()
        .forEach(
            identifier -> {
              String value = identifier.value();
              if (value != null && !value.isBlank()) {
                issnCache.put(value, venueId);
              }
            });
  }
}
