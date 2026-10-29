package dev.patra.catalog.domain.portal.port.read;

import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFacets;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFilter;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseReadModel;
import java.util.Collection;
import java.util.List;

/// 期刊浏览检索 CQRS 读端口。
///
/// 由 Infra 层 [VenueBrowseReadAdapter] 实现，
/// 支持多维度过滤、多种排序和 facet 聚合计数。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueBrowseReadPort {

  /// 分页检索期刊。
  ///
  /// @param filter 过滤 + 排序参数
  /// @param paging 已归一化的分页参数
  /// @return 分页结果
  PageResult<VenueBrowseReadModel> search(VenueBrowseFilter filter, PagingParams paging);

  /// 按 ID 批量读期刊卡片（门户 `/portal/venues/batch`）：只返回期刊类型且未软删的，顺序不保证。
  ///
  /// @param ids 期刊 ID；空集合时直接返回空列表、不查库
  /// @return 卡片，与浏览结果同形
  List<VenueBrowseReadModel> findCardsByIds(Collection<Long> ids);

  /// 计算各维度 facet 计数，支持 drill-down 多选语义。
  ///
  /// 每个分组维度（subjects / jcrQuartiles / casQuartiles / countries）的选项计数
  /// 按「当前 query + 除本维度外其他已选维度」计算——组内多选不互相清零，
  /// 即用户已选 Q1 时仍能看到 Q2 的候选数量，方便追加筛选。
  ///
  /// 布尔维度（casTop / openAccess / doaj）直接返回满足当前全量 filter 的绝对计数。
  ///
  /// @param filter 过滤参数（含已选维度）
  /// @return facet 聚合结果
  VenueBrowseFacets facets(VenueBrowseFilter filter);
}
