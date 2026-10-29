package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.PortalPaperReadModel;
import dev.patra.catalog.domain.portal.model.read.PublicationSearchFacets;
import dev.patra.catalog.domain.portal.model.read.PublicationSearchFilter;
import dev.patra.catalog.domain.portal.port.read.PublicationSearchReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Portal 文献检索 CQRS 查询服务。
///
/// 分页归一化与请求校验由 Adapter 层负责；本服务直接委托 [PublicationSearchReadPort]。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalPublicationSearchQueryService {

  private final PublicationSearchReadPort readPort;

  /// 分页检索文献。
  ///
  /// @param filter 过滤 + 排序参数
  /// @param paging 已归一化的分页参数
  /// @return 分页结果
  @Transactional(readOnly = true)
  public PageResult<PortalPaperReadModel> search(
      PublicationSearchFilter filter, PagingParams paging) {
    return readPort.search(filter, paging);
  }

  /// 计算各维度 facet 计数（drill-down 语义）。
  ///
  /// @param filter 过滤参数（忽略 sort）
  /// @return facet 聚合结果
  @Transactional(readOnly = true)
  public PublicationSearchFacets facets(PublicationSearchFilter filter) {
    return readPort.facets(filter);
  }
}
