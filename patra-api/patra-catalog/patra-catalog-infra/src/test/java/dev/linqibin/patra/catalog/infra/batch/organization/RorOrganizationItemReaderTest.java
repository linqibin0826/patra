package dev.linqibin.patra.catalog.infra.batch.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

/// RorOrganizationItemReader 单元测试。
///
/// **测试策略**：
///
/// - 使用 Mock 的 FileDownloadPort 模拟文件下载
/// - 使用 @TempDir 创建临时文件（JSON 或 ZIP）模拟下载结果
/// - 使用真实的 RorOrganizationParser 验证端到端解析逻辑
/// - 验证断点续传（ExecutionContext 保存/恢复）
///
/// **重点测试场景**：
///
/// - 正常读取 JSON 数组
/// - ZIP 文件解压并读取内部 JSON
/// - 断点续传（跳过已处理记录）
/// - 临时文件正确删除
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("RorOrganizationItemReader 单元测试")
class RorOrganizationItemReaderTest {

  private static final String TEST_URL = "https://example.com/v2.0-2025-12-16-ror-data.zip";
  private static final String TEST_VERSION = "v2.0";
  private static final String CURRENT_INDEX_KEY = "ror.organization.current.index";

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

  @Mock private FileDownloadPort fileDownloadPort;

  @TempDir Path tempDir;

  private RorOrganizationParser parser;
  private RorOrganizationItemReader reader;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    parser = new RorOrganizationParser();
    executionContext = new ExecutionContext();
  }

  @AfterEach
  void tearDown() {
    if (reader != null) {
      reader.close();
    }
  }

  /// 创建临时 JSON 文件并返回 FileDownloadResult。
  private FileDownloadResult createJsonFileResult(String json) throws Exception {
    Path tempFile = tempDir.resolve("test-ror.json");
    Files.writeString(tempFile, json, StandardCharsets.UTF_8);
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  /// 创建临时 ZIP 文件（包含 JSON）并返回 FileDownloadResult。
  private FileDownloadResult createZipFileResult(String json) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("v2.0-2025-12-16-ror-data.json"));
      zos.write(json.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    Path tempFile = tempDir.resolve("test-ror.zip");
    Files.write(tempFile, baos.toByteArray());
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  // ==================== 正常读取测试 ====================

  @Nested
  @DisplayName("正常读取测试")
  class NormalReadingTests {

    @Test
    @DisplayName("应该从纯 JSON 文件读取组织数据")
    void shouldReadFromJsonFile() throws Exception {
      // Given - 使用纯 JSON URL（不含 .zip）
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(fileDownloadPort.download(any(URI.class)))
          .thenReturn(createJsonFileResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, jsonUrl, TEST_VERSION);

      // When
      reader.open(executionContext);
      OrganizationAggregate first = reader.read();
      OrganizationAggregate second = reader.read();
      OrganizationAggregate third = reader.read();
      reader.close();
      reader = null;

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
      when(fileDownloadPort.download(any(URI.class)))
          .thenReturn(createZipFileResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, TEST_URL, TEST_VERSION);

      // When
      reader.open(executionContext);
      OrganizationAggregate first = reader.read();
      OrganizationAggregate second = reader.read();
      reader.close();
      reader = null;

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
      when(fileDownloadPort.download(any(URI.class)))
          .thenReturn(createJsonFileResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, jsonUrl, TEST_VERSION);

      // When
      reader.open(executionContext);
      reader.read(); // 读取第 1 条
      reader.update(executionContext);

      // Then
      assertThat(executionContext.getInt(CURRENT_INDEX_KEY)).isEqualTo(1);

      // 继续读取并更新
      reader.read(); // 读取第 2 条
      reader.update(executionContext);

      assertThat(executionContext.getInt(CURRENT_INDEX_KEY)).isEqualTo(2);
    }

    @Test
    @DisplayName("应该从 ExecutionContext 恢复进度并跳过已处理记录")
    void shouldResumeFromCheckpoint() throws Exception {
      // Given - 模拟已处理 1 条记录
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(fileDownloadPort.download(any(URI.class)))
          .thenAnswer(invocation -> createJsonFileResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, jsonUrl, TEST_VERSION);
      executionContext.putInt(CURRENT_INDEX_KEY, 1); // 已处理 1 条

      // When
      reader.open(executionContext);
      OrganizationAggregate first = reader.read(); // 应该是第 2 条（跳过第 1 条）
      OrganizationAggregate second = reader.read();

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
      when(fileDownloadPort.download(any(URI.class))).thenThrow(new RuntimeException("网络连接失败"));

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, TEST_URL, TEST_VERSION);

      // When & Then
      assertThatThrownBy(() -> reader.open(executionContext))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("打开 ROR 机构数据流失败")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("无效 JSON 格式时应该抛出异常")
    void shouldThrowExceptionForInvalidJson() throws Exception {
      // Given
      String invalidJson = "{ invalid json }";
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createJsonFileResult(invalidJson));

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, jsonUrl, TEST_VERSION);

      // When & Then
      assertThatThrownBy(() -> reader.open(executionContext))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("打开 ROR 机构数据流失败");
    }

    @Test
    @DisplayName("ZIP 中无 JSON 文件时应该抛出异常")
    void shouldThrowExceptionWhenNoJsonInZip() throws Exception {
      // Given - 创建含非 JSON 文件的 ZIP
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ZipOutputStream zos = new ZipOutputStream(baos)) {
        zos.putNextEntry(new ZipEntry("readme.txt"));
        zos.write("Not a JSON file".getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }

      Path tempFile = tempDir.resolve("test-no-json.zip");
      Files.write(tempFile, baos.toByteArray());
      FileDownloadResult result = FileDownloadResult.of(tempFile, Files.size(tempFile));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(result);

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, TEST_URL, TEST_VERSION);

      // When & Then
      assertThatThrownBy(() -> reader.open(executionContext))
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
    @DisplayName("close() 应该删除临时文件")
    void close_afterOpen_shouldDeleteTempFile() throws Exception {
      // Given
      Path tempFile = tempDir.resolve("test-close.json");
      Files.writeString(tempFile, MINIMAL_ROR_JSON, StandardCharsets.UTF_8);
      FileDownloadResult downloadResult = FileDownloadResult.of(tempFile, Files.size(tempFile));

      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, jsonUrl, TEST_VERSION);
      reader.open(executionContext);

      // When
      reader.close();
      reader = null;

      // Then
      assertThat(tempFile).doesNotExist();
    }

    @Test
    @DisplayName("重复关闭 - 不应该抛出异常")
    void close_calledTwice_shouldNotThrowException() throws Exception {
      // Given
      String jsonUrl = "https://example.com/v2.0-2025-12-16-ror-data.json";
      when(fileDownloadPort.download(any(URI.class)))
          .thenReturn(createJsonFileResult(MINIMAL_ROR_JSON));

      reader = new RorOrganizationItemReader(fileDownloadPort, parser, jsonUrl, TEST_VERSION);
      reader.open(executionContext);

      // When & Then
      reader.close();
      reader.close();
      reader = null;
    }
  }
}
