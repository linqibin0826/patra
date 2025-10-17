package com.patra.ingest.smoke;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.gateway.GatewayRequestBuilder;
import com.patra.starter.provenance.crossref.model.request.CrossrefWorksRequest;
import com.patra.starter.provenance.crossref.request.CrossrefWorksRequestAssembler;
import com.patra.starter.provenance.epmc.model.request.SearchRequest;
import com.patra.starter.provenance.epmc.request.EpmcSearchRequestAssembler;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 6 — Rollout & Smoke Testing.
 *
 * <p>These are opt-in smoke tests that hit the live registry via Feign to load rule snapshots and
 * compile expressions for PUBMED/EPMC/CROSSREF. They are skipped by default. To run locally:
 *
 * <pre>
 *   RUN_SMOKE=1 mvn -q -pl patra-ingest/patra-ingest-boot -Dtest=ExprSmokeTest test
 * </pre>
 *
 * Requirements: - patra-registry-boot running on localhost with seeds applied (Flyway V1.1.x) -
 * Nacos discovery configured so Feign (name = "patra-registry") can resolve the service
 */
@SpringBootTest(
    properties = {
      // Disable Nacos for tests; route Feign via SimpleDiscoveryClient
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      // Point service-id 'patra-registry' to local instance on port 6000
      "spring.cloud.discovery.client.simple.instances.patra-registry[0].uri=http://localhost:6000",
      // Allow duplicate FeignClientSpecification beans registered by scanning
      "spring.main.allow-bean-definition-overriding=true",
      // Disable XXL-Job to avoid missing property failures in test profile
      "xxl.job.enabled=false"
    })
@ActiveProfiles("dev")
class ExprSmokeTest {

  @Autowired private ExpressionCompilerPort compiler;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader snapshotLoader;

  // Resolve repo-root samples when test module is executed from patra-ingest-boot
  private static final Path SAMPLES_DIR =
      Path.of("../../docs/expr/smoke/samples").toAbsolutePath().normalize();

  @Test
  @DisplayName("P6.2.1 — PubMed: phrase + date → term/mindate/maxdate/datetype")
  void pubmed_phrase_date() throws Exception {
    assumeTrue(runSmoke(), "Set RUN_SMOKE=1 to enable smoke tests");
    boolean seedSupportsDate = pubmedSeedSupportsDate();
    String exprJson = buildPubmedExprJson(seedSupportsDate);
    ExprCompilationResult result = compiler.compile(new ExprCompilationRequest("PUBMED", exprJson));

    assertTrue(result.isValid(), () -> "Compilation failed: " + result.validationMessage());
    assertNotNull(result.query(), "Rendered query must not be null");
    String q = result.query();
    assertTrue(
        q.contains("heart failure[TIAB]") || q.contains("\"heart failure\"[TIAB]"),
        () -> "Unexpected query: " + q);

    JsonNode params = result.params();
    assertEquals(result.query(), params.path("term").asText());
    if (seedSupportsDate) {
      assertEquals("2023-01-01", params.path("mindate").asText());
      assertEquals("2023-12-30", params.path("maxdate").asText());
      assertEquals("pdat", params.path("datetype").asText());
    }

    // Adapter assembly (count request) + gateway URL build (format only)
    ESearchRequest req = new PubMedESearchRequestAssembler().buildCount(params);
    ProvenanceConfig cfg =
        new ProvenanceConfig(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
            new HttpConfig(Map.of(), null, null, 5000),
            null,
            null,
            null,
            null,
            null);
    ExternalCallRequestDTO call =
        new GatewayRequestBuilder().build(cfg.baseUrl(), "/esearch.fcgi", req, cfg);
    Map<String, String> qs2 = parseQuery(call.url());
    assertEquals(result.query(), qs2.get("term"), () -> call.url());
    if (seedSupportsDate) {
      assertEquals("pdat", qs2.get("datetype"));
      assertEquals("2023-01-01", qs2.get("mindate"));
      assertEquals("2023-12-30", qs2.get("maxdate"));
    }
  }

  @Test
  @DisplayName("P6.2.2 — EPMC: mixed boolean → query param bridged")
  void epmc_mixed_boolean() throws Exception {
    assumeTrue(runSmoke(), "Set RUN_SMOKE=1 to enable smoke tests");
    String exprJson = buildEpmcExprJson();
    ExprCompilationResult result = compiler.compile(new ExprCompilationRequest("EPMC", exprJson));

    assertTrue(result.isValid(), () -> "Compilation failed: " + result.validationMessage());
    String q = result.query();
    assertNotNull(q);
    assertTrue(q.contains("cancer"));
    assertTrue(q.contains("therapy") && q.contains("surgery"));

    // Assemble request and build URL
    SearchRequest req = new EpmcSearchRequestAssembler().build(result.params());
    ProvenanceConfig cfg =
        new ProvenanceConfig(
            "https://www.ebi.ac.uk/europepmc/webservices/rest",
            new HttpConfig(Map.of(), null, null, 5000),
            null,
            null,
            null,
            null,
            null);
    ExternalCallRequestDTO call =
        new GatewayRequestBuilder().build(cfg.baseUrl(), "/search", req, cfg);
    Map<String, String> qs = parseQuery(call.url());
    assertTrue(qs.containsKey("query"), () -> call.url());
  }

