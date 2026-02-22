package com.patra.catalog.domain.port.read;

import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import com.patra.catalog.domain.model.read.publication.PublicationFilter;
import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
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
}
