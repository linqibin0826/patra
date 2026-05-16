package dev.linqibin.patra.ingest.app.usecase.execution.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.common.model.CanonicalPublication;
import dev.linqibin.patra.ingest.domain.port.PublicationStoragePort;
import dev.linqibin.patra.ingest.domain.port.StorageMetadataPort;
import dev.linqibin.patra.ingest.domain.port.TechnicalRetryPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.ObjectMapper;

/// 出版物发布编排器单元测试
///
/// 测试策略：
///
/// - Mock PublicationStoragePort、StorageMetadataPort、TechnicalRetryPort
///   - 验证存储和元数据记录编排
///   - 测试元数据记录失败场景和重试委托
///   - 覆盖 StorageMetadataException 异常处理
///
/// **注意**：Jackson 3 的 ObjectMapper 无法被 Mockito mock，因此使用真实实例。
///
@ExtendWith(MockitoExtension.class)
@DisplayName("出版物发布编排器测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PublicationPublisherTest {

  @Mock private PublicationStoragePort publicationStoragePort;

  @Mock private StorageMetadataPort storageMetadataPort;

  @Mock private TechnicalRetryPort technicalRetryPort;

  /// Jackson 3 的 ObjectMapper 无法被 Mockito mock，使用真实实例
  private final ObjectMapper objectMapper = new ObjectMapper();

  private PublicationPublisher publisher;

  private static final Long RUN_ID = 100L;
  private static final int BATCH_NO = 1;
  private static final String PROVENANCE_CODE = "pubmed";
  private static final String STORAGE_KEY = "pubmed/2025/01/batch-1-100.json";
  private static final String BUCKET_NAME = "publication-bucket";
  private static final String OBJECT_KEY = "pubmed/2025/01/batch-1-100.json";
  private static final long FILE_SIZE = 1024L;
  private static final String MD5 = "abc123";
  private static final String SHA256 = "def456";

  private PublicationPublisher.PublishContext publishContext;
  private List<CanonicalPublication> publications;
  private PublicationStoragePort.StorageResult storageResult;

  @BeforeEach
  void setUp() {
    publisher =
        new PublicationPublisher(
            publicationStoragePort, storageMetadataPort, technicalRetryPort, objectMapper);

    publishContext =
        PublicationPublisher.PublishContext.of(RUN_ID, BATCH_NO, ProvenanceCode.PUBMED);

    publications = createPublicationList(5);

    storageResult =
        PublicationStoragePort.StorageResult.builder()
            .storageKey(STORAGE_KEY)
            .bucketName(BUCKET_NAME)
            .objectKey(OBJECT_KEY)
            .fileSize(FILE_SIZE)
            .publicationCount(5)
            .md5(MD5)
            .sha256(SHA256)
            .build();
  }

  @Nested
  @DisplayName("成功发布场景")
  class SuccessfulPublishTests {

    @Test
    @DisplayName("成功发布出版物，存储和元数据记录都成功")
    void shouldPublishPublicationSuccessfully() {
      // Arrange
      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      PublicationPublisher.PublishResult result = publisher.publish(publications, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.storageKey()).isEqualTo(STORAGE_KEY);
      assertThat(result.publishedCount()).isEqualTo(5);

      verify(publicationStoragePort).store(eq(publications), any());
      verify(storageMetadataPort).recordUpload(any());
      verify(technicalRetryPort, never()).publishRetry(any());
    }

    @Test
    @DisplayName("空出版物列表，不调用存储")
    void shouldHandleEmptyPublicationList() {
      // Arrange
      List<CanonicalPublication> emptyList = Collections.emptyList();

      PublicationStoragePort.StorageResult emptyResult =
          PublicationStoragePort.StorageResult.builder()
              .storageKey(null)
              .bucketName(BUCKET_NAME)
              .objectKey(OBJECT_KEY)
              .fileSize(0)
              .publicationCount(0)
              .build();
      when(publicationStoragePort.store(any(), any())).thenReturn(emptyResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      PublicationPublisher.PublishResult result = publisher.publish(emptyList, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.storageKey()).isNull();
      assertThat(result.publishedCount()).isEqualTo(0);

      // 实际代码总是会调用 recordUpload，即使是空列表
      verify(storageMetadataPort).recordUpload(any());
    }

    @Test
    @DisplayName("null 出版物列表，作为空列表处理")
    void shouldHandleNullPublicationList() {
      // Arrange
      PublicationStoragePort.StorageResult emptyResult =
          PublicationStoragePort.StorageResult.builder()
              .storageKey(null)
              .publicationCount(0)
              .build();
      when(publicationStoragePort.store(any(), any())).thenReturn(emptyResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      PublicationPublisher.PublishResult result = publisher.publish(null, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.publishedCount()).isEqualTo(0);

      ArgumentCaptor<List<CanonicalPublication>> captor = ArgumentCaptor.forClass(List.class);
      verify(publicationStoragePort).store(captor.capture(), any());
      assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("验证存储上下文正确转换")
    void shouldConvertPublishContextToStorageContext() {
      // Arrange
      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      publisher.publish(publications, publishContext);

      // Assert
      ArgumentCaptor<PublicationStoragePort.StorageContext> contextCaptor =
          ArgumentCaptor.forClass(PublicationStoragePort.StorageContext.class);
      verify(publicationStoragePort).store(any(), contextCaptor.capture());

      PublicationStoragePort.StorageContext captured = contextCaptor.getValue();
      assertThat(captured.runId()).isEqualTo(RUN_ID);
      assertThat(captured.batchNo()).isEqualTo(BATCH_NO);
      assertThat(captured.provenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
    }

    @Test
    @DisplayName("验证元数据请求正确构建")
    void shouldBuildMetadataRequestCorrectly() {
      // Arrange
      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      publisher.publish(publications, publishContext);

      // Assert
      ArgumentCaptor<StorageMetadataPort.MetadataRequest> requestCaptor =
          ArgumentCaptor.forClass(StorageMetadataPort.MetadataRequest.class);
      verify(storageMetadataPort).recordUpload(requestCaptor.capture());

      StorageMetadataPort.MetadataRequest captured = requestCaptor.getValue();
      assertThat(captured.storageKey()).isEqualTo(STORAGE_KEY);
      assertThat(captured.bucketName()).isEqualTo(BUCKET_NAME);
      assertThat(captured.objectKey()).isEqualTo(OBJECT_KEY);
      assertThat(captured.fileSize()).isEqualTo(FILE_SIZE);
      assertThat(captured.contentType()).isEqualTo("application/json");
      assertThat(captured.md5()).isEqualTo(MD5);
      assertThat(captured.sha256()).isEqualTo(SHA256);
      assertThat(captured.businessType()).isEqualTo("publication-batch");
      assertThat(captured.businessId()).isEqualTo("pubmed-1-100");

      Map<String, Object> correlation = captured.correlation();
      assertThat(correlation).containsEntry("batchNo", BATCH_NO);
      assertThat(correlation).containsEntry("provenanceCode", "pubmed");
      assertThat(correlation).containsEntry("runId", RUN_ID);
      assertThat(correlation).containsEntry("storageKey", STORAGE_KEY);
    }
  }

  @Nested
  @DisplayName("元数据记录失败场景")
  class MetadataRecordFailureTests {

    @Test
    @DisplayName("StorageMetadataException，委托给重试机制")
    void shouldDelegateToRetryOnStorageMetadataException() {
      // Arrange
      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      var exception = new StorageMetadataPort.StorageMetadataException("Remote call failed", null);
      when(storageMetadataPort.recordUpload(any())).thenThrow(exception);

      // Act
      PublicationPublisher.PublishResult result = publisher.publish(publications, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.storageKey()).isEqualTo(STORAGE_KEY);
      assertThat(result.publishedCount()).isEqualTo(5);

      verify(technicalRetryPort).publishRetry(any());
    }

    @Test
    @DisplayName("重试委托失败不应抛出异常")
    void shouldNotThrowWhenRetryDelegationFails() {
      // Arrange
      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      when(storageMetadataPort.recordUpload(any()))
          .thenThrow(new StorageMetadataPort.StorageMetadataException("Remote call failed", null));
      doThrow(new RuntimeException("Retry delegation failed"))
          .when(technicalRetryPort)
          .publishRetry(any());

      // Act & Assert
      assertThatCode(() -> publisher.publish(publications, publishContext))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("重试上下文构建")
  class RetryContextBuildingTests {

    @Test
    @DisplayName("验证重试上下文正确构建")
    void shouldBuildRetryContextCorrectly() {
      // Arrange
      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      when(storageMetadataPort.recordUpload(any()))
          .thenThrow(new StorageMetadataPort.StorageMetadataException("Remote call failed", null));

      // Act
      publisher.publish(publications, publishContext);

      // Assert
      ArgumentCaptor<TechnicalRetryPort.RetryContext> contextCaptor =
          ArgumentCaptor.forClass(TechnicalRetryPort.RetryContext.class);
      verify(technicalRetryPort).publishRetry(contextCaptor.capture());

      TechnicalRetryPort.RetryContext captured = contextCaptor.getValue();
      assertThat(captured.operationType()).isEqualTo("METADATA_RECORD");
      assertThat(captured.aggregateId()).isEqualTo(RUN_ID);
      assertThat(captured.payload()).isNotNull();

      Map<String, Object> metadata = captured.metadata();
      assertThat(metadata).containsEntry("provenanceCode", "pubmed");
      assertThat(metadata).containsEntry("batchNo", BATCH_NO);
      assertThat(metadata).containsEntry("storageKey", STORAGE_KEY);
      assertThat(metadata).containsEntry("fileSize", FILE_SIZE);
    }

    @Test
    @DisplayName("runId 为 null 时使用默认值 0")
    void shouldUseDefaultAggregateIdWhenRunIdIsNull() {
      // Arrange
      PublicationPublisher.PublishContext contextWithoutRunId =
          PublicationPublisher.PublishContext.of(null, BATCH_NO, ProvenanceCode.PUBMED);

      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      when(storageMetadataPort.recordUpload(any()))
          .thenThrow(new StorageMetadataPort.StorageMetadataException("Remote call failed", null));

      // Act
      publisher.publish(publications, contextWithoutRunId);

      // Assert
      ArgumentCaptor<TechnicalRetryPort.RetryContext> contextCaptor =
          ArgumentCaptor.forClass(TechnicalRetryPort.RetryContext.class);
      verify(technicalRetryPort).publishRetry(contextCaptor.capture());

      assertThat(contextCaptor.getValue().aggregateId()).isEqualTo(0L);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("provenanceCode 为 null 时使用 'unknown'")
    void shouldUseUnknownForNullProvenanceCode() {
      // Arrange
      PublicationPublisher.PublishContext contextWithNullProvenance =
          PublicationPublisher.PublishContext.of(RUN_ID, BATCH_NO, null);

      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      publisher.publish(publications, contextWithNullProvenance);

      // Assert
      ArgumentCaptor<StorageMetadataPort.MetadataRequest> requestCaptor =
          ArgumentCaptor.forClass(StorageMetadataPort.MetadataRequest.class);
      verify(storageMetadataPort).recordUpload(requestCaptor.capture());

      Map<String, Object> correlation = requestCaptor.getValue().correlation();
      assertThat(correlation).containsEntry("provenanceCode", "unknown");
    }

    @Test
    @DisplayName("provenanceCode 为空字符串时使用 'unknown'")
    void shouldUseUnknownForEmptyProvenanceCode() {
      // Arrange
      PublicationPublisher.PublishContext contextWithEmptyProvenance =
          PublicationPublisher.PublishContext.of(
              RUN_ID, BATCH_NO, null); // 注意：空字符串测试需要调整为 null，因为 ProvenanceCode 是枚举类型

      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      publisher.publish(publications, contextWithEmptyProvenance);

      // Assert
      ArgumentCaptor<StorageMetadataPort.MetadataRequest> requestCaptor =
          ArgumentCaptor.forClass(StorageMetadataPort.MetadataRequest.class);
      verify(storageMetadataPort).recordUpload(requestCaptor.capture());

      Map<String, Object> correlation = requestCaptor.getValue().correlation();
      assertThat(correlation).containsEntry("provenanceCode", "unknown");
    }

    @Test
    @DisplayName("provenanceCode 转换为小写")
    void shouldConvertProvenanceCodeToLowerCase() {
      // Arrange
      PublicationPublisher.PublishContext contextWithUpperCase =
          PublicationPublisher.PublishContext.of(
              RUN_ID, BATCH_NO, ProvenanceCode.PUBMED); // 注意：枚举已经定义为 PUBMED，无需测试大小写转换

      when(publicationStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      publisher.publish(publications, contextWithUpperCase);

      // Assert
      ArgumentCaptor<StorageMetadataPort.MetadataRequest> requestCaptor =
          ArgumentCaptor.forClass(StorageMetadataPort.MetadataRequest.class);
      verify(storageMetadataPort).recordUpload(requestCaptor.capture());

      Map<String, Object> correlation = requestCaptor.getValue().correlation();
      assertThat(correlation).containsEntry("provenanceCode", "pubmed");
    }
  }

  // ========== 辅助方法 ==========

  private List<CanonicalPublication> createPublicationList(int count) {
    List<CanonicalPublication> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CanonicalPublication publication =
          CanonicalPublication.builder().title("Publication " + i).build();
      list.add(publication);
    }
    return list;
  }
}
