package com.patra.catalog.infra.adapter.batch.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;

/// RorOrganizationItemReader 单元测试。
///
/// **测试策略**：
///
/// - 使用 Mock 的 StreamingDownloadPort 模拟 HTTP 下载
/// - 使用内存中的 JSON 数据验证解析逻辑
/// - 验证断点续传（ExecutionContext 保存/恢复）
///
/// **重点测试场景**：
///
/// - 正常流式读取 JSON 数组
/// - ZIP 文件解压并读取内部 JSON
/// - 断点续传（跳过已处理记录）
/// - 资源正确关闭
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("RorOrganizationItemReader 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class RorOrganizationItemReaderTest {

  private static final String TEST_URL = "https://example.com/v2.0-2025-12-16-ror-data.zip";
  private static final String TEST_VERSION = "v2.0";

  /// 最小化的 ROR 组织 JSON（包含必需字段）
  private static final String MINIMAL_ROR_JSON =
      """
      [
        {
          "id": "https://ror.org/00hj8s172",
          "names": [
            {"value": "Test University", "types": ["ror_display"]}
          ],
          "status": "active",
          "types": ["education"],
          "locations": [],
          "relationships": [],
          "external_ids": [],
          "links": [],
          "admin": {
            "created": {"date": "2024-01-01", "schema_version": "2.0"},
            "last_modified": {"date": "2024-06-01", "schema_version": "2.0"}
          }
        },
        {
          "id": "https://ror.org/01234abcd",
          "names": [
            {"value": "Another University", "types": ["ror_display"]}
          ],
          "status": "active",
          "types": ["education"],
          "locations": [],
          "relationships": [],
          "external_ids": [],
          "links": [],
          "admin": null
        }
      ]
      """;

  @Mock private StreamingDownloadPort streamingDownloadPort;

  private RorOrganizationParser parser;
  private RorOrganizationItemReader reader;

  @BeforeEach
  void setUp() {
    parser = new RorOrganizationParser();
  }

  /// 创建模拟的下载结果（纯 JSON）。
  private StreamingDownloadResult createJsonDownloadResult(String json) {
    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    return StreamingDownloadResult.of(inputStream);
  }

  /// 创建模拟的下载结果（ZIP 包含 JSON）。
  private StreamingDownloadResult createZipDownloadResult(String json) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("v2.0-2025-12-16-ror-data.json"));
      zos.write(json.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    byte[] zipBytes = baos.toByteArray();
    InputStream inputStream = new ByteArrayInputStream(zipBytes);
    return StreamingDownloadResult.of(inputStream);
  }

  // ==================== 正常读取测试 ====================

  @Nested
  @DisplayName("正常读取测试")
  class NormalReadingTests {

    @Test
    @DisplayName("应该从纯 JSON 文件流式读取组织数据")
    void shouldReadFromJsonFile() throws Exception {
      // Given - 使用纯 JSON URL（不含 .zip）
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(streamingDownloadPort.download(any(URI.class)))
          .thenReturn(createJsonDownloadResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, jsonUrl, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();

      // When
      reader.open(context);
      OrganizationAggregate first = reader.read();
      OrganizationAggregate second = reader.read();
      OrganizationAggregate third = reader.read();
      reader.close();

      // Then
      assertThat(first).isNotNull();
      assertThat(first.getRorId().getId()).isEqualTo("00hj8s172");
      assertThat(first.getDisplayName()).isEqualTo("Test University");

      assertThat(second).isNotNull();
      assertThat(second.getRorId().getId()).isEqualTo("01234abcd");
      assertThat(second.getDisplayName()).isEqualTo("Another University");

      assertThat(third).isNull(); // 读取完毕
    }

    @Test
    @DisplayName("应该从 ZIP 文件中提取并解析 JSON")
    void shouldReadFromZipFile() throws Exception {
      // Given
      when(streamingDownloadPort.download(any(URI.class)))
          .thenReturn(createZipDownloadResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, TEST_URL, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();

      // When
      reader.open(context);
      OrganizationAggregate first = reader.read();
      OrganizationAggregate second = reader.read();
      reader.close();

      // Then
      assertThat(first).isNotNull();
      assertThat(first.getRorId().getId()).isEqualTo("00hj8s172");

      assertThat(second).isNotNull();
      assertThat(second.getRorId().getId()).isEqualTo("01234abcd");
    }
  }

  // ==================== 断点续传测试 ====================

  @Nested
  @DisplayName("断点续传测试")
  class CheckpointTests {

    @Test
    @DisplayName("应该在 update() 中保存当前进度到 ExecutionContext")
    void shouldSaveProgressInUpdate() throws Exception {
      // Given
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(streamingDownloadPort.download(any(URI.class)))
          .thenReturn(createJsonDownloadResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, jsonUrl, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();

      // When
      reader.open(context);
      reader.read(); // 读取第 1 条
      reader.update(context);

      // Then
      assertThat(context.getInt("ror.organization.current.index")).isEqualTo(1);

      // 继续读取并更新
      reader.read(); // 读取第 2 条
      reader.update(context);

      assertThat(context.getInt("ror.organization.current.index")).isEqualTo(2);

      reader.close();
    }

    @Test
    @DisplayName("应该从 ExecutionContext 恢复进度并跳过已处理记录")
    void shouldResumeFromCheckpoint() throws Exception {
      // Given - 模拟已处理 1 条记录
      // 注意：每次测试需要创建新的 InputStream，因为 Mockito thenReturn 会复用同一个实例
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(streamingDownloadPort.download(any(URI.class)))
          .thenAnswer(
              invocation ->
                  StreamingDownloadResult.of(
                      new ByteArrayInputStream(MINIMAL_ROR_JSON.getBytes(StandardCharsets.UTF_8))));

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, jsonUrl, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();
      context.putInt("ror.organization.current.index", 1); // 已处理 1 条

      // When
      reader.open(context);
      OrganizationAggregate first = reader.read(); // 应该是第 2 条（跳过第 1 条）
      OrganizationAggregate second = reader.read();
      reader.close();

      // Then
      assertThat(first).isNotNull();
      assertThat(first.getRorId().getId()).isEqualTo("01234abcd"); // 第 2 条

      assertThat(second).isNull(); // 读取完毕
    }
  }

  // ==================== 异常处理测试 ====================

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("下载失败时应该抛出 ItemStreamException")
    void shouldThrowExceptionWhenDownloadFails() {
      // Given
      when(streamingDownloadPort.download(any(URI.class)))
          .thenThrow(new RuntimeException("网络连接失败"));

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, TEST_URL, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();

      // When & Then
      assertThatThrownBy(() -> reader.open(context))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("打开 ROR 机构数据流失败")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("无效 JSON 格式时应该抛出异常")
    void shouldThrowExceptionForInvalidJson() {
      // Given
      String invalidJson = "{ invalid json }";
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(streamingDownloadPort.download(any(URI.class)))
          .thenReturn(createJsonDownloadResult(invalidJson));

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, jsonUrl, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();

      // When & Then
      assertThatThrownBy(() -> reader.open(context))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("打开 ROR 机构数据流失败");
    }

    @Test
    @DisplayName("ZIP 中无 JSON 文件时应该抛出异常")
    void shouldThrowExceptionWhenNoJsonInZip() throws Exception {
      // Given - 创建空 ZIP
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ZipOutputStream zos = new ZipOutputStream(baos)) {
        zos.putNextEntry(new ZipEntry("readme.txt"));
        zos.write("Not a JSON file".getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }

      byte[] zipBytes = baos.toByteArray();
      StreamingDownloadResult result =
          StreamingDownloadResult.of(new ByteArrayInputStream(zipBytes));

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(result);

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, TEST_URL, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();

      // When & Then
      assertThatThrownBy(() -> reader.open(context))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("打开 ROR 机构数据流失败")
          .cause()
          .hasMessageContaining("ZIP 中未找到 JSON 数据文件");
    }
  }

  // ==================== 资源管理测试 ====================

  @Nested
  @DisplayName("资源管理测试")
  class ResourceManagementTests {

    @Test
    @DisplayName("close() 应该正确关闭 reader 而不抛出异常")
    void shouldCloseWithoutException() throws Exception {
      // Given
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      StreamingDownloadResult result =
          StreamingDownloadResult.of(
              new ByteArrayInputStream(MINIMAL_ROR_JSON.getBytes(StandardCharsets.UTF_8)));

      when(streamingDownloadPort.download(any(URI.class))).thenReturn(result);

      reader = new RorOrganizationItemReader(streamingDownloadPort, parser, jsonUrl, TEST_VERSION);
      ExecutionContext context = new ExecutionContext();

      // When
      reader.open(context);
      reader.read();
      reader.close();

      // Then - close() 应该正常完成，不抛出异常
      // StreamingDownloadResult.of() 创建的结果会在 close() 时关闭底层 InputStream
    }
  }
}
