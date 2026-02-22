package com.patra.catalog.app.usecase.venue.query;

import static com.patra.common.util.StringUtils.trimToNull;

import com.patra.catalog.app.usecase.venue.query.dto.VenueDetailQuery;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.exception.VenueNotFoundException;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// Venue 查询服务。
///
/// 负责对查询参数做规范化，并委托领域读端口执行分页查询。
@Service
@RequiredArgsConstructor
public class VenueQueryService {

  private final VenueReadPort venueReadPort;

  /// 查询 Venue 分页列表。
  ///
  /// @param query 查询参数
  /// @return Venue 分页结果
  public PageResult<VenueSummaryReadModel> listVenues(VenueListQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    PagingParams paging = PagingParams.normalize(query.page(), query.pageSize());
    VenueFilter filter =
        VenueFilter.builder()
            .keyword(trimToNull(query.q()))
            .countryCode(trimToNull(query.countryCode()))
            .issnL(trimToNull(query.issnL()))
            .nlmId(trimToNull(query.nlmId()))
            .build();
    return venueReadPort.findVenuePage(paging, filter);
  }

  /// 查询 Venue 详情。
  ///
  /// @param query 详情查询参数
  /// @return Venue 详情读模型
  /// @throws VenueNotFoundException 当 Venue 不存在时
  public VenueDetailReadModel getVenueDetail(VenueDetailQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    return venueReadPort
        .findVenueDetail(query.id())
        .orElseThrow(() -> new VenueNotFoundException(query.id()));
  }
}
