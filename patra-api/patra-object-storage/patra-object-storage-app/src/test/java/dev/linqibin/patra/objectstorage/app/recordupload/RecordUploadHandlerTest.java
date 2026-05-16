package dev.linqibin.patra.objectstorage.app.recordupload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.linqibin.patra.objectstorage.domain.model.aggregate.FileMetadata;
import dev.linqibin.patra.objectstorage.domain.model.aggregate.FileMetadataTestDataBuilder;
import dev.linqibin.patra.objectstorage.domain.model.enums.FileStatus;
import dev.linqibin.patra.objectstorage.domain.model.enums.StorageProvider;
import dev.linqibin.patra.objectstorage.domain.port.FileMetadataRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// RecordUploadHandler 单元测试。
///
/// 测试策略：纯单元测试，使用 Mockito Mock {@link FileMetadataRepository}。
///
/// 测试覆盖：
///
/// - 核心功能：命令处理、聚合根创建、仓储保存
/// - 可选字段：contentType、expiresAt、ipAddress、recordRemarks、correlationData
/// - 存储提供商：MINIO、S3、OSS、COS
/// - 异常处理：null 命令
/// - 集成场景：完整字段、最小字段
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("RecordUploadHandler 单元测试")
class RecordUploadHandlerTest {

  @Mock private FileMetadataRepository repository;

  @InjectMocks private RecordUploadHandler handler;

  /// 配置 Repository Mock 的默认行为。
  ///
  /// 模拟数据库保存后返回带 ID 的实体。使用 lenient() 允许某些测试不使用此 stub（如异常测试）。
  @BeforeEach
  void setUp() {
    lenient()
        .when(repository.save(any(FileMetadata.class)))
        .thenAnswer(
            invocation -> {
              FileMetadata metadata = invocation.getArgument(0);
              // 使用 TestDataBuilder 构建 Mock 返回值，模拟数据库保存后的状态
              return FileMetadataTestDataBuilder.anActiveFile()
                  .id(123L)
                  .storageKey(metadata.getStorageKey())
                  .fileSize(metadata.getFileSize())
                  .checksum(metadata.getChecksum())
                  .context(metadata.getContext())
                  .provider(metadata.getProvider())
                  .status(metadata.getStatus())
                  .contentType(metadata.getContentType())
                  .uploadedAt(metadata.getUploadedAt())
                  .expiresAt(metadata.getExpiresAt())
                  .recordRemarks(metadata.getRecordRemarks())
                  .ipAddress(metadata.getIpAddress())
                  .version(0L)
                  .buildRestored();
            });
  }

  @Nested
  @DisplayName("核心功能测试")
  class CoreFunctionalityTests {

    @Test
    @DisplayName("handle - 有效命令应创建聚合根并保存")
    void handle_withValidCommand_shouldCreateAndSaveAggregate() {
      // Given
      RecordUploadCommand command = createFullCommand();

      // When
      RecordUploadResult result = handler.handle(command);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.metadataId()).isEqualTo(123L);
      assertThat(result.recordedAt()).isNotNull();

      // 验证 repository.save() 被调用
      verify(repository, times(1)).save(any(FileMetadata.class));
    }

    @Test
    @DisplayName("handle - 仓储保存成功应返回正确的元数据ID")
    void handle_whenRepositorySaveSucceeds_shouldReturnCorrectMetadataId() {
      // Given
      RecordUploadCommand command = createMinimalCommand();

      // When
      RecordUploadResult result = handler.handle(command);

      // Then
      assertThat(result.metadataId()).isEqualTo(123L);
      assertThat(result.recordedAt()).isNotNull();
    }

