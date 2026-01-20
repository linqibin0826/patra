package com.patra.catalog.infra.adapter.lookup;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/// 默认 Venue 查找适配器（无缓存）。
///
/// 直接查询 VenueRepository，适用于：
///
/// - API 单次查询
/// - 事件处理
/// - 低频查询场景
///
/// 作为 `@Primary` 实现，在未指定 `@Qualifier` 时默认注入此实现。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultVenueLookupAdapter implements VenueLookupPort {

  private final VenueRepository venueRepository;

  @Override
  public Optional<VenueId> findByNlmId(String nlmId) {
    if (nlmId == null || nlmId.isBlank()) {
      return Optional.empty();
    }
    Map<String, VenueAggregate> result = venueRepository.findByNlmIds(Set.of(nlmId));
    return Optional.ofNullable(result.get(nlmId)).map(venue -> VenueId.of(venue.getId().value()));
  }

  @Override
  public Optional<VenueId> findByIssn(String issn) {
    if (issn == null || issn.isBlank()) {
      return Optional.empty();
    }
    Map<String, VenueAggregate> result = venueRepository.findByIssns(Set.of(issn));
    return Optional.ofNullable(result.get(issn)).map(venue -> VenueId.of(venue.getId().value()));
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
}
