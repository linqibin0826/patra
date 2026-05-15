package dev.linqibin.patra.catalog.infra.batch.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.domain.model.aggregate.AuthorAggregate;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import tools.jackson.databind.json.JsonMapper;

/// AuthorItemReader 单元测试。
///
/// **测试策略**：
///
/// - 使用 Mock 的 FileDownloadPort 模拟文件下载
/// - 使用 @TempDir 创建临时 JSON Lines 文件模拟下载结果
/// - 使用真实的 PubMedComputedAuthorParser 验证端到端解析逻辑
/// - 使用 PubMedComputedAuthorTestDataGenerator 生成测试数据
/// - 验证断点续传（ExecutionContext 保存/恢复）
/// - 验证 maxRecords 限制功能
/// - 验证临时文件正确删除
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorItemReader 单元测试")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class AuthorItemReaderTest {

  private static final String TEST_URL = "https://example.com/computed_authors.jsonl";
  private static final String CURRENT_INDEX_KEY = "author.import.current.index";

  @Mock private FileDownloadPort fileDownloadPort;

  @TempDir Path tempDir;

  private PubMedComputedAuthorParser parser;
  private AuthorItemReader reader;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    parser = new PubMedComputedAuthorParser(JsonMapper.builder().build());
    executionContext = new ExecutionContext();
  }

  @AfterEach
  void tearDown() {
    if (reader != null) {
      reader.close();
    }
  }

  /// 使用测试数据生成器创建 JSON Lines 临时文件。
  ///
  /// @param count 生成的记录数量
  /// @return FileDownloadResult
  private FileDownloadResult createTestDataFileResult(int count) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PubMedComputedAuthorTestDataGenerator.generate(count, baos);
    Path tempFile = tempDir.resolve("test-authors.jsonl");
    Files.write(tempFile, baos.toByteArray());
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  /// 创建包含指定 JSON Lines 内容的临时文件。
  ///
  /// @param lines JSON Lines 内容
  /// @return FileDownloadResult
  private FileDownloadResult createJsonLinesFileResult(String... lines) throws Exception {
    String content = String.join("\n", lines) + "\n";
    Path tempFile = tempDir.resolve("test-authors.jsonl");
    Files.writeString(tempFile, content, StandardCharsets.UTF_8);
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  // ==================== 正常读取测试 ====================

  @Nested
  @DisplayName("正常读取测试")
  class NormalReadingTests {

    @Test
    @DisplayName("应该正常读取 JSON Lines 并解析为 AuthorAggregate")
    void shouldReadJsonLinesAndParseToAuthorAggregate() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTestDataFileResult(3));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);

      // When
      reader.open(executionContext);
      AuthorAggregate first = reader.read();
      AuthorAggregate second = reader.read();
      AuthorAggregate third = reader.read();
      AuthorAggregate fourth = reader.read();

      // Then
      assertThat(first).isNotNull();
      assertThat(first.getNormalizedKey()).isNotNull();
      assertThat(second).isNotNull();
      assertThat(third).isNotNull();
      assertThat(fourth).isNull(); // 读取完毕
    }

    @Test
    @DisplayName("空文件应该直接返回 null")
    void shouldReturnNullForEmptyFile() throws Exception {
      // Given
      Path tempFile = tempDir.resolve("empty.jsonl");
      Files.writeString(tempFile, "", StandardCharsets.UTF_8);
      when(fileDownloadPort.download(any(URI.class)))
          .thenReturn(FileDownloadResult.of(tempFile, 0));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);

      // When
      reader.open(executionContext);
      AuthorAggregate result = reader.read();

      // Then
      assertThat(result).isNull();
    }
  }

  // ==================== maxRecords 限制测试 ====================

  @Nested
  @DisplayName("maxRecords 限制测试")
  class MaxRecordsTests {

    @Test
    @DisplayName("应该在达到 maxRecords 限制后停止读取")
    void shouldStopReadingWhenMaxRecordsReached() throws Exception {
      // Given - 生成 5 条记录，但只允许读取 2 条
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTestDataFileResult(5));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, 2L);

      // When
      reader.open(executionContext);
      AuthorAggregate first = reader.read();
      AuthorAggregate second = reader.read();
      AuthorAggregate third = reader.read();

      // Then
      assertThat(first).isNotNull();
      assertThat(second).isNotNull();
      assertThat(third).isNull(); // 达到 maxRecords=2，停止读取
    }

    @Test
    @DisplayName("maxRecords 为 null 时不限制")
    void shouldNotLimitWhenMaxRecordsIsNull() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTestDataFileResult(3));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);

      // When
      reader.open(executionContext);
      int count = 0;
      while (reader.read() != null) {
        count++;
      }

      // Then
      assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("maxRecords 为 0 时不限制")
    void shouldNotLimitWhenMaxRecordsIsZero() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTestDataFileResult(3));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, 0L);

      // When
      reader.open(executionContext);
      int count = 0;
      while (reader.read() != null) {
        count++;
      }

      // Then
      assertThat(count).isEqualTo(3);
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
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTestDataFileResult(5));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);

      // When
      reader.open(executionContext);
      reader.read(); // 读取第 1 条
      reader.update(executionContext);

      // Then
      assertThat(executionContext.getInt(CURRENT_INDEX_KEY)).isEqualTo(1);

      // 继续读取并更新
      reader.read(); // 读取第 2 条
      reader.read(); // 读取第 3 条
      reader.update(executionContext);

      assertThat(executionContext.getInt(CURRENT_INDEX_KEY)).isEqualTo(3);
    }

    @Test
    @DisplayName("应该从 ExecutionContext 恢复进度并跳过已处理记录")
    void shouldResumeFromCheckpoint() throws Exception {
      // Given - 生成 5 条记录
      when(fileDownloadPort.download(any(URI.class)))
          .thenAnswer(invocation -> createTestDataFileResult(5));

      // 第一轮：读取 2 条
      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);
      reader.open(executionContext);
      reader.read(); // 第 1 条
      reader.read(); // 第 2 条
      reader.update(executionContext);
      reader.close();
      reader = null;

      // 验证进度已保存
      assertThat(executionContext.getInt(CURRENT_INDEX_KEY)).isEqualTo(2);

      // 第二轮：从断点恢复
      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);
      reader.open(executionContext);

      // When - 应该从第 3 条开始读取
      int count = 0;
      while (reader.read() != null) {
        count++;
      }

      // Then - 应该读取剩余 3 条（第 3、4、5 条）
      assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("断点恢复时文件记录不足应该抛出异常")
    void shouldThrowExceptionWhenInsufficientRecordsForResume() throws Exception {
      // Given - 保存的进度是 10，但文件只有 3 条记录
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTestDataFileResult(3));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);
      executionContext.putInt(CURRENT_INDEX_KEY, 10);

      // When & Then
      assertThatThrownBy(() -> reader.open(executionContext))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("打开 PubMed Computed Authors 读取器失败")
          .cause()
          .hasMessageContaining("断点恢复失败");
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

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);

      // When & Then
      assertThatThrownBy(() -> reader.open(executionContext))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("打开 PubMed Computed Authors 读取器失败")
          .hasCauseInstanceOf(RuntimeException.class);
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
      Path tempFile = tempDir.resolve("test-close.jsonl");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PubMedComputedAuthorTestDataGenerator.generate(2, baos);
      Files.write(tempFile, baos.toByteArray());
      FileDownloadResult downloadResult = FileDownloadResult.of(tempFile, Files.size(tempFile));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);
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
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTestDataFileResult(2));

      reader = new AuthorItemReader(fileDownloadPort, parser, TEST_URL, null);
      reader.open(executionContext);

      // When & Then
      reader.close();
      reader.close();
      reader = null;
    }
  }
}
