package dev.linqibin.patra.objectstorage.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.objectstorage.adapter.rest.internal.StorageEndpointImpl;
import dev.linqibin.patra.objectstorage.api.dto.RecordUploadResponse;
import dev.linqibin.patra.objectstorage.api.dto.UploadRecordRequest;
import dev.linqibin.patra.objectstorage.app.recordupload.RecordUploadCommand;
import dev.linqibin.patra.objectstorage.app.recordupload.RecordUploadResult;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// StorageEndpoint REST 接口集成测试。
///
/// 使用 Spring Boot 4.0 的 RestTestClient 进行 HTTP 层测试，验证：
///
/// - HTTP 请求/响应的序列化和反序列化
/// - 路径匹配和 Content-Type 处理
/// - 请求参数验证（@NotBlank、@PositiveOrZero 等）
/// - 异常处理和错误响应格式
///
/// 测试策略：
///
/// - IT 测试关注 HTTP 层行为（状态码、Content-Type、响应体结构）
/// - 验证细节（如字段级别错误消息）由单元测试覆盖
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest
@Import(StorageEndpointImpl.class)
@AutoConfigureRestTestClient
@DisplayName("StorageEndpoint REST 接口集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class StorageEndpointIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @Nested
  @DisplayName("POST /internal/storage/files/record")
  class RecordUploadTests {

    @Test
    @DisplayName("应该成功记录上传并返回 200 OK")
    void shouldRecordUploadAndReturn200() {
      // Given
      UploadRecordRequest request = createValidRequest();

      Instant recordedAt = Instant.parse("2024-01-15T10:30:00Z");
      RecordUploadResult result = new RecordUploadResult(12345L, recordedAt);
      when(commandBus.handle(any(RecordUploadCommand.class))).thenReturn(result);

      // When & Then
      restClient
          .post()
          .uri("/_internal/storage/files/record")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody(RecordUploadResponse.class)
          .value(
              resp -> {
                assertThat(resp.metadataId()).isEqualTo(12345L);
                assertThat(resp.recordedAt()).isEqualTo(recordedAt);
              });

      verify(commandBus).handle(any(RecordUploadCommand.class));
    }

    @Test
    @DisplayName("应该正确设置 Content-Type 为 application/json")
    void shouldSetCorrectContentType() {
      // Given
      UploadRecordRequest request = createValidRequest();

      Instant recordedAt = Instant.now();
      RecordUploadResult result = new RecordUploadResult(1L, recordedAt);
      when(commandBus.handle(any(RecordUploadCommand.class))).thenReturn(result);

      // When & Then
      restClient
          .post()
          .uri("/_internal/storage/files/record")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("当 bucketName 为空时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenBucketNameIsBlank() {
      // When & Then
      restClient
          .post()
          .uri("/_internal/storage/files/record")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "bucketName": "",
                        "objectKey": "path/to/file.xml",
                        "fileSize": 1024,
                        "contentType": "application/xml",
                        "md5Hash": "d41d8cd98f00b204e9800998ecf8427e",
                        "serviceName": "patra-ingest",
                        "businessType": "MESH_IMPORT",
                        "businessId": "task-001",
                        "providerType": "MINIO"
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }

    @Test
    @DisplayName("当 objectKey 为空时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenObjectKeyIsBlank() {
      // When & Then
      restClient
          .post()
          .uri("/_internal/storage/files/record")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "bucketName": "patra-ingest",
                        "objectKey": "   ",
                        "fileSize": 1024,
                        "contentType": "application/xml",
                        "md5Hash": "d41d8cd98f00b204e9800998ecf8427e",
                        "serviceName": "patra-ingest",
                        "businessType": "MESH_IMPORT",
                        "businessId": "task-001",
                        "providerType": "MINIO"
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }

    @Test
    @DisplayName("当 md5Hash 为空时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenMd5HashIsBlank() {
      // When & Then
      restClient
          .post()
          .uri("/_internal/storage/files/record")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "bucketName": "patra-ingest",
                        "objectKey": "path/to/file.xml",
                        "fileSize": 1024,
                        "contentType": "application/xml",
                        "md5Hash": "",
                        "serviceName": "patra-ingest",
                        "businessType": "MESH_IMPORT",
                        "businessId": "task-001",
                        "providerType": "MINIO"
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }

    @Test
    @DisplayName("当 fileSize 为负数时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenFileSizeIsNegative() {
      // When & Then
      restClient
          .post()
          .uri("/_internal/storage/files/record")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "bucketName": "patra-ingest",
                        "objectKey": "path/to/file.xml",
                        "fileSize": -1,
                        "contentType": "application/xml",
                        "md5Hash": "d41d8cd98f00b204e9800998ecf8427e",
                        "serviceName": "patra-ingest",
                        "businessType": "MESH_IMPORT",
                        "businessId": "task-001",
                        "providerType": "MINIO"
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }

    @Test
    @DisplayName("当多个必填字段缺失时应该返回 422 Unprocessable Entity")
    void shouldReturn422WhenMultipleRequiredFieldsMissing() {
      // When & Then
      restClient
          .post()
          .uri("/_internal/storage/files/record")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                    {
                        "bucketName": "patra-ingest",
                        "objectKey": "path/to/file.xml",
                        "fileSize": 1024,
                        "contentType": "application/xml",
                        "md5Hash": "d41d8cd98f00b204e9800998ecf8427e"
                    }
                    """)
          .exchange()
          .expectStatus()
          .isEqualTo(422);
    }
  }

  // ========== 测试数据构建助手 ==========

  private UploadRecordRequest createValidRequest() {
    return new UploadRecordRequest(
        "patra-ingest",
        "mesh/2024/desc2024.xml",
        1024000L,
        "application/xml",
        "d41d8cd98f00b204e9800998ecf8427e",
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "patra-ingest",
        "MESH_IMPORT",
        "task-12345",
        Map.of("year", 2024, "type", "descriptor"),
        "MINIO",
        Instant.parse("2025-01-15T00:00:00Z"),
        "Imported from NLM FTP");
  }
}
