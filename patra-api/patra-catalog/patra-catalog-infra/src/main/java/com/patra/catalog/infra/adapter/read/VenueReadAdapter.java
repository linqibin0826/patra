package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/// Venue 读适配器。
///
/// 基于 JPA 从 `cat_venue` 查询 Venue 分页数据，供前端列表查询使用。
@Repository
@RequiredArgsConstructor
public class VenueReadAdapter implements VenueReadPort {

  private final VenueDao venueDao;
  private final VenueReadModelMapper venueReadModelMapper;

  /// 查询 Venue 分页列表。
  ///
  /// @param paging 已验证的分页参数
  /// @param filter 筛选条件
  /// @return Venue 分页结果
  @Override
  public PageResult<VenueSummaryReadModel> findVenuePage(PagingParams paging, VenueFilter filter) {
    Pageable pageable = PageRequest.of(paging.page() - 1, paging.pageSize());

    var entityPage =
        venueDao.findJournalPage(
            filter.keyword(), filter.countryCode(), filter.issnL(), filter.nlmId(), pageable);
    List<VenueSummaryReadModel> items =
        entityPage.getContent().stream().map(venueReadModelMapper::toReadModel).toList();

    return PageResult.of(items, paging.page(), paging.pageSize(), entityPage.getTotalElements());
  }

  /// 查询 Venue 详情。
  ///
  /// @param id 期刊主键 ID
  /// @return Venue 详情读模型，不存在时返回 Optional.empty()
  @Override
  public Optional<VenueDetailReadModel> findVenueDetail(Long id) {
    return venueDao.findById(id).map(venueReadModelMapper::toDetailReadModel);
  }
}
