package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFacets;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFilter;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseReadModel;
import dev.patra.catalog.domain.portal.port.read.VenueBrowseReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Portal 期刊浏览/检索 CQRS 查询服务。
///
/// 分页归一化由调用方（Adapter 层）负责；本服务直接委托 [VenueBrowseReadPort]。
/// LIKE 转义由读适配器负责。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalVenueBrowseQueryService {

  private final VenueBrowseReadPort readPort;

  /// 分页检索期刊。
  ///
  /// @param filter 过滤 + 排序参数（已构建）
  /// @param paging 已归一化的分页参数
  /// @return 分页结果
  @Transactional(readOnly = true)
  public PageResult<VenueBrowseReadModel> browse(VenueBrowseFilter filter, PagingParams paging) {
    return readPort.search(filter, paging);
  }

  /// 计算各维度 facet 计数，支持 drill-down 多选语义。
  ///
  /// @param filter 过滤参数（含已选维度，忽略 sort）
  /// @return facet 聚合结果
  @Transactional(readOnly = true)
  public VenueBrowseFacets facets(VenueBrowseFilter filter) {
    return readPort.facets(filter);
  }
}
