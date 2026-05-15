package dev.linqibin.patra.catalog.infra.adapter.enrichment.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort.PersistStats;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
import dev.linqibin.patra.catalog.infra.persistence.dao.CasRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.CasWarningDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.JcrRatingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueDao;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/// `LetPubEnrichmentPersistAdapter` 单元测试。
///
/// 测试策略：mock 4 个 DAO，聚焦"去重逻辑正确 + 封面条件分支正确"。
/// fixture 使用 `"YYYY-YYYY"` 格式的 IF 趋势 key，与 `LetPubDataMapper` 的
/// `YEAR_RANGE_PATTERN` 解析方式保持一致。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubEnrichmentPersistAdapter 单元测试")
class LetPubEnrichmentPersistAdapterTest {

  @Mock JcrRatingDao jcrDao;
  @Mock CasRatingDao casDao;
  @Mock CasWarningDao warnDao;
  @Mock VenueDao venueDao;

  LetPubEnrichmentPersistAdapter adapter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    adapter = new LetPubEnrichmentPersistAdapter(jcrDao, casDao, warnDao, venueDao);
  }

  @Test
  @DisplayName("去重过滤 - 已有年份的 JCR 行不会重复插入")
  void persist_filtersOutExistingYears() {
    LetPubVenueData data = dataWith10YearTrend();
    when(jcrDao.findYearsByVenueId(100L))
        .thenReturn(Set.of((short) 2020, (short) 2021, (short) 2022));
    when(casDao.findKeysByVenueId(100L)).thenReturn(Set.of());
    when(warnDao.findKeysByVenueId(100L)).thenReturn(Set.of());

    PersistStats stats = adapter.persist(100L, data, null);

    // 10 年趋势 - 3 年已存在 = 7 年应插入
    assertThat(stats.jcrInserted()).isEqualTo(7);
    verify(jcrDao).saveAll(argThat(list -> ((List<?>) list).size() == 7));
    verify(venueDao, never()).updateImageObjectKey(anyLong(), any());
  }

  @Test
  @DisplayName("封面更新 - coverObjectKey 非 null 时 UPDATE 成功")
  void persist_updatesCoverImageWhenKeyProvided() {
    LetPubVenueData data = dataSingleYear();
    when(jcrDao.findYearsByVenueId(anyLong())).thenReturn(Set.of());
    when(casDao.findKeysByVenueId(anyLong())).thenReturn(Set.of());
    when(warnDao.findKeysByVenueId(anyLong())).thenReturn(Set.of());
    when(venueDao.updateImageObjectKey(100L, "catalog/venue-cover/100.jpg")).thenReturn(1);

    PersistStats stats = adapter.persist(100L, data, "catalog/venue-cover/100.jpg");

    assertThat(stats.coverUpdated()).isTrue();
    verify(venueDao).updateImageObjectKey(100L, "catalog/venue-cover/100.jpg");
  }

  @Test
  @DisplayName("全部已存在 - 不调用 saveAll")
  void persist_allYearsExisting_savesNothing() {
    LetPubVenueData data = dataWith10YearTrend();
    // Set 所有 10 个年份（注意：YEAR_RANGE_PATTERN 提取后半年份，"2015-2016" → 2016）
    Set<Short> allYears =
        Set.of(
            (short) 2016,
            (short) 2017,
            (short) 2018,
            (short) 2019,
            (short) 2020,
            (short) 2021,
            (short) 2022,
            (short) 2023,
            (short) 2024,
            (short) 2025);
    when(jcrDao.findYearsByVenueId(100L)).thenReturn(allYears);
    when(casDao.findKeysByVenueId(100L)).thenReturn(Set.of());
    when(warnDao.findKeysByVenueId(100L)).thenReturn(Set.of());

    PersistStats stats = adapter.persist(100L, data, null);

    assertThat(stats.jcrInserted()).isZero();
    verify(jcrDao, never()).saveAll(any());
  }

  // ========== Fixture 构造器（内联辅助方法） ==========

  /// 构造含 10 年 IF 趋势的测试数据（2016-2025）。
  ///
  /// trend key 格式为 `"YYYY-YYYY"`（如 `"2015-2016"`），与 `LetPubDataMapper.extractLaterYear`
  /// 使用的 `YEAR_RANGE_PATTERN` 保持一致，后半年份被提取为 JCR 行的 year 字段。
  private LetPubVenueData dataWith10YearTrend() {
    Map<String, Double> trend =
        Map.of(
            "2015-2016",
            40.0,
            "2016-2017",
            41.0,
            "2017-2018",
            42.0,
            "2018-2019",
            43.0,
            "2019-2020",
            44.0,
            "2020-2021",
            45.0,
            "2021-2022",
            46.0,
            "2022-2023",
            47.0,
            "2023-2024",
            48.0,
            "2024-2025",
            49.0);
    return LetPubVenueData.of(
        LetPubVenueData.BasicInfo.builder().letPubJournalId("1").letPubName("Test Journal").build(),
        LetPubVenueData.JcrMetrics.builder().impactFactorTrend(trend).build(),
        LetPubVenueData.CasData.of(List.of(), List.of()),
        null);
  }

  /// 构造仅含单年（2024-2025，提取年份 2025）IF 的测试数据。
  ///
  /// 用于验证封面更新的测试，不关心 JCR 行数。
  private LetPubVenueData dataSingleYear() {
    return LetPubVenueData.of(
        LetPubVenueData.BasicInfo.builder().letPubJournalId("1").letPubName("Test Journal").build(),
        LetPubVenueData.JcrMetrics.builder().impactFactorTrend(Map.of("2024-2025", 50.0)).build(),
        LetPubVenueData.CasData.of(List.of(), List.of()),
        null);
  }
}
