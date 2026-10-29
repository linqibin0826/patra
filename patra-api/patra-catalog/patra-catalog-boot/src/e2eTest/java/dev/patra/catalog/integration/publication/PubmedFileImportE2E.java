package dev.patra.catalog.integration.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.publication.usecase.sync.PubmedFileImportCommand;
import dev.patra.catalog.app.publication.usecase.sync.PubmedFileImportResult;
import dev.patra.catalog.app.publication.usecase.sync.PubmedReparseCommand;
import dev.patra.catalog.app.publication.usecase.sync.PubmedReparseResult;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadResult;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.port.source.PubmedFileListingPort;
import dev.patra.catalog.integration.config.CatalogE2EPostgreSQLContainerInitializer;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.objectstorage.ObjectStorageOperations;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// PubMed 文件导入端到端测试：baseline `ALL` 导入 → update 覆盖与软删 → `ALL` 重放 update →
/// 重放 baseline 不回退 → 本地重算。
///
/// 下载端口按文件名返回夹具的 gzip 字节，`.md5` 旁文件按 NCBI 格式（`MD5(name)= hex`）现算。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(
    properties = {"spring.cloud.nacos.discovery.enabled=false", "patra.batch.chunk.default-size=2"})
@ContextConfiguration(initializers = CatalogE2EPostgreSQLContainerInitializer.class)
@ActiveProfiles("e2e-test")
@DisplayName("PubMed 文件导入 E2E 测试")
class PubmedFileImportE2E {

  private static final String BASELINE_URL = "https://test.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final String UPDATE_URL = "https://test.ncbi.nlm.nih.gov/pubmed/updatefiles/";
  private static final String PREFIX = "pubmed26n";
  private static final int BASELINE_RANK = 269000;
  private static final int UPDATE_RANK = 269001;
  private static final long VENUE_ID = 8_000_001L;

  @Autowired private CommandBus commandBus;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private FileDownloadPort fileDownloadPort;
  @MockitoBean private PubmedFileListingPort listingPort;
  @MockitoBean private ObjectStorageOperations objectStorageOperations;

  private final Map<String, byte[]> served = new HashMap<>();

  /// 清库、建期刊，并让下载端口按文件名返回 gzip 夹具与 `.md5` 旁文件。
  ///
  /// @throws IOException 读取夹具失败时
  @BeforeEach
  void setUp() throws IOException {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                + " AND (tablename LIKE 'cat_publication%' OR tablename IN"
                + " ('cat_pubmed_file', 'cat_venue_instance', 'cat_venue_identifier', 'cat_venue'))",
            String.class);
    jdbc.execute("TRUNCATE " + String.join(", ", tables));
    jdbc.update(
        "INSERT INTO cat_venue (id, venue_type, title, provenance_code, nlm_id)"
            + " VALUES (?, 'JOURNAL', 'Journal of Sync Fixtures', 'PUBMED', 'NLM9000001')",
        VENUE_ID);
    jdbc.update(
        "INSERT INTO cat_venue_identifier (id, venue_id, identifier_type, identifier_value, is_primary)"
            + " VALUES (?, ?, 'NLM', 'NLM9000001', true)",
        VENUE_ID + 1,
        VENUE_ID);

