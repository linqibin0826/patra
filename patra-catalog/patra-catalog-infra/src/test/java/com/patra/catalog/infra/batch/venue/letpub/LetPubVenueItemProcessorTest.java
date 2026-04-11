package com.patra.catalog.infra.batch.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.common.error.trait.StandardErrorTrait;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// LetPubVenueItemProcessor 单元测试。
///
/// **测试策略**：
///
/// - Mock `LetPubEnrichmentPort`：验证正常/未找到/ISSN 空 场景
/// - Mock `VenueCoverImageDownloadPort`：验证封面下载成功 / 失败隔离
/// - 真实注入 `LetPubDataMapper`：验证 JCR + CAS 拆解结果
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LetPubVenueItemProcessor 单元测试")
@Timeout(2)
class LetPubVenueItemProcessorTest {

  private LetPubEnrichmentPort enrichmentPort;
  private VenueCoverImageDownloadPort coverImageDownloadPort;
  private LetPubVenueItemProcessor processor;

  @BeforeEach
  void setUp() {
    enrichmentPort = mock(LetPubEnrichmentPort.class);
    coverImageDownloadPort = mock(VenueCoverImageDownloadPort.class);
    processor =
        new LetPubVenueItemProcessor(
            enrichmentPort, new LetPubDataMapper(), coverImageDownloadPort);
  }

  /// 创建测试用 VenueEntity。
  private VenueEntity createVenueEntity(Long id, String issnL) {
    VenueEntity entity = new VenueEntity();
    entity.setId(id);
    entity.setIssnL(issnL);
    entity.setTitle("Test Journal");
    entity.setVenueType("JOURNAL");
    entity.setProvenanceCode("PUBMED");
    return entity;
  }

  @Test
  @DisplayName("LetPub 查到数据时应返回分开的 JCR 和 CAS 列表")
  void shouldReturnSeparateJcrAndCasResults() throws Exception {
    VenueEntity entity = createVenueEntity(100L, "0028-0836");
    var xinruiPartition =
        LetPubVenueData.CasPartition.builder()
            .version("2026年3月新锐版")
            .majorCategory("综合性期刊")
            .majorQuartile("1区")
            .topJournal(true)
            .reviewJournal(false)
            .build();
    var shengjiPartition =
        LetPubVenueData.CasPartition.builder()
            .version("2025年3月升级版")
            .majorCategory("综合性期刊")
            .majorQuartile("1区")
            .topJournal(true)
            .reviewJournal(false)
            .build();

    LetPubVenueData letPubData =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("10000")
                .letPubName("Nature")
                .build(),
            LetPubVenueData.JcrMetrics.builder()
                .jifQuartile("Q1")
                .impactFactorTrend(Map.of("2024-2025", 48.5))
                .build(),
            LetPubVenueData.CasData.of(List.of(xinruiPartition, shengjiPartition), List.of()),
            null);
    when(enrichmentPort.findByIssn("0028-0836")).thenReturn(Optional.of(letPubData));

    LetPubEnrichResult result = processor.process(entity);

    assertThat(result).isNotNull();
    assertThat(result.venueId()).isEqualTo(100L);

    // JCR: 1 条（一年的 IF 趋势）
    assertThat(result.jcr().ratings()).hasSize(1);
    assertThat(result.jcr().ratings().getFirst().getJifQuartile()).isEqualTo("Q1");

    // CAS: 2 条（新锐版 + 升级版）
    assertThat(result.cas().ratings()).hasSize(2);
    assertThat(result.cas().ratings())
        .extracting(e -> e.getEdition())
        .containsExactlyInAnyOrder("新锐版", "升级版");
    assertThat(result.cas().ratings()).allMatch(e -> "1区".equals(e.getMajorQuartile()));

