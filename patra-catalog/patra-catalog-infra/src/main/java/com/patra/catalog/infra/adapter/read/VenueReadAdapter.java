package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.catalog.infra.adapter.persistence.dao.VenueDao;
import com.patra.catalog.infra.adapter.persistence.entity.VenueEntity;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import com.patra.starter.jpa.entity.BaseJpaEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

  /// 查询 Venue 分页列表。
  ///
  /// @param paging 已验证的分页参数
  /// @param keyword 关键词（可空）
  /// @return Venue 分页结果
  @Override
  public PageResult<VenueSummaryReadModel> findVenuePage(PagingParams paging, String keyword) {
    Pageable pageable =
        PageRequest.of(paging.page() - 1, paging.pageSize(), BaseJpaEntity.DEFAULT_SORT);

    Page<VenueEntity> entityPage = venueDao.findJournalPage(keyword, pageable);
    List<VenueSummaryReadModel> items =
        entityPage.getContent().stream().map(this::toReadModel).toList();

    return PageResult.of(items, paging.page(), paging.pageSize(), entityPage.getTotalElements());
  }

  /// 将 VenueEntity 转换为 Venue 列表读模型。
  ///
  /// @param entity Venue 实体
  /// @return Venue 列表项读模型
  private VenueSummaryReadModel toReadModel(VenueEntity entity) {
    return new VenueSummaryReadModel(
        entity.getId(),
        entity.getDisplayName(),
        entity.getIssnL(),
        entity.getNlmId(),
        entity.getProvenanceCode(),
        entity.getCountryCode(),
        entity.getLastSyncedAt());
  }
}