    serve("pubmed26n9000.xml.gz", "/pubmed/pubmed26n9000.xml");
    serve("pubmed26n9001.xml.gz", "/pubmed/pubmed26n9001.xml");
    when(fileDownloadPort.download(any(URI.class))).thenAnswer(inv -> download(inv.getArgument(0)));
    when(listingPort.findLatestSeq(anyString(), anyString())).thenReturn(9001);
  }

  @Test
  @DisplayName("baseline 导入 → update 覆盖与软删 → ALL 重放 → baseline 重放不回退 → 本地重算")
  void should_import_overwrite_replay_without_regression_and_reparse() {
    // 1. baseline ALL：8 篇入库，年份 1700 与缺标题的 2 篇跳过；日期按实际精度存
    PubmedFileImportResult baseline = commandBus.handle(baseline(false));

    assertThat(baseline.filesProcessed()).isEqualTo(1);
    assertThat(baseline.total())
        .extracting(PubmedFileCounts::created, PubmedFileCounts::skippedInvalid)
        .containsExactly(8, 2);
    assertThat(titles()).hasSize(8).doesNotContainKeys("90009010", "90009011");
    assertThat(progress("pubmed26n9000.xml.gz"))
        .containsEntry("kind", "BASELINE")
        .containsEntry("status", "COMPLETED")
        .containsEntry("created_count", 8)
        .containsEntry("skipped_invalid_count", 2);
    assertThat(publishedDate("90009006")).containsExactly(2023, null, null, "year", null);
    assertThat(publishedDate("90009007")).containsExactly(2024, null, null, "year", "Spring");
    assertThat(publishedDate("90009008")).containsExactly(2024, 2, null, "month", null);
    assertThat(publishedDate("90009009")).containsExactly(2024, null, null, "year", null);
    assertThat(
            count(
                "SELECT count(*) FROM cat_publication_source WHERE source_rank = "
                    + BASELINE_RANK
                    + " AND raw_xml LIKE '<PubmedArticle%'"))
        .isEqualTo(8);
    long firstId = id("90009001");
    Timestamp firstSyncedAt = lastSyncedAt("90009001");

    // 2. update EXISTING_ONLY：3 篇覆盖、新记录跳过、90009004 软删
    PubmedFileImportResult update =
        commandBus.handle(update(null, PubmedImportScope.EXISTING_ONLY, false));

    assertThat(update.total())
        .extracting(
            PubmedFileCounts::updated,
            PubmedFileCounts::skippedAbsent,
            PubmedFileCounts::deleted,
            PubmedFileCounts::created)
        .containsExactly(3, 1, 1, 0);
    assertThat(titles())
        .containsEntry("90009001", "Revised title 1")
        .containsEntry("90009002", "Revised title 2")
        .containsEntry("90009003", "Revised title 3")
        .doesNotContainKeys("90009004", "90009005");
    assertThat(id("90009001")).isEqualTo(firstId);
    assertThat(lastSyncedAt("90009001")).isAfter(firstSyncedAt);
    assertThat(sourceRank("90009001")).isEqualTo(UPDATE_RANK);
    assertThat(sourceRank("90009004")).isEqualTo(UPDATE_RANK);
    assertThat(progress("pubmed26n9001.xml.gz"))
        .containsEntry("kind", "UPDATE")
        .containsEntry("status", "COMPLETED");

    // 3. update ALL 重放：新记录入库，同位置的 3 篇照常覆盖
    PubmedFileImportResult replayUpdate =
        commandBus.handle(update(9001, PubmedImportScope.ALL, true));

    assertThat(replayUpdate.total())
        .extracting(PubmedFileCounts::created, PubmedFileCounts::updated)
        .containsExactly(1, 3);
    assertThat(titles()).containsEntry("90009005", "Brand new record");
    assertThat(progress("pubmed26n9001.xml.gz")).containsEntry("scope", "ALL");

    // 4. baseline 重放：更新过的 3 篇与软删的 90009004 都比 baseline 新，跳过不回退
    PubmedFileImportResult replayBaseline = commandBus.handle(baseline(true));

    assertThat(replayBaseline.total())
        .extracting(
            PubmedFileCounts::skippedStale,
            PubmedFileCounts::updated,
            PubmedFileCounts::created,
            PubmedFileCounts::skippedInvalid)
        .containsExactly(4, 4, 0, 2);
    assertThat(titles()).containsEntry("90009001", "Revised title 1").doesNotContainKey("90009004");
    assertThat(sourceRank("90009001")).isEqualTo(UPDATE_RANK);
    assertThat(progress("pubmed26n9000.xml.gz")).containsEntry("skipped_stale_count", 4);

    // 5. 本地重算：改写原文里的页码后重算，文献行更新，last_synced_at 不变，软删文献不复活
    jdbc.update(
        "UPDATE cat_publication_source SET raw_xml = replace(raw_xml, '</ArticleTitle>',"
            + " '</ArticleTitle><Pagination><StartPage>100</StartPage><EndPage>109</EndPage>"
            + "<MedlinePgn>100-9</MedlinePgn></Pagination>') WHERE pmid = '90009001'");
    Timestamp syncedBeforeReparse = lastSyncedAt("90009001");

    PubmedReparseResult reparse = commandBus.handle(new PubmedReparseCommand());

    assertThat(reparse.skippedByLock()).isFalse();
    assertThat(reparse.counts().updated()).isEqualTo(8);
    assertThat(
            jdbc.queryForMap(
                "SELECT medline_pgn, start_page, end_page FROM cat_publication WHERE pmid = '90009001'"))
        .containsEntry("medline_pgn", "100-9")
        .containsEntry("start_page", "100")
        .containsEntry("end_page", "109");
    assertThat(lastSyncedAt("90009001")).isEqualTo(syncedBeforeReparse);
    assertThat(titles()).doesNotContainKey("90009004").containsEntry("90009001", "Revised title 1");
  }

  /// baseline 9000 的导入命令。
  ///
  /// @param reprocess 是否重处理已完成的文件
  /// @return 命令
  private static PubmedFileImportCommand baseline(boolean reprocess) {
    return PubmedFileImportCommand.builder()
        .kind(PubmedFileKind.BASELINE)
        .baseUrl(BASELINE_URL)
        .filePrefix(PREFIX)
        .startSeq(9000)
        .scope(PubmedImportScope.ALL)
        .from(9000)
        .reprocess(reprocess)
        .build();
  }

  /// updatefiles 的导入命令。
  ///
  /// @param from 起始序号；为空时按进度表续跑
  /// @param scope 范围
  /// @param reprocess 是否重处理已完成的文件
  /// @return 命令
  private static PubmedFileImportCommand update(
      Integer from, PubmedImportScope scope, boolean reprocess) {
    return PubmedFileImportCommand.builder()
        .kind(PubmedFileKind.UPDATE)
        .baseUrl(UPDATE_URL)
        .filePrefix(PREFIX)
        .startSeq(9001)
        .scope(scope)
        .from(from)
        .reprocess(reprocess)
        .build();
  }

  /// 未软删文献的 PMID → 标题。
  ///
  /// @return 映射
  private Map<String, String> titles() {
    return jdbc.query(
        "SELECT pmid, title FROM cat_publication WHERE deleted_at IS NULL",
        rs -> {
          Map<String, String> result = new HashMap<>();
          while (rs.next()) {
            result.put(rs.getString(1), rs.getString(2));
          }
          return result;
        });
  }

  /// 主出版日期的年、月、日、精度、季节。
  ///
  /// @param pmid PMID
  /// @return 五个值，按上述顺序
  private List<Object> publishedDate(String pmid) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT d.year, d.month, d.day, d.date_precision, d.season"
                + " FROM cat_publication_date d JOIN cat_publication p ON p.id = d.publication_id"
                + " WHERE p.pmid = ? AND d.is_primary",
            pmid);
    return Arrays.asList(
        toInteger(row.get("year")),
        toInteger(row.get("month")),
        toInteger(row.get("day")),
        row.get("date_precision"),
        row.get("season"));
  }

  /// 把 JDBC 返回的数值列统一成 `Integer`。
  ///
  /// @param value 列值，可空
  /// @return 整数，可空
  private static Integer toInteger(Object value) {
    return value == null ? null : ((Number) value).intValue();
  }

  /// 进度表一行。
  ///
  /// @param fileName 文件名
  /// @return 列名 → 值
  private Map<String, Object> progress(String fileName) {
    return jdbc.queryForMap("SELECT * FROM cat_pubmed_file WHERE file_name = ?", fileName);
  }

  /// 来源表里的位置。
  ///
  /// @param pmid PMID
  /// @return source_rank
  private int sourceRank(String pmid) {
    return jdbc.queryForObject(
        "SELECT source_rank FROM cat_publication_source WHERE pmid = ?", Integer.class, pmid);
  }

  /// 文献主键。
  ///
  /// @param pmid PMID
  /// @return 主键
  private long id(String pmid) {
    return jdbc.queryForObject("SELECT id FROM cat_publication WHERE pmid = ?", Long.class, pmid);
  }

  /// 文献的最后采集时间。
  ///
  /// @param pmid PMID
  /// @return last_synced_at
  private Timestamp lastSyncedAt(String pmid) {
    return jdbc.queryForObject(
        "SELECT last_synced_at FROM cat_publication WHERE pmid = ?", Timestamp.class, pmid);
  }

  /// 执行计数 SQL。
  ///
  /// @param sql SQL
  /// @return 计数
  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  /// 登记一个可下载的文件：gzip 夹具字节。
  ///
  /// @param fileName 文件名
  /// @param resource 夹具资源路径
  /// @throws IOException 读取失败时
  private void serve(String fileName, String resource) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = getClass().getResourceAsStream(resource);
        GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
      if (in == null) {
        throw new IllegalStateException("测试资源不存在：" + resource);
      }
      in.transferTo(gzip);
    }
    served.put(fileName, bytes.toByteArray());
  }

  /// 模拟下载：`.xml.gz` 返回登记的字节，`.md5` 返回按 NCBI 格式现算的旁文件。
  ///
  /// @param uri 下载地址
  /// @return 下载结果
  /// @throws IOException 写临时文件失败时
  /// @throws NoSuchAlgorithmException 不支持 MD5 时
  private FileDownloadResult download(URI uri) throws IOException, NoSuchAlgorithmException {
    String name = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
    byte[] content;
    if (name.endsWith(".md5")) {
      String target = name.substring(0, name.length() - ".md5".length());
      String md5 =
          HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(served.get(target)));
      content = ("MD5(" + target + ")= " + md5 + "\n").getBytes(StandardCharsets.US_ASCII);
    } else {
      content = served.get(name);
    }
    if (content == null) {
      throw new IllegalStateException("夹具里没有这个文件：" + uri);
    }
    Path file = Files.createTempFile("pubmed-e2e-", "-" + name);
    Files.write(file, content);
    return FileDownloadResult.of(file, content.length);
  }
}
