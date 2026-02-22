package com.patra.catalog.app.usecase.venue.pubmed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.pubmed.command.VenuePubmedImportCommand;
import com.patra.catalog.app.usecase.venue.pubmed.dto.VenuePubmedImportResult;
import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo.ApcPrice;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueOpenAlexEnrichment;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.model.vo.venue.VenueWikidataEnrichment;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedMeshHeading;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import com.patra.catalog.domain.port.enrichment.OpenAlexEnrichmentQueryPort;
import com.patra.catalog.domain.port.enrichment.WikidataEnrichmentQueryPort;
import com.patra.catalog.domain.port.parser.LsiouParserPort;
import com.patra.catalog.domain.port.registry.DictionaryResolverPort;
import com.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import com.patra.catalog.domain.port.repository.MeshQualifierRepository;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.trait.StandardErrorTrait;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/// PubMed Venue 数据导入命令处理器单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock 所有 Port 依赖
/// - 测试隔离：每个测试方法独立
///
/// **重点测试场景**：
///
/// - 正常导入流程：下载到临时文件 → 解析 → 匹配 → 更新/创建
/// - 匹配优先级：ISSN-L → NLM ID → ISSN
/// - 异常处理
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("VenuePubmedImportHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenuePubmedImportHandlerTest {

  private static final String TEST_URL = "ftp://ftp.nlm.nih.gov/online/journals/lsi2024.xml";
  private static final String TEST_VERSION = "2024";

  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private LsiouParserPort parserPort;
  @Mock private VenueRepository venueRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private TransactionStatus transactionStatus;
  @Mock private DictionaryResolverPort dictionaryResolverPort;
  @Mock private MeshDescriptorRepository meshDescriptorRepository;
  @Mock private MeshQualifierRepository meshQualifierRepository;
  @Mock private WikidataEnrichmentQueryPort wikidataEnrichmentQueryPort;
  @Mock private OpenAlexEnrichmentQueryPort openAlexEnrichmentQueryPort;

  @Captor private ArgumentCaptor<List<VenueAggregate>> updateBatchCaptor;
  @Captor private ArgumentCaptor<List<VenueAggregate>> insertAllCaptor;

  @SuppressWarnings("unchecked")
  @Captor
  private ArgumentCaptor<Map<Long, List<VenuePublicationStats>>> yearlyMetricsCaptor;

  @TempDir Path tempDir;
  private FileDownloadResult downloadResult;
  private VenuePubmedImportHandler handler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    handler =
        new VenuePubmedImportHandler(
            fileDownloadPort,
            parserPort,
            venueRepository,
            transactionTemplate,
            dictionaryResolverPort,
            meshDescriptorRepository,
            meshQualifierRepository,
            wikidataEnrichmentQueryPort,
            openAlexEnrichmentQueryPort);

    // 创建临时 XML 文件，供 Handler 中 Files.newInputStream() 使用
    Path tempFile = tempDir.resolve("test-lsiou.xml");
    Files.write(tempFile, new byte[0]);
    downloadResult = FileDownloadResult.of(tempFile, 0L);

    // 配置 TransactionTemplate：直接执行回调，模拟事务行为
    // 使用 lenient() 因为异常测试可能在到达事务逻辑前就失败
    lenient()
        .doAnswer(
            invocation -> {
              Consumer<TransactionStatus> callback = invocation.getArgument(0);
              callback.accept(transactionStatus);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());

    // 配置 dictionaryResolverPort 默认返回空 Map（测试不关心国家编码解析）
    lenient().when(dictionaryResolverPort.resolve(any(), any(), any())).thenReturn(Map.of());

    // 配置 MeSH Repository 默认返回空 Map（测试不关心 MeSH UI 导入）
    lenient().when(meshDescriptorRepository.findAllByNameIn(any())).thenReturn(Map.of());
    lenient().when(meshQualifierRepository.findAllByNameIn(any())).thenReturn(Map.of());

    // 配置 Wikidata 富化端口默认返回空 Map（测试不关心 Wikidata 富化查询）
    lenient()
        .when(wikidataEnrichmentQueryPort.findEnrichmentData(any()))
        .thenReturn(Map.<String, VenueWikidataEnrichment>of());

    // 配置 OpenAlex 富化端口默认返回空 Map（测试不关心 OpenAlex 富化查询）
    lenient()
        .when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
        .thenReturn(Map.<String, VenueOpenAlexEnrichment>of());
  }

  @Nested
  @DisplayName("正常导入流程测试")
  class NormalImportFlowTest {

    @Test
    @DisplayName("应该正确执行完整导入流程：下载到临时文件 → 解析 → 匹配 → 更新/创建")
    void shouldExecuteCompleteImportFlow() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record1 =
          createSerialData("0000001", "Journal One", "1111-1111", null, null);
      PubmedSerialData record2 =
          createSerialData("0000002", "Journal Two", null, "2222-2222", null);

      VenueAggregate existingVenue = createExistingVenue("Existing Journal", null, "1111-1111");

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record1, record2));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("1111-1111", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then - 验证调用顺序
      verify(fileDownloadPort).download(URI.create(TEST_URL));
      verify(parserPort).parse(any(InputStream.class));
      verify(venueRepository).findByIssnLs(any());
      verify(venueRepository).findByNlmIds(any());
      verify(venueRepository).findByIssns(any());
      verify(venueRepository).updateBatch(anyList());
      verify(venueRepository).insertAll(anyList());

      // Then - 验证结果
      assertThat(result).isNotNull();
      assertThat(result.totalParsed()).isEqualTo(2);
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.lsiouVersion()).isEqualTo(TEST_VERSION);
    }

    @Test
    @DisplayName("空 XML 应该返回零计数结果")
    void shouldReturnZeroCountsForEmptyXml() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then
      assertThat(result.totalParsed()).isZero();
      assertThat(result.updatedCount()).isZero();
      assertThat(result.createdCount()).isZero();
      assertThat(result.isSuccess()).isFalse();

      // 不应该调用更新和插入
      verify(venueRepository, never()).updateBatch(anyList());
      verify(venueRepository, never()).insertAll(anyList());
    }
  }

  @Nested
  @DisplayName("匹配优先级测试")
  class MatchingPriorityTest {

    @Test
    @DisplayName("优先级 1：应该优先使用 ISSN-L 匹配")
    void shouldPrioritizeIssnLMatching() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      // 记录同时有 ISSN-L, NLM ID 和 ISSN，应该使用 ISSN-L 匹配
      PubmedSerialData record =
          createSerialData("0000001", "Test Journal", "1111-1111", "2222-2222", "3333-3333");

      VenueAggregate issnLMatch = createExistingVenue("ISSN-L Matched", null, "1111-1111");
      VenueAggregate nlmIdMatch = createExistingVenue("NLM ID Matched", "0000001", null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("1111-1111", issnLMatch));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of("0000001", nlmIdMatch));
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      handler.handle(command);

      // Then - 验证更新的是 ISSN-L 匹配的期刊
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      List<VenueAggregate> updated = updateBatchCaptor.getValue();
      assertThat(updated).hasSize(1);
      assertThat(updated.getFirst().getIdentifier(VenueIdentifierType.ISSN_L))
          .hasValue("1111-1111");
    }

    @Test
    @DisplayName("优先级 2：ISSN-L 无匹配时应该使用 NLM ID 匹配")
    void shouldFallbackToNlmIdMatching() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          createSerialData("0000001", "Test Journal", null, "2222-2222", null);

      VenueAggregate nlmIdMatch = createExistingVenue("NLM ID Matched", "0000001", null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of("0000001", nlmIdMatch));
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      handler.handle(command);

      // Then - 验证更新的是 NLM ID 匹配的期刊
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      List<VenueAggregate> updated = updateBatchCaptor.getValue();
      assertThat(updated).hasSize(1);
      assertThat(updated.getFirst().getIdentifier(VenueIdentifierType.NLM)).hasValue("0000001");
    }

    @Test
    @DisplayName("优先级 3：ISSN-L 和 NLM ID 都无匹配时应该使用 ISSN 匹配")
    void shouldFallbackToIssnMatching() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          createSerialData("0000001", "Test Journal", null, "2222-2222", null);

      VenueAggregate issnMatch = createExistingVenue("ISSN Matched", null, "9999-9999");

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of("2222-2222", issnMatch));

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      List<VenueAggregate> updated = updateBatchCaptor.getValue();
      assertThat(updated).hasSize(1);
    }

    @Test
    @DisplayName("所有匹配都失败时应该创建新记录")
    void shouldCreateNewVenueWhenNoMatch() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "New Journal", "1111-1111", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      List<VenueAggregate> created = insertAllCaptor.getValue();
      assertThat(created).hasSize(1);
      assertThat(created.getFirst().getTitle()).isEqualTo("New Journal");

      assertThat(result.createdCount()).isEqualTo(1);
      assertThat(result.updatedCount()).isZero();
    }
  }

  @Nested
  @DisplayName("结果统计测试")
  class ResultStatisticsTest {

    @Test
    @DisplayName("应该正确统计更新和创建数量")
    void shouldCountUpdatedAndCreatedCorrectly() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);

      // 3 条记录：1 条匹配更新，2 条新建
      PubmedSerialData matched = createSerialData("0000001", "Matched", "1111-1111", null, null);
      PubmedSerialData new1 = createSerialData("0000002", "New One", "2222-2222", null, null);
      PubmedSerialData new2 = createSerialData("0000003", "New Two", "3333-3333", null, null);

      VenueAggregate existingVenue = createExistingVenue("Existing", null, "1111-1111");

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(matched, new1, new2));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("1111-1111", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then
      assertThat(result.totalParsed()).isEqualTo(3);
      assertThat(result.updatedCount()).isEqualTo(1);
      assertThat(result.createdCount()).isEqualTo(2);
      assertThat(result.processedCount()).isEqualTo(3);
      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("耗时应该被正确记录")
    void shouldRecordDuration() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then - 耗时应该大于等于 0
      assertThat(result.durationMillis()).isGreaterThanOrEqualTo(0);
    }
  }

  @Nested
  @DisplayName("归档回退测试")
  class ArchiveFallbackTest {

    @Test
    @DisplayName("主目录文件不存在时应回退到 archive 目录")
    void shouldFallbackToArchiveWhenFileNotFound() {
      // Given
      String primaryUrl = "ftp://ftp.nlm.nih.gov/online/journals/lsi2023.xml";
      String archiveUrl = "ftp://ftp.nlm.nih.gov/online/journals/archive/lsi2023.xml";
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(primaryUrl, TEST_VERSION);

      when(fileDownloadPort.download(URI.create(primaryUrl)))
          .thenThrow(new FileDownloadException("not found", StandardErrorTrait.NOT_FOUND));
      when(fileDownloadPort.download(URI.create(archiveUrl))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then
      verify(fileDownloadPort).download(URI.create(primaryUrl));
      verify(fileDownloadPort).download(URI.create(archiveUrl));
      assertThat(result.sourceUrl()).isEqualTo(archiveUrl);
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTest {

    @Test
    @DisplayName("下载失败时应该包装为 ApplicationException")
    void shouldWrapDownloadException() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);

      when(fileDownloadPort.download(any(URI.class)))
          .thenThrow(new RuntimeException("Network error"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("PubMed Venue 导入失败");

      // 不应该调用后续操作
      verify(parserPort, never()).parse(any());
    }

    @Test
    @DisplayName("解析失败时应该包装为 ApplicationException")
    void shouldWrapParsingException() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class)))
          .thenThrow(new RuntimeException("XML parse error"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("PubMed Venue 导入失败");
    }

    @Test
    @DisplayName("Repository 操作失败时应该包装为 ApplicationException")
    void shouldWrapRepositoryException() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Journal", "1111-1111", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      doThrow(new RuntimeException("Database error")).when(venueRepository).insertAll(anyList());

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("PubMed Venue 导入失败");
    }
  }

  @Nested
  @DisplayName("MeSH UI 导入测试")
  class MeshUiImportTest {

    @Test
    @DisplayName("应该正确导入 MeSH Descriptor UI")
    void shouldImportMeshDescriptorUi() {
      // Given: 带 MeSH 主题词的记录
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          PubmedSerialData.builder()
              .nlmUniqueId("0000001")
              .title("Test Journal")
              .issnL("1111-1111")
              .meshHeadings(
                  List.of(
                      PubmedMeshHeading.of("Cardiovascular Diseases", true),
                      PubmedMeshHeading.of("Neoplasms", false)))
              .build();

      // 配置 MeSH Repository 返回 UI 映射
      when(meshDescriptorRepository.findAllByNameIn(any()))
          .thenReturn(
              Map.of(
                  "Cardiovascular Diseases", "D002318",
                  "Neoplasms", "D009369"));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      handler.handle(command);

      // Then: 验证 MeSH Repository 被调用
      verify(meshDescriptorRepository).findAllByNameIn(any());
    }

    @Test
    @DisplayName("应该正确导入 MeSH Qualifier UI")
    void shouldImportMeshQualifierUi() {
      // Given: 带限定词的 MeSH 主题词记录
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          PubmedSerialData.builder()
              .nlmUniqueId("0000001")
              .title("Test Journal")
              .issnL("1111-1111")
              .meshHeadings(
                  List.of(
                      PubmedMeshHeading.of("Cardiovascular Diseases", true, "diagnosis", false),
                      PubmedMeshHeading.of("Neoplasms", false, "therapy", true)))
              .build();

      // 配置 MeSH Repository 返回 UI 映射
      when(meshDescriptorRepository.findAllByNameIn(any()))
          .thenReturn(
              Map.of(
                  "Cardiovascular Diseases", "D002318",
                  "Neoplasms", "D009369"));
      when(meshQualifierRepository.findAllByNameIn(any()))
          .thenReturn(
              Map.of(
                  "diagnosis", "Q000175",
                  "therapy", "Q000628"));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      handler.handle(command);

      // Then: 验证两个 MeSH Repository 都被调用
      verify(meshDescriptorRepository).findAllByNameIn(any());
      verify(meshQualifierRepository).findAllByNameIn(any());
    }

    @Test
    @DisplayName("MeSH UI 查找失败时应该保持 null")
    void shouldKeepNullWhenMeshUiNotFound() {
      // Given: 带 MeSH 主题词的记录，但 Repository 返回空
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          PubmedSerialData.builder()
              .nlmUniqueId("0000001")
              .title("Test Journal")
              .issnL("1111-1111")
              .meshHeadings(List.of(PubmedMeshHeading.of("Unknown Disease", true)))
              .build();

      // MeSH Repository 返回空（表示未找到）
      when(meshDescriptorRepository.findAllByNameIn(any())).thenReturn(Map.of());

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then: 不应该抛出异常，处理应该继续
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.createdCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有 MeSH 主题词时不应调用 MeSH Repository")
    void shouldNotCallMeshRepositoryWhenNoMeshHeadings() {
      // Given: 没有 MeSH 主题词的记录
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          PubmedSerialData.builder()
              .nlmUniqueId("0000001")
              .title("Test Journal")
              .issnL("1111-1111")
              .build(); // 没有 meshHeadings

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      handler.handle(command);

      // Then: MeSH Repository 应该被调用（收集到空集合，但方法仍被调用）
      // 由于批次处理，即使没有 MeSH 也会调用 findAllByNameIn(emptySet)
      verify(meshDescriptorRepository).findAllByNameIn(any());
    }
  }

  @Nested
  @DisplayName("ISSN 标识符存储测试")
  class IssnIdentifierStorageTest {

    @Test
    @DisplayName("创建新期刊时应该存储 ISSN Print 和 Electronic 标识符")
    void shouldStoreIssnIdentifiersOnCreate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          createSerialData("0000001", "New Journal", "1111-1111", "2222-2222", "3333-3333");

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      handler.handle(command);

      // Then — 新建的聚合根应包含所有 5 种标识符
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      VenueAggregate created = insertAllCaptor.getValue().getFirst();

      assertThat(created.getIdentifier(VenueIdentifierType.NLM)).hasValue("0000001");
      assertThat(created.getIdentifier(VenueIdentifierType.ISSN_L)).hasValue("1111-1111");
      assertThat(created.getIdentifiers(VenueIdentifierType.ISSN))
          .containsExactlyInAnyOrder("2222-2222", "3333-3333");
    }

    @Test
    @DisplayName("更新已有期刊时应该补充 ISSN Print 和 Electronic 标识符")
    void shouldAddIssnIdentifiersOnUpdate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record =
          createSerialData("0000001", "Existing Journal", "1111-1111", "2222-2222", "3333-3333");

      // 已有期刊只有 ISSN-L，没有 ISSN
      VenueAggregate existingVenue = createExistingVenue("Existing", null, "1111-1111");

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("1111-1111", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      handler.handle(command);

      // Then — 更新后应补充 NLM ID 和 ISSN
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      VenueAggregate updated = updateBatchCaptor.getValue().getFirst();

      assertThat(updated.getIdentifier(VenueIdentifierType.NLM)).hasValue("0000001");
      assertThat(updated.getIdentifiers(VenueIdentifierType.ISSN))
          .containsExactlyInAnyOrder("2222-2222", "3333-3333");
    }
  }

  @Nested
  @DisplayName("批内去重测试")
  class BatchInternalDeduplicationTest {

    @Test
    @DisplayName("同批次内相同 NLM ID 的记录应该被去重")
    void shouldDeduplicateByNlmIdWithinBatch() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record1 = createSerialData("0000001", "Journal A", "1111-1111", null, null);
      PubmedSerialData record2 = createSerialData("0000001", "Journal A Variant", null, null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record1, record2));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then — 只创建 1 条，跳过 1 条
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      assertThat(insertAllCaptor.getValue()).hasSize(1);
      assertThat(result.createdCount()).isEqualTo(1);
      assertThat(result.skippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("同批次内相同 ISSN-L 的记录应该被去重")
    void shouldDeduplicateByIssnLWithinBatch() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record1 = createSerialData("0000001", "Journal A", "1111-1111", null, null);
      PubmedSerialData record2 =
          createSerialData("0000002", "Journal A (New Title)", "1111-1111", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record1, record2));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then — 只创建 1 条，跳过 1 条
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      assertThat(insertAllCaptor.getValue()).hasSize(1);
      assertThat(insertAllCaptor.getValue().getFirst().getTitle()).isEqualTo("Journal A");
      assertThat(result.skippedCount()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("已删除 Serial 过滤测试")
  class DeletedSerialFilterTest {

    @Test
    @DisplayName("应该跳过已删除的 Serial 记录")
    void shouldSkipDeletedSerials() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData activeRecord =
          createSerialData("0000001", "Active Journal", "1111-1111", null, null);
      PubmedSerialData deletedRecord =
          PubmedSerialData.builder()
              .nlmUniqueId("0000002")
              .title("Deleted Journal")
              .issnL("2222-2222")
              .deletedTimestamp(LocalDateTime.of(2020, 1, 1, 0, 0))
              .build();

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class)))
          .thenReturn(Stream.of(activeRecord, deletedRecord));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then — 只创建活跃记录，删除的被跳过
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      assertThat(insertAllCaptor.getValue()).hasSize(1);
      assertThat(insertAllCaptor.getValue().getFirst().getTitle()).isEqualTo("Active Journal");
      assertThat(result.totalParsed()).isEqualTo(2);
      assertThat(result.createdCount()).isEqualTo(1);
      assertThat(result.skippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("所有记录都已删除时应该返回零计数")
    void shouldReturnZeroWhenAllDeleted() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData deletedRecord =
          PubmedSerialData.builder()
              .nlmUniqueId("0000001")
              .title("Deleted Journal")
              .issnL("1111-1111")
              .deletedTimestamp(LocalDateTime.of(2020, 6, 15, 12, 0))
              .build();

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(deletedRecord));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      VenuePubmedImportResult result = handler.handle(command);

      // Then
      assertThat(result.createdCount()).isZero();
      assertThat(result.updatedCount()).isZero();
      assertThat(result.skippedCount()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Wikidata 富化测试（中文标题 + 封面图片 + 官方网站）")
  class WikidataEnrichmentTest {

    private static final String IMAGE_URL =
        "http://commons.wikimedia.org/wiki/Special:FilePath/Nature_magazine.jpg";
    private static final String HOMEPAGE_URL = "https://www.nature.com/nm";

    @Test
    @DisplayName("CREATE 路径：新建期刊应该携带中文标题、封面图片和官方网站")
    void shouldSetAllWikidataEnrichmentOnCreate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(wikidataEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(
              Map.of("0028-0836", VenueWikidataEnrichment.of("自然", IMAGE_URL, HOMEPAGE_URL)));

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      VenueAggregate created = insertAllCaptor.getValue().getFirst();
      assertThat(created.getTitleZh()).isEqualTo("自然");
      assertThat(created.getImageUrl()).isEqualTo(IMAGE_URL);
      assertThat(created.getPublicationProfile().homepageUrl()).isEqualTo(HOMEPAGE_URL);
    }

    @Test
    @DisplayName("UPDATE 路径：已有期刊应该被富化中文标题、封面图片和官方网站")
    void shouldEnrichAllWikidataFieldsOnUpdate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      VenueAggregate existingVenue = createExistingVenue("Nature", null, "0028-0836");
      assertThat(existingVenue.getTitleZh()).isNull();

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("0028-0836", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(wikidataEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(
              Map.of("0028-0836", VenueWikidataEnrichment.of("自然", IMAGE_URL, HOMEPAGE_URL)));

      // When
      handler.handle(command);

      // Then — 更新路径的期刊应该被富化所有 Wikidata 字段
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      VenueAggregate updated = updateBatchCaptor.getValue().getFirst();
      assertThat(updated.getTitleZh()).isEqualTo("自然");
      assertThat(updated.getImageUrl()).isEqualTo(IMAGE_URL);
      assertThat(updated.getPublicationProfile().homepageUrl()).isEqualTo(HOMEPAGE_URL);
    }

    @Test
    @DisplayName("UPDATE 路径：Wikidata 无数据时不应清除已有富化字段")
    void shouldNotClearExistingEnrichmentWhenWikidataReturnsEmpty() {
      // Given — 已有期刊本身已有中文标题和封面图片
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      VenueAggregate existingVenue =
          VenueAggregate.restore(
              VenueId.of(venueIdCounter++), VenueType.JOURNAL, "Nature", "自然", IMAGE_URL, 0L);
      existingVenue.addIdentifier(VenueIdentifier.forIssnL("0028-0836"));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("0028-0836", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      // Wikidata 返回空 Map（无数据）
      when(wikidataEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(Map.<String, VenueWikidataEnrichment>of());

      // When
      handler.handle(command);

      // Then — 已有的中文标题和封面图片不应被清除
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      VenueAggregate updated = updateBatchCaptor.getValue().getFirst();
      assertThat(updated.getTitleZh()).isEqualTo("自然");
      assertThat(updated.getImageUrl()).isEqualTo(IMAGE_URL);
    }
  }

  @Nested
  @DisplayName("OpenAlex 富化测试（引用指标 + 年度统计 + OpenAlex ID）")
  class OpenAlexEnrichmentTest {

    private static final CitationMetrics SAMPLE_METRICS =
        CitationMetrics.of(150000, 2500000, 285, 1200, new BigDecimal("3.45"));
    private static final List<VenuePublicationStats> SAMPLE_STATS =
        List.of(
            VenuePublicationStats.create(2024, 1500, 25000, 800),
            VenuePublicationStats.create(2023, 1400, 22000, 700));
    private static final OpenAccessInfo SAMPLE_OA_INFO =
        OpenAccessInfo.of(
            true, true, null, 11390, List.of(ApcPrice.of(11390, "USD"), ApcPrice.of(9500, "EUR")));

    @Test
    @DisplayName("CREATE 路径：新建期刊应该携带引用指标和 OpenAlex ID")
    void shouldSetCitationMetricsAndOpenAlexIdOnCreate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(
              Map.of(
                  "0028-0836",
                  VenueOpenAlexEnrichment.of("S137773608", SAMPLE_METRICS, SAMPLE_STATS, null)));

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      VenueAggregate created = insertAllCaptor.getValue().getFirst();
      assertThat(created.getCitationMetrics()).isEqualTo(SAMPLE_METRICS);
      assertThat(created.getIdentifier(VenueIdentifierType.OPENALEX)).hasValue("S137773608");
    }

    @Test
    @DisplayName("UPDATE 路径：已有期刊应该被富化引用指标和 OpenAlex ID")
    void shouldEnrichCitationMetricsAndOpenAlexIdOnUpdate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      VenueAggregate existingVenue = createExistingVenue("Nature", null, "0028-0836");
      assertThat(existingVenue.getCitationMetrics()).isNull();

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("0028-0836", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(
              Map.of(
                  "0028-0836",
                  VenueOpenAlexEnrichment.of("S137773608", SAMPLE_METRICS, SAMPLE_STATS, null)));

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      VenueAggregate updated = updateBatchCaptor.getValue().getFirst();
      assertThat(updated.getCitationMetrics()).isEqualTo(SAMPLE_METRICS);
      assertThat(updated.getIdentifier(VenueIdentifierType.OPENALEX)).hasValue("S137773608");
    }

    @Test
    @DisplayName("OpenAlex 无数据时不应设置引用指标")
    void shouldNotSetCitationMetricsWhenOpenAlexReturnsEmpty() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(Map.<String, VenueOpenAlexEnrichment>of());

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      VenueAggregate created = insertAllCaptor.getValue().getFirst();
      assertThat(created.getCitationMetrics()).isNull();
    }

    @Test
    @DisplayName("UPDATE 路径：应该将年度统计持久化到 Repository")
    @SuppressWarnings("unchecked")
    void shouldPersistYearlyStatsToRepository() {
      // Given — 使用 UPDATE 路径（已有 ID 的聚合根），
      // 因为 CREATE 路径的 ID 在 mock 中未回填，toVenueIdMap 会过滤掉
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      VenueAggregate existingVenue = createExistingVenue("Nature", null, "0028-0836");

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("0028-0836", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(
              Map.of(
                  "0028-0836",
                  VenueOpenAlexEnrichment.of("S137773608", SAMPLE_METRICS, SAMPLE_STATS, null)));

      // When
      handler.handle(command);

      // Then — 验证年度统计被持久化
      verify(venueRepository).replaceYearlyMetricsBatch(yearlyMetricsCaptor.capture());
      Map<Long, List<VenuePublicationStats>> yearlyMetrics = yearlyMetricsCaptor.getValue();
      assertThat(yearlyMetrics).isNotEmpty();
      // 验证年度统计包含 2 条记录（2024 和 2023）
      assertThat(yearlyMetrics.values().iterator().next()).hasSize(2);
    }

    @Test
    @DisplayName("CREATE 路径：新建期刊应该携带 OA 信息")
    void shouldSetOpenAccessInfoOnCreate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(
              Map.of(
                  "0028-0836",
                  VenueOpenAlexEnrichment.of(
                      "S137773608", SAMPLE_METRICS, SAMPLE_STATS, SAMPLE_OA_INFO)));

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      VenueAggregate created = insertAllCaptor.getValue().getFirst();
      assertThat(created.getOpenAccess()).isNotNull();
      assertThat(created.getOpenAccess().isOa()).isTrue();
      assertThat(created.getOpenAccess().isInDoaj()).isTrue();
      assertThat(created.getOpenAccess().apcUsd()).isEqualTo(11390);
      assertThat(created.getOpenAccess().apcPrices()).hasSize(2);
    }

    @Test
    @DisplayName("UPDATE 路径：已有期刊应该被富化 OA 信息")
    void shouldSetOpenAccessInfoOnUpdate() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      VenueAggregate existingVenue = createExistingVenue("Nature", null, "0028-0836");
      assertThat(existingVenue.getOpenAccess()).isNull();

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("0028-0836", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(
              Map.of(
                  "0028-0836",
                  VenueOpenAlexEnrichment.of(
                      "S137773608", SAMPLE_METRICS, SAMPLE_STATS, SAMPLE_OA_INFO)));

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      VenueAggregate updated = updateBatchCaptor.getValue().getFirst();
      assertThat(updated.getOpenAccess()).isNotNull();
      assertThat(updated.getOpenAccess().isOa()).isTrue();
      assertThat(updated.getOpenAccess().apcUsd()).isEqualTo(11390);
    }

    @Test
    @DisplayName("OpenAlex 返回空结果时 openAccess 应保持 null")
    void shouldKeepOpenAccessNullWhenOpenAlexReturnsEmpty() {
      // Given
      VenuePubmedImportCommand command = VenuePubmedImportCommand.of(TEST_URL, TEST_VERSION);
      PubmedSerialData record = createSerialData("0000001", "Nature", "0028-0836", null, null);

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      when(openAlexEnrichmentQueryPort.findEnrichmentData(any()))
          .thenReturn(Map.<String, VenueOpenAlexEnrichment>of());

      // When
      handler.handle(command);

      // Then
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      VenueAggregate created = insertAllCaptor.getValue().getFirst();
      assertThat(created.getOpenAccess()).isNull();
    }
  }

  // ========== 辅助方法 ==========

  /// 创建测试用 PubmedSerialData。
  private PubmedSerialData createSerialData(
      String nlmId, String title, String issnL, String issnPrint, String issnElectronic) {
    return PubmedSerialData.builder()
        .nlmUniqueId(nlmId)
        .title(title)
        .issnL(issnL)
        .issnPrint(issnPrint)
        .issnElectronic(issnElectronic)
        .build();
  }

  private static long venueIdCounter = 1L;

  /// 创建模拟已存在的 VenueAggregate（带 ID，模拟从数据库查询返回）。
  ///
  /// @param title 期刊标题
  /// @param nlmId NLM 唯一标识符（可为 null）
  /// @param issnL Linking ISSN（可为 null）
  /// @return 带 ID 的 VenueAggregate
  private VenueAggregate createExistingVenue(String title, String nlmId, String issnL) {
    VenueAggregate venue =
        VenueAggregate.restore(
            VenueId.of(venueIdCounter++), VenueType.JOURNAL, title, null, null, 0L);
    if (nlmId != null) {
      venue.addIdentifier(VenueIdentifier.forNlm(nlmId));
    }
    if (issnL != null) {
      venue.addIdentifier(VenueIdentifier.forIssnL(issnL));
    }
    return venue;
  }
}
