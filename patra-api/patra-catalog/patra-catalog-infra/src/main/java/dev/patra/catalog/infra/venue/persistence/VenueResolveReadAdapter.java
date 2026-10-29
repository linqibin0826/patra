package dev.patra.catalog.infra.venue.persistence;

import dev.patra.catalog.domain.venue.model.read.VenueResolveReadModel;
import dev.patra.catalog.domain.venue.port.read.VenueResolveReadPort;
import dev.patra.catalog.infra.venue.persistence.dao.VenueResolveDao;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 期刊批量解析 CQRS 读适配器：一个请求一条 SQL，软删行照样返回，只认期刊类型。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class VenueResolveReadAdapter implements VenueResolveReadPort {

  private final VenueResolveDao dao;

  @Override
  public List<VenueResolveReadModel> findByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return dao.findResolveRowsByIds(ids.toArray(Long[]::new)).stream()
        .map(VenueResolveReadAdapter::toReadModel)
        .toList();
  }

  @Override
  public Map<String, VenueResolveReadModel> findByNlmIds(Collection<String> nlmIds) {
    if (nlmIds.isEmpty()) {
      return Map.of();
    }
    // 依赖唯一索引 uk_venue_identifier_nlm：每个 NLM 值只属于一个期刊，映射不会撞键。
    // 约束缺失时 toUnmodifiableMap 会抛异常（500），不会静默挑一个期刊返回
    return dao.findResolveRowsByNlmIds(nlmIds.toArray(String[]::new)).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                VenueNlmMatchRow::getMatchedNlmId, VenueResolveReadAdapter::toReadModel));
  }

  /// 投影行 → 读模型。
  ///
  /// @param row 投影行
  /// @return 读模型
  private static VenueResolveReadModel toReadModel(VenueResolveRow row) {
    return VenueResolveReadModel.builder()
        .id(row.getId())
        .nlmId(row.getNlmId())
        .title(row.getTitle())
        .abbreviatedTitle(row.getAbbreviatedTitle())
        .deleted(Boolean.TRUE.equals(row.getDeleted()))
        .build();
  }
}
