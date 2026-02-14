package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import com.patra.starter.jpa.entity.BaseJpaEntity;
import java.util.List;
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
  /// @param keyword 关键词（可空）
  /// @return Venue 分页结果
  @Override
  public PageResult<VenueSummaryReadModel> findVenuePage(PagingParams paging, String keyword) {
    Pageable pageable =
        PageRequest.of(paging.page() - 1, paging.pageSize(), BaseJpaEntity.DEFAULT_SORT);

    var entityPage = venueDao.findJournalPage(keyword, pageable);
    List<VenueSummaryReadModel> items =
        entityPage.getContent().stream().map(venueReadModelMapper::toReadModel).toList();

    return PageResult.of(items, paging.page(), paging.pageSize(), entityPage.getTotalElements());
  }
}
