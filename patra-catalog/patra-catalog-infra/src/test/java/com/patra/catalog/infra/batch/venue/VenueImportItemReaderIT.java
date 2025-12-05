package com.patra.catalog.infra.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;

/// VenueImportItemReader 集成测试。
///
/// 测试 Spring Batch ItemStreamReader 的多文件读取和断点续传功能。
///
/// **测试策略**：
///
/// - 使用真实的 OpenAlexSourceParser 进行解析
/// - 创建临时 .gz 文件模拟真实数据
/// - 测试生命周期：open()、read()、update()、close()
///
/// **重点测试场景**：
///
/// - 单文件读取
/// - 多文件顺序读取
/// - 断点续传（文件内和跨文件）
/// - 空文件处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueImportItemReader 集成测试")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class VenueImportItemReaderIT {

  private static final String FILE_INDEX_KEY = "venue.import.file.index";
  private static final String LINE_INDEX_KEY = "venue.import.line.index";

  @TempDir Path tempDir;

  private OpenAlexSourceParser parser;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    parser = new OpenAlexSourceParser();
    executionContext = new ExecutionContext();
  }

  /// 创建 GZIP 压缩的 JSON Lines 文件。
  private Path createGzipJsonLinesFile(String fileName, String... jsonLines) throws IOException {
    Path file = tempDir.resolve(fileName);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
      for (String line : jsonLines) {
        gzos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
      }
    }
    Files.write(file, baos.toByteArray());
    return file;
  }

  /// 创建测试用的 OpenAlex Source JSON。
  private String createSourceJson(String id, String displayName) {
    return String.format(
        """
        {"id":"https://openalex.org/%s","display_name":"%s","type":"journal"}""",
        id, displayName);
  }

  @Nested
  @DisplayName("单文件读取测试")
  class SingleFileReadTest {

    @Test
    @DisplayName("读取单个文件 - 应该返回所有记录")
    void read_singleFile_shouldReturnAllRecords() throws Exception {
      // Given: 创建包含 3 条记录的文件
      Path file =
          createGzipJsonLinesFile(
              "part_000.gz",
              createSourceJson("S1", "Journal A"),
              createSourceJson("S2", "Journal B"),
              createSourceJson("S3", "Journal C"));

      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(file));
      reader.open(executionContext);

      // When: 读取所有记录
      List<VenueAggregate> results = new ArrayList<>();
      VenueAggregate item;
      while ((item = reader.read()) != null) {
        results.add(item);
      }

      // Then: 应该返回 3 条记录
      assertThat(results).hasSize(3);
      assertThat(results.get(0).getOpenalexId()).isEqualTo("S1");
      assertThat(results.get(0).getDisplayName()).isEqualTo("Journal A");
      assertThat(results.get(1).getOpenalexId()).isEqualTo("S2");
      assertThat(results.get(2).getOpenalexId()).isEqualTo("S3");

      reader.close();
    }

    @Test
    @DisplayName("空文件列表 - read() 应该立即返回 null")
    void read_emptyFileList_shouldReturnNull() throws Exception {
      // Given: 空文件列表
      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of());
      reader.open(executionContext);

      // When & Then: 应该立即返回 null
      assertThat(reader.read()).isNull();

      reader.close();
    }
  }

  @Nested
  @DisplayName("多文件读取测试")
  class MultiFileReadTest {

    @Test
    @DisplayName("顺序读取多个文件 - 应该返回所有文件的记录")
    void read_multipleFiles_shouldReturnAllRecordsInOrder() throws Exception {
      // Given: 创建 2 个文件
      Path file1 =
          createGzipJsonLinesFile(
              "part_000.gz",
              createSourceJson("S1", "Journal A"),
              createSourceJson("S2", "Journal B"));
      Path file2 =
          createGzipJsonLinesFile(
              "part_001.gz",
              createSourceJson("S3", "Journal C"),
              createSourceJson("S4", "Journal D"));

      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(file1, file2));
      reader.open(executionContext);

      // When: 读取所有记录
      List<VenueAggregate> results = new ArrayList<>();
      VenueAggregate item;
      while ((item = reader.read()) != null) {
        results.add(item);
      }

      // Then: 应该返回 4 条记录，顺序正确
      assertThat(results).hasSize(4);
      assertThat(results.get(0).getOpenalexId()).isEqualTo("S1");
      assertThat(results.get(1).getOpenalexId()).isEqualTo("S2");
      assertThat(results.get(2).getOpenalexId()).isEqualTo("S3");
      assertThat(results.get(3).getOpenalexId()).isEqualTo("S4");

      reader.close();
    }

    @Test
    @DisplayName("包含空文件 - 应该跳过空文件继续读取")
    void read_withEmptyFile_shouldSkipAndContinue() throws Exception {
      // Given: 创建包含空文件的列表
      Path file1 = createGzipJsonLinesFile("part_000.gz", createSourceJson("S1", "Journal A"));
      Path emptyFile = createGzipJsonLinesFile("part_001.gz"); // 空文件
      Path file3 = createGzipJsonLinesFile("part_002.gz", createSourceJson("S2", "Journal B"));

      VenueImportItemReader reader =
          new VenueImportItemReader(parser, List.of(file1, emptyFile, file3));
      reader.open(executionContext);

      // When: 读取所有记录
      List<VenueAggregate> results = new ArrayList<>();
      VenueAggregate item;
      while ((item = reader.read()) != null) {
        results.add(item);
      }

      // Then: 应该返回 2 条记录
      assertThat(results).hasSize(2);
      assertThat(results.get(0).getOpenalexId()).isEqualTo("S1");
      assertThat(results.get(1).getOpenalexId()).isEqualTo("S2");

      reader.close();
    }
  }

  @Nested
  @DisplayName("断点续传测试")
  class CheckpointResumeTest {

    @Test
    @DisplayName("update() - 应该保存文件索引和行索引")
    void update_shouldSaveFileAndLineIndex() throws Exception {
      // Given: 创建包含多条记录的文件
      Path file =
          createGzipJsonLinesFile(
              "part_000.gz",
              createSourceJson("S1", "Journal A"),
              createSourceJson("S2", "Journal B"),
              createSourceJson("S3", "Journal C"));

      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(file));
      reader.open(executionContext);

      // When: 读取 2 条后保存进度
      reader.read();
      reader.read();
      reader.update(executionContext);

      // Then: ExecutionContext 应该保存正确的索引
      assertThat(executionContext.getInt(FILE_INDEX_KEY)).isEqualTo(0);
      assertThat(executionContext.getInt(LINE_INDEX_KEY)).isEqualTo(2);

      reader.close();
    }

    @Test
    @DisplayName("从断点恢复（文件内） - 应该跳过已处理记录")
    void resume_withinFile_shouldSkipProcessedRecords() throws Exception {
      // Given: 创建文件
      Path file =
          createGzipJsonLinesFile(
              "part_000.gz",
              createSourceJson("S1", "Journal A"),
              createSourceJson("S2", "Journal B"),
              createSourceJson("S3", "Journal C"));

      // 模拟已处理 2 条
      executionContext.putInt(FILE_INDEX_KEY, 0);
      executionContext.putInt(LINE_INDEX_KEY, 2);

      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(file));
      reader.open(executionContext);

      // When: 读取第一条
      VenueAggregate result = reader.read();

      // Then: 应该是第 3 条记录
      assertThat(result).isNotNull();
      assertThat(result.getOpenalexId()).isEqualTo("S3");

      // 读取完成
      assertThat(reader.read()).isNull();

      reader.close();
    }

    @Test
    @DisplayName("从断点恢复（跨文件） - 应该从正确的文件和行开始")
    void resume_acrossFiles_shouldStartFromCorrectPosition() throws Exception {
      // Given: 创建 2 个文件
      Path file1 =
          createGzipJsonLinesFile(
              "part_000.gz",
              createSourceJson("S1", "Journal A"),
              createSourceJson("S2", "Journal B"));
      Path file2 =
          createGzipJsonLinesFile(
              "part_001.gz",
              createSourceJson("S3", "Journal C"),
              createSourceJson("S4", "Journal D"));

      // 模拟已完成第一个文件，第二个文件处理了 1 条
      executionContext.putInt(FILE_INDEX_KEY, 1);
      executionContext.putInt(LINE_INDEX_KEY, 1);

      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(file1, file2));
      reader.open(executionContext);

      // When: 读取
      VenueAggregate result = reader.read();

      // Then: 应该是第二个文件的第 2 条记录（S4）
      assertThat(result).isNotNull();
      assertThat(result.getOpenalexId()).isEqualTo("S4");

      // 读取完成
      assertThat(reader.read()).isNull();

      reader.close();
    }

    @Test
    @DisplayName("完整断点续传流程 - 模拟中断恢复")
    void fullCheckpointResumeWorkflow() throws Exception {
      // Given: 创建 2 个文件
      Path file1 =
          createGzipJsonLinesFile(
              "part_000.gz",
              createSourceJson("S1", "Journal A"),
              createSourceJson("S2", "Journal B"));
      Path file2 =
          createGzipJsonLinesFile(
              "part_001.gz",
              createSourceJson("S3", "Journal C"),
              createSourceJson("S4", "Journal D"));

      List<Path> files = List.of(file1, file2);

      // === 第一阶段：处理 3 条后"中断" ===
      ExecutionContext context1 = new ExecutionContext();
      VenueImportItemReader reader1 = new VenueImportItemReader(parser, files);
      reader1.open(context1);

      reader1.read(); // S1
      reader1.read(); // S2
      reader1.read(); // S3
      reader1.update(context1);
      reader1.close();

      // 验证进度
      assertThat(context1.getInt(FILE_INDEX_KEY)).isEqualTo(1);
      assertThat(context1.getInt(LINE_INDEX_KEY)).isEqualTo(1);

      // === 第二阶段：从断点恢复 ===
      VenueImportItemReader reader2 = new VenueImportItemReader(parser, files);
      reader2.open(context1);

      // Then: 应该从 S4 开始
      VenueAggregate result = reader2.read();
      assertThat(result).isNotNull();
      assertThat(result.getOpenalexId()).isEqualTo("S4");

      // 读取完成
      assertThat(reader2.read()).isNull();

      reader2.close();
    }
  }

  @Nested
  @DisplayName("错误处理测试")
  class ErrorHandlingTest {

    @Test
    @DisplayName("文件不存在 - 应该抛出 ItemStreamException")
    void open_fileNotExists_shouldThrowItemStreamException() {
      // Given: 不存在的文件
      Path nonExistent = tempDir.resolve("non-existent.gz");

      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(nonExistent));

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> reader.open(executionContext))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("无法打开文件");
    }
  }

  @Nested
  @DisplayName("close() 测试")
  class CloseTest {

    @Test
    @DisplayName("正常关闭 - 不应该抛出异常")
    void close_afterOpen_shouldNotThrowException() throws Exception {
      // Given
      Path file = createGzipJsonLinesFile("part_000.gz", createSourceJson("S1", "Journal A"));
      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(file));
      reader.open(executionContext);

      // When & Then
      reader.close();
    }

    @Test
    @DisplayName("重复关闭 - 不应该抛出异常")
    void close_calledTwice_shouldNotThrowException() throws Exception {
      // Given
      Path file = createGzipJsonLinesFile("part_000.gz", createSourceJson("S1", "Journal A"));
      VenueImportItemReader reader = new VenueImportItemReader(parser, List.of(file));
      reader.open(executionContext);

      // When & Then
      reader.close();
      reader.close();
    }
  }
}
