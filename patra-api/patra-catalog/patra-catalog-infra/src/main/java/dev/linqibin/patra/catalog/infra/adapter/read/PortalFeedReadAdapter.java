package dev.linqibin.patra.catalog.infra.adapter.read;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalFeedFilter;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.port.read.PortalFeedReadPort;
import dev.linqibin.patra.catalog.infra.persistence.dao.PortalFeedDao;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/// Portal 文献流 CQRS 读适配器。
///
/// 单次 native query（venue LEFT JOIN + author string_agg + type 子查询）组装读模型，
/// 无 N+1。排序内嵌 SQL，由 tab 决定。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class PortalFeedReadAdapter implements PortalFeedReadPort {

  /// 作者拼接分隔符，与 [PortalFeedDao] 的 `string_agg(..., E'\x1f', ...)` 一致（U+001F）。
  private static final String AUTHOR_DELIMITER = "";

  private final PortalFeedDao portalFeedDao;

  @Override
  public PageResult<PortalPaperReadModel> findFeedPage(
      PagingParams paging, PortalFeedFilter filter) {
    Pageable pageable = PageRequest.of(paging.page() - 1, paging.pageSize());
    Page<PortalFeedRow> page = portalFeedDao.findFeedPage(filter.tab().name(), pageable);

    List<PortalPaperReadModel> items = page.getContent().stream().map(this::toReadModel).toList();
    return PageResult.of(items, paging.page(), paging.pageSize(), page.getTotalElements());
  }

  private PortalPaperReadModel toReadModel(PortalFeedRow row) {
    return PortalPaperReadModel.builder()
        .id(row.getId())
        .title(row.getTitle())
        .venueName(row.getVenueName())
        .publicationYear(row.getPublicationYear())
        .authors(splitAuthors(row.getAuthorNames()))
        .citationCount(row.getCitationCount())
        .doi(row.getDoi())
        .pmid(row.getPmid())
        .provenanceCode(row.getProvenanceCode())
        .studyType(row.getStudyType())
        .lastSyncedAt(row.getLastSyncedAt())
        .build();
  }

  private List<String> splitAuthors(String authorNames) {
    if (authorNames == null || authorNames.isBlank()) {
      return List.of();
    }
    return List.of(authorNames.split(AUTHOR_DELIMITER, -1));
  }
}
