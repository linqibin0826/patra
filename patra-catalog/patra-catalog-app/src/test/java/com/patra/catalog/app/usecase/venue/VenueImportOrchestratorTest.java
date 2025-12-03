package com.patra.catalog.app.usecase.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;
import com.patra.catalog.domain.model.enums.DataImportMode;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.model.vo.venue.VenueImportParams;
import com.patra.catalog.domain.port.VenueImportBatchPort;
import com.patra.catalog.domain.port.VenueRepository;
import com.patra.catalog.domain.port.VenueSourceFilePort;
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
/// - importVenues() INCREMENTAL 模式：不清空表，幂等执行
/// - importVenues() TRUNCATE_REIMPORT 模式：先清空再导入
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
  @DisplayName("importVenues() 方法测试")
  class ImportVenuesTest {

    @Test
    @DisplayName("INCREMENTAL 模式 - 不应该清空表")
    void incrementalMode_shouldNotTruncateTable() {
      // Given
      VenueImportCommand command = VenueImportCommand.incremental();
      OpenAlexManifest manifest = createTestManifest();
      List<Path> localFiles = List.of(TEST_FILE_1, TEST_FILE_2);

      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchAllPartitionFiles(manifest)).thenReturn(localFiles);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class))).thenReturn(12345L);

      // When
      VenueImportResult result = orchestrator.importVenues(command);

      // Then
      verify(venueSourceFilePort).fetchManifest();
      verify(venueSourceFilePort).fetchAllPartitionFiles(manifest);
      verify(venueRepository, never()).truncateAll();
      verify(venueImportBatchPort).launchImport(any(VenueImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(12345L);
      assertThat(result.mode()).isEqualTo(DataImportMode.INCREMENTAL);
    }

    @Test
    @DisplayName("INCREMENTAL 模式 - forceNewInstance 应该为 false")
    void incrementalMode_shouldSetForceNewInstanceFalse() {
      // Given
      VenueImportCommand command = VenueImportCommand.incremental();
      OpenAlexManifest manifest = createTestManifest();
      List<Path> localFiles = List.of(TEST_FILE_1, TEST_FILE_2);

      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchAllPartitionFiles(manifest)).thenReturn(localFiles);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class))).thenReturn(12345L);

      // When
      orchestrator.importVenues(command);

      // Then
      ArgumentCaptor<VenueImportParams> captor = ArgumentCaptor.forClass(VenueImportParams.class);
      verify(venueImportBatchPort).launchImport(captor.capture());

      VenueImportParams params = captor.getValue();
      assertThat(params.forceNewInstance()).isFalse();
      assertThat(params.filePaths())
          .containsExactly(TEST_FILE_1.toString(), TEST_FILE_2.toString());
    }

    @Test
    @DisplayName("TRUNCATE_REIMPORT 模式 - 应该先清空表再导入")
    void truncateReimportMode_shouldTruncateBeforeImport() {
      // Given
      VenueImportCommand command = VenueImportCommand.truncateReimport();
      OpenAlexManifest manifest = createTestManifest();
      List<Path> localFiles = List.of(TEST_FILE_1, TEST_FILE_2);

      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchAllPartitionFiles(manifest)).thenReturn(localFiles);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class))).thenReturn(67890L);

      // When
      VenueImportResult result = orchestrator.importVenues(command);

      // Then
      verify(venueSourceFilePort).fetchManifest();
      verify(venueSourceFilePort).fetchAllPartitionFiles(manifest);
      verify(venueRepository).truncateAll();
      verify(venueImportBatchPort).launchImport(any(VenueImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(67890L);
      assertThat(result.mode()).isEqualTo(DataImportMode.TRUNCATE_REIMPORT);
    }

    @Test
    @DisplayName("TRUNCATE_REIMPORT 模式 - forceNewInstance 应该为 true")
    void truncateReimportMode_shouldSetForceNewInstanceTrue() {
      // Given
      VenueImportCommand command = VenueImportCommand.truncateReimport();
      OpenAlexManifest manifest = createTestManifest();
      List<Path> localFiles = List.of(TEST_FILE_1, TEST_FILE_2);

      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchAllPartitionFiles(manifest)).thenReturn(localFiles);
      when(venueImportBatchPort.launchImport(any(VenueImportParams.class))).thenReturn(67890L);

      // When
      orchestrator.importVenues(command);

      // Then
      ArgumentCaptor<VenueImportParams> captor = ArgumentCaptor.forClass(VenueImportParams.class);
      verify(venueImportBatchPort).launchImport(captor.capture());

      VenueImportParams params = captor.getValue();
      assertThat(params.forceNewInstance()).isTrue();
      assertThat(params.filePaths())
          .containsExactly(TEST_FILE_1.toString(), TEST_FILE_2.toString());
    }

    @Test
    @DisplayName("返回结果应该包含正确的分区数和记录数")
    void shouldReturnCorrectPartitionAndRecordCount() {
      // Given
      VenueImportCommand command = VenueImportCommand.incremental();
      OpenAlexManifest manifest = createTestManifest();
      List<Path> localFiles = List.of(TEST_FILE_1, TEST_FILE_2);

      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchAllPartitionFiles(manifest)).thenReturn(localFiles);
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
      VenueImportCommand command = VenueImportCommand.incremental();
      when(venueSourceFilePort.fetchManifest()).thenThrow(new RuntimeException("网络连接失败"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importVenues(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("OpenAlex Venue 导入失败")
          .hasMessageContaining("网络连接失败");
    }

    @Test
    @DisplayName("fetchAllPartitionFiles 失败时应该抛出 ApplicationException")
    void shouldThrowExceptionWhenFetchPartitionFilesFails() {
      // Given
      VenueImportCommand command = VenueImportCommand.incremental();
      OpenAlexManifest manifest = createTestManifest();

      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchAllPartitionFiles(manifest))
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
      VenueImportCommand command = VenueImportCommand.incremental();
      OpenAlexManifest manifest = createTestManifest();
      List<Path> localFiles = List.of(TEST_FILE_1, TEST_FILE_2);

      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueSourceFilePort.fetchAllPartitionFiles(manifest)).thenReturn(localFiles);
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
      VenueImportCommand command = VenueImportCommand.incremental();
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

      when(venueSourceFilePort.fetchManifest()).thenThrow(originalException);

      // When & Then
      assertThatThrownBy(() -> orchestrator.importVenues(command))
          .isInstanceOf(ApplicationException.class)
          .isSameAs(originalException);
    }
  }
}
