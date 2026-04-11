package com.patra.catalog.infra.batch.venue.letpub;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private VenueDao venueDao;

  private LetPubVenueItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new LetPubVenueItemWriter(jcrRatingDao, casRatingDao, venueDao);
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
}
