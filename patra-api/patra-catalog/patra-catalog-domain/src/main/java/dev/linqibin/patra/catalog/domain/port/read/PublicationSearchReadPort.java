package dev.linqibin.patra.catalog.domain.port.read;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;

/// 文献检索 CQRS 读端口，首页 feed 与 `/portal/publications/search` 共用。
///
/// 由 Infra 层 [PublicationSearchReadAdapter] 实现：单条 native query 组装列表项，
/// facets 采用 drill-down 语义（分组维度忽略自身已选值）。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationSearchReadPort {

  /// 分页检索文献。
  ///
  /// @param filter 过滤 + 排序参数
  /// @param paging 已归一化的分页参数
  /// @return 分页结果
  PageResult<PortalPaperReadModel> search(PublicationSearchFilter filter, PagingParams paging);

  /// 计算各维度 facet 计数（忽略 filter.sort）。
  ///
  /// @param filter 过滤参数（含已选维度）
  /// @return facet 聚合结果
  PublicationSearchFacets facets(PublicationSearchFilter filter);
}
