package com.patra.catalog.infra.batch.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.port.parser.MeshScrParserPort;
import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
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

/// MeshScrItemReader 单元测试。
///
/// 测试 Spring Batch ItemStreamReader 的生命周期和断点续传功能。
///
/// **测试策略**：
///
/// - 单元测试：Mock FileDownloadPort 和 MeshScrParserPort
/// - 使用 @TempDir 创建临时文件模拟下载结果
/// - 测试覆盖：open()、read()、update()、close() 生命周期
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshScrItemReader 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshScrItemReaderTest {

  private static final String TEST_DOWNLOAD_URL =
      "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/supp2025.xml";
  private static final String TEST_MESH_VERSION = "2025";
  private static final String CURRENT_INDEX_KEY = "mesh.scr.current.index";

  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private MeshScrParserPort scrParserPort;

  @TempDir Path tempDir;

  private MeshScrItemReader itemReader;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    executionContext = new ExecutionContext();
  }

  @AfterEach
  void tearDown() {
    if (itemReader != null) {
      itemReader.close();
    }
  }

  /// 创建临时 XML 文件并返回 FileDownloadResult。
  private FileDownloadResult createTempFileResult() throws Exception {
    String dummyXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <SupplementalRecordSet/>
        """;
    Path tempFile = tempDir.resolve("test-scr.xml");
    Files.writeString(tempFile, dummyXml);
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  /// 创建测试用的 MeshScrAggregate。
  private MeshScrAggregate createTestScr(String ui, String name) {
    return MeshScrAggregate.create(MeshUI.of(ui), name);
  }

  @Nested
  @DisplayName("open() 测试")
  class OpenTest {

    @Test
    @DisplayName("正常下载并解析 - 应该成功初始化")
    void open_validUrl_shouldInitializeSuccessfully() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempFileResult());
      when(scrParserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      itemReader =
          new MeshScrItemReader(
              fileDownloadPort, scrParserPort, TEST_DOWNLOAD_URL, TEST_MESH_VERSION);

      // When & Then: 不应该抛出异常
      itemReader.open(executionContext);
    }

    @Test
    @DisplayName("从断点恢复 - 应该跳过已处理记录")
    void open_withCheckpoint_shouldSkipProcessedRecords() throws Exception {
      // Given
      executionContext.putInt(CURRENT_INDEX_KEY, 2);

      Stream<MeshScrAggregate> mockStream =
          Stream.of(
              createTestScr("C000001", "SCR 1"),
              createTestScr("C000002", "SCR 2"),
              createTestScr("C000003", "SCR 3"),
              createTestScr("C000004", "SCR 4"),
              createTestScr("C000005", "SCR 5"));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempFileResult());
      when(scrParserPort.parse(any(InputStream.class))).thenReturn(mockStream);

      itemReader =
          new MeshScrItemReader(
              fileDownloadPort, scrParserPort, TEST_DOWNLOAD_URL, TEST_MESH_VERSION);

      // When
      itemReader.open(executionContext);

      // Then: 应该从第 3 条开始
      MeshScrAggregate result = itemReader.read();
      assertThat(result).isNotNull();
      assertThat(result.getUi().ui()).isEqualTo("C000003");
    }
  }

  @Nested
  @DisplayName("read() 测试")
  class ReadTest {

    @Test
    @DisplayName("逐条读取 - 应该返回所有记录")
    void read_multipleRecords_shouldReturnAllRecords() throws Exception {
      // Given
      Stream<MeshScrAggregate> mockStream =
          Stream.of(
              createTestScr("C000001", "SCR 1"),
              createTestScr("C000002", "SCR 2"),
              createTestScr("C000003", "SCR 3"));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempFileResult());
      when(scrParserPort.parse(any(InputStream.class))).thenReturn(mockStream);

      itemReader =
          new MeshScrItemReader(
              fileDownloadPort, scrParserPort, TEST_DOWNLOAD_URL, TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When & Then
      assertThat(itemReader.read().getUi().ui()).isEqualTo("C000001");
      assertThat(itemReader.read().getUi().ui()).isEqualTo("C000002");
      assertThat(itemReader.read().getUi().ui()).isEqualTo("C000003");
      assertThat(itemReader.read()).isNull();
    }

    @Test
    @DisplayName("空流 - 应该立即返回 null")
    void read_emptyStream_shouldReturnNull() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempFileResult());
      when(scrParserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      itemReader =
          new MeshScrItemReader(
              fileDownloadPort, scrParserPort, TEST_DOWNLOAD_URL, TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When & Then
      assertThat(itemReader.read()).isNull();
    }
  }

  @Nested
  @DisplayName("update() 测试")
  class UpdateTest {

    @Test
    @DisplayName("保存进度 - 应该更新 ExecutionContext")
    void update_afterReading_shouldUpdateExecutionContext() throws Exception {
      // Given
      Stream<MeshScrAggregate> mockStream =
          Stream.of(
              createTestScr("C000001", "SCR 1"),
              createTestScr("C000002", "SCR 2"),
              createTestScr("C000003", "SCR 3"));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempFileResult());
      when(scrParserPort.parse(any(InputStream.class))).thenReturn(mockStream);

      itemReader =
          new MeshScrItemReader(
              fileDownloadPort, scrParserPort, TEST_DOWNLOAD_URL, TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When
      itemReader.read();
      itemReader.read();
      itemReader.update(executionContext);

      // Then
      assertThat(executionContext.getInt(CURRENT_INDEX_KEY)).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("close() 测试")
  class CloseTest {

    @Test
    @DisplayName("关闭 Reader - 应该删除临时文件")
    void close_afterOpen_shouldDeleteTempFile() throws Exception {
      // Given
      Path tempFile = tempDir.resolve("test-close.xml");
      Files.writeString(tempFile, "<root/>");
      FileDownloadResult downloadResult = FileDownloadResult.of(tempFile, Files.size(tempFile));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(scrParserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      itemReader =
          new MeshScrItemReader(
              fileDownloadPort, scrParserPort, TEST_DOWNLOAD_URL, TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When
      itemReader.close();
      itemReader = null;

      // Then
      assertThat(tempFile).doesNotExist();
    }

    @Test
    @DisplayName("重复关闭 - 不应该抛出异常")
    void close_calledTwice_shouldNotThrowException() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempFileResult());
      when(scrParserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      itemReader =
          new MeshScrItemReader(
              fileDownloadPort, scrParserPort, TEST_DOWNLOAD_URL, TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When & Then
      itemReader.close();
      itemReader.close();
      itemReader = null;
    }
  }
}
