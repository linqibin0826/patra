package com.patra.catalog.infra.adapter.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.Identifier;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/// PubmedArticleItemReader 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleItemReader")
@ExtendWith(MockitoExtension.class)
class PubmedArticleItemReaderTest {

  @Mock private StreamingDownloadPort downloadPort;

  @Mock private PubmedXmlParserPort parserPort;

  @Mock private StreamingDownloadResult downloadResult;

  private PubmedArticleItemReader reader;

  private static final String DOWNLOAD_URL =
      "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed25n0001.xml.gz";

  @BeforeEach
  void setUp() {
    reader = new PubmedArticleItemReader(downloadPort, parserPort, DOWNLOAD_URL);
  }

  @Nested
  @DisplayName("open()")
  class OpenTest {

    @Test
    @DisplayName("应该下载文件并初始化解析器")
    void should_download_file_and_initialize_parser() throws Exception {
      // given
      InputStream mockGzipStream = createMockGzipStream();
      when(downloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(downloadResult.inputStream()).thenReturn(mockGzipStream);
      when(downloadResult.contentLength()).thenReturn(1000L);
      when(parserPort.parse(any())).thenReturn(Stream.empty());

      // when
      reader.open(new ExecutionContext());

      // then
      verify(downloadPort).download(URI.create(DOWNLOAD_URL));
      verify(parserPort).parse(any(InputStream.class));
    }

    @Test
    @DisplayName("应该从断点恢复，跳过已处理的记录")
    void should_resume_from_checkpoint_and_skip_processed_records() throws Exception {
      // given
      InputStream mockGzipStream = createMockGzipStream();
      when(downloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(downloadResult.inputStream()).thenReturn(mockGzipStream);

      CanonicalPublication publication1 = createPublication("1");
      CanonicalPublication publication2 = createPublication("2");
      CanonicalPublication publication3 = createPublication("3");
      when(parserPort.parse(any())).thenReturn(Stream.of(publication1, publication2, publication3));

      ExecutionContext context = new ExecutionContext();
      context.putInt("pubmed.article.current.index", 2);

      // when
      reader.open(context);

      // then - 应该跳过前 2 条，第一次 read 返回第 3 条
      CanonicalPublication result = reader.read();
      assertThat(result).isNotNull();
      assertThat(extractPmid(result)).isEqualTo("3");
    }
  }

  @Nested
  @DisplayName("read()")
  class ReadTest {

    @Test
    @DisplayName("应该逐条返回文献记录")
    void should_return_publications_one_by_one() throws Exception {
      // given
      InputStream mockGzipStream = createMockGzipStream();
      when(downloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(downloadResult.inputStream()).thenReturn(mockGzipStream);

      CanonicalPublication publication1 = createPublication("12345");
      CanonicalPublication publication2 = createPublication("67890");
      when(parserPort.parse(any())).thenReturn(Stream.of(publication1, publication2));

      reader.open(new ExecutionContext());

      // when & then
      assertThat(reader.read()).isEqualTo(publication1);
      assertThat(reader.read()).isEqualTo(publication2);
      assertThat(reader.read()).isNull(); // 读取完成返回 null
    }

    @Test
    @DisplayName("空流应该直接返回 null")
    void should_return_null_for_empty_stream() throws Exception {
      // given
      InputStream mockGzipStream = createMockGzipStream();
      when(downloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(downloadResult.inputStream()).thenReturn(mockGzipStream);
      when(parserPort.parse(any())).thenReturn(Stream.empty());

      reader.open(new ExecutionContext());

      // when
      CanonicalPublication result = reader.read();

      // then
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("update()")
  class UpdateTest {

    @Test
    @DisplayName("应该保存当前进度到 ExecutionContext")
    void should_save_current_index_to_execution_context() throws Exception {
      // given
      InputStream mockGzipStream = createMockGzipStream();
      when(downloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(downloadResult.inputStream()).thenReturn(mockGzipStream);

      CanonicalPublication publication = createPublication("12345");
      when(parserPort.parse(any())).thenReturn(Stream.of(publication));

      ExecutionContext context = new ExecutionContext();
      reader.open(context);

      // 读取一条记录
      reader.read();

      // when
      reader.update(context);

      // then
      assertThat(context.getInt("pubmed.article.current.index")).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("close()")
  class CloseTest {

    @Test
    @DisplayName("应该关闭流和 HTTP 连接")
    void should_close_stream_and_http_connection() throws Exception {
      // given
      InputStream mockGzipStream = createMockGzipStream();
      when(downloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(downloadResult.inputStream()).thenReturn(mockGzipStream);
      when(parserPort.parse(any())).thenReturn(Stream.empty());

      reader.open(new ExecutionContext());

      // when
      reader.close();

      // then
      verify(downloadResult).close();
    }
  }

  /// 创建测试用的 CanonicalPublication。
  private CanonicalPublication createPublication(String pmid) {
    return CanonicalPublication.builder()
        .identifiers(List.of(Identifier.builder().type("pmid").value(pmid).build()))
        .title("Test Article " + pmid)
        .build();
  }

  /// 从 CanonicalPublication 中提取 PMID。
  private String extractPmid(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> "pmid".equals(id.getType()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  /// 创建模拟的 gzip 输入流。
  ///
  /// 由于我们 mock 了 parserPort，实际不需要真正的 gzip 数据。
  /// 但为了让 GZIPInputStream 初始化成功，我们返回一个有效的 gzip 流。
  private InputStream createMockGzipStream() throws Exception {
    // 创建一个最小的有效 gzip 流
    // gzip 魔数：0x1f 0x8b，加上压缩空内容
    byte[] minimalGzip = {
      0x1f,
      (byte) 0x8b, // magic number
      0x08, // compression method (deflate)
      0x00, // flags
      0x00,
      0x00,
      0x00,
      0x00, // modification time
      0x00, // extra flags
      (byte) 0xff, // OS (unknown)
      0x03,
      0x00, // compressed data (empty)
      0x00,
      0x00,
      0x00,
      0x00, // CRC32
      0x00,
      0x00,
      0x00,
      0x00 // uncompressed size
    };
    return new ByteArrayInputStream(minimalGzip);
  }
}
