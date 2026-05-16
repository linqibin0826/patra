package dev.linqibin.patra.catalog.infra.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import dev.linqibin.patra.common.model.CanonicalPublication;
import dev.linqibin.patra.common.model.CanonicalPublication.Identifier;
import dev.linqibin.patra.common.model.enums.PublicationIdentifierType;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
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

/// PubmedArticleItemReader 单元测试。
///
/// 测试 Spring Batch ItemStreamReader 的生命周期和断点续传功能。
///
/// **测试策略**：
///
/// - 单元测试：Mock FileDownloadPort 和 PubmedXmlParserPort
/// - 使用 @TempDir 创建临时 gzip 文件模拟下载结果
/// - 测试覆盖：open()、read()、update()、close() 生命周期
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleItemReader 单元测试")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PubmedArticleItemReaderTest {

  private static final String DOWNLOAD_URL =
      "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0001.xml.gz";
  private static final String CURRENT_INDEX_KEY = "pubmed.article.current.index";

  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private PubmedXmlParserPort parserPort;

  @TempDir Path tempDir;

  private PubmedArticleItemReader reader;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    executionContext = new ExecutionContext();
  }

  @AfterEach
  void tearDown() {
    if (reader != null) {
      reader.close();
    }
  }

  /// 创建临时 gzip 文件并返回 FileDownloadResult。
  ///
  /// 由于 Parser 已被 Mock，gzip 文件内容不重要，
  /// 但必须是有效的 gzip 格式以通过 GZIPInputStream 初始化。
  private FileDownloadResult createTempGzipFileResult() throws Exception {
    // 创建有效的 gzip 文件（压缩空 XML）
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
      gzos.write("<?xml version=\"1.0\"?><PubmedArticleSet/>".getBytes());
    }
    Path tempFile = tempDir.resolve("test-pubmed.xml.gz");
    Files.write(tempFile, baos.toByteArray());
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  /// 创建测试用的 CanonicalPublication。
  private CanonicalPublication createPublication(String pmid) {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(pmid).build()))
        .title("Test Article " + pmid)
        .build();
  }

  /// 从 CanonicalPublication 中提取 PMID。
  private String extractPmid(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> PublicationIdentifierType.PMID == id.getType())
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  @Nested
  @DisplayName("open() 测试")
  class OpenTest {

    @Test
    @DisplayName("正常下载并解析 - 应该成功初始化")
    void open_validUrl_shouldInitializeSuccessfully() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempGzipFileResult());
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      reader = new PubmedArticleItemReader(fileDownloadPort, parserPort, DOWNLOAD_URL);

      // When & Then: 不应该抛出异常
      reader.open(executionContext);
    }

    @Test
    @DisplayName("从断点恢复 - 应该跳过已处理记录")
    void open_withCheckpoint_shouldSkipProcessedRecords() throws Exception {
      // Given
      executionContext.putInt(CURRENT_INDEX_KEY, 2);

      Stream<CanonicalPublication> mockStream =
          Stream.of(
              createPublication("1"),
              createPublication("2"),
              createPublication("3"),
              createPublication("4"),
              createPublication("5"));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempGzipFileResult());
      when(parserPort.parse(any(InputStream.class))).thenReturn(mockStream);

      reader = new PubmedArticleItemReader(fileDownloadPort, parserPort, DOWNLOAD_URL);

      // When
      reader.open(executionContext);

      // Then: 应该从第 3 条开始
      CanonicalPublication result = reader.read();
      assertThat(result).isNotNull();
      assertThat(extractPmid(result)).isEqualTo("3");
    }
  }

  @Nested
  @DisplayName("read() 测试")
  class ReadTest {

    @Test
    @DisplayName("逐条读取 - 应该返回所有记录")
    void read_multipleRecords_shouldReturnAllRecords() throws Exception {
      // Given
      CanonicalPublication pub1 = createPublication("12345");
      CanonicalPublication pub2 = createPublication("67890");

      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempGzipFileResult());
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.of(pub1, pub2));

      reader = new PubmedArticleItemReader(fileDownloadPort, parserPort, DOWNLOAD_URL);
      reader.open(executionContext);

      // When & Then
      assertThat(reader.read()).isEqualTo(pub1);
      assertThat(reader.read()).isEqualTo(pub2);
      assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("空流 - 应该立即返回 null")
    void read_emptyStream_shouldReturnNull() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempGzipFileResult());
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      reader = new PubmedArticleItemReader(fileDownloadPort, parserPort, DOWNLOAD_URL);
      reader.open(executionContext);

      // When & Then
      assertThat(reader.read()).isNull();
    }
  }

  @Nested
  @DisplayName("update() 测试")
  class UpdateTest {

    @Test
    @DisplayName("保存进度 - 应该更新 ExecutionContext")
    void update_afterReading_shouldUpdateExecutionContext() throws Exception {
      // Given
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempGzipFileResult());
      when(parserPort.parse(any(InputStream.class)))
          .thenReturn(Stream.of(createPublication("1"), createPublication("2")));

      reader = new PubmedArticleItemReader(fileDownloadPort, parserPort, DOWNLOAD_URL);
      reader.open(executionContext);

      // When
      reader.read();
      reader.read();
      reader.update(executionContext);

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
      Path tempFile = tempDir.resolve("test-close.xml.gz");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
        gzos.write("<root/>".getBytes());
      }
      Files.write(tempFile, baos.toByteArray());
      FileDownloadResult downloadResult = FileDownloadResult.of(tempFile, Files.size(tempFile));

      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      reader = new PubmedArticleItemReader(fileDownloadPort, parserPort, DOWNLOAD_URL);
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
      when(fileDownloadPort.download(any(URI.class))).thenReturn(createTempGzipFileResult());
      when(parserPort.parse(any(InputStream.class))).thenReturn(Stream.empty());

      reader = new PubmedArticleItemReader(fileDownloadPort, parserPort, DOWNLOAD_URL);
      reader.open(executionContext);

      // When & Then
      reader.close();
      reader.close();
      reader = null;
    }
  }
}
