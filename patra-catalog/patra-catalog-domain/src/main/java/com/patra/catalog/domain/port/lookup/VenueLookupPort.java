package com.patra.catalog.domain.port.lookup;

import com.patra.catalog.domain.model.vo.venue.VenueId;
import java.util.Collection;
import java.util.Optional;

/// Venue 查找端口。
///
/// 提供 Venue 标识符匹配能力，适用于多种场景：
///
/// - API 单次查询
/// - 批量数据导入
/// - 事件处理
///
/// 实现层可自由选择缓存策略（无缓存、内存缓存、分布式缓存等）。
///
/// **匹配优先级**：
///
/// 1. NLM Unique ID（最可靠，PubMed 原生标识）
/// 2. ISSN-L（Linking ISSN，期刊链接标识）
/// 3. Print ISSN
/// 4. Electronic ISSN
///
/// @author linqibin
/// @since 0.1.0
public interface VenueLookupPort {

  /// 根据 NLM ID 查找 Venue。
  ///
  /// @param nlmId NLM Unique ID
  /// @return VenueId，如果不存在返回 empty
  Optional<VenueId> findByNlmId(String nlmId);

  /// 根据 ISSN 查找 Venue。
  ///
  /// @param issn ISSN（Print、Electronic 或 Linking）
  /// @return VenueId，如果不存在返回 empty
  Optional<VenueId> findByIssn(String issn);

  /// 按优先级匹配 Venue（NLM ID → ISSNs）。
  ///
  /// 匹配优先级：NLM ID > ISSN-L > Print ISSN > Electronic ISSN
  ///
  /// @param nlmId NLM Unique ID
  /// @param issns ISSN 列表（按优先级排序）
  /// @return VenueId，如果所有标识符都无法匹配返回 empty
  Optional<VenueId> findByPriority(String nlmId, Collection<String> issns);
}
