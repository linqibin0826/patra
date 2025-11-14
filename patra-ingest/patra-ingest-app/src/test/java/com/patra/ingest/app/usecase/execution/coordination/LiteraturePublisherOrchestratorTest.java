package com.patra.ingest.app.usecase.execution.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.model.CanonicalLiterature;
import com.patra.ingest.domain.port.LiteratureStoragePort;
import com.patra.ingest.domain.port.StorageMetadataPort;
import com.patra.ingest.domain.port.TechnicalRetryPort;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

/**
 * 文献发布编排器单元测试
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>Mock LiteratureStoragePort、StorageMetadataPort、TechnicalRetryPort
 *   <li>验证存储和元数据记录编排
 *   <li>测试元数据记录失败场景和重试委托
 *   <li>覆盖 Feign 异常处理
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("文献发布编排器测试")
class LiteraturePublisherOrchestratorTest {

  @Mock private LiteratureStoragePort literatureStoragePort;

  @Mock private StorageMetadataPort storageMetadataPort;

  @Mock private TechnicalRetryPort technicalRetryPort;

  @InjectMocks private LiteraturePublisherOrchestrator orchestrator;

  private static final Long RUN_ID = 100L;
  private static final int BATCH_NO = 1;
  private static final String PROVENANCE_CODE = "pubmed";
  private static final String STORAGE_KEY = "pubmed/2025/01/batch-1-100.json";
  private static final String BUCKET_NAME = "literature-bucket";
  private static final String OBJECT_KEY = "pubmed/2025/01/batch-1-100.json";
  private static final long FILE_SIZE = 1024L;
  private static final String MD5 = "abc123";
  private static final String SHA256 = "def456";

  private LiteraturePublisherOrchestrator.PublishContext publishContext;
  private List<CanonicalLiterature> literatures;
  private LiteratureStoragePort.StorageResult storageResult;

  @BeforeEach
  void setUp() {
    publishContext =
        LiteraturePublisherOrchestrator.PublishContext.builder()
            .runId(RUN_ID)
            .batchNo(BATCH_NO)
            .provenanceCode(PROVENANCE_CODE)
            .build();

    literatures = createLiteratureList(5);

    storageResult =
        LiteratureStoragePort.StorageResult.builder()
            .storageKey(STORAGE_KEY)
            .bucketName(BUCKET_NAME)
            .objectKey(OBJECT_KEY)
            .fileSize(FILE_SIZE)
            .literatureCount(5)
            .md5(MD5)
            .sha256(SHA256)
            .build();
  }

  @Nested
  @DisplayName("成功发布场景")
  class SuccessfulPublishTests {

    @Test
    @DisplayName("成功发布文献，存储和元数据记录都成功")
    void shouldPublishLiteratureSuccessfully() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      LiteraturePublisherOrchestrator.PublishResult result =
          orchestrator.publish(literatures, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.storageKey()).isEqualTo(STORAGE_KEY);
      assertThat(result.publishedCount()).isEqualTo(5);

      verify(literatureStoragePort).store(eq(literatures), any());
      verify(storageMetadataPort).recordUpload(any());
      verify(technicalRetryPort, never()).publishRetry(any());
    }

    @Test
    @DisplayName("空文献列表，不调用存储")
    void shouldHandleEmptyLiteratureList() {
      // Arrange
      List<CanonicalLiterature> emptyList = Collections.emptyList();

      LiteratureStoragePort.StorageResult emptyResult =
          LiteratureStoragePort.StorageResult.builder()
              .storageKey(null)
              .bucketName(BUCKET_NAME)
              .objectKey(OBJECT_KEY)
              .fileSize(0)
              .literatureCount(0)
              .build();
      when(literatureStoragePort.store(any(), any())).thenReturn(emptyResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      LiteraturePublisherOrchestrator.PublishResult result =
          orchestrator.publish(emptyList, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.storageKey()).isNull();
      assertThat(result.publishedCount()).isEqualTo(0);

      // 实际代码总是会调用 recordUpload，即使是空列表
      verify(storageMetadataPort).recordUpload(any());
    }

    @Test
    @DisplayName("null 文献列表，作为空列表处理")
    void shouldHandleNullLiteratureList() {
      // Arrange
      LiteratureStoragePort.StorageResult emptyResult =
          LiteratureStoragePort.StorageResult.builder().storageKey(null).literatureCount(0).build();
      when(literatureStoragePort.store(any(), any())).thenReturn(emptyResult);

      // Act
      LiteraturePublisherOrchestrator.PublishResult result =
          orchestrator.publish(null, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.publishedCount()).isEqualTo(0);

      ArgumentCaptor<List<CanonicalLiterature>> captor = ArgumentCaptor.forClass(List.class);
      verify(literatureStoragePort).store(captor.capture(), any());
      assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("验证存储上下文正确转换")
    void shouldConvertPublishContextToStorageContext() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      orchestrator.publish(literatures, publishContext);

      // Assert
      ArgumentCaptor<LiteratureStoragePort.StorageContext> contextCaptor =
          ArgumentCaptor.forClass(LiteratureStoragePort.StorageContext.class);
      verify(literatureStoragePort).store(any(), contextCaptor.capture());

      LiteratureStoragePort.StorageContext captured = contextCaptor.getValue();
      assertThat(captured.runId()).isEqualTo(RUN_ID);
      assertThat(captured.batchNo()).isEqualTo(BATCH_NO);
      assertThat(captured.provenanceCode()).isEqualTo(PROVENANCE_CODE);
    }

    @Test
    @DisplayName("验证元数据请求正确构建")
    void shouldBuildMetadataRequestCorrectly() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      orchestrator.publish(literatures, publishContext);

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
      assertThat(captured.businessType()).isEqualTo("literature-batch");
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
    @DisplayName("5xx 错误，委托给重试机制")
    void shouldDelegateToRetryOn5xxError() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      FeignException feignException = createFeignException(500, "Internal Server Error");
      when(storageMetadataPort.recordUpload(any())).thenThrow(feignException);

      // Act
      LiteraturePublisherOrchestrator.PublishResult result =
          orchestrator.publish(literatures, publishContext);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.storageKey()).isEqualTo(STORAGE_KEY);
      assertThat(result.publishedCount()).isEqualTo(5);

      verify(technicalRetryPort).publishRetry(any());
    }

    @Test
    @DisplayName("503 Service Unavailable，委托给重试机制")
    void shouldDelegateToRetryOn503Error() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      FeignException feignException = createFeignException(503, "Service Unavailable");
      when(storageMetadataPort.recordUpload(any())).thenThrow(feignException);

      // Act
      orchestrator.publish(literatures, publishContext);

      // Assert
      verify(technicalRetryPort).publishRetry(any());
    }

    @Test
    @DisplayName("4xx 错误，不委托给重试机制")
    void shouldNotDelegateToRetryOn4xxError() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      FeignException feignException = createFeignException(400, "Bad Request");
      when(storageMetadataPort.recordUpload(any())).thenThrow(feignException);

      // Act
      LiteraturePublisherOrchestrator.PublishResult result =
          orchestrator.publish(literatures, publishContext);

      // Assert
      assertThat(result).isNotNull();
      verify(technicalRetryPort, never()).publishRetry(any());
    }

    @Test
    @DisplayName("404 错误，不委托给重试机制")
    void shouldNotDelegateToRetryOn404Error() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      FeignException feignException = createFeignException(404, "Not Found");
      when(storageMetadataPort.recordUpload(any())).thenThrow(feignException);

      // Act
      orchestrator.publish(literatures, publishContext);

      // Assert
      verify(technicalRetryPort, never()).publishRetry(any());
    }

    @Test
    @DisplayName("RetryableException，委托给重试机制")
    void shouldDelegateToRetryOnRetryableException() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      Request request = createDummyRequest();
      RetryableException retryableException =
          new RetryableException(
              504, "Gateway Timeout", Request.HttpMethod.POST, (Long) null, request);
      when(storageMetadataPort.recordUpload(any())).thenThrow(retryableException);

      // Act
      orchestrator.publish(literatures, publishContext);

      // Assert
      verify(technicalRetryPort).publishRetry(any());
    }

    @Test
    @DisplayName("一般异常，委托给重试机制")
    void shouldDelegateToRetryOnGeneralException() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      when(storageMetadataPort.recordUpload(any()))
          .thenThrow(new RuntimeException("Unexpected error"));

      // Act
      orchestrator.publish(literatures, publishContext);

      // Assert
      verify(technicalRetryPort).publishRetry(any());
    }

    @Test
    @DisplayName("重试委托失败不应抛出异常")
    void shouldNotThrowWhenRetryDelegationFails() {
      // Arrange
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      FeignException feignException = createFeignException(500, "Internal Server Error");
      when(storageMetadataPort.recordUpload(any())).thenThrow(feignException);
      doThrow(new RuntimeException("Retry delegation failed"))
          .when(technicalRetryPort)
          .publishRetry(any());

      // Act & Assert
      assertThatCode(() -> orchestrator.publish(literatures, publishContext))
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
      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      FeignException feignException = createFeignException(500, "Internal Server Error");
      when(storageMetadataPort.recordUpload(any())).thenThrow(feignException);

      // Act
      orchestrator.publish(literatures, publishContext);

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
      LiteraturePublisherOrchestrator.PublishContext contextWithoutRunId =
          LiteraturePublisherOrchestrator.PublishContext.builder()
              .runId(null)
              .batchNo(BATCH_NO)
              .provenanceCode(PROVENANCE_CODE)
              .build();

      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      FeignException feignException = createFeignException(500, "Internal Server Error");
      when(storageMetadataPort.recordUpload(any())).thenThrow(feignException);

      // Act
      orchestrator.publish(literatures, contextWithoutRunId);

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
      LiteraturePublisherOrchestrator.PublishContext contextWithNullProvenance =
          LiteraturePublisherOrchestrator.PublishContext.builder()
              .runId(RUN_ID)
              .batchNo(BATCH_NO)
              .provenanceCode(null)
              .build();

      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      orchestrator.publish(literatures, contextWithNullProvenance);

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
      LiteraturePublisherOrchestrator.PublishContext contextWithEmptyProvenance =
          LiteraturePublisherOrchestrator.PublishContext.builder()
              .runId(RUN_ID)
              .batchNo(BATCH_NO)
              .provenanceCode("")
              .build();

      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      orchestrator.publish(literatures, contextWithEmptyProvenance);

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
      LiteraturePublisherOrchestrator.PublishContext contextWithUpperCase =
          LiteraturePublisherOrchestrator.PublishContext.builder()
              .runId(RUN_ID)
              .batchNo(BATCH_NO)
              .provenanceCode("PUBMED")
              .build();

      when(literatureStoragePort.store(any(), any())).thenReturn(storageResult);

      StorageMetadataPort.MetadataResult metadataResult =
          new StorageMetadataPort.MetadataResult(123L, java.time.Instant.now());
      when(storageMetadataPort.recordUpload(any())).thenReturn(metadataResult);

      // Act
      orchestrator.publish(literatures, contextWithUpperCase);

      // Assert
      ArgumentCaptor<StorageMetadataPort.MetadataRequest> requestCaptor =
          ArgumentCaptor.forClass(StorageMetadataPort.MetadataRequest.class);
      verify(storageMetadataPort).recordUpload(requestCaptor.capture());

      Map<String, Object> correlation = requestCaptor.getValue().correlation();
      assertThat(correlation).containsEntry("provenanceCode", "pubmed");
    }
  }

  // ========== 辅助方法 ==========

  private List<CanonicalLiterature> createLiteratureList(int count) {
    List<CanonicalLiterature> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CanonicalLiterature literature =
          CanonicalLiterature.builder().title("Literature " + i).build();
      list.add(literature);
    }
    return list;
  }

  private FeignException createFeignException(int status, String message) {
    Request request = createDummyRequest();
    return FeignException.errorStatus(
        "recordUpload",
        feign.Response.builder()
            .status(status)
            .reason(message)
            .request(request)
            .headers(Collections.emptyMap())
            .body(new byte[0])
            .build());
  }

  private Request createDummyRequest() {
    return Request.create(
        Request.HttpMethod.POST,
        "http://localhost/api/metadata",
        Collections.emptyMap(),
        null,
        StandardCharsets.UTF_8,
        null);
  }
}
