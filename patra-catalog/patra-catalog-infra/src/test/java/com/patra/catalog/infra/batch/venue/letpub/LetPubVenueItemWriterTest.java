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
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
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
}
