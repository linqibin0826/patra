package com.patra.catalog.infra.adapter.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.trait.StandardErrorTrait;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
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

/// VenueInitializeItemReader 集成测试。
///
/// 测试 Spring Batch ItemStreamReader 的流式多文件读取和断点续传功能。
///
/// **测试策略**：
///
/// - Mock StreamingDownloadPort 返回 GZIP 压缩的 JSON Lines 数据
/// - 使用真实的 OpenAlexSourceParser 进行解析
/// - 测试生命周期：open()、read()、update()、close()
///
/// **重点测试场景**：
///
/// - 单文件读取
/// - 多文件顺序读取
/// - 断点续传（文件内和跨文件）
/// - 空文件处理
/// - 下载失败处理
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueInitializeItemReader 集成测试")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class VenueInitializeItemReaderIT {

  private static final String FILE_INDEX_KEY = "venue.import.file.index";
  private static final String LINE_INDEX_KEY = "venue.import.line.index";

  private static final String URL_1 = "https://openalex.s3.amazonaws.com/data/sources/part_000.gz";
  private static final String URL_2 = "https://openalex.s3.amazonaws.com/data/sources/part_001.gz";
  private static final String URL_3 = "https://openalex.s3.amazonaws.com/data/sources/part_002.gz";

  @Mock private StreamingDownloadPort streamingDownloadPort;

  private OpenAlexSourceParser parser;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    parser = new OpenAlexSourceParser();
    executionContext = new ExecutionContext();
  }

  /// 创建 GZIP 压缩的 JSON Lines 输入流。
  private InputStream createGzipJsonLinesStream(String... jsonLines) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
      for (String line : jsonLines) {
        gzos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
      }
    }
    return new ByteArrayInputStream(baos.toByteArray());
  }

  /// 创建 StreamingDownloadResult（模拟 HTTP 响应）。
  private StreamingDownloadResult createDownloadResult(String... jsonLines) throws Exception {
    return StreamingDownloadResult.of(createGzipJsonLinesStream(jsonLines));
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
      // Given: 配置 Mock 返回 3 条记录
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S1", "Journal A"),
                  createSourceJson("S2", "Journal B"),
                  createSourceJson("S3", "Journal C")));

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1));
      reader.open(executionContext);

      // When: 读取所有记录
      List<VenueParseResult> results = new ArrayList<>();
      VenueParseResult item;
      while ((item = reader.read()) != null) {
        results.add(item);
      }

      // Then: 应该返回 3 条记录
      assertThat(results).hasSize(3);
      // CQRS 最小聚合设计：openalexId 现在存储在 identifiers 中
      assertThat(results.get(0).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S1");
      assertThat(results.get(0).aggregate().getDisplayName()).isEqualTo("Journal A");
      assertThat(results.get(1).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S2");
      assertThat(results.get(2).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S3");

      reader.close();
    }

    @Test
    @DisplayName("空 URL 列表 - read() 应该立即返回 null")
    void read_emptyUrlList_shouldReturnNull() throws Exception {
      // Given: 空 URL 列表
      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of());
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
      // Given: 配置 Mock 为每个 URL 返回数据
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S1", "Journal A"), createSourceJson("S2", "Journal B")));
      when(streamingDownloadPort.download(URI.create(URL_2)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S3", "Journal C"), createSourceJson("S4", "Journal D")));

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1, URL_2));
      reader.open(executionContext);

      // When: 读取所有记录
      List<VenueParseResult> results = new ArrayList<>();
      VenueParseResult item;
      while ((item = reader.read()) != null) {
        results.add(item);
      }

      // Then: 应该返回 4 条记录，顺序正确
      assertThat(results).hasSize(4);
      assertThat(results.get(0).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S1");
      assertThat(results.get(1).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S2");
      assertThat(results.get(2).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S3");
      assertThat(results.get(3).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S4");

      reader.close();
    }

    @Test
    @DisplayName("包含空文件 - 应该跳过空文件继续读取")
    void read_withEmptyFile_shouldSkipAndContinue() throws Exception {
      // Given: 中间有一个空文件
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(createDownloadResult(createSourceJson("S1", "Journal A")));
      when(streamingDownloadPort.download(URI.create(URL_2)))
          .thenReturn(createDownloadResult()); // 空文件
      when(streamingDownloadPort.download(URI.create(URL_3)))
          .thenReturn(createDownloadResult(createSourceJson("S2", "Journal B")));

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(
              streamingDownloadPort, parser, List.of(URL_1, URL_2, URL_3));
      reader.open(executionContext);

      // When: 读取所有记录
      List<VenueParseResult> results = new ArrayList<>();
      VenueParseResult item;
      while ((item = reader.read()) != null) {
        results.add(item);
      }

      // Then: 应该返回 2 条记录
      assertThat(results).hasSize(2);
      assertThat(results.get(0).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S1");
      assertThat(results.get(1).aggregate().getIdentifier(VenueIdentifierType.OPENALEX))
          .hasValue("S2");

      reader.close();
    }
  }

  @Nested
  @DisplayName("断点续传测试")
  class CheckpointResumeTest {

    @Test
    @DisplayName("update() - 应该保存文件索引和行索引")
    void update_shouldSaveFileAndLineIndex() throws Exception {
      // Given: 配置 Mock 返回多条记录
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S1", "Journal A"),
                  createSourceJson("S2", "Journal B"),
                  createSourceJson("S3", "Journal C")));

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1));
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
      // Given: 配置 Mock 返回数据
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S1", "Journal A"),
                  createSourceJson("S2", "Journal B"),
                  createSourceJson("S3", "Journal C")));

      // 模拟已处理 2 条
      executionContext.putInt(FILE_INDEX_KEY, 0);
      executionContext.putInt(LINE_INDEX_KEY, 2);

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1));
      reader.open(executionContext);

      // When: 读取第一条
      VenueParseResult result = reader.read();

      // Then: 应该是第 3 条记录
      assertThat(result).isNotNull();
      assertThat(result.aggregate().getIdentifier(VenueIdentifierType.OPENALEX)).hasValue("S3");

      // 读取完成
      assertThat(reader.read()).isNull();

      reader.close();
    }

    @Test
    @DisplayName("从断点恢复（跨文件） - 应该从正确的文件和行开始")
    void resume_acrossFiles_shouldStartFromCorrectPosition() throws Exception {
      // Given: 配置 Mock（注意：第一个文件不会被访问）
      lenient()
          .when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S1", "Journal A"), createSourceJson("S2", "Journal B")));
      when(streamingDownloadPort.download(URI.create(URL_2)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S3", "Journal C"), createSourceJson("S4", "Journal D")));

      // 模拟已完成第一个文件，第二个文件处理了 1 条
      executionContext.putInt(FILE_INDEX_KEY, 1);
      executionContext.putInt(LINE_INDEX_KEY, 1);

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1, URL_2));
      reader.open(executionContext);

      // When: 读取
      VenueParseResult result = reader.read();

      // Then: 应该是第二个文件的第 2 条记录（S4）
      assertThat(result).isNotNull();
      assertThat(result.aggregate().getIdentifier(VenueIdentifierType.OPENALEX)).hasValue("S4");

      // 读取完成
      assertThat(reader.read()).isNull();

      reader.close();
    }

    @Test
    @DisplayName("完整断点续传流程 - 模拟中断恢复")
    void fullCheckpointResumeWorkflow() throws Exception {
      // Given: 配置 Mock
      List<String> urls = List.of(URL_1, URL_2);

      // 第一阶段使用的 Mock
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S1", "Journal A"), createSourceJson("S2", "Journal B")));
      when(streamingDownloadPort.download(URI.create(URL_2)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S3", "Journal C"), createSourceJson("S4", "Journal D")));

      // === 第一阶段：处理 3 条后"中断" ===
      ExecutionContext context1 = new ExecutionContext();
      VenueInitializeItemReader reader1 =
          new VenueInitializeItemReader(streamingDownloadPort, parser, urls);
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
      // 重新配置 Mock（因为 InputStream 只能读取一次）
      when(streamingDownloadPort.download(URI.create(URL_2)))
          .thenReturn(
              createDownloadResult(
                  createSourceJson("S3", "Journal C"), createSourceJson("S4", "Journal D")));

      VenueInitializeItemReader reader2 =
          new VenueInitializeItemReader(streamingDownloadPort, parser, urls);
      reader2.open(context1);

      // Then: 应该从 S4 开始
      VenueParseResult result = reader2.read();
      assertThat(result).isNotNull();
      assertThat(result.aggregate().getIdentifier(VenueIdentifierType.OPENALEX)).hasValue("S4");

      // 读取完成
      assertThat(reader2.read()).isNull();

      reader2.close();
    }
  }

  @Nested
  @DisplayName("错误处理测试")
  class ErrorHandlingTest {

    @Test
    @DisplayName("下载失败 - 应该抛出 ItemStreamException")
    void open_downloadFails_shouldThrowItemStreamException() {
      // Given: Mock 下载失败
      when(streamingDownloadPort.download(any(URI.class)))
          .thenThrow(
              new FileDownloadException(
                  "网络连接失败", new RuntimeException("Timeout"), StandardErrorTrait.TIMEOUT));

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1));

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> reader.open(executionContext))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("无法下载分区文件");
    }
  }

  @Nested
  @DisplayName("close() 测试")
  class CloseTest {

    @Test
    @DisplayName("正常关闭 - 不应该抛出异常")
    void close_afterOpen_shouldNotThrowException() throws Exception {
      // Given
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(createDownloadResult(createSourceJson("S1", "Journal A")));

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1));
      reader.open(executionContext);

      // When & Then
      reader.close();
    }

    @Test
    @DisplayName("重复关闭 - 不应该抛出异常")
    void close_calledTwice_shouldNotThrowException() throws Exception {
      // Given
      when(streamingDownloadPort.download(URI.create(URL_1)))
          .thenReturn(createDownloadResult(createSourceJson("S1", "Journal A")));

      VenueInitializeItemReader reader =
          new VenueInitializeItemReader(streamingDownloadPort, parser, List.of(URL_1));
      reader.open(executionContext);

      // When & Then
      reader.close();
      reader.close();
    }
  }
}
