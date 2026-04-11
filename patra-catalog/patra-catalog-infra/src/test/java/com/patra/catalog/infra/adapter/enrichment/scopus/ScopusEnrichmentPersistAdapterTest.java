package com.patra.catalog.infra.adapter.enrichment.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort.PersistStats;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData.YearlyMetric;
import com.patra.catalog.infra.batch.venue.scopus.ScopusDataMapper;
import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("ScopusEnrichmentPersistAdapter 单元测试")
class ScopusEnrichmentPersistAdapterTest {

  @Mock ScopusRatingDao scopusRatingDao;

  ScopusEnrichmentPersistAdapter adapter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    adapter = new ScopusEnrichmentPersistAdapter(new ScopusDataMapper(), scopusRatingDao);
  }

  @Test
  @DisplayName("新 venue - 所有年份都被插入")
  void persist_newVenue_insertsAllYears() {
    ScopusVenueData data = dataWith3YearsOfMetrics();
    when(scopusRatingDao.findYearsByVenueId(100L)).thenReturn(Set.of());

    PersistStats stats = adapter.persist(100L, data);

    assertThat(stats.scopusRatingsInserted()).isEqualTo(3);
    verify(scopusRatingDao).saveAll(argThat(list -> ((List<?>) list).size() == 3));
  }

  @Test
  @DisplayName("部分年份已存在 - 只插入新年份")
  void persist_filtersOutExistingYears() {
    ScopusVenueData data = dataWith3YearsOfMetrics(); // 2023, 2024, 2025
    when(scopusRatingDao.findYearsByVenueId(100L)).thenReturn(Set.of((short) 2023, (short) 2024));

    PersistStats stats = adapter.persist(100L, data);

    // 3 - 2 已存在 = 1 新行
    assertThat(stats.scopusRatingsInserted()).isEqualTo(1);
    verify(scopusRatingDao).saveAll(argThat(list -> ((List<?>) list).size() == 1));
  }

  @Test
  @DisplayName("全部已存在 - 不调用 saveAll")
  void persist_allYearsExisting_savesNothing() {
    ScopusVenueData data = dataWith3YearsOfMetrics();
    when(scopusRatingDao.findYearsByVenueId(100L))
        .thenReturn(Set.of((short) 2023, (short) 2024, (short) 2025));

    PersistStats stats = adapter.persist(100L, data);

    assertThat(stats.scopusRatingsInserted()).isZero();
    verify(scopusRatingDao, never()).saveAll(any());
  }

  // ========== Fixture 构造器 ==========

  /// 构造含 3 年（2023/2024/2025）指标的测试数据。
  /// 最新年 = 2025，会触发 Mapper 的 isFirst 分支填充 SJR/SNIP 等字段；
  /// 2023/2024 只填充 CiteScore 基础字段。
  private ScopusVenueData dataWith3YearsOfMetrics() {
    return ScopusVenueData.builder()
        .scopusSourceId("21100")
        .title("Test Journal")
        .citeScoreTracker(5.0)
        .sjr(2.5)
        .snip(1.8)
        .subjectArea("Computer Science")
        .quartile("Q1")
        .percentile(95.0)
        .yearlyMetrics(
            List.of(
                new YearlyMetric(2025, 6.0, 100, 600, 85.0),
                new YearlyMetric(2024, 5.5, 95, 523, 82.0),
                new YearlyMetric(2023, 5.0, 90, 450, 80.0)))
        .build();
  }
}
