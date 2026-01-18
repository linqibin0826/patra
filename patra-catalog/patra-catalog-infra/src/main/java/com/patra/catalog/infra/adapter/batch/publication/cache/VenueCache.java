package com.patra.catalog.infra.adapter.batch.publication.cache;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.port.repository.VenueRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/// Venue 查找缓存。
///
/// 用于 PubMed 文献导入时的 Venue 匹配缓存，减少数据库查询次数。
///
/// **缓存策略**：
///
/// - 按 NLM ID 和 ISSN 两种索引缓存 VenueId
/// - 使用 ConcurrentHashMap，线程安全
/// - 缓存命中率高（同一期刊的文献聚集）
///
/// **匹配优先级**：
///
/// 1. NLM Unique ID（最可靠，PubMed 原生标识）
/// 2. ISSN-L（Linking ISSN，期刊链接标识）
/// 3. Print ISSN
/// 4. Electronic ISSN
///
/// **使用方式**：
///
/// ```java
/// // 在 Step 初始化时预热缓存（可选）
/// venueCache.warmup(nlmIds);
///
/// // 在 Processor 中匹配 Venue
/// Optional<VenueId> venueId = venueCache.findByNlmId(nlmUniqueId);
/// if (venueId.isEmpty()) {
///     venueId = venueCache.findByIssn(issn);
/// }
/// ```
///
/// **生命周期**：
///
/// - Step 范围内使用（@StepScope）
/// - 每次 Step 执行创建新实例
/// - Step 结束后自动销毁
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class VenueCache {

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
  /// @param venueRepository Venue 仓储
  public VenueCache(VenueRepository venueRepository) {
    this.venueRepository = venueRepository;
  }

  /// 根据 NLM ID 查找 Venue。
  ///
  /// 优先从缓存获取，缓存未命中时查询数据库并缓存结果。
  ///
  /// @param nlmId NLM Unique ID
  /// @return VenueId，如果不存在返回 empty
  public Optional<VenueId> findByNlmId(String nlmId) {
    if (nlmId == null || nlmId.isBlank()) {
      return Optional.empty();
    }

    // 缓存命中
    VenueId cached = nlmIdCache.get(nlmId);
    if (cached != null) {
      return Optional.of(cached);
    }

    // 已确认不存在
    if (notFoundNlmIds.contains(nlmId)) {
      return Optional.empty();
    }

    // 查询数据库
    Map<String, VenueAggregate> result = venueRepository.findByNlmIds(Set.of(nlmId));
    VenueAggregate venue = result.get(nlmId);

    if (venue != null) {
      VenueId venueId = VenueId.of(venue.getId().value());
      nlmIdCache.put(nlmId, venueId);
      // 同时缓存该 Venue 的所有 ISSN
      cacheVenueIssns(venue);
      return Optional.of(venueId);
    } else {
      notFoundNlmIds.add(nlmId);
      return Optional.empty();
    }
  }

  /// 根据 ISSN 查找 Venue。
  ///
  /// 优先从缓存获取，缓存未命中时查询数据库并缓存结果。
  ///
  /// @param issn ISSN（Print、Electronic 或 Linking）
  /// @return VenueId，如果不存在返回 empty
  public Optional<VenueId> findByIssn(String issn) {
    if (issn == null || issn.isBlank()) {
      return Optional.empty();
    }

    // 缓存命中
    VenueId cached = issnCache.get(issn);
    if (cached != null) {
      return Optional.of(cached);
    }

    // 已确认不存在
    if (notFoundIssns.contains(issn)) {
      return Optional.empty();
    }

    // 查询数据库
    Map<String, VenueAggregate> result = venueRepository.findByIssns(Set.of(issn));
    VenueAggregate venue = result.get(issn);

    if (venue != null) {
      VenueId venueId = VenueId.of(venue.getId().value());
      issnCache.put(issn, venueId);
      // 同时缓存该 Venue 的所有 ISSN
      cacheVenueIssns(venue);
      return Optional.of(venueId);
    } else {
      notFoundIssns.add(issn);
      return Optional.empty();
    }
  }

  /// 按优先级匹配 Venue（NLM ID → ISSNs）。
  ///
  /// 这是 Processor 的主要入口方法，按优先级尝试匹配。
  ///
  /// @param nlmId NLM Unique ID
  /// @param issns ISSN 列表（按优先级排序：Linking → Print → Electronic）
  /// @return VenueId，如果所有标识符都无法匹配返回 empty
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

    log.info("预热 VenueCache，加载 {} 个 NLM ID", nlmIds.size());
    Map<String, VenueAggregate> venues = venueRepository.findByNlmIds(nlmIds);

    for (Map.Entry<String, VenueAggregate> entry : venues.entrySet()) {
      VenueAggregate venue = entry.getValue();
      VenueId venueId = VenueId.of(venue.getId().value());
      nlmIdCache.put(entry.getKey(), venueId);
      cacheVenueIssns(venue);
    }

    log.info("VenueCache 预热完成，缓存 {} 个 Venue", venues.size());
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

  /// 获取缓存统计信息。
  ///
  /// @return 缓存统计描述
  public String getStats() {
    return "VenueCache[nlmIdCache=%d, issnCache=%d, notFoundNlmIds=%d, notFoundIssns=%d]"
        .formatted(
            nlmIdCache.size(), issnCache.size(), notFoundNlmIds.size(), notFoundIssns.size());
  }

  /// 清空缓存。
  public void clear() {
    nlmIdCache.clear();
    issnCache.clear();
    notFoundNlmIds.clear();
    notFoundIssns.clear();
    log.debug("VenueCache 已清空");
  }
}
