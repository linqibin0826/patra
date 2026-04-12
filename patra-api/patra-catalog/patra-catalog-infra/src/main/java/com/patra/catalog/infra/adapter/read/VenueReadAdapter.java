package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueLatestRating;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.CasWarningDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/// Venue 读适配器。
///
/// 基于 JPA 从 `cat_venue` 查询 Venue 分页数据，并批量 JOIN JCR/CAS/Scopus 评级数据，
/// 组装为 {@link VenueSummaryReadModel} 供前端列表查询使用。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class VenueReadAdapter implements VenueReadPort {

  private final VenueDao venueDao;
  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;
  private final ScopusRatingDao scopusRatingDao;
  private final CasWarningDao casWarningDao;
  private final VenueReadModelMapper venueReadModelMapper;

  /// 查询 Venue 分页列表，包含最新 JCR/CAS/Scopus 评级数据。
  ///
  /// **查询策略**：先分页查 VenueEntity，再按 venueIds 批量加载三套评级，
  /// 避免 N+1 查询问题。
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

    // 批量加载评级数据
    List<Long> venueIds = entityPage.getContent().stream().map(VenueEntity::getId).toList();
    Map<Long, JcrRatingEntity> latestJcr = findLatestJcrByVenueIds(venueIds);
    Map<Long, CasRatingEntity> latestCas = findLatestCasByVenueIds(venueIds);
    Map<Long, ScopusRatingEntity> latestScopus = findLatestScopusByVenueIds(venueIds);

    // 组装读模型
    List<VenueSummaryReadModel> items =
        entityPage.getContent().stream()
            .map(
                entity ->
                    venueReadModelMapper.toReadModel(
                        entity,
                        latestJcr.get(entity.getId()),
                        latestCas.get(entity.getId()),
                        latestScopus.get(entity.getId())))
            .toList();

    return PageResult.of(items, paging.page(), paging.pageSize(), entityPage.getTotalElements());
  }

  /// 查询 Venue 详情，包含最新 JCR/CAS/Scopus 评级和 CAS 预警数据。
  ///
  /// @param id 期刊主键 ID
  /// @return Venue 详情读模型，不存在时返回 Optional.empty()
  @Override
  public Optional<VenueDetailReadModel> findVenueDetail(Long id) {
    return venueDao
        .findById(id)
        .map(
            entity -> {
              VenueLatestRating latestRating = buildLatestRating(entity.getId());
              return venueReadModelMapper.toDetailReadModel(entity, latestRating);
            });
  }

  /// 构建最新评级摘要，聚合 JCR/CAS/Scopus 评级和 CAS 预警数据。
  ///
  /// @param venueId 期刊 ID
  /// @return 评级摘要（全部数据源无数据时返回 null）
  private VenueLatestRating buildLatestRating(Long venueId) {
    var jcr = jcrRatingDao.findLatestByVenueId(venueId).orElse(null);
    var cas = casRatingDao.findLatestByVenueId(venueId).orElse(null);
    var scopus = scopusRatingDao.findLatestByVenueId(venueId).orElse(null);
    var warning = findLatestWarning(venueId);

    if (jcr == null && cas == null && scopus == null && warning == null) {
      return null;
    }
    return venueReadModelMapper.toLatestRating(jcr, cas, scopus, warning);
  }

  /// 查找某期刊最新的 CAS 预警记录，按发布年份降序取第一条。
  ///
  /// @param venueId 期刊 ID
  /// @return 最新预警记录，无预警时返回 null
  private CasWarningEntity findLatestWarning(Long venueId) {
    return casWarningDao.findByVenueId(venueId).stream()
        .max(Comparator.comparing(CasWarningEntity::getPublishedYear))
        .orElse(null);
  }

  /// 批量查找最新评级的通用方法，按 venueId 分组并用 mergeFunction 保留最新记录。
  private <T> Map<Long, T> findLatestByVenueIds(
      List<Long> venueIds,
      Function<List<Long>, List<T>> finder,
      Function<T, Long> venueIdExtractor,
      BinaryOperator<T> mergeFunction) {
    if (venueIds.isEmpty()) {
      return Map.of();
    }
    return finder.apply(venueIds).stream()
        .collect(Collectors.toMap(venueIdExtractor, r -> r, mergeFunction));
  }

  /// 批量查找最新 JCR 评级，按 venueId 分组取最新年份。
  private Map<Long, JcrRatingEntity> findLatestJcrByVenueIds(List<Long> venueIds) {
    return findLatestByVenueIds(
        venueIds,
        jcrRatingDao::findByVenueIdIn,
        JcrRatingEntity::getVenueId,
        (a, b) -> a.getYear() >= b.getYear() ? a : b);
  }

  /// 批量查找最新 Scopus 评级，按 venueId 分组取最新年份。
  private Map<Long, ScopusRatingEntity> findLatestScopusByVenueIds(List<Long> venueIds) {
    return findLatestByVenueIds(
        venueIds,
        scopusRatingDao::findByVenueIdIn,
        ScopusRatingEntity::getVenueId,
        (a, b) -> a.getYear() >= b.getYear() ? a : b);
  }

  /// 批量查找最新 CAS 评级，按 venueId 分组取最新年份（同年取更高优先级版本）。
  private Map<Long, CasRatingEntity> findLatestCasByVenueIds(List<Long> venueIds) {
    return findLatestByVenueIds(
        venueIds,
        casRatingDao::findByVenueIdIn,
        CasRatingEntity::getVenueId,
        (a, b) -> {
          int cmp = Short.compare(a.getYear(), b.getYear());
          return cmp != 0
              ? (cmp > 0 ? a : b)
              : a.getEdition().compareTo(b.getEdition()) <= 0 ? a : b;
        });
  }
}
