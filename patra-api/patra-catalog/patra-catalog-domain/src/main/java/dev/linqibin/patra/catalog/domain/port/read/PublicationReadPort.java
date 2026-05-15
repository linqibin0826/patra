package dev.linqibin.patra.catalog.domain.port.read;

import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationFilter;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import java.util.List;
import java.util.Optional;

/// 文献出版物 CQRS 读端口。
///
/// 提供面向查询场景的分页检索和详情查询能力，
/// 返回只读投影（ReadModel），不涉及聚合根的持久化。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationReadPort {

  /// 分页查询文献列表。
  ///
  /// @param paging 分页参数
  /// @param filter 筛选条件
  /// @return 分页结果
  PageResult<PublicationSummaryReadModel> findPublicationPage(
      PagingParams paging, PublicationFilter filter);

  /// 查询文献详情。
  ///
  /// @param id 文献 ID
  /// @return 文献详情，不存在返回 Optional.empty()
  Optional<PublicationDetailReadModel> findPublicationDetail(Long id);

  /// 查询指定期刊的 Top N 高被引文献。
  ///
  /// 按 `citation_count` 降序、`publication_year` 降序排序。
  ///
  /// @param venueId 期刊 ID
  /// @param since   发表年下限（可为 null，不过滤）
  /// @param limit   返回条数上限（建议 1-20）；`limit<=0` 时返回空列表
  /// @return Top N 文献摘要 ReadModel 列表
  List<PublicationSummaryReadModel> findTopByVenue(Long venueId, Integer since, int limit);
}
