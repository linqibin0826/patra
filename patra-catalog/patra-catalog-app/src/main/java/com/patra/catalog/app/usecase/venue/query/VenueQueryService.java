package com.patra.catalog.app.usecase.venue.query;

import com.patra.catalog.app.usecase.venue.query.dto.VenueDetailQuery;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// Venue 查询服务。
///
/// 负责对查询参数做规范化，并委托领域读仓储执行分页查询。
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
    String keyword = normalizeKeyword(query.q());
    return venueReadPort.findVenuePage(paging, keyword);
  }

  /// 查询 Venue 详情。
  ///
  /// @param query 详情查询参数
  /// @return Venue 详情读模型
  /// @throws ApplicationException 当 Venue 不存在时抛出 CAT-0404 异常
  public VenueDetailReadModel getVenueDetail(VenueDetailQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    return venueReadPort
        .findVenueDetail(query.id())
        .orElseThrow(
            () ->
                new ApplicationException(
                    HttpStdErrors.of("CAT").NOT_FOUND(), "Venue not found with id: " + query.id()));
  }

  /// 归一化关键词。
  ///
  /// @param keyword 原始关键词
  /// @return 去空白后的关键词，空白返回 null
  private String normalizeKeyword(String keyword) {
    if (keyword == null) {
      return null;
    }
    String trimmed = keyword.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