    verify(enrichmentPort).findByIssn("0028-0836");
  }

  @Test
  @DisplayName("LetPub 未找到数据时应返回 null（跳过）")
  void shouldReturnNullWhenNotFound() throws Exception {
    VenueEntity entity = createVenueEntity(200L, "1234-5678");
    when(enrichmentPort.findByIssn("1234-5678")).thenReturn(Optional.empty());

    LetPubEnrichResult result = processor.process(entity);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("ISSN-L 为空时应返回 null（跳过）")
  void shouldReturnNullWhenIssnLBlank() throws Exception {
    VenueEntity entity = createVenueEntity(300L, null);

    LetPubEnrichResult result = processor.process(entity);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("封面下载成功时应将对象键写入 LetPubEnrichResult")
  void shouldIncludeImageObjectKeyWhenCoverDownloadSucceeds() throws Exception {
    // Given
    VenueEntity entity = createVenueEntity(401L, "1111-2222");
    LetPubVenueData data =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("6054")
                .letPubName("Nature")
                .coverImageSourceUrl(
                    "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/6054.jpg")
                .build(),
            null,
            null,
            null);
    when(enrichmentPort.findByIssn("1111-2222")).thenReturn(Optional.of(data));
    when(coverImageDownloadPort.downloadAndStore(any(URI.class), eq("catalog/venue-cover/401.jpg")))
        .thenReturn("catalog/venue-cover/401.jpg");

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.imageObjectKey()).isEqualTo("catalog/venue-cover/401.jpg");
    verify(coverImageDownloadPort)
        .downloadAndStore(any(URI.class), eq("catalog/venue-cover/401.jpg"));
  }

  @Test
  @DisplayName("封面下载失败时主流程应继续返回评级数据且 imageObjectKey 为空")
  void shouldContinueProcessingWhenCoverDownloadFails() throws Exception {
    // Given
    VenueEntity entity = createVenueEntity(402L, "3333-4444");
    LetPubVenueData data =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("9999")
                .letPubName("Nature")
                .coverImageSourceUrl(
                    "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/9999.jpg")
                .build(),
            null,
            null,
            null);
    when(enrichmentPort.findByIssn("3333-4444")).thenReturn(Optional.of(data));
    when(coverImageDownloadPort.downloadAndStore(any(URI.class), any()))
        .thenThrow(new FileDownloadException("MinIO down", StandardErrorTrait.DEP_UNAVAILABLE));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).as("下载失败不应阻断主流程").isNotNull();
    assertThat(result.venueId()).isEqualTo(402L);
    assertThat(result.imageObjectKey()).as("下载失败时不写入对象键").isNull();
    verify(coverImageDownloadPort)
        .downloadAndStore(any(URI.class), eq("catalog/venue-cover/402.jpg"));
  }

  @Test
  @DisplayName("LetPub 返回的封面 URL 格式非法时应跳过下载且不阻断主流程")
  void shouldContinueProcessingWhenCoverUrlIsMalformed() throws Exception {
    // Given — 带空格的 URL 会让 URI.create 抛 IllegalArgumentException
    VenueEntity entity = createVenueEntity(500L, "7777-8888");
    LetPubVenueData data =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("6054")
                .letPubName("Nature")
                .coverImageSourceUrl("not a valid url with spaces")
                .build(),
            null,
            null,
            null);
    when(enrichmentPort.findByIssn("7777-8888")).thenReturn(Optional.of(data));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).as("非法 URL 不应阻断主流程").isNotNull();
    assertThat(result.venueId()).isEqualTo(500L);
    assertThat(result.imageObjectKey()).as("非法 URL 时不应写入对象键").isNull();
    verify(coverImageDownloadPort, never()).downloadAndStore(any(), any());
  }

  @Test
  @DisplayName("LetPub 返回的封面 URL 为空字符串时应跳过下载（URI.create(\"\") 不会抛异常）")
  void shouldSkipDownloadWhenCoverSourceUrlIsEmpty() throws Exception {
    // Given — URI.create("") 在 JDK 25 返回空 URI 而非抛异常；
    // 若 Processor 不在上游显式拦截，会让空 URI 流向下载端口。
    VenueEntity entity = createVenueEntity(700L, "1010-2020");
    LetPubVenueData data =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("6054")
                .letPubName("Nature")
                .coverImageSourceUrl("")
                .build(),
            null,
            null,
            null);
    when(enrichmentPort.findByIssn("1010-2020")).thenReturn(Optional.of(data));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.imageObjectKey()).isNull();
    verify(coverImageDownloadPort, never()).downloadAndStore(any(), any());
  }

  @Test
  @DisplayName("封面下载端口抛出非 FileDownloadException 的 RuntimeException 时应兜底不阻断主流程")
  void shouldContinueProcessingWhenDownloadPortThrowsUncheckedRuntimeException() throws Exception {
    // Given — 契约上 Adapter 应抛 FileDownloadException，但若有未包装的 bug
    // 逃出，必须被处理器兜底，否则会触发 Spring Batch 的 chunk 回滚，
    // 把同一 venue 的 JCR/CAS ratings 一并丢弃。
    VenueEntity entity = createVenueEntity(800L, "2020-3030");
    LetPubVenueData data =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("6054")
                .letPubName("Nature")
                .coverImageSourceUrl("https://media-cdn.example.com/cover/journal/6054.jpg")
                .build(),
            null,
            null,
            null);
    when(enrichmentPort.findByIssn("2020-3030")).thenReturn(Optional.of(data));
    when(coverImageDownloadPort.downloadAndStore(any(URI.class), any()))
        .thenThrow(new IllegalStateException("MinIO client 未初始化"));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).as("未知 RuntimeException 不应阻断主流程").isNotNull();
    assertThat(result.venueId()).isEqualTo(800L);
    assertThat(result.imageObjectKey()).as("失败时不应写入对象键").isNull();
  }

  @Test
  @DisplayName("LetPub 未返回封面 URL 时应跳过下载（数据缺失）")
  void shouldSkipDownloadWhenCoverSourceUrlIsNull() throws Exception {
    // Given — coverImageSourceUrl 未设置，默认为 null
    VenueEntity entity = createVenueEntity(600L, "9999-0000");
    LetPubVenueData data =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("6054")
                .letPubName("Nature")
                .build(),
            null,
            null,
            null);
    when(enrichmentPort.findByIssn("9999-0000")).thenReturn(Optional.of(data));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.imageObjectKey()).isNull();
    verify(coverImageDownloadPort, never()).downloadAndStore(any(), any());
  }

  @Test
  @DisplayName("当 VenueEntity 已存在 imageObjectKey 时应跳过下载（幂等）")
  void shouldSkipDownloadWhenImageObjectKeyAlreadyExists() throws Exception {
    // Given
    VenueEntity entity = createVenueEntity(403L, "5555-6666");
    entity.setImageObjectKey("catalog/venue-cover/403.jpg");
    LetPubVenueData data =
        LetPubVenueData.of(
            LetPubVenueData.BasicInfo.builder()
                .letPubJournalId("6054")
                .letPubName("Nature")
                .coverImageSourceUrl(
                    "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/cover/journal/6054.jpg")
                .build(),
            null,
            null,
            null);
    when(enrichmentPort.findByIssn("5555-6666")).thenReturn(Optional.of(data));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.imageObjectKey()).as("幂等跳过：不重复下载，Result 仅携带本轮新下载的对象键").isNull();
    verify(coverImageDownloadPort, never()).downloadAndStore(any(), any());
  }
}
