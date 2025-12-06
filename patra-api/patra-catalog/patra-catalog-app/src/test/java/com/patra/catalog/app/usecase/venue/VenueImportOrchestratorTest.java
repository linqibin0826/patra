package com.patra.catalog.app.usecase.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.model.vo.venue.VenueImportParams;
import com.patra.catalog.domain.port.batch.VenueImportBatchPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.VenueSourceFilePort;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// OpenAlex Venue 导入编排器单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock 所有 Port 依赖
/// - 测试隔离：每个测试方法独立
///
/// **重点测试场景**：
///
/// - 数据存在性检查：表中有数据时应拒绝导入
/// - 正常导入流程：获取 manifest → 下载文件 → 启动批处理
/// - 参数传递正确性验证
/// - 失败时临时文件清理
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueImportOrchestrator 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueImportOrchestratorTest {

  private static final Path TEST_FILE_1 = Path.of("/tmp/openalex-venue-part-001.gz");
  private static final Path TEST_FILE_2 = Path.of("/tmp/openalex-venue-part-002.gz");

  @Mock private VenueSourceFilePort venueSourceFilePort;
  @Mock private VenueImportBatchPort venueImportBatchPort;
  @Mock private VenueRepository venueRepository;

  private VenueImportOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
        new VenueImportOrchestrator(venueSourceFilePort, venueImportBatchPort, venueRepository);
  }

  /// 创建测试用的 OpenAlexManifest。
  private OpenAlexManifest createTestManifest() {
    return new OpenAlexManifest(
        List.of(
            new OpenAlexManifest.Entry(
                "https://openalex.s3.amazonaws.com/data/sources/updated_date=2024-01-01/part_000.gz",
                1024L,
                100),
            new OpenAlexManifest.Entry(
                "https://openalex.s3.amazonaws.com/data/sources/updated_date=2024-01-02/part_000.gz",
                2048L,
                200)),
        3072L,
        300);
  }

  @Nested
  @DisplayName("数据存在性检查测试")
  class DataExistenceCheckTest {

    @Test
    @DisplayName("表中已有数据时 - 应该抛出 DataAlreadyExistsException")
    void shouldThrowException_whenDataAlreadyExists() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      when(venueRepository.hasAnyData()).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> orchestrator.importVenues(command))
          .isInstanceOf(DataAlreadyExistsException.class)
          .hasMessageContaining("Venue");

      // 验证没有进行后续操作
      verify(venueSourceFilePort, never()).fetchManifest();
      verify(venueImportBatchPort, never()).launchImport(any());
    }

    @Test
    @DisplayName("表中无数据时 - 应该正常执行导入")
    void shouldProceed_whenNoDataExists() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      // Mock fetchPartitionFile 为每个分区文件返回对应的本地路径
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-01/part_000.gz"))
          .thenReturn(TEST_FILE_1);
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-02/part_000.gz"))
          .thenReturn(TEST_FILE_2);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class))).thenReturn(12345L);

      // When
      VenueImportResult result = orchestrator.importVenues(command);

      // Then
      verify(venueRepository).hasAnyData();
      verify(venueSourceFilePort).fetchManifest();
      verify(venueSourceFilePort, times(2)).fetchPartitionFile(anyString());
      verify(venueImportBatchPort).launchImport(any(VenueImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(12345L);
    }
  }

  @Nested
  @DisplayName("importVenues() 方法测试")
  class ImportVenuesTest {

    @Test
    @DisplayName("正常导入 - 应该正确传递参数到 BatchPort")
    void shouldPassCorrectParamsToBatchPort() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-01/part_000.gz"))
          .thenReturn(TEST_FILE_1);
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-02/part_000.gz"))
          .thenReturn(TEST_FILE_2);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class))).thenReturn(12345L);

      // When
      orchestrator.importVenues(command);

      // Then
      ArgumentCaptor<VenueImportParams> captor = ArgumentCaptor.forClass(VenueImportParams.class);
      verify(venueImportBatchPort).launchImport(captor.capture());

      VenueImportParams params = captor.getValue();
      assertThat(params.tempFiles()).isTrue();
      assertThat(params.filePaths())
          .containsExactly(TEST_FILE_1.toString(), TEST_FILE_2.toString());
    }

    @Test
    @DisplayName("返回结果应该包含正确的分区数和记录数")
    void shouldReturnCorrectPartitionAndRecordCount() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-01/part_000.gz"))
          .thenReturn(TEST_FILE_1);
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-02/part_000.gz"))
          .thenReturn(TEST_FILE_2);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class))).thenReturn(12345L);

      // When
      VenueImportResult result = orchestrator.importVenues(command);

      // Then
      assertThat(result.fileCount()).isEqualTo(2);
      assertThat(result.totalRecordCount()).isEqualTo(300);
      assertThat(result.message()).isNotBlank();
    }

    @Test
    @DisplayName("fetchManifest 失败时应该抛出 ApplicationException")
    void shouldThrowExceptionWhenFetchManifestFails() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenThrow(new RuntimeException("网络连接失败"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importVenues(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("OpenAlex Venue 导入失败")
          .hasMessageContaining("网络连接失败");
    }

    @Test
    @DisplayName("fetchPartitionFile 失败时应该抛出 ApplicationException")
    void shouldThrowExceptionWhenFetchPartitionFileFails() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchPartitionFile(anyString()))
          .thenThrow(new RuntimeException("下载分区文件失败"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importVenues(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("OpenAlex Venue 导入失败")
          .hasMessageContaining("下载分区文件失败");
    }

    @Test
    @DisplayName("launchImport 失败时应该抛出 ApplicationException")
    void shouldThrowExceptionWhenLaunchImportFails() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-01/part_000.gz"))
          .thenReturn(TEST_FILE_1);
      when(venueSourceFilePort.fetchPartitionFile("updated_date=2024-01-02/part_000.gz"))
          .thenReturn(TEST_FILE_2);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class)))
          .thenThrow(new RuntimeException("Job 启动失败"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importVenues(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("OpenAlex Venue 导入失败")
          .hasMessageContaining("Job 启动失败");

      // 注意：临时文件清理是静态方法调用，无法在单元测试中验证
      // 实际清理行为需要在集成测试中验证
    }

    @Test
    @DisplayName("ApplicationException 应该直接抛出，不重复包装")
    void shouldRethrowApplicationExceptionWithoutWrapping() {
      // Given
      VenueImportCommand command = VenueImportCommand.create();
      // 使用匿名 ErrorCodeLike 避免依赖 api 模块
      ApplicationException originalException =
          new ApplicationException(
              new ErrorCodeLike() {
                @Override
                public String code() {
                  return "TEST_ERROR";
                }

                @Override
                public int httpStatus() {
                  return 500;
                }
              },
              "原始错误");

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenThrow(originalException);

      // When & Then
      assertThatThrownBy(() -> orchestrator.importVenues(command))
          .isInstanceOf(ApplicationException.class)
          .isSameAs(originalException);
    }
  }
}
