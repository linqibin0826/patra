package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.read.publication.PublicationResolveReadModel;
import dev.patra.catalog.domain.port.read.PublicationResolveReadPort;
import dev.patra.catalog.infra.persistence.dao.PublicationResolveDao;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 文献批量解析 CQRS 读适配器：一个请求一条 SQL，软删行照样返回。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class PublicationResolveReadAdapter implements PublicationResolveReadPort {

  private final PublicationResolveDao dao;

  @Override
  public List<PublicationResolveReadModel> findByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return dao.findResolveRowsByIds(ids.toArray(Long[]::new)).stream()
        .map(PublicationResolveReadAdapter::toReadModel)
        .toList();
  }

  @Override
  public List<PublicationResolveReadModel> findByPmids(Collection<String> pmids) {
    if (pmids.isEmpty()) {
      return List.of();
    }
    return dao.findResolveRowsByPmids(pmids.toArray(String[]::new)).stream()
        .map(PublicationResolveReadAdapter::toReadModel)
        .toList();
  }

  /// 投影行 → 读模型。
  ///
  /// @param row 投影行
  /// @return 读模型
  private static PublicationResolveReadModel toReadModel(PublicationResolveRow row) {
    return PublicationResolveReadModel.builder()
        .id(row.getId())
        .pmid(row.getPmid())
        .title(row.getTitle())
        .venueName(row.getVenueName())
        .publicationYear(row.getPublicationYear())
        .deleted(Boolean.TRUE.equals(row.getDeleted()))
        .build();
  }
}
