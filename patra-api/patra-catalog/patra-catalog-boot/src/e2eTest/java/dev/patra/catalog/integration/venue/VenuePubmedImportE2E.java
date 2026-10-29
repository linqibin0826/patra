package dev.patra.catalog.integration.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.venue.usecase.pubmed.VenuePubmedImportCommand;
import dev.patra.catalog.app.venue.usecase.pubmed.VenuePubmedImportResult;
import dev.patra.catalog.domain.kernel.port.registry.DictionaryResolverPort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadResult;
import dev.patra.catalog.domain.venue.port.enrichment.OpenAlexEnrichmentQueryPort;
import dev.patra.catalog.integration.config.CatalogE2EPostgreSQLContainerInitializer;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.objectstorage.ObjectStorageOperations;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// 期刊 LSIOU 导入端到端测试（PAP-117）：真实处理器 + PG，同一份 LSIOU 导入两遍。
///
/// 覆盖：NLM 与 ISSN-L 指向不同期刊时按 NLM 合并；NLM 属于软删期刊的记录跳过；
/// 无主 NLM 经 ISSN-L 并进已有期刊成为副 NLM、主 NLM 不变；重导不违反 V12 的两条唯一约束。
/// 下载端口每次把夹具复制成新的临时文件（处理器用完会删掉）；字典与 OpenAlex 端口返回空。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(properties = "spring.cloud.nacos.discovery.enabled=false")
@ContextConfiguration(initializers = CatalogE2EPostgreSQLContainerInitializer.class)
@ActiveProfiles("e2e-test")
@DisplayName("期刊 LSIOU 导入 E2E 测试（NLM 身份）")
class VenuePubmedImportE2E {

  private static final String LSIOU_URL = "https://test.nlm.nih.gov/online/journals/lsi2026.xml";
  private static final String FIXTURE = "/lsiou/lsiou-identity-e2e.xml";
  private static final long X_ID = 8_100_001L;
  private static final long Y_ID = 8_100_002L;
  private static final long Z_ID = 8_100_003L;
  private static final String NEW_NLM = "9918523075306676";

  @Autowired private CommandBus commandBus;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private FileDownloadPort fileDownloadPort;
  @MockitoBean private DictionaryResolverPort dictionaryResolverPort;
  @MockitoBean private OpenAlexEnrichmentQueryPort openAlexEnrichmentQueryPort;
  @MockitoBean private ObjectStorageOperations objectStorageOperations;

