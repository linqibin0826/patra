package dev.patra.catalog.integration.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.usecase.publication.baseline.command.PublicationBaselineImportCommand;
import dev.patra.catalog.app.usecase.publication.update.command.PubmedUpdateSyncCommand;
import dev.patra.catalog.app.usecase.publication.update.dto.PubmedUpdateSyncResult;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.port.source.FileDownloadPort;
import dev.patra.catalog.domain.port.source.FileDownloadResult;
import dev.patra.catalog.domain.port.source.PubmedUpdateFileListingPort;
import dev.patra.catalog.integration.config.CatalogE2EPostgreSQLContainerInitializer;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.objectstorage.ObjectStorageOperations;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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

/// PubMed updatefiles 增量同步端到端测试：baseline 导入 → `EXISTING_ONLY` 同步 → `ALL` 重放。
@SpringBootTest(
    properties = {"spring.cloud.nacos.discovery.enabled=false", "patra.batch.chunk.default-size=2"})
@ContextConfiguration(initializers = CatalogE2EPostgreSQLContainerInitializer.class)
@ActiveProfiles("e2e-test")
@DisplayName("PubMed 增量同步 E2E 测试")
class PubmedUpdateSyncE2E {

  private static final String BASELINE_URL = "https://test.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final String UPDATE_URL = "https://test.ncbi.nlm.nih.gov/pubmed/updatefiles/";
  private static final long VENUE_ID = 8_000_001L;

  @Autowired private CommandBus commandBus;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private FileDownloadPort fileDownloadPort;
  @MockitoBean private PubmedUpdateFileListingPort listingPort;
  @MockitoBean private ObjectStorageOperations objectStorageOperations;

  @BeforeEach
  void setUp() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                + " AND (tablename LIKE 'cat_publication%' OR tablename IN"
                + " ('cat_pubmed_update_file', 'cat_venue_instance', 'cat_venue_identifier', 'cat_venue'))",
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
    when(fileDownloadPort.download(any(URI.class)))
        .thenAnswer(
            inv -> {
              URI uri = inv.getArgument(0);
              String resource =
                  uri.getPath().contains("/baseline/")
                      ? "/pubmed/pubmed26n9000-baseline.xml"
                      : "/pubmed/pubmed26n9001.xml";
              return gzipResource(resource);
            });
    when(listingPort.findLatestSeq(anyString(), anyString())).thenReturn(9001);
  }

  @Test
  @DisplayName("EXISTING_ONLY 只覆盖已有、跳过新记录、软删除；ALL 重放后新记录入库")
  void should_overwrite_skip_and_delete_then_replay_with_all_scope() {
    commandBus.handle(PublicationBaselineImportCommand.of(BASELINE_URL, 1));
    assertThat(count("SELECT count(*) FROM cat_publication")).isEqualTo(4);
    long firstId = id("90009001");

    PubmedUpdateSyncResult first =
        commandBus.handle(command(null, PubmedUpdateScope.EXISTING_ONLY));

    assertThat(first.filesProcessed()).isEqualTo(1);
    assertThat(titles())
        .containsEntry("90009001", "Revised title 1")
        .containsEntry("90009002", "Revised title 2")
        .containsEntry("90009003", "Revised title 3")
        .doesNotContainKey("90009005");
    assertThat(id("90009001")).isEqualTo(firstId);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM cat_publication WHERE pmid = '90009004'",
                Boolean.class))
        .isTrue();
    Map<String, Object> progress =
        jdbc.queryForMap("SELECT * FROM cat_pubmed_update_file WHERE file_seq = 9001");
    assertThat(progress)
        .containsEntry("status", "COMPLETED")
        .containsEntry("updated_count", 3)
        .containsEntry("skipped_absent_count", 1)
        .containsEntry("deleted_count", 1)
        .containsEntry("created_count", 0);

    PubmedUpdateSyncResult replay = commandBus.handle(command(9001, PubmedUpdateScope.ALL));

    assertThat(replay.filesProcessed()).isEqualTo(1);
    assertThat(titles()).containsEntry("90009005", "Brand new record");
    assertThat(jdbc.queryForMap("SELECT * FROM cat_pubmed_update_file WHERE file_seq = 9001"))
        .containsEntry("status", "COMPLETED")
        .containsEntry("scope", "ALL")
        .containsEntry("created_count", 1)
        .containsEntry("updated_count", 3);
  }

  private static PubmedUpdateSyncCommand command(Integer fromFile, PubmedUpdateScope scope) {
    return PubmedUpdateSyncCommand.builder()
        .updateUrl(UPDATE_URL)
        .filePrefix("pubmed26n")
        .startSeq(9001)
        .scope(scope)
        .fromFile(fromFile)
        .build();
  }

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

  private long id(String pmid) {
    return jdbc.queryForObject("SELECT id FROM cat_publication WHERE pmid = ?", Long.class, pmid);
  }

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  private FileDownloadResult gzipResource(String resource) throws Exception {
    Path file = Files.createTempFile("pubmed-e2e-", ".xml.gz");
    try (InputStream in = getClass().getResourceAsStream(resource);
        OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      if (in == null) {
        throw new IllegalStateException("测试资源不存在：" + resource);
      }
      in.transferTo(out);
    }
    return FileDownloadResult.of(file, Files.size(file));
  }
}
