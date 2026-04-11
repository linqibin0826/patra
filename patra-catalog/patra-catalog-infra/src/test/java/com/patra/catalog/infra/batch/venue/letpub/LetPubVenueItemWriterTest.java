package com.patra.catalog.infra.batch.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.CasWarningDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

/// LetPubVenueItemWriter 单元测试。
///
/// **测试策略**：
///
/// - Mock `JcrRatingDao` / `CasRatingDao` / `VenueDao`，验证 Writer 能正确
///   将 `LetPubEnrichResult` 的三类数据分别写入对应 DAO
/// - 封面对象键的 UPDATE：仅在 `imageObjectKey` 非空时触发
/// - 游离态 `VenueEntity` 的坑（见 Task 7 设计说明）：Writer 必须显式调用
///   `VenueDao.updateImageObjectKey`，不能依赖 JPA dirty check
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("LetPubVenueItemWriter 单元测试")
class LetPubVenueItemWriterTest {

  @Mock private JcrRatingDao jcrRatingDao;
  @Mock private CasRatingDao casRatingDao;
  @Mock private CasWarningDao casWarningDao;
  @Mock private VenueDao venueDao;

  private LetPubVenueItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new LetPubVenueItemWriter(jcrRatingDao, casRatingDao, casWarningDao, venueDao);
  }

  private static CasWarningEntity buildWarning(Long venueId, int year, String label) {
    CasWarningEntity w = new CasWarningEntity();
    w.setVenueId(venueId);
    w.setPublishedYear((short) year);
    w.setEditionLabel(label);
    w.setInWarningList(false);
    return w;
  }

  private static JcrRatingEntity buildJcr(Long venueId, int year) {
    JcrRatingEntity e = new JcrRatingEntity();
    e.setVenueId(venueId);
    e.setYear((short) year);
    return e;
  }

  private static CasRatingEntity buildCas(Long venueId, int year, String edition) {
    CasRatingEntity e = new CasRatingEntity();
    e.setVenueId(venueId);
    e.setYear((short) year);
    e.setEdition(edition);
    return e;
  }

  @Test
  @DisplayName("当 imageObjectKey 非空时应调用 VenueDao.updateImageObjectKey")
  void shouldUpdateVenueImageObjectKeyWhenPresent() throws Exception {
    // Given
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            42L,
            "catalog/venue-cover/42.jpg",
            LetPubEnrichResult.JcrBatch.empty(),
            LetPubEnrichResult.CasBatch.empty());

    // When
    writer.write(Chunk.of(result));

    // Then
    verify(venueDao).updateImageObjectKey(eq(42L), eq("catalog/venue-cover/42.jpg"));
  }

  @Test
  @DisplayName("当 imageObjectKey 为空时不应触达 VenueDao（避免把已有对象键清空）")
  void shouldNotUpdateVenueWhenImageObjectKeyIsNull() throws Exception {
    // Given
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            43L, null, LetPubEnrichResult.JcrBatch.empty(), LetPubEnrichResult.CasBatch.empty());

    // When
    writer.write(Chunk.of(result));

    // Then — 使用 verifyNoInteractions 而非 never(anyString())，
    // 因为 Mockito 5 中 anyString() 不匹配 null 参数，会导致 false GREEN
    verifyNoInteractions(venueDao);
  }

  @Test
  @DisplayName("CAS 预警批次非空时应调用 CasWarningDao.saveAll")
  void shouldPersistCasWarningsWhenPresent() throws Exception {
    // Given
    List<CasWarningEntity> warnings =
        List.of(buildWarning(100L, 2026, "新锐学术版"), buildWarning(100L, 2025, "2025版"));
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            100L,
            null,
            LetPubEnrichResult.JcrBatch.empty(),
            LetPubEnrichResult.CasBatch.of(List.of(), warnings));
    when(casWarningDao.findKeysByVenueId(100L)).thenReturn(Set.of());

    // When
    writer.write(Chunk.of(result));

    // Then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CasWarningEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(casWarningDao).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
  }

  @Test
  @DisplayName("CAS 预警批次为空时不应调用 CasWarningDao")
  void shouldNotTouchCasWarningDaoWhenBatchEmpty() throws Exception {
    // Given
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            101L, null, LetPubEnrichResult.JcrBatch.empty(), LetPubEnrichResult.CasBatch.empty());

    // When
    writer.write(Chunk.of(result));

    // Then
    verifyNoInteractions(casWarningDao);
  }

  @Test
  @DisplayName("应过滤掉已存在的 (year:editionLabel) 预警记录（幂等）")
  void shouldFilterExistingCasWarnings() throws Exception {
    // Given — DB 已有 2026 + 2025 两条；新批次带 2026 + 2024（2026 应被过滤）
    List<CasWarningEntity> incoming =
        List.of(buildWarning(102L, 2026, "新锐学术版"), buildWarning(102L, 2024, "2024版"));
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            102L,
            null,
            LetPubEnrichResult.JcrBatch.empty(),
            LetPubEnrichResult.CasBatch.of(List.of(), incoming));
    when(casWarningDao.findKeysByVenueId(102L)).thenReturn(Set.of("2026:新锐学术版", "2025:2025版"));

    // When
    writer.write(Chunk.of(result));

    // Then — 只应保存 2024 那一条
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CasWarningEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(casWarningDao).saveAll(captor.capture());
    assertThat(captor.getValue())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getPublishedYear()).isEqualTo((short) 2024);
              assertThat(e.getEditionLabel()).isEqualTo("2024版");
            });
  }

  @Test
  @DisplayName("所有预警记录都已存在时不应调用 saveAll")
  void shouldSkipSaveWhenAllCasWarningsAlreadyExist() throws Exception {
    // Given
    List<CasWarningEntity> incoming = List.of(buildWarning(103L, 2026, "新锐学术版"));
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            103L,
            null,
            LetPubEnrichResult.JcrBatch.empty(),
            LetPubEnrichResult.CasBatch.of(List.of(), incoming));
    when(casWarningDao.findKeysByVenueId(103L)).thenReturn(Set.of("2026:新锐学术版"));

    // When
    writer.write(Chunk.of(result));

    // Then — 唯一一条已存在，全部过滤，saveAll 不应被调用
    verify(casWarningDao).findKeysByVenueId(103L);
    verify(casWarningDao, never())
        .saveAll(org.mockito.ArgumentMatchers.<Iterable<CasWarningEntity>>any());
  }

  /// **关键回归场景**：JCR 跨年过滤（Reader/Mapper 维度不对齐）。
  ///
  /// 复现 Writer Javadoc 描述的真实 UK 冲突：Venue 此前以 `targetYear=2024` 运行
  /// 已持久化 2020-2024；本轮以 `targetYear=2025` 再跑，Reader 认为它缺 2025
  /// 而捞它出来，Mapper 从 LetPub 拿到完整 6 年趋势生成 2020-2025 共 6 条。
  /// 若 Writer 不过滤而直接 saveAll，`INSERT (venueId, 2020)` 会撞 UK `(venue_id, year)`
  /// 导致整个 chunk 回滚，连带 2025 也没写入——形成每次尝试每次失败的死循环。
  ///
  /// 过滤后 saveAll 只收到 2025 这唯一的新年行。
  ///
  /// **若该测试失败**：说明有人删除了 filterNewJcrRatings 或改坏了判断逻辑，
  /// 必须回到 Javadoc 的场景说明重新评估，绝不能以"Reader 已经守卫"为由去掉去重。
  @Test
  @DisplayName("JCR 跨年：已存在 2020-2024 时应只写入 2025（防 UK 冲突）")
  void shouldFilterJcrRatingsAcrossYearsToAvoidUniqueKeyCollision() throws Exception {
    // Given — Mapper 生成 2020-2025 共 6 年；DB 已有 2020-2024（来自上轮运行）
    Long venueId = 200L;
    List<JcrRatingEntity> incoming =
        List.of(
            buildJcr(venueId, 2020),
            buildJcr(venueId, 2021),
            buildJcr(venueId, 2022),
            buildJcr(venueId, 2023),
            buildJcr(venueId, 2024),
            buildJcr(venueId, 2025));
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            venueId,
            null,
            LetPubEnrichResult.JcrBatch.of(incoming),
            LetPubEnrichResult.CasBatch.empty());
    when(jcrRatingDao.findYearsByVenueId(venueId))
        .thenReturn(Set.of((short) 2020, (short) 2021, (short) 2022, (short) 2023, (short) 2024));

    // When
    writer.write(Chunk.of(result));

    // Then — 只应保存 2025 这一行
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<JcrRatingEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(jcrRatingDao).saveAll(captor.capture());
    assertThat(captor.getValue())
        .singleElement()
        .satisfies(e -> assertThat(e.getYear()).isEqualTo((short) 2025));
  }

  /// **关键回归场景**：CAS 跨版本过滤（Reader/Mapper 维度不对齐）。
  ///
  /// Mapper 从 LetPub 拿到多个 CAS 版本（升级版/新锐版/旧升级版），但 DB 中已有
  /// 其中部分 `(year, edition)` 组合。若不过滤直接 saveAll，重复 key 会撞
  /// UK `(venue_id, year, edition)` 致整个 chunk 回滚。
  ///
  /// 与 JCR 过滤唯一区别是组合键维度（year + edition vs 仅 year），失败后果一致。
  @Test
  @DisplayName("CAS 跨版本：已存在 (2025,升级版) 时应只写入 (2026,新锐版)")
  void shouldFilterCasRatingsAcrossEditionsToAvoidUniqueKeyCollision() throws Exception {
    // Given — Mapper 生成两个版本；DB 已有 2025 升级版
    Long venueId = 201L;
    List<CasRatingEntity> incoming =
        List.of(buildCas(venueId, 2025, "升级版"), buildCas(venueId, 2026, "新锐版"));
    LetPubEnrichResult result =
        LetPubEnrichResult.of(
            venueId,
            null,
            LetPubEnrichResult.JcrBatch.empty(),
            LetPubEnrichResult.CasBatch.of(incoming, List.of()));
    when(casRatingDao.findKeysByVenueId(venueId)).thenReturn(Set.of("2025:升级版"));

    // When
    writer.write(Chunk.of(result));

    // Then — 只应保存 2026 新锐版这一行
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CasRatingEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(casRatingDao).saveAll(captor.capture());
    assertThat(captor.getValue())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getYear()).isEqualTo((short) 2026);
              assertThat(e.getEdition()).isEqualTo("新锐版");
            });
  }
}