  /// 清空期刊表，预置 X、Y 与软删的 Z，并让下载端口返回夹具。
  @BeforeEach
  void setUp() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                + " AND tablename LIKE 'cat_venue%'",
            String.class);
    jdbc.execute("TRUNCATE " + String.join(", ", tables));
    insertVenue(X_ID, "Journal X", "0000001", "1000-0001", false);
    insertIdentifier(8_200_001L, X_ID, "NLM", "0000001");
    insertIdentifier(8_200_002L, X_ID, "ISSN_L", "1000-0001");
    insertVenue(Y_ID, "Journal Y Old", "0000002", null, false);
    insertIdentifier(8_200_003L, Y_ID, "NLM", "0000002");
    insertVenue(Z_ID, "Journal Z", "0000003R", null, true);
    insertIdentifier(8_200_004L, Z_ID, "NLM", "0000003R");

    when(fileDownloadPort.download(any(URI.class))).thenAnswer(inv -> copyFixture());
    when(dictionaryResolverPort.resolve(any(), any(), any())).thenReturn(Map.of());
    when(openAlexEnrichmentQueryPort.findEnrichmentData(any())).thenReturn(Map.of());
  }

  @Test
  @DisplayName("按 NLM 合并、跳过软删期刊的 NLM、副 NLM 并入，两遍导入后主 NLM 不变")
  void should_merge_by_nlm_skip_soft_deleted_and_keep_primary_across_imports() {
    VenuePubmedImportResult first = commandBus.handle(command());

    assertThat(first)
        .extracting(
            VenuePubmedImportResult::totalParsed,
            VenuePubmedImportResult::updatedCount,
            VenuePubmedImportResult::createdCount,
            VenuePubmedImportResult::skippedCount)
        .containsExactly(4, 2, 1, 1);
    assertIdentity();

    VenuePubmedImportResult second = commandBus.handle(command());

    assertThat(second)
        .extracting(
            VenuePubmedImportResult::updatedCount,
            VenuePubmedImportResult::createdCount,
            VenuePubmedImportResult::skippedCount)
        .containsExactly(3, 0, 1);
    assertIdentity();
  }

  /// 断言 NLM 归属、主 NLM、期刊数，以及 Z 仍是软删。
  private void assertIdentity() {
    long newVenueId =
        jdbc.queryForObject("SELECT id FROM cat_venue WHERE nlm_id = ?", Long.class, NEW_NLM);
    assertThat(nlmOwners())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "0000001",
                X_ID,
                "0000004",
                X_ID,
                "0000002",
                Y_ID,
                "0000003R",
                Z_ID,
                NEW_NLM,
                newVenueId));
    assertThat(primaryNlm(X_ID)).isEqualTo("0000001");
    assertThat(primaryNlm(Y_ID)).isEqualTo("0000002");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM cat_venue", Integer.class)).isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM cat_venue WHERE id = ?", Boolean.class, Z_ID))
        .isTrue();
  }

  /// 标识符表里每个 NLM 的归属期刊。
  ///
  /// @return NLM ID → 期刊 ID
  private Map<String, Long> nlmOwners() {
    Map<String, Long> owners = new HashMap<>();
    jdbc.query(
        "SELECT identifier_value, venue_id FROM cat_venue_identifier WHERE identifier_type = 'NLM'",
        rs -> {
          owners.put(rs.getString("identifier_value"), rs.getLong("venue_id"));
        });
    return owners;
  }

  /// 期刊的主 NLM（`cat_venue.nlm_id`）。
  ///
  /// @param venueId 期刊 ID
  /// @return 主 NLM
  private String primaryNlm(long venueId) {
    return jdbc.queryForObject("SELECT nlm_id FROM cat_venue WHERE id = ?", String.class, venueId);
  }

  /// 预置一个期刊。
  ///
  /// @param id 期刊 ID
  /// @param title 刊名
  /// @param nlmId 主 NLM
  /// @param issnL ISSN-L，可空
  /// @param deleted 是否软删
  private void insertVenue(long id, String title, String nlmId, String issnL, boolean deleted) {
    jdbc.update(
        "INSERT INTO cat_venue (id, venue_type, title, provenance_code, nlm_id, issn_l, deleted_at)"
            + " VALUES (?, 'JOURNAL', ?, 'PUBMED', ?, ?, CASE WHEN ? THEN now() END)",
        id,
        title,
        nlmId,
        issnL,
        deleted);
  }

  /// 预置一行期刊标识符。
  ///
  /// @param id 行 ID
  /// @param venueId 期刊 ID
  /// @param type 标识符类型
  /// @param value 标识符值
  private void insertIdentifier(long id, long venueId, String type, String value) {
    jdbc.update(
        "INSERT INTO cat_venue_identifier (id, venue_id, identifier_type, identifier_value)"
            + " VALUES (?, ?, ?, ?)",
        id,
        venueId,
        type,
        value);
  }

  /// 导入命令。
  ///
  /// @return 命令
  private static VenuePubmedImportCommand command() {
    return VenuePubmedImportCommand.of(LSIOU_URL, "2026");
  }

  /// 把夹具复制成新的临时文件：处理器导入完会删掉下载文件。
  ///
  /// @return 下载结果
  /// @throws IOException 读夹具或写临时文件失败时
  private FileDownloadResult copyFixture() throws IOException {
    Path file = Files.createTempFile("lsiou-e2e-", ".xml");
    try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
      if (in == null) {
        throw new IllegalStateException("测试资源不存在：" + FIXTURE);
      }
      Files.write(file, in.readAllBytes());
    }
    return FileDownloadResult.of(file, Files.size(file));
  }
}