    @Test
    @DisplayName("handle - 应正确传递命令参数到聚合根")
    void handle_shouldPassCommandParametersToAggregate() {
      // Given
      RecordUploadCommand command =
          new RecordUploadCommand(
              "test-bucket",
              "test/object/key.pdf",
              1024000L,
              "application/pdf",
              "md5hash",
              "sha256hash",
              "patra-ingest",
              "publication_batch",
              "batch-001",
              Map.of("pmcId", "PMC123456"),
              "MINIO",
              Instant.now().plus(30, ChronoUnit.DAYS),
              new byte[] {127, 0, 0, 1},
              "测试上传");

      // When
      handler.handle(command);

      // Then - 使用 ArgumentCaptor 捕获传递给 Repository 的聚合根
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured).isNotNull();
      assertThat(captured.getStorageKey().bucket()).isEqualTo("test-bucket");
      assertThat(captured.getStorageKey().objectKey()).isEqualTo("test/object/key.pdf");
      assertThat(captured.getFileSize().bytes()).isEqualTo(1024000L);
      assertThat(captured.getChecksum().md5Hash()).isEqualTo("md5hash");
      assertThat(captured.getChecksum().sha256Hash()).isEqualTo("sha256hash");
      assertThat(captured.getContext().serviceName()).isEqualTo("patra-ingest");
      assertThat(captured.getContext().businessType()).isEqualTo("publication_batch");
      assertThat(captured.getContext().businessId()).isEqualTo("batch-001");
      assertThat(captured.getContext().correlationData()).containsEntry("pmcId", "PMC123456");
      assertThat(captured.getProvider()).isEqualTo(StorageProvider.MINIO);
      assertThat(captured.getStatus()).isEqualTo(FileStatus.ACTIVE);
    }
  }

  @Nested
  @DisplayName("可选字段配置测试")
  class OptionalFieldsTests {

    @Test
    @DisplayName("handle - 包含 ContentType 应配置到聚合根")
    void handle_withContentType_shouldConfigureToAggregate() {
      // Given
      RecordUploadCommand command = createFullCommand();

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("handle - 包含过期时间应配置到聚合根")
    void handle_withExpiresAt_shouldConfigureToAggregate() {
      // Given
      Instant expectedExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
      RecordUploadCommand command =
          new RecordUploadCommand(
              "test-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "test",
              "test-001",
              Map.of(),
              "MINIO",
              expectedExpiry,
              null,
              null);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getExpiresAt()).isEqualTo(expectedExpiry);
    }

    @Test
    @DisplayName("handle - 包含 IP 地址应配置到聚合根")
    void handle_withIpAddress_shouldConfigureToAggregate() {
      // Given
      byte[] ipAddress = {127, 0, 0, 1}; // 127.0.0.1
      RecordUploadCommand command =
          new RecordUploadCommand(
              "test-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "test",
              "test-001",
              Map.of(),
              "MINIO",
              null,
              ipAddress,
              null);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    @DisplayName("handle - 包含备注应配置到聚合根")
    void handle_withRemarks_shouldConfigureToAggregate() {
      // Given
      String remarks = "{\"source\":\"api\",\"version\":\"v1\"}";
      RecordUploadCommand command =
          new RecordUploadCommand(
              "test-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "test",
              "test-001",
              Map.of(),
              "MINIO",
              null,
              null,
              remarks);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getRecordRemarks()).isEqualTo(remarks);
    }

    @Test
    @DisplayName("handle - 包含关联数据应传递给业务上下文")
    void handle_withCorrelationData_shouldPassToBusinessContext() {
      // Given
      Map<String, Object> correlationData =
          Map.of(
              "pmcId", "PMC123456",
              "doi", "10.1234/example",
              "year", 2024);

      RecordUploadCommand command =
          new RecordUploadCommand(
              "test-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "publication_batch",
              "batch-001",
              correlationData,
              "MINIO",
              null,
              null,
              null);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getContext().correlationData())
          .containsEntry("pmcId", "PMC123456")
          .containsEntry("doi", "10.1234/example")
          .containsEntry("year", 2024);
    }

    @Test
    @DisplayName("handle - 可选字段为 null 应正确处理")
    void handle_withNullOptionalFields_shouldHandleCorrectly() {
      // Given
      RecordUploadCommand command = createMinimalCommand();

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getContentType()).isNull();
      assertThat(captured.getExpiresAt()).isNull();
      assertThat(captured.getIpAddress()).isNull();
      assertThat(captured.getRecordRemarks()).isNull();
      assertThat(captured.getChecksum().sha256Hash()).isNull();
    }
  }

  @Nested
  @DisplayName("存储提供商测试")
  class StorageProviderTests {

    @Test
    @DisplayName("handle - MINIO 提供商应正确映射")
    void handle_withMinioProvider_shouldMapCorrectly() {
      // Given
      RecordUploadCommand command =
          new RecordUploadCommand(
              "test-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "test",
              "test-001",
              Map.of(),
              "MINIO",
              null,
              null,
              null);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getProvider()).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    @DisplayName("handle - S3 提供商应正确映射")
    void handle_withS3Provider_shouldMapCorrectly() {
      // Given
      RecordUploadCommand command =
          new RecordUploadCommand(
              "aws-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "test",
              "test-001",
              Map.of(),
              "S3",
              null,
              null,
              null);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getProvider()).isEqualTo(StorageProvider.S3);
    }

    @Test
    @DisplayName("handle - OSS 提供商应正确映射")
    void handle_withOssProvider_shouldMapCorrectly() {
      // Given
      RecordUploadCommand command =
          new RecordUploadCommand(
              "aliyun-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "test",
              "test-001",
              Map.of(),
              "OSS",
              null,
              null,
              null);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getProvider()).isEqualTo(StorageProvider.OSS);
    }

    @Test
    @DisplayName("handle - COS 提供商应正确映射")
    void handle_withCosProvider_shouldMapCorrectly() {
      // Given
      RecordUploadCommand command =
          new RecordUploadCommand(
              "tencent-bucket",
              "test/key.pdf",
              1024L,
              null,
              "md5",
              null,
              "patra-ingest",
              "test",
              "test-001",
              Map.of(),
              "COS",
              null,
              null,
              null);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getProvider()).isEqualTo(StorageProvider.COS);
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("handle - null 命令应抛出 NullPointerException")
    void handle_withNullCommand_shouldThrowNullPointerException() {
      // When & Then
      assertThatNullPointerException()
          .isThrownBy(() -> handler.handle(null))
          .withMessage("command 不能为 null");

      // 验证 repository.save() 未被调用
      // 注意：无需显式验证，Mockito 的 UnnecessaryStubbingException 会自动检测未使用的 stub
    }
  }

  @Nested
  @DisplayName("集成场景测试")
  class IntegrationScenarioTests {

    @Test
    @DisplayName("handle - 文献批次上传场景完整字段测试")
    void handle_publicationBatchUpload_fullFieldsTest() {
      // Given - 模拟真实的文学批次上传场景
      Instant expiryDate = Instant.now().plus(90, ChronoUnit.DAYS);
      RecordUploadCommand command =
          new RecordUploadCommand(
              "patra-publication",
              "batches/batch-20240101/PMC123456.pdf",
              5242880L, // 5 MB
              "application/pdf",
              "e4d909c290d0fb1ca068ffaddf22cbd0",
              "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
              "patra-ingest",
              "publication_batch",
              "batch-20240101",
              Map.of(
                  "pmcId", "PMC123456",
                  "doi", "10.1234/example.2024.001",
                  "publishedYear", 2024,
                  "journal", "Nature"),
              "MINIO",
              expiryDate,
              new byte[] {(byte) 192, (byte) 168, 1, 100}, // 192.168.1.100
              "{\"source\":\"pubmed_api\",\"retry_count\":0,\"upload_duration_ms\":1234}");

      // When
      RecordUploadResult result = handler.handle(command);

      // Then - 验证返回结果
      assertThat(result).isNotNull();
      assertThat(result.metadataId()).isEqualTo(123L);
      assertThat(result.recordedAt()).isNotNull();

      // 验证聚合根完整性
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getStorageKey().bucket()).isEqualTo("patra-publication");
      assertThat(captured.getStorageKey().objectKey())
          .isEqualTo("batches/batch-20240101/PMC123456.pdf");
      assertThat(captured.getFileSize().bytes()).isEqualTo(5242880L);
      assertThat(captured.getContentType()).isEqualTo("application/pdf");
      assertThat(captured.getChecksum().md5Hash()).isEqualTo("e4d909c290d0fb1ca068ffaddf22cbd0");
      assertThat(captured.getChecksum().sha256Hash())
          .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
      assertThat(captured.getContext().serviceName()).isEqualTo("patra-ingest");
      assertThat(captured.getContext().businessType()).isEqualTo("publication_batch");
      assertThat(captured.getContext().businessId()).isEqualTo("batch-20240101");
      assertThat(captured.getContext().correlationData()).hasSize(4);
      assertThat(captured.getProvider()).isEqualTo(StorageProvider.MINIO);
      assertThat(captured.getExpiresAt()).isEqualTo(expiryDate);
      assertThat(captured.getIpAddress()).containsExactly((byte) 192, (byte) 168, 1, 100);
      assertThat(captured.getRecordRemarks())
          .contains("pubmed_api")
          .contains("retry_count")
          .contains("upload_duration_ms");
    }

    @Test
    @DisplayName("handle - 临时文件上传场景最小字段测试")
    void handle_tempFileUpload_minimalFieldsTest() {
      // Given - 模拟临时文件上传（只有必需字段）
      RecordUploadCommand command =
          new RecordUploadCommand(
              "temp-bucket",
              "temp/upload-abc123.tmp",
              2048L,
              null, // 无 contentType
              "098f6bcd4621d373cade4e832627b4f6",
              null, // 无 SHA-256
              "patra-api",
              "temp_upload",
              "upload-abc123",
              Map.of(), // 无关联数据
              "MINIO",
              null, // 无过期时间
              null, // 无 IP
              null); // 无备注

      // When
      RecordUploadResult result = handler.handle(command);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.metadataId()).isEqualTo(123L);
      assertThat(result.recordedAt()).isNotNull();

      // 验证最小字段配置
      ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
      verify(repository).save(captor.capture());

      FileMetadata captured = captor.getValue();
      assertThat(captured.getStorageKey().bucket()).isEqualTo("temp-bucket");
      assertThat(captured.getStorageKey().objectKey()).isEqualTo("temp/upload-abc123.tmp");
      assertThat(captured.getFileSize().bytes()).isEqualTo(2048L);
      assertThat(captured.getContentType()).isNull();
      assertThat(captured.getChecksum().md5Hash()).isEqualTo("098f6bcd4621d373cade4e832627b4f6");
      assertThat(captured.getChecksum().sha256Hash()).isNull();
      assertThat(captured.getContext().serviceName()).isEqualTo("patra-api");
      assertThat(captured.getContext().businessType()).isEqualTo("temp_upload");
      assertThat(captured.getContext().businessId()).isEqualTo("upload-abc123");
      assertThat(captured.getContext().correlationData()).isEmpty();
      assertThat(captured.getProvider()).isEqualTo(StorageProvider.MINIO);
      assertThat(captured.getExpiresAt()).isNull();
      assertThat(captured.getIpAddress()).isNull();
      assertThat(captured.getRecordRemarks()).isNull();
    }
  }

  // ========== 辅助方法 ==========

  /// 创建包含完整字段的命令对象。
  ///
  /// @return 完整配置的命令
  private RecordUploadCommand createFullCommand() {
    return new RecordUploadCommand(
        "test-bucket",
        "test/object/key.pdf",
        1024000L,
        "application/pdf",
        "md5hash",
        "sha256hash",
        "patra-ingest",
        "publication_batch",
        "batch-001",
        Map.of("pmcId", "PMC123456"),
        "MINIO",
        Instant.now().plus(30, ChronoUnit.DAYS),
        new byte[] {127, 0, 0, 1}, // 127.0.0.1
        "测试上传");
  }

  /// 创建只包含必需字段的命令对象。
  ///
  /// @return 最小配置的命令
  private RecordUploadCommand createMinimalCommand() {
    return new RecordUploadCommand(
        "test-bucket",
        "test/key.pdf",
        1024L,
        null, // contentType
        "md5",
        null, // sha256Hash
        "patra-ingest",
        "test",
        "test-001",
        Map.of(),
        "MINIO",
        null, // expiresAt
        null, // ipAddress
        null); // recordRemarks
  }
}
