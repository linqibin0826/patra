package dev.patra.catalog.infra.publication.batch;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadResult;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportContextKeys;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/// `PubmedFileItemReader` 单元测试：下载校验、逐篇产出带来源位置的原文、断点续传、收集删除列表。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedFileItemReader 单元测试")
class PubmedFileItemReaderTest {

  private static final String URL = "https://ftp.example/updatefiles/pubmed26n1350.xml.gz";

  private static final String XML =
      """
      <PubmedArticleSet>
        <PubmedArticle><MedlineCitation><PMID>101</PMID><Article><ArticleTitle>A</ArticleTitle></Article></MedlineCitation></PubmedArticle>
        <PubmedArticle><MedlineCitation><PMID>102</PMID><Article><ArticleTitle>B</ArticleTitle></Article></MedlineCitation></PubmedArticle>
        <DeleteCitation><PMID>900</PMID></DeleteCitation>
      </PubmedArticleSet>
      """;

  @TempDir Path tmp;

  @Test
  @DisplayName("逐篇产出原文、PMID 与来源位置，读完后删除列表进上下文")
  void should_read_records_with_position_and_collect_deletes() throws Exception {
    PubmedFileItemReader reader = reader();
    ExecutionContext ctx = new ExecutionContext();
    reader.open(ctx);

    List<PubmedSourceRecord> records = new ArrayList<>();
    for (PubmedSourceRecord r = reader.read(); r != null; r = reader.read()) {
      records.add(r);
    }
    reader.update(ctx);
    reader.close();

    assertThat(records).extracting(PubmedSourceRecord::pmid).containsExactly("101", "102");
    assertThat(records.get(0).position().fileName()).isEqualTo("pubmed26n1350.xml.gz");
    assertThat(records.get(0).position().rank()).isEqualTo(261_350);
    assertThat(records.get(0).rawXml()).startsWith("<PubmedArticle").contains("<PMID>101</PMID>");
    assertThat(ctx.get(PubmedImportContextKeys.DELETED_PMIDS)).isEqualTo(List.of("900"));
    assertThat(ctx.getInt(PubmedImportContextKeys.CURRENT_INDEX)).isEqualTo(2);
  }

  @Test
  @DisplayName("断点续传：跳过已读条数")
  void should_resume_from_saved_index() throws Exception {
    PubmedFileItemReader reader = reader();
    ExecutionContext ctx = new ExecutionContext();
    ctx.putInt(PubmedImportContextKeys.CURRENT_INDEX, 1);
    reader.open(ctx);

    assertThat(reader.read().pmid()).isEqualTo("102");
    assertThat(reader.read()).isNull();
    reader.close();
  }

  private PubmedFileItemReader reader() throws Exception {
    byte[] gz = gzip(XML.getBytes(StandardCharsets.UTF_8));
    String md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(gz));
    FileDownloadPort port =
        url -> {
          byte[] body =
              url.toString().endsWith(".md5")
                  ? ("MD5(pubmed26n1350.xml.gz)= " + md5).getBytes(StandardCharsets.US_ASCII)
                  : gz;
          try {
            Path file = Files.createTempFile(tmp, "dl", ".tmp");
            Files.write(file, body);
            return FileDownloadResult.of(file, body.length);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        };
    return new PubmedFileItemReader(new PubmedFileFetcher(port, 3), URL);
  }

  private static byte[] gzip(byte[] raw) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
      gz.write(raw);
    }
    return out.toByteArray();
  }
}
