package com.patra.catalog.infra.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.entity.VenuePublicationStats;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.repository.VenueSupplementRepository;
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

/// VenueInitializeItemWriter 单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock VenueRepository 和 VenueSupplementRepository 依赖
/// - 测试乐观插入策略：正常插入和异常降级处理
/// - 测试 Chunk 内去重逻辑
/// - 测试年度指标写入逻辑
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueInitializeItemWriter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueInitializeItemWriterTest {

  @Mock private VenueRepository venueRepository;
  @Mock private VenueSupplementRepository venueSupplementRepository;

  @Captor private ArgumentCaptor<List<VenueAggregate>> aggregatesCaptor;

  private VenueInitializeItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new VenueInitializeItemWriter(venueRepository, venueSupplementRepository);
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
      verify(venueSupplementRepository, never()).replaceYearlyMetricsBatch(anyMap());
    }

    @Test
    @DisplayName("正常数据 - 应该插入聚合根和年度指标")
    void write_normalData_shouldInsertAggregatesAndMetrics() throws Exception {
      // Given
      VenueParseResult result1 = createParseResult("S1", "1111-1111", true);
      VenueParseResult result2 = createParseResult("S2", "2222-2222", true);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2);

      doNothing().when(venueRepository).insertAll(anyList());
      doNothing().when(venueSupplementRepository).replaceYearlyMetricsBatch(anyMap());

      // When
      writer.write(chunk);

      // Then: 验证聚合根插入
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      List<VenueAggregate> inserted = aggregatesCaptor.getValue();
      assertThatListContainsOpenalexIds(inserted, "S1", "S2");

      // Then: 验证年度指标插入
      verify(venueSupplementRepository, times(1)).replaceYearlyMetricsBatch(anyMap());
    }

    @Test
    @DisplayName("无年度指标 - 不应调用 supplementRepository")
    void write_noMetrics_shouldNotCallSupplementRepository() throws Exception {
      // Given
      VenueParseResult result1 = createParseResult("S1", "1111-1111", false);
      VenueParseResult result2 = createParseResult("S2", "2222-2222", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2);

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then
      verify(venueRepository, times(1)).insertAll(anyList());
      verify(venueSupplementRepository, never()).replaceYearlyMetricsBatch(anyMap());
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
      VenueParseResult result1 = createParseResult("S1", "1111-1111", false);
      VenueParseResult result2 = createParseResult("S2", "1111-1111", false); // 重复的 ISSN-L
      VenueParseResult result3 = createParseResult("S3", "3333-3333", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2, result3);

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
      VenueParseResult result1 = createParseResultWithoutIssnL("S1");
      VenueParseResult result2 = createParseResultWithoutIssnL("S2");
      VenueParseResult result3 = createParseResult("S3", "3333-3333", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2, result3);

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
      VenueParseResult result1 = createParseResult("S1", "1111-1111", false);
      VenueParseResult result2 = createParseResult("S2", "2222-2222", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2);

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
      VenueParseResult result1 = createParseResult("S1", "1111-1111", false);
      VenueParseResult result2 = createParseResult("S2", "2222-2222", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2);

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
      VenueParseResult result = createParseResult("S1", "1111-1111", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result);

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
      VenueParseResult result = createParseResult("S1", "1111-1111", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result);

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

  private VenueParseResult createParseResult(String openalexId, String issnL, boolean withMetrics) {
    VenueAggregate venue =
        VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
    venue.withIssnL(issnL);

    List<VenuePublicationStats> metrics =
        withMetrics ? List.of(VenuePublicationStats.create(2024, 100, 500)) : List.of();

    return new VenueParseResult(venue, metrics);
  }

  private VenueParseResult createParseResultWithoutIssnL(String openalexId) {
    VenueAggregate venue =
        VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
    return new VenueParseResult(venue, List.of());
  }

  private void assertThatListContainsOpenalexIds(List<VenueAggregate> list, String... expectedIds) {
    List<String> actualIds = list.stream().map(VenueAggregate::getOpenalexId).toList();
    assertThat(actualIds).containsExactlyInAnyOrder(expectedIds);
  }
}
