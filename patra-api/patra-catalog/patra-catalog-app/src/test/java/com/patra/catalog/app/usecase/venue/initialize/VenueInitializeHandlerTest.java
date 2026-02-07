package com.patra.catalog.app.usecase.venue.initialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.initialize.command.VenueInitializeCommand;
import com.patra.catalog.app.usecase.venue.initialize.dto.VenueInitializeResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.model.vo.venue.VenueInitializeParams;
import com.patra.catalog.domain.port.batch.VenueInitializeBatchPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.VenueSourceFilePort;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
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

/// OpenAlex Venue 导入命令处理器单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock 所有 Port 依赖
/// - 测试隔离：每个测试方法独立
///
/// **重点测试场景**：
///
/// - 数据存在性检查：表中有数据时应拒绝导入
/// - 正常导入流程：获取 manifest → 传递 URL 列表 → 启动批处理
/// - 参数传递正确性验证
///
/// **临时文件下载架构**：
///
/// Handler 只负责获取 manifest 并提取分区 URL 列表，
/// 实际的分区文件下载由 ItemReader 按需完成。
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueInitializeHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueInitializeHandlerTest {

  private static final String PARTITION_URL_1 =
      "https://openalex.s3.amazonaws.com/data/sources/updated_date=2024-01-01/part_000.gz";
  private static final String PARTITION_URL_2 =
      "https://openalex.s3.amazonaws.com/data/sources/updated_date=2024-01-02/part_000.gz";

  @Mock private VenueSourceFilePort venueSourceFilePort;
  @Mock private VenueInitializeBatchPort venueImportBatchPort;
  @Mock private VenueRepository venueRepository;

  private VenueInitializeHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new VenueInitializeHandler(venueSourceFilePort, venueImportBatchPort, venueRepository);
  }

  /// 创建测试用的 OpenAlexManifest。
  private OpenAlexManifest createTestManifest() {
    return new OpenAlexManifest(
        List.of(
            new OpenAlexManifest.Entry(PARTITION_URL_1, 1024L, 100),
            new OpenAlexManifest.Entry(PARTITION_URL_2, 2048L, 200)),
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
      VenueInitializeCommand command = VenueInitializeCommand.create();
      when(venueRepository.hasAnyData()).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
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
      VenueInitializeCommand command = VenueInitializeCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueImportBatchPort.launchImport(any(VenueInitializeParams.class))).thenReturn(12345L);

      // When
      VenueInitializeResult result = handler.handle(command);

      // Then
      verify(venueRepository).hasAnyData();
      verify(venueSourceFilePort).fetchManifest();
      verify(venueImportBatchPort).launchImport(any(VenueInitializeParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(12345L);
    }
  }

  @Nested
  @DisplayName("importVenues() 方法测试")
  class ImportVenuesTest {

    @Test
    @DisplayName("正常导入 - 应该正确传递分区 URL 到 BatchPort")
    void shouldPassCorrectParamsToBatchPort() {
      // Given
      VenueInitializeCommand command = VenueInitializeCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueImportBatchPort.launchImport(any(VenueInitializeParams.class))).thenReturn(12345L);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<VenueInitializeParams> captor =
          ArgumentCaptor.forClass(VenueInitializeParams.class);
      verify(venueImportBatchPort).launchImport(captor.capture());

      VenueInitializeParams params = captor.getValue();
      assertThat(params.partitionUrls()).containsExactly(PARTITION_URL_1, PARTITION_URL_2);
      assertThat(params.getPartitionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("返回结果应该包含正确的分区数和记录数")
    void shouldReturnCorrectPartitionAndRecordCount() {
      // Given
      VenueInitializeCommand command = VenueInitializeCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueImportBatchPort.launchImport(any(VenueInitializeParams.class))).thenReturn(12345L);

      // When
      VenueInitializeResult result = handler.handle(command);

      // Then
      assertThat(result.fileCount()).isEqualTo(2);
      assertThat(result.totalRecordCount()).isEqualTo(300);
      assertThat(result.message()).isNotBlank();
    }

    @Test
    @DisplayName("fetchManifest 失败时应该抛出 ApplicationException")
    void shouldThrowExceptionWhenFetchManifestFails() {
      // Given
      VenueInitializeCommand command = VenueInitializeCommand.create();
      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenThrow(new RuntimeException("网络连接失败"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("OpenAlex Venue 导入失败")
          .hasMessageContaining("网络连接失败");
    }

    @Test
    @DisplayName("launchImport 失败时应该抛出 ApplicationException")
    void shouldThrowExceptionWhenLaunchImportFails() {
      // Given
      VenueInitializeCommand command = VenueInitializeCommand.create();
      OpenAlexManifest manifest = createTestManifest();

      when(venueRepository.hasAnyData()).thenReturn(false);
      when(venueSourceFilePort.fetchManifest()).thenReturn(manifest);
      when(venueImportBatchPort.launchImport(any(VenueInitializeParams.class)))
          .thenThrow(new RuntimeException("Job 启动失败"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("OpenAlex Venue 导入失败")
          .hasMessageContaining("Job 启动失败");
    }

    @Test
    @DisplayName("ApplicationException 应该直接抛出，不重复包装")
    void shouldRethrowApplicationExceptionWithoutWrapping() {
      // Given
      VenueInitializeCommand command = VenueInitializeCommand.create();
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
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .isSameAs(originalException);
    }
  }
}
