package com.patra.catalog.infra.adapter.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.DictionaryType;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.common.SourceStandard;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.port.registry.DictionaryResolverPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import java.util.List;
import java.util.Map;
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
/// - 单元测试：Mock VenueRepository 依赖
/// - 测试乐观插入策略：正常插入和异常降级处理
/// - 测试 Chunk 内去重逻辑
/// - 测试年度指标写入逻辑
///
/// **DDD 嵌入式值对象设计**：
///
/// 聚合根（VenueAggregate）已包含所有嵌入式值对象，不再需要分别保存：
/// - publicationProfile → cat_venue.publication_profile (JSON)
/// - citationMetrics → cat_venue.citation_metrics (JSON)
/// - openAccess → cat_venue.open_access (JSON)
/// - affiliatedSocieties → cat_venue.affiliated_societies (JSON)
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueInitializeItemWriter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueInitializeItemWriterTest {

  @Mock private VenueRepository venueRepository;

  @Mock private DictionaryResolverPort dictionaryResolverPort;

  @Captor private ArgumentCaptor<List<VenueAggregate>> aggregatesCaptor;

  private VenueInitializeItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new VenueInitializeItemWriter(venueRepository, dictionaryResolverPort);
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
      verify(venueRepository, never()).replaceYearlyMetricsBatch(any());
    }

    @Test
    @DisplayName("正常数据 - 应该插入聚合根和年度指标")
    void write_normalData_shouldInsertAggregatesAndMetrics() throws Exception {
      // Given
      VenueParseResult result1 = createParseResult("S1", "1111-1111", true);
      VenueParseResult result2 = createParseResult("S2", "2222-2222", true);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2);

      // 模拟 insertAll 后的 ID 回填（真实场景由应用层预分配雪花 ID）
      doAnswer(
              invocation -> {
                List<VenueAggregate> aggregates = invocation.getArgument(0);
                long idCounter = 100L;
                for (VenueAggregate aggregate : aggregates) {
                  aggregate.assignId(VenueId.of(idCounter++));
                }
                return null;
              })
          .when(venueRepository)
          .insertAll(anyList());
      doNothing().when(venueRepository).replaceYearlyMetricsBatch(any());

      // When
      writer.write(chunk);

      // Then: 验证聚合根插入
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      List<VenueAggregate> inserted = aggregatesCaptor.getValue();
      assertThatListContainsOpenalexIds(inserted, "S1", "S2");

      // Then: 验证年度指标插入
      verify(venueRepository, times(1)).replaceYearlyMetricsBatch(any());
    }

    @Test
    @DisplayName("无年度指标 - 不应调用 replaceYearlyMetricsBatch")
    void write_noMetrics_shouldNotCallReplaceYearlyMetricsBatch() throws Exception {
      // Given
      VenueParseResult result1 = createParseResult("S1", "1111-1111", false);
      VenueParseResult result2 = createParseResult("S2", "2222-2222", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2);

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then
      verify(venueRepository, times(1)).insertAll(anyList());
      verify(venueRepository, never()).replaceYearlyMetricsBatch(any());
    }
  }

  // ========== Chunk 内去重测试 ==========

  @Nested
  @DisplayName("Chunk 内去重测试")
  class ChunkDeduplicationTests {

    @Test
    @DisplayName("Chunk 内 ISSN-L 重复 - 应该使用后者覆盖")
    void write_duplicateIssnLInChunk_shouldUseLatest() throws Exception {
      // Given: 两条记录有相同的 ISSN-L
      VenueParseResult result1 = createParseResult("S1", "1111-1111", false);
      VenueParseResult result2 = createParseResult("S2", "1111-1111", false); // 重复的 ISSN-L
      VenueParseResult result3 = createParseResult("S3", "3333-3333", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2, result3);

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 只插入 2 条（S2 覆盖 S1，保留 S3）
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      List<VenueAggregate> inserted = aggregatesCaptor.getValue();
      assertThatListContainsOpenalexIds(inserted, "S2", "S3");
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

  // ========== 国家编码标准化测试 ==========

  @Nested
  @DisplayName("国家编码标准化测试")
  class CountryCodeNormalizationTests {

    @Test
    @DisplayName("有效国家编码 - 应该通过验证保持不变")
    void write_validCountryCode_shouldKeepOriginal() throws Exception {
      // Given: 创建带有有效国家编码的记录
      VenueParseResult result = createParseResultWithCountryCode("S1", "1111-1111", "GB");
      Chunk<VenueParseResult> chunk = Chunk.of(result);

      // Mock: 字典解析返回有效映射（ISO_3166_1_ALPHA2 标准验证）
      when(dictionaryResolverPort.resolve(
              DictionaryType.COUNTRY, SourceStandard.ISO_3166_1_ALPHA2, Set.of("GB")))
          .thenReturn(Map.of("GB", "GB"));

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 验证国家编码保持不变
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      VenueAggregate inserted = aggregatesCaptor.getValue().get(0);
      assertThat(inserted.getPublicationProfile()).isNotNull();
      assertThat(inserted.getPublicationProfile().countryCode()).isEqualTo("GB");
    }

    @Test
    @DisplayName("无效国家编码 - 应该被置为 null")
    void write_invalidCountryCode_shouldBeSetToNull() throws Exception {
      // Given: 创建带有无效国家编码的记录
      VenueParseResult result = createParseResultWithCountryCode("S1", "1111-1111", "XX");
      Chunk<VenueParseResult> chunk = Chunk.of(result);

      // Mock: 字典解析返回空映射（表示 XX 不是有效的 ISO 国家编码）
      when(dictionaryResolverPort.resolve(
              DictionaryType.COUNTRY, SourceStandard.ISO_3166_1_ALPHA2, Set.of("XX")))
          .thenReturn(Map.of());

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 验证国家编码被置为 null
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      VenueAggregate inserted = aggregatesCaptor.getValue().get(0);
      assertThat(inserted.getPublicationProfile()).isNotNull();
      assertThat(inserted.getPublicationProfile().countryCode()).isNull();
    }

    @Test
    @DisplayName("混合有效和无效国家编码 - 批量处理应正确")
    void write_mixedCountryCodes_shouldProcessCorrectly() throws Exception {
      // Given: 创建多条记录，包含有效和无效的国家编码
      VenueParseResult result1 = createParseResultWithCountryCode("S1", "1111-1111", "US");
      VenueParseResult result2 = createParseResultWithCountryCode("S2", "2222-2222", "INVALID");
      VenueParseResult result3 = createParseResultWithCountryCode("S3", "3333-3333", "CN");
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2, result3);

      // Mock: 字典解析批量返回结果（只有 US 和 CN 有效）
      when(dictionaryResolverPort.resolve(
              DictionaryType.COUNTRY,
              SourceStandard.ISO_3166_1_ALPHA2,
              Set.of("US", "INVALID", "CN")))
          .thenReturn(Map.of("US", "US", "CN", "CN"));

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 验证国家编码处理结果
      verify(venueRepository, times(1)).insertAll(aggregatesCaptor.capture());
      List<VenueAggregate> insertedList = aggregatesCaptor.getValue();
      assertThat(insertedList).hasSize(3);

      // 验证各记录的国家编码
      VenueAggregate venue1 =
          insertedList.stream()
              .filter(v -> "S1".equals(v.getIdentifier(VenueIdentifierType.OPENALEX).orElse(null)))
              .findFirst()
              .orElseThrow();
      VenueAggregate venue2 =
          insertedList.stream()
              .filter(v -> "S2".equals(v.getIdentifier(VenueIdentifierType.OPENALEX).orElse(null)))
              .findFirst()
              .orElseThrow();
      VenueAggregate venue3 =
          insertedList.stream()
              .filter(v -> "S3".equals(v.getIdentifier(VenueIdentifierType.OPENALEX).orElse(null)))
              .findFirst()
              .orElseThrow();

      assertThat(venue1.getPublicationProfile().countryCode()).isEqualTo("US");
      assertThat(venue2.getPublicationProfile().countryCode()).isNull(); // 无效编码被清除
      assertThat(venue3.getPublicationProfile().countryCode()).isEqualTo("CN");
    }

    @Test
    @DisplayName("无 PublicationProfile - 不应调用字典解析")
    void write_noPublicationProfile_shouldNotCallDictionaryResolver() throws Exception {
      // Given: 创建没有 PublicationProfile 的记录
      VenueParseResult result = createParseResult("S1", "1111-1111", false);
      Chunk<VenueParseResult> chunk = Chunk.of(result);

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 不应调用字典解析（因为没有国家编码需要验证）
      verify(dictionaryResolverPort, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("countryCode 为 null - 不应包含在字典解析请求中")
    void write_nullCountryCode_shouldNotBeIncludedInResolveRequest() throws Exception {
      // Given: 一条有国家编码，一条没有
      VenueParseResult result1 = createParseResultWithCountryCode("S1", "1111-1111", "GB");
      VenueParseResult result2 = createParseResultWithNullCountryCode("S2", "2222-2222");
      Chunk<VenueParseResult> chunk = Chunk.of(result1, result2);

      // Mock: 只有 GB 应该被解析
      when(dictionaryResolverPort.resolve(
              DictionaryType.COUNTRY, SourceStandard.ISO_3166_1_ALPHA2, Set.of("GB")))
          .thenReturn(Map.of("GB", "GB"));

      doNothing().when(venueRepository).insertAll(anyList());

      // When
      writer.write(chunk);

      // Then: 验证只解析了非空的国家编码
      verify(dictionaryResolverPort, times(1))
          .resolve(DictionaryType.COUNTRY, SourceStandard.ISO_3166_1_ALPHA2, Set.of("GB"));
    }
  }

  // ========== 辅助方法 ==========

  private VenueParseResult createParseResult(String openalexId, String issnL, boolean withMetrics) {
    VenueAggregate venue =
        VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
    // 添加 ISSN-L 标识符
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));

    List<VenuePublicationStats> metrics =
        withMetrics ? List.of(VenuePublicationStats.create(2024, 100, 500)) : List.of();

    // 嵌入引用指标到聚合根（如果有年度指标）
    if (withMetrics) {
      venue.withCitationMetrics(CitationMetrics.ofBasic(100, 500));
    }

    // 使用新的简化构造函数（只有 aggregate 和 yearlyMetrics）
    return new VenueParseResult(venue, metrics);
  }

  private VenueParseResult createParseResultWithoutIssnL(String openalexId) {
    VenueAggregate venue =
        VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
    return new VenueParseResult(venue, List.of());
  }

  private VenueParseResult createParseResultWithCountryCode(
      String openalexId, String issnL, String countryCode) {
    VenueAggregate venue =
        VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));

    // 嵌入 PublicationProfile 包含国家编码
    PublicationProfile profile = PublicationProfile.builder().countryCode(countryCode).build();
    venue.withPublicationProfile(profile);

    return new VenueParseResult(venue, List.of());
  }

  private VenueParseResult createParseResultWithNullCountryCode(String openalexId, String issnL) {
    VenueAggregate venue =
        VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, "Journal " + openalexId);
    venue.addIdentifier(VenueIdentifier.forIssnL(issnL));

    // 嵌入 PublicationProfile 但国家编码为 null
    PublicationProfile profile =
        PublicationProfile.builder().abbreviatedTitle("Abbr " + openalexId).build();
    venue.withPublicationProfile(profile);

    return new VenueParseResult(venue, List.of());
  }

  private void assertThatListContainsOpenalexIds(List<VenueAggregate> list, String... expectedIds) {
    // 使用 getIdentifier() 方法访问 OpenAlex ID
    List<String> actualIds =
        list.stream().map(v -> v.getIdentifier(VenueIdentifierType.OPENALEX).orElse(null)).toList();
    assertThat(actualIds).containsExactlyInAnyOrder(expectedIds);
  }
}
