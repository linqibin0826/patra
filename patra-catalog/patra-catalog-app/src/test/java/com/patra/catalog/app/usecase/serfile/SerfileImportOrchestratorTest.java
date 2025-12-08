package com.patra.catalog.app.usecase.serfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.serfile.command.SerfileImportCommand;
import com.patra.catalog.app.usecase.serfile.dto.SerfileImportResult;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import com.patra.catalog.domain.port.parser.SerfileParserPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.repository.VenueSupplementRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.ApplicationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/// NLM Serfile 导入编排器单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock 所有 Port 依赖
/// - 测试隔离：每个测试方法独立
///
/// **重点测试场景**：
///
/// - 正常导入流程：流式下载 → 解析 → 匹配 → 更新/创建
/// - 匹配优先级：ISSN-L → NLM ID → ISSN
/// - 异常处理
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SerfileImportOrchestrator 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class SerfileImportOrchestratorTest {

  private static final String TEST_URL =
      "https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfilebase2025.xml";
  private static final String TEST_VERSION = "2025";

  @Mock private StreamingDownloadPort streamingDownloadPort;
  @Mock private SerfileParserPort parserPort;
  @Mock private VenueRepository venueRepository;
  @Mock private VenueSupplementRepository supplementRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private TransactionStatus transactionStatus;
  @Mock private StreamingDownloadResult downloadResult;

  @Captor private ArgumentCaptor<List<VenueAggregate>> updateBatchCaptor;
  @Captor private ArgumentCaptor<List<VenueAggregate>> insertAllCaptor;

  private SerfileImportOrchestrator orchestrator;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    orchestrator =
        new SerfileImportOrchestrator(
            streamingDownloadPort,
            parserPort,
            venueRepository,
            supplementRepository,
            transactionTemplate);

    // 配置 TransactionTemplate：直接执行回调，模拟事务行为
    org.mockito.Mockito.doAnswer(
            invocation -> {
              Consumer<TransactionStatus> callback = invocation.getArgument(0);
              callback.accept(transactionStatus);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());

    // 配置 downloadResult 的默认行为（用于 try-with-resources）
    lenient().when(downloadResult.inputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
  }

  @Nested
  @DisplayName("正常导入流程测试")
  class NormalImportFlowTest {

    @Test
    @DisplayName("应该正确执行完整导入流程：流式下载 → 解析 → 匹配 → 更新/创建")
    void shouldExecuteCompleteImportFlow() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);
      SerialRecord record1 = createSerialRecord("0000001", "Journal One", "1111-1111", null, null);
      SerialRecord record2 = createSerialRecord("0000002", "Journal Two", null, "2222-2222", null);

      VenueAggregate existingVenue =
          VenueAggregate.fromPubMed("Existing Journal", null, "1111-1111");

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record1, record2));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("1111-1111", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      SerfileImportResult result = orchestrator.importSerfile(command);

      // Then - 验证调用顺序
      verify(streamingDownloadPort).download(URI.create(TEST_URL));
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
      assertThat(result.serfileVersion()).isEqualTo(TEST_VERSION);
    }

    @Test
    @DisplayName("空 XML 应该返回零计数结果")
    void shouldReturnZeroCountsForEmptyXml() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      // When
      SerfileImportResult result = orchestrator.importSerfile(command);

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
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);
      // 记录同时有 ISSN-L, NLM ID 和 ISSN，应该使用 ISSN-L 匹配
      SerialRecord record =
          createSerialRecord("0000001", "Test Journal", "1111-1111", "2222-2222", "3333-3333");

      VenueAggregate issnLMatch = VenueAggregate.fromPubMed("ISSN-L Matched", null, "1111-1111");
      VenueAggregate nlmIdMatch = VenueAggregate.fromPubMed("NLM ID Matched", "0000001", null);

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("1111-1111", issnLMatch));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of("0000001", nlmIdMatch));
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      orchestrator.importSerfile(command);

      // Then - 验证更新的是 ISSN-L 匹配的期刊
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      List<VenueAggregate> updated = updateBatchCaptor.getValue();
      assertThat(updated).hasSize(1);
      assertThat(updated.getFirst().getIssnL()).isEqualTo("1111-1111");
    }

    @Test
    @DisplayName("优先级 2：ISSN-L 无匹配时应该使用 NLM ID 匹配")
    void shouldFallbackToNlmIdMatching() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);
      SerialRecord record = createSerialRecord("0000001", "Test Journal", null, "2222-2222", null);

      VenueAggregate nlmIdMatch = VenueAggregate.fromPubMed("NLM ID Matched", "0000001", null);

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of("0000001", nlmIdMatch));
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      orchestrator.importSerfile(command);

      // Then - 验证更新的是 NLM ID 匹配的期刊
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      List<VenueAggregate> updated = updateBatchCaptor.getValue();
      assertThat(updated).hasSize(1);
      assertThat(updated.getFirst().getNlmId()).isEqualTo("0000001");
    }

    @Test
    @DisplayName("优先级 3：ISSN-L 和 NLM ID 都无匹配时应该使用 ISSN 匹配")
    void shouldFallbackToIssnMatching() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);
      SerialRecord record = createSerialRecord("0000001", "Test Journal", null, "2222-2222", null);

      VenueAggregate issnMatch = VenueAggregate.fromPubMed("ISSN Matched", null, "9999-9999");

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of("2222-2222", issnMatch));

      // When
      orchestrator.importSerfile(command);

      // Then
      verify(venueRepository).updateBatch(updateBatchCaptor.capture());
      List<VenueAggregate> updated = updateBatchCaptor.getValue();
      assertThat(updated).hasSize(1);
    }

    @Test
    @DisplayName("所有匹配都失败时应该创建新记录")
    void shouldCreateNewVenueWhenNoMatch() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);
      SerialRecord record = createSerialRecord("0000001", "New Journal", "1111-1111", null, null);

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      SerfileImportResult result = orchestrator.importSerfile(command);

      // Then
      verify(venueRepository).insertAll(insertAllCaptor.capture());
      List<VenueAggregate> created = insertAllCaptor.getValue();
      assertThat(created).hasSize(1);
      assertThat(created.getFirst().getDisplayName()).isEqualTo("New Journal");

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
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);

      // 3 条记录：1 条匹配更新，2 条新建
      SerialRecord matched = createSerialRecord("0000001", "Matched", "1111-1111", null, null);
      SerialRecord new1 = createSerialRecord("0000002", "New One", "2222-2222", null, null);
      SerialRecord new2 = createSerialRecord("0000003", "New Two", "3333-3333", null, null);

      VenueAggregate existingVenue = VenueAggregate.fromPubMed("Existing", null, "1111-1111");

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(matched, new1, new2));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of("1111-1111", existingVenue));
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // When
      SerfileImportResult result = orchestrator.importSerfile(command);

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
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      // When
      SerfileImportResult result = orchestrator.importSerfile(command);

      // Then - 耗时应该大于等于 0
      assertThat(result.durationMillis()).isGreaterThanOrEqualTo(0);
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTest {

    @Test
    @DisplayName("下载失败时应该包装为 ApplicationException")
    void shouldWrapDownloadException() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);

      when(streamingDownloadPort.download(any(URI.class)))
          .thenThrow(new RuntimeException("Network error"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importSerfile(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("Serfile 导入失败");

      // 不应该调用后续操作
      verify(parserPort, never()).parse(any());
    }

    @Test
    @DisplayName("解析失败时应该包装为 ApplicationException")
    void shouldWrapParsingException() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class)))
          .thenThrow(new RuntimeException("XML parse error"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importSerfile(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("Serfile 导入失败");
    }

    @Test
    @DisplayName("Repository 操作失败时应该包装为 ApplicationException")
    void shouldWrapRepositoryException() {
      // Given
      SerfileImportCommand command = SerfileImportCommand.of(TEST_URL, TEST_VERSION);
      SerialRecord record = createSerialRecord("0000001", "Journal", "1111-1111", null, null);

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(record));
      when(venueRepository.findByIssnLs(any())).thenReturn(Map.of());
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());
      doThrow(new RuntimeException("Database error")).when(venueRepository).insertAll(anyList());

      // When & Then
      assertThatThrownBy(() -> orchestrator.importSerfile(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("Serfile 导入失败");
    }
  }

  // ========== 辅助方法 ==========

  /// 创建测试用 SerialRecord。
  private SerialRecord createSerialRecord(
      String nlmId, String title, String issnL, String issnPrint, String issnElectronic) {
    return SerialRecord.builder()
        .nlmUniqueId(nlmId)
        .title(title)
        .issnL(issnL)
        .issnPrint(issnPrint)
        .issnElectronic(issnElectronic)
        .build();
  }
}
