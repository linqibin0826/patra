package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueLatestRating;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.CasWarningDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.dao.VenueIndexingHistoryDao;
import com.patra.catalog.infra.persistence.dao.VenueMeshDao;
import com.patra.catalog.infra.persistence.dao.VenueRelationDao;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/// Venue 读适配器。
///
/// 基于 JPA 从 `cat_venue` 查询 Venue 分页数据，并批量 JOIN JCR/CAS/Scopus 评级数据，
/// 组装为 {@link VenueSummaryReadModel} 供前端列表查询使用。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueReadAdapter implements VenueReadPort {

  private final VenueDao venueDao;
  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;
  private final ScopusRatingDao scopusRatingDao;
  private final CasWarningDao casWarningDao;
  private final VenueMeshDao venueMeshDao;
  private final VenueRelationDao venueRelationDao;
  private final VenueIndexingHistoryDao venueIndexingHistoryDao;
  private final VenueReadModelMapper venueReadModelMapper;
  private final ObjectMapper objectMapper;

  /// 查询 Venue 分页列表，包含最新 JCR/CAS/Scopus 评级数据。
  ///
  /// **查询策略**：
  ///
  /// - 仅基础筛选 + 默认排序：先分页查 VenueEntity，再批量加载评级（简单高效）
  /// - 高级筛选或非默认排序：使用 LATERAL JOIN 单次查询完成（避免评级表无法过滤的问题）
  ///
  /// @param paging 已验证的分页参数
  /// @param filter 筛选条件
  /// @return Venue 分页结果
  @Override
  public PageResult<VenueSummaryReadModel> findVenuePage(PagingParams paging, VenueFilter filter) {
    Pageable pageable = PageRequest.of(paging.page() - 1, paging.pageSize());

    if (filter.hasAdvancedFilters()) {
      return findVenuePageWithLateralJoin(paging, filter, pageable);
    }
    return findVenuePageWithBatchLoad(paging, filter, pageable);
  }

  /// 基础路径：先分页查 VenueEntity，再批量加载评级数据。
  private PageResult<VenueSummaryReadModel> findVenuePageWithBatchLoad(
      PagingParams paging, VenueFilter filter, Pageable pageable) {
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

  /// 高级路径：使用 LATERAL JOIN 单次查询，直接从 Object[] 构建读模型。
  private PageResult<VenueSummaryReadModel> findVenuePageWithLateralJoin(
      PagingParams paging, VenueFilter filter, Pageable pageable) {
    Page<Object[]> resultPage =
        venueDao.findFilteredJournalPage(
            filter.keyword(),
            filter.countryCode(),
            filter.issnL(),
            filter.nlmId(),
            filter.jifQuartile(),
            filter.casMajorQuartile(),
            filter.casTopJournal(),
            filter.oaType(),
            filter.collection(),
            filter.researchDirection(),
            filter.warningOnly(),
            filter.sortBy(),
            pageable);

    List<VenueSummaryReadModel> items =
        resultPage.getContent().stream().map(this::mapRowToReadModel).toList();

    return PageResult.of(items, paging.page(), paging.pageSize(), resultPage.getTotalElements());
  }

  /// 将 LATERAL JOIN 查询的 Object[] 行映射为 VenueSummaryReadModel。
  ///
  /// 列索引对应 `findFilteredJournalPage` SELECT 子句：
  ///
  /// - 0: v.id, 1: v.title, 2: v.country_code, 3: v.image_object_key
  /// - 4: v.citation_metrics (JSON), 5: v.open_access (JSON), 6: v.cited_by_count
  /// - 7: jcr.impact_factor, 8: jcr.jif_quartile, 9: jcr.collection, 10: jcr.research_direction
  /// - 11: cas.major_quartile, 12: cas.is_top_journal
  /// - 13: scopus.cite_score, 14: scopus.quartile
  private VenueSummaryReadModel mapRowToReadModel(Object[] row) {
    Integer hIndex = extractHIndex(row[4]);
    Boolean isOa = extractIsOa(row[5]);

    return VenueSummaryReadModel.builder()
        .id(((Number) row[0]).longValue())
        .title((String) row[1])
        .countryCode((String) row[2])
        .imageObjectKey((String) row[3])
        .hIndex(hIndex)
        .isOa(isOa)
        .impactFactor(toBigDecimal(row[7]))
        .jifQuartile((String) row[8])
        .collection((String) row[9])
        .researchDirection((String) row[10])
        .casMajorQuartile((String) row[11])
        .casTopJournal(toBoolean(row[12]))
        .citeScore(toBigDecimal(row[13]))
        .citeScoreQuartile((String) row[14])
        .build();
  }

  /// 从 citation_metrics JSON 列提取 hIndex 值。
  private Integer extractHIndex(Object citationMetricsJson) {
    if (citationMetricsJson == null) {
      return null;
    }
    try {
      CitationMetrics metrics =
          objectMapper.readValue(citationMetricsJson.toString(), CitationMetrics.class);
      return metrics != null ? metrics.hIndex() : null;
    } catch (Exception e) {
      log.warn("Failed to extract hIndex from citationMetrics JSON for venue row", e);
      return null;
    }
  }

  /// 从 open_access JSON 列提取 isOa 值。
  private Boolean extractIsOa(Object openAccessJson) {
    if (openAccessJson == null) {
      return null;
    }
    try {
      OpenAccessInfo info = objectMapper.readValue(openAccessJson.toString(), OpenAccessInfo.class);
      return info != null ? info.isOa() : null;
    } catch (Exception e) {
      log.warn("Failed to extract isOa from openAccess JSON for venue row", e);
      return null;
    }
  }

  /// 安全转换为 BigDecimal。
  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    return new BigDecimal(value.toString());
  }

  /// 安全转换为 Boolean。
  private static Boolean toBoolean(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      return n.intValue() != 0;
    }
    return Boolean.parseBoolean(value.toString());
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
              var meshEntities = venueMeshDao.findByVenueId(id);
              var relationEntities = venueRelationDao.findByVenueId(id);
              var indexingEntities = venueIndexingHistoryDao.findByVenueId(id);
              return venueReadModelMapper.toDetailReadModel(
                  entity, latestRating, meshEntities, relationEntities, indexingEntities);
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
