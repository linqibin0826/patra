package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueInstanceSummaryReadModel;
import com.patra.catalog.domain.model.read.venue.VenueLatestRating;
import com.patra.catalog.domain.model.read.venue.VenueRatingHistoryReadModel;
import com.patra.catalog.domain.model.read.venue.VenueStatsReadModel;
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
import com.patra.catalog.infra.persistence.dao.VenueInstanceDao;
import com.patra.catalog.infra.persistence.dao.VenueMeshDao;
import com.patra.catalog.infra.persistence.dao.VenuePublicationStatsDao;
import com.patra.catalog.infra.persistence.dao.VenueRelationDao;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsEntity;
import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
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
  private final VenueInstanceDao venueInstanceDao;
  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;
  private final ScopusRatingDao scopusRatingDao;
  private final CasWarningDao casWarningDao;
  private final VenuePublicationStatsDao venuePublicationStatsDao;
  private final VenueMeshDao venueMeshDao;
  private final VenueRelationDao venueRelationDao;
  private final VenueIndexingHistoryDao venueIndexingHistoryDao;
  private final VenueReadModelMapper venueReadModelMapper;
  private final ObjectMapper objectMapper;

  @Override
  public boolean existsById(Long id) {
    return venueDao.existsById(id);
  }

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

  /// 批量查询 Venue 详情用于对比，包含基础信息和最新评级。
  ///
  /// 不包含 MeSH/关联关系/索引历史（对比场景无需这些数据）。
  /// 不存在的 ID 会被静默忽略，返回结果按输入 ID 顺序排列。
  ///
  /// @param ids 期刊主键 ID 列表
  /// @return Venue 详情读模型列表
  @Override
  public List<VenueDetailReadModel> findVenuesForCompare(List<Long> ids) {
    List<VenueEntity> entities = venueDao.findByIdIn(ids);
    if (entities.isEmpty()) {
      return List.of();
    }

    List<Long> foundIds = entities.stream().map(VenueEntity::getId).toList();
    Map<Long, JcrRatingEntity> latestJcr = findLatestJcrByVenueIds(foundIds);
    Map<Long, CasRatingEntity> latestCas = findLatestCasByVenueIds(foundIds);
    Map<Long, ScopusRatingEntity> latestScopus = findLatestScopusByVenueIds(foundIds);
    Map<Long, CasWarningEntity> latestWarnings = findLatestWarningByVenueIds(foundIds);

    // 按输入 ID 顺序组装结果，忽略不存在的 ID
    Map<Long, VenueEntity> entityMap =
        entities.stream().collect(Collectors.toMap(VenueEntity::getId, e -> e));

    return ids.stream()
        .filter(entityMap::containsKey)
        .map(
            id -> {
              VenueEntity entity = entityMap.get(id);
              VenueLatestRating latestRating =
                  buildLatestRatingFromBatch(
                      id, latestJcr, latestCas, latestScopus, latestWarnings);
              return venueReadModelMapper.toDetailReadModel(
                  entity, latestRating, List.of(), List.of(), List.of());
            })
        .toList();
  }

  /// 从批量加载的评级数据中构建单个 Venue 的最新评级摘要。
  ///
  /// @param venueId 期刊 ID
  /// @param jcrMap JCR 评级批量数据
  /// @param casMap CAS 评级批量数据
  /// @param scopusMap Scopus 评级批量数据
  /// @param warningMap CAS 预警批量数据
  /// @return 评级摘要（全部数据源无数据时返回 null）
  private VenueLatestRating buildLatestRatingFromBatch(
      Long venueId,
      Map<Long, JcrRatingEntity> jcrMap,
      Map<Long, CasRatingEntity> casMap,
      Map<Long, ScopusRatingEntity> scopusMap,
      Map<Long, CasWarningEntity> warningMap) {
    var jcr = jcrMap.get(venueId);
    var cas = casMap.get(venueId);
    var scopus = scopusMap.get(venueId);
    var warning = warningMap.get(venueId);

    if (jcr == null && cas == null && scopus == null && warning == null) {
      return null;
    }
    return venueReadModelMapper.toLatestRating(jcr, cas, scopus, warning);
  }

  /// 批量查找最新 CAS 预警，按 venueId 分组取最新发布年份。
  private Map<Long, CasWarningEntity> findLatestWarningByVenueIds(List<Long> venueIds) {
    return findLatestByVenueIds(
        venueIds,
        casWarningDao::findByVenueIdIn,
        CasWarningEntity::getVenueId,
        (a, b) -> a.getPublishedYear() >= b.getPublishedYear() ? a : b);
  }

  /// 查询 Venue 评级历史，聚合 JCR/CAS/Scopus 评级和 CAS 预警的全部年份记录。
  ///
  /// 各列表按年份降序排列。
  ///
  /// @param venueId 期刊主键 ID
  /// @return 评级历史读模型
  @Override
  public VenueRatingHistoryReadModel findVenueRatingHistory(Long venueId) {
    var jcrRecords =
        jcrRatingDao.findByVenueId(venueId).stream()
            .sorted(Comparator.comparing(JcrRatingEntity::getYear).reversed())
            .map(venueReadModelMapper::toJcrRecord)
            .toList();

    var casRecords =
        casRatingDao.findByVenueId(venueId).stream()
            .sorted(Comparator.comparing(CasRatingEntity::getYear).reversed())
            .map(venueReadModelMapper::toCasRecord)
            .toList();

    var scopusRecords =
        scopusRatingDao.findByVenueId(venueId).stream()
            .sorted(Comparator.comparing(ScopusRatingEntity::getYear).reversed())
            .map(venueReadModelMapper::toScopusRecord)
            .toList();

    var warningRecords =
        casWarningDao.findByVenueId(venueId).stream()
            .sorted(Comparator.comparing(CasWarningEntity::getPublishedYear).reversed())
            .map(venueReadModelMapper::toWarningRecord)
            .toList();

    return VenueRatingHistoryReadModel.builder()
        .jcr(jcrRecords)
        .cas(casRecords)
        .scopus(scopusRecords)
        .warnings(warningRecords)
        .build();
  }

  /// 查询 Venue 年度发文统计，按年份降序排列。
  ///
  /// @param venueId 期刊主键 ID
  /// @return 发文统计读模型
  @Override
  public VenueStatsReadModel findVenueStats(Long venueId) {
    var yearStats =
        venuePublicationStatsDao.findByVenueId(venueId).stream()
            .sorted(Comparator.comparing(VenuePublicationStatsEntity::getYear).reversed())
            .map(venueReadModelMapper::toYearStats)
            .toList();
    return new VenueStatsReadModel(yearStats);
  }

  /// 查询 Venue 实例（卷/期）分页列表，包含关联文献数量。
  ///
  /// @param venueId 期刊主键 ID
  /// @param paging 已验证的分页参数
  /// @param year 出版年份过滤（可为 null）
  /// @return Venue 实例分页结果
  @Override
  public PageResult<VenueInstanceSummaryReadModel> findVenueInstances(
      Long venueId, PagingParams paging, Integer year) {
    Pageable pageable = PageRequest.of(paging.page() - 1, paging.pageSize());
    Page<Object[]> resultPage =
        venueInstanceDao.findVenueInstancesWithPubCount(venueId, year, pageable);

    List<VenueInstanceSummaryReadModel> items =
        resultPage.getContent().stream().map(this::mapRowToInstanceReadModel).toList();

    return PageResult.of(items, paging.page(), paging.pageSize(), resultPage.getTotalElements());
  }

  /// 将 native query 的 Object[] 行映射为 VenueInstanceSummaryReadModel。
  ///
  /// 列索引对应 `findVenueInstancesWithPubCount` SELECT 子句：
  ///
  /// - 0: vi.id, 1: vi.volume, 2: vi.issue
  /// - 3: vi.publication_year, 4: vi.publication_month, 5: vi.publication_day
  /// - 6: pub_count
  private VenueInstanceSummaryReadModel mapRowToInstanceReadModel(Object[] row) {
    return new VenueInstanceSummaryReadModel(
        ((Number) row[0]).longValue(),
        (String) row[1],
        (String) row[2],
        row[3] != null ? ((Number) row[3]).intValue() : null,
        row[4] != null ? ((Number) row[4]).intValue() : null,
        row[5] != null ? ((Number) row[5]).intValue() : null,
        ((Number) row[6]).longValue());
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

  /// 查找某期刊最新的 CAS 预警记录。
  ///
  /// @param venueId 期刊 ID
  /// @return 最新预警记录，无预警时返回 null
  private CasWarningEntity findLatestWarning(Long venueId) {
    return casWarningDao.findLatestByVenueId(venueId).orElse(null);
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
