package dev.linqibin.patra.catalog.infra.adapter.read;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import dev.linqibin.patra.catalog.domain.model.vo.publication.InlineMarkup;
import dev.linqibin.patra.catalog.domain.port.read.PublicationSearchReadPort;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationSearchDao;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/// 文献检索 CQRS 读适配器。
///
/// 单条 native query 组装列表项；负责 LIKE 转义、DOI 归一化、类型拆分 + [EvidenceLevel#classify]、
/// 摘要片段派生（[InlineMarkup]）。facets 采用 drill-down 语义。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class PublicationSearchReadAdapter implements PublicationSearchReadPort {

  /// 聚合列分隔符，与 DAO 的 `string_agg(..., E'\\x1f', ...)` 一致（U+001F）。
  private static final String DELIMITER = "\u001f";

  /// 摘要片段长度（码点）。
  private static final int SNIPPET_CODE_POINTS = 300;

  private final PublicationSearchDao dao;

  @Override
  public PageResult<PortalPaperReadModel> search(
      PublicationSearchFilter filter, PagingParams paging) {
    Page<PublicationSearchRow> page =
        dao.findSearchPage(
            filter.sort().name(), PageRequest.of(paging.page() - 1, paging.pageSize()));
    List<PortalPaperReadModel> items = page.getContent().stream().map(this::toReadModel).toList();
    return PageResult.of(items, paging.page(), paging.pageSize(), page.getTotalElements());
  }

  @Override
  public PublicationSearchFacets facets(PublicationSearchFilter filter) {
    throw new UnsupportedOperationException("任务 9 实现");
  }

  /// 投影行 → 读模型：类型聚合列拆开后第一个当 studyType、整个列表衍生证据等级；摘要原文派生片段。
  ///
  /// @param row 投影行
  /// @return 读模型
  private PortalPaperReadModel toReadModel(PublicationSearchRow row) {
    List<String> types = split(row.getPublicationTypesAgg());
    return PortalPaperReadModel.builder()
        .id(row.getId())
        .title(row.getTitle())
        .venueId(row.getVenueId())
        .venueName(row.getVenueName())
        .publicationYear(row.getPublicationYear())
        .authors(split(row.getAuthorNames()))
        .citationCount(row.getCitationCount())
        .doi(row.getDoi())
        .pmid(row.getPmid())
        .provenanceCode(row.getProvenanceCode())
        .studyType(types.isEmpty() ? null : types.get(0))
        .evidenceLevel(EvidenceLevel.classify(types))
        .abstractSnippet(toSnippet(row.getAbstractRaw()))
        .lastSyncedAt(row.getLastSyncedAt())
        .build();
  }

  /// 摘要原文 → 可见纯文本前 300 码点；空白结果视为无摘要。
  ///
  /// @param abstractRaw 摘要原文（含内联标记），可为 null
  /// @return 片段或 null
  private static String toSnippet(String abstractRaw) {
    if (abstractRaw == null) {
      return null;
    }
    String plain =
        InlineMarkup.truncate(InlineMarkup.toPlainText(abstractRaw), SNIPPET_CODE_POINTS);
    return plain.isBlank() ? null : plain;
  }

  /// 拆分单元分隔符聚合列；null / 空白 → 空列表。
  ///
  /// @param agg 聚合列值
  /// @return 拆分结果
  private static List<String> split(String agg) {
    if (agg == null || agg.isBlank()) {
      return List.of();
    }
    return List.of(agg.split(DELIMITER, -1));
  }
}