  @Test
  @DisplayName("P6.2.3 — Crossref: phrase + date filter → query + filter")
  void crossref_phrase_date() throws Exception {
    assumeTrue(runSmoke(), "Set RUN_SMOKE=1 to enable smoke tests");
    String exprJson = buildCrossrefExprJson();
    ExprCompilationResult result =
        compiler.compile(new ExprCompilationRequest("CROSSREF", exprJson));

    assertTrue(result.isValid(), () -> "Compilation failed: " + result.validationMessage());
    assertEquals("\"machine learning\"", result.query());
    assertEquals(
        "from-pub-date:2022-01-01,until-pub-date:2022-12-31",
        result.params().path("filter").asText());

    CrossrefWorksRequest req = new CrossrefWorksRequestAssembler().build(result.params());
    ProvenanceConfig cfg =
        new ProvenanceConfig(
            "https://api.crossref.org",
            new HttpConfig(Map.of(), null, null, 5000),
            null,
            null,
            null,
            null,
            null);
    ExternalCallRequestDTO call =
        new GatewayRequestBuilder().build(cfg.baseUrl(), "/works", req, cfg);
    Map<String, String> qs = parseQuery(call.url());
    assertEquals("\"machine learning\"", qs.get("query"));
    assertEquals("from-pub-date:2022-01-01,until-pub-date:2022-12-31", qs.get("filter"));
  }

  @Test
  @DisplayName("P6.2.5/6 — STRICT vs non-STRICT: NOT on RANGE produces error or warning")
  void strict_mode_not_on_range() throws Exception {
    assumeTrue(runSmoke(), "Set RUN_SMOKE=1 to enable smoke tests");

    // Use NOT over PubMed entrez_date RANGE: supports_not=0 for that field → capability violation
    String exprJson = buildPubmedNotRangeExprJson();

    // non-STRICT (dev profile): expect warning (downgraded)
    ExprCompilationResult nonStrict =
        compiler.compile(new ExprCompilationRequest("PUBMED", exprJson));
    assertTrue(nonStrict.isValid(), () -> nonStrict.validationMessage());
    assertNotNull(nonStrict.warnings());
    assertTrue(nonStrict.warnings().contains("W-NOT-SKIPPED"), () -> nonStrict.warnings());

    // STRICT behaviour is verified in ExprStrictSmokeTest (separate test context)
  }

  private static boolean runSmoke() {
    String v = System.getenv("RUN_SMOKE");
    return v != null && ("1".equals(v) || "true".equalsIgnoreCase(v));
  }

  private String readSample(String name) throws Exception {
    return Files.readString(SAMPLES_DIR.resolve(name));
  }

  private static Map<String, String> parseQuery(String url) {
    String qs = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
    java.util.Map<String, String> map = new java.util.HashMap<>();
    for (String pair : qs.split("&")) {
      if (pair.isEmpty()) continue;
      int idx = pair.indexOf('=');
      String k = idx >= 0 ? pair.substring(0, idx) : pair;
      String v = idx >= 0 ? pair.substring(idx + 1) : "";
      map.put(
          java.net.URLDecoder.decode(k, java.nio.charset.StandardCharsets.UTF_8),
          java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8));
    }
    return map;
  }

  private boolean pubmedSeedSupportsDate() {
    try {
      com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot snapshot =
          snapshotLoader.load(com.patra.common.enums.ProvenanceCode.PUBMED, "ALL", "SEARCH");
      var cap = snapshot.capabilityMatrix().get("entrez_date");
      return cap != null
          && cap.rangeKind()
              == com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot.RangeKind.DATE;
    } catch (Exception e) {
      return false;
    }
  }

  private String buildPubmedExprJson(boolean withDate) {
    Expr expr =
        withDate
            ? Exprs.and(
                java.util.List.of(
                    Exprs.term("tiab", "heart failure", TextMatch.PHRASE),
                    Exprs.rangeDate(
                        "entrez_date",
                        java.time.LocalDate.parse("2023-01-01"),
                        java.time.LocalDate.parse("2023-12-31"))))
            : Exprs.term("tiab", "heart failure", TextMatch.PHRASE);
    return Exprs.toJson(expr);
  }

  private String buildEpmcExprJson() {
    Expr expr =
        Exprs.and(
            java.util.List.of(
                Exprs.term("text", "cancer", TextMatch.ANY),
                Exprs.or(
                    java.util.List.of(
                        Exprs.term("text", "therapy", TextMatch.ANY),
                        Exprs.not(Exprs.term("text", "surgery", TextMatch.ANY))))));
    return Exprs.toJson(expr);
  }

  private String buildCrossrefExprJson() {
    Expr expr =
        Exprs.and(
            java.util.List.of(
                Exprs.term("text", "machine learning", TextMatch.PHRASE),
                Exprs.rangeDate(
                    "publication_date",
                    java.time.LocalDate.parse("2022-01-01"),
                    java.time.LocalDate.parse("2022-12-31"))));
    return Exprs.toJson(expr);
  }

  private String buildPubmedNotRangeExprJson() {
    Expr expr =
        Exprs.not(
            Exprs.rangeDate(
                "entrez_date",
                java.time.LocalDate.parse("2024-01-01"),
                java.time.LocalDate.parse("2024-12-31")));
    return Exprs.toJson(expr);
  }
}
