package com.patra.catalog.infra.batch.venue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.port.VenueRepository;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/// VenueImportItemWriter 单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock VenueRepository 依赖
/// - 测试乐观插入策略：正常插入和异常降级处理
/// - 测试 Chunk 内去重逻辑
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueImportItemWriter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueImportItemWriterTest {

  @Mock private VenueRepository venueRepository;

  @Captor private ArgumentCaptor<List<VenueAggregate>> aggregatesCaptor;

  private VenueImportItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new VenueImportItemWriter(venueRepository);
  }

  // ========== 正常写入测试 ==========

  @Nested
  @DisplayName("正常写入测试")
  class NormalWriteTests {

    @Test
    @DisplayName("空 Chunk - 不应调用 Repository")
    void write_emptyChunk_shouldNotCallRepository() throws Exception {
      // When
      writer.write(Chunk.of());

      // Then
      verify(venueRepository, never()).insertAll(anyList());
    }

    @Test
    @DisplayName("正常数据 - 应该直接插入")
    void write_normalData_shouldInsertDirectly() throws Exception {
      // Given
      VenueAggregate venue1 = createVenue("S1", "1111-1111");
      VenueAggregate venue2 = createVenue("S2", "2222-2222");
      Chunk<VenueAggregate> chunk = Chunk.of(venue1, venue2);

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      List<VenueAggregate> inserted = aggregatesCaptor.getValue();
      assertThatListContainsOpenalexIds(inserted, "S1", "S2");
    }
  }

  // ========== Chunk 内去重测试 ==========

  @Nested
  @DisplayName("Chunk 内去重测试")
  class ChunkDeduplicationTests {

    @Test
    @DisplayName("Chunk 内 ISSN-L 重复 - 应该跳过重复记录")
    void write_duplicateIssnLInChunk_shouldSkipDuplicates() throws Exception {
      // Given: 两条记录有相同的 ISSN-L
      VenueAggregate venue1 = createVenue("S1", "1111-1111");
      VenueAggregate venue2 = createVenue("S2", "1111-1111"); // 重复的 ISSN-L
      VenueAggregate venue3 = createVenue("S3", "3333-3333");
      Chunk<VenueAggregate> chunk = Chunk.of(venue1, venue2, venue3);

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 只插入 2 条（S1 和 S3）
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      List<VenueAggregate> inserted = aggregatesCaptor.getValue();
      assertThatListContainsOpenalexIds(inserted, "S1", "S3");
    }

    @Test
    @DisplayName("ISSN-L 为 null - 不应被去重")
    void write_nullIssnL_shouldNotBeAffectedByDeduplication() throws Exception {
      // Given: 两条记录都没有 ISSN-L
      VenueAggregate venue1 = createVenueWithoutIssnL("S1");
      VenueAggregate venue2 = createVenueWithoutIssnL("S2");
      VenueAggregate venue3 = createVenue("S3", "3333-3333");
      Chunk<VenueAggregate> chunk = Chunk.of(venue1, venue2, venue3);

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 所有 3 条都应该插入
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      List<VenueAggregate> inserted = aggregatesCaptor.getValue();
      assertThatListContainsOpenalexIds(inserted, "S1", "S2", "S3");
    }
  }

  // ========== 异常降级处理测试 ==========

  @Nested
  @DisplayName("异常降级处理测试")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("DuplicateKeyException - 应该触发降级处理")
    void write_duplicateKeyException_shouldTriggerFallback() throws Exception {
      // Given
      VenueAggregate venue1 = createVenue("S1", "1111-1111");
      VenueAggregate venue2 = createVenue("S2", "2222-2222");
      Chunk<VenueAggregate> chunk = Chunk.of(venue1, venue2);

      // 第一次插入抛出 DuplicateKeyException
      doThrow(new DuplicateKeyException("Duplicate entry '1111-1111' for key 'uk_issn_l'"))
          .doNothing()
          .when(venueRepository)
          .insertAll(anyList());

      // 模拟 findExistingIssnLs 返回已存在的 ISSN-L
      when(venueRepository.findExistingIssnLs(any())).thenReturn(Set.of("1111-1111"));

      // When
      writer.write(chunk);

      // Then: 应该调用两次 insertAll（第一次失败，第二次成功）
      verify(venueRepository, times(2)).insertAll(aggregatesCaptor.capture());

      // 第二次只插入不重复的记录
      List<VenueAggregate> secondInsert = aggregatesCaptor.getAllValues().get(1);
      assertThatListContainsOpenalexIds(secondInsert, "S2");
    }

    @Test
    @DisplayName("DataIntegrityViolationException（唯一键冲突）- 应该触发降级处理")
    void write_dataIntegrityViolationWithDuplicateEntry_shouldTriggerFallback() throws Exception {
      // Given
      VenueAggregate venue1 = createVenue("S1", "1111-1111");
      VenueAggregate venue2 = createVenue("S2", "2222-2222");
      Chunk<VenueAggregate> chunk = Chunk.of(venue1, venue2);

      // 抛出 DataIntegrityViolationException，消息包含 "Duplicate entry"
      doThrow(
              new DataIntegrityViolationException(
                  "Duplicate entry '1111-1111' for key 'uk_issn_l'"))
          .doNothing()
          .when(venueRepository)
          .insertAll(anyList());

      when(venueRepository.findExistingIssnLs(any())).thenReturn(Set.of("1111-1111"));

      // When
      writer.write(chunk);

      // Then: 应该触发降级处理
      verify(venueRepository, times(2)).insertAll(anyList());
      verify(venueRepository, times(1)).findExistingIssnLs(any());
    }

    @Test
    @DisplayName("DataIntegrityViolationException（非唯一键冲突）- 应该抛出异常")
    void write_dataIntegrityViolationWithOtherError_shouldThrowException() {
      // Given
      VenueAggregate venue = createVenue("S1", "1111-1111");
      Chunk<VenueAggregate> chunk = Chunk.of(venue);

      // 抛出非唯一键冲突的 DataIntegrityViolationException
      doThrow(new DataIntegrityViolationException("Cannot add or update a child row: foreign key"))
          .when(venueRepository)
          .insertAll(anyList());

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> writer.write(chunk))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("foreign key");
    }

    @Test
    @DisplayName("降级处理后全部重复 - 不应再次调用 insertAll")
    void write_allDuplicatesAfterFallback_shouldNotInsertAgain() throws Exception {
      // Given: 只有一条记录，且与数据库重复
      VenueAggregate venue = createVenue("S1", "1111-1111");
      Chunk<VenueAggregate> chunk = Chunk.of(venue);

      doThrow(new DuplicateKeyException("Duplicate entry '1111-1111' for key 'uk_issn_l'"))
          .when(venueRepository)
          .insertAll(anyList());

      when(venueRepository.findExistingIssnLs(any())).thenReturn(Set.of("1111-1111"));

      // When
      writer.write(chunk);

      // Then: insertAll 只被调用一次（第一次失败后，发现全部重复，不再调用）
      verify(venueRepository, times(1)).insertAll(anyList());
    }
  }

  // ========== 辅助方法 ==========

  private VenueAggregate createVenue(String openalexId, String issnL) {
    VenueAggregate venue =
        VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
    venue.withIssnL(issnL);
    return venue;
  }

  private VenueAggregate createVenueWithoutIssnL(String openalexId) {
    return VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
  }

  private void assertThatListContainsOpenalexIds(List<VenueAggregate> list, String... expectedIds) {
    List<String> actualIds = list.stream().map(VenueAggregate::getOpenalexId).toList();
    org.assertj.core.api.Assertions.assertThat(actualIds).containsExactlyInAnyOrder(expectedIds);
  }
}
