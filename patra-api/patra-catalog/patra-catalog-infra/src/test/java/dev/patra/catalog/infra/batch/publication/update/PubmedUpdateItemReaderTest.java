package dev.patra.catalog.infra.batch.publication.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import dev.patra.catalog.domain.port.source.FileDownloadPort;
import dev.patra.catalog.domain.port.source.FileDownloadResult;
import dev.patra.common.CanonicalPublication;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/// `PubmedUpdateItemReader` 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("PubmedUpdateItemReader")
class PubmedUpdateItemReaderTest {

  private static final String URL =
      "https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/pubmed26n1335.xml.gz";

  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private PubmedXmlParserPort parserPort;
  @TempDir Path tempDir;

  private PubmedUpdateItemReader reader;

  @AfterEach
  void tearDown() {
    if (reader != null) {
      reader.close();
    }
  }

  @Test
  @DisplayName("读完后把删除 PMID 写进 ExecutionContext")
  void should_store_deleted_pmids_in_execution_context() throws Exception {
    when(fileDownloadPort.download(any(URI.class))).thenReturn(gzipFile());
    CanonicalPublication article = CanonicalPublication.builder().title("A").build();
    when(parserPort.parseUpdateFile(any(InputStream.class), any()))
        .thenAnswer(
            inv -> {
              Consumer<String> sink = inv.getArgument(1);
              return Stream.of(article)
                  .onClose(() -> {})
                  .peek(
                      a -> {
                        sink.accept("111");
                        sink.accept("222");
                      });
            });
    ExecutionContext ctx = new ExecutionContext();
    reader = new PubmedUpdateItemReader(fileDownloadPort, parserPort, URL);

    reader.open(ctx);
    assertThat(reader.read()).isSameAs(article);
    assertThat(reader.read()).isNull();
    reader.update(ctx);

    assertThat(ctx.get(PubmedUpdateContextKeys.DELETED_PMIDS)).isEqualTo(List.of("111", "222"));
    assertThat(ctx.getInt(PubmedUpdateContextKeys.CURRENT_INDEX)).isEqualTo(1);
  }

  private FileDownloadResult gzipFile() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
      gz.write("<?xml version=\"1.0\"?><PubmedArticleSet/>".getBytes());
    }
    Path file = tempDir.resolve("u.xml.gz");
    Files.write(file, baos.toByteArray());
    return FileDownloadResult.of(file, Files.size(file));
  }
}
