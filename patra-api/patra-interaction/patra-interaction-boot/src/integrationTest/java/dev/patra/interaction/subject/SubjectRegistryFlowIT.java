package dev.patra.interaction.subject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconcileSubjectsCommand;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconciliationResult;
import dev.patra.interaction.domain.subject.exception.SubjectCapabilityNotSupportedException;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import dev.patra.tool.cqrs.CommandBus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/// 对象登记表的完整流程（PAP-112 的全部 AC）：真实 PostgreSQL，catalog 由 WireMock 扮演，经 Spring Cloud
/// LoadBalancer + SimpleDiscoveryClient 走 `lb://patra-catalog` 这条真实的路（照 identity 的
// `ProfileFlowIT`）。
///
/// 「互动数据不变」在本 Issue 里就是 `subject_id` 不变：互动表只存它。
@SpringBootTest
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
class SubjectRegistryFlowIT {

  private static final SubjectType PUB = SubjectType.PUBLICATION;
  private static final SubjectCapability COLLECT = SubjectCapability.COLLECT;
  private static final String BY_IDS = "/_internal/publications/resolve/by-ids";
  private static final String BY_PMIDS = "/_internal/publications/resolve/by-pmids";
  private static final SubjectType JOURNAL = SubjectType.JOURNAL;
  private static final SubjectCapability EXPERIENCE = SubjectCapability.SUBMISSION_EXPERIENCE;
  private static final String VENUE_BY_IDS = "/_internal/venues/resolve/by-ids";
  private static final String VENUE_BY_NLM_IDS = "/_internal/venues/resolve/by-nlm-ids";
  private static final String DOWN =
      """
      {"type":"about:blank","title":"Service Unavailable","status":503,
       "code":"CAT-0503","traits":["DEP_UNAVAILABLE"]}""";

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  @DynamicPropertySource
  static void catalogInstance(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.discovery.enabled", () -> "true");
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  @Autowired private SubjectGateway gateway;
  @Autowired private SubjectReadPort readPort;
  @Autowired private CommandBus commandBus;
  @Autowired private JdbcTemplate jdbcTemplate;

  /// 每例从干净的桩和空表开始。
  @BeforeEach
  void reset() {
    catalog.resetAll();
    jdbcTemplate.update("TRUNCATE subject.registry");
  }

  /// AC1：catalog 内部 ID 变化后，对账让登记表指向新 ID，`subject_id` 不变。
  @Test
  void should_follow_new_catalog_id_after_reconciliation() {
    stubById(100L, "1", "ACTIVE");
    SubjectId subject = gateway.resolveForInteraction(PUB, "100", COLLECT);

    stubByPmids(Map.of("1", item(200L, "1", "ACTIVE")));
    reconcile();

    assertThat(readPort.findByIds(List.of(subject)).get(subject).key()).isEqualTo("200");
    assertThat(readPort.findIdsByKeys(PUB, List.of("200"))).containsEntry("200", subject);
    assertThat(readPort.findIdsByKeys(PUB, List.of("100"))).isEmpty();
    catalog.resetRequests();
    assertThat(gateway.resolveForInteraction(PUB, "200", COLLECT)).isEqualTo(subject);
    catalog.verify(0, postRequestedFor(anyUrl()));
  }

  /// AC2：软删后 UNAVAILABLE（此时不能新增互动），复活后变回 ACTIVE。
  @Test
  void should_mark_unavailable_on_soft_delete_and_revive() {
    stubById(100L, "1", "ACTIVE");
    SubjectId subject = gateway.resolveForInteraction(PUB, "100", COLLECT);

    stubByPmids(Map.of("1", item(100L, "1", "DELETED")));
    reconcile();
    SubjectView deleted = readPort.findByIds(List.of(subject)).get(subject);
    assertThat(deleted.state()).isEqualTo(SubjectState.UNAVAILABLE);
    assertThat(deleted.key()).isEqualTo("100");
    assertThat(deleted.snapshot().title()).isEqualTo("Title 1 (DELETED)");
    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "100", COLLECT))
        .isInstanceOf(SubjectNotFoundException.class);

    stubByPmids(Map.of("1", item(100L, "1", "ACTIVE")));
    reconcile();
    assertThat(readPort.findByIds(List.of(subject)).get(subject).state())
        .isEqualTo(SubjectState.ACTIVE);
  }

  /// AC3：catalog 回 503 时首次互动是依赖不可用，不写库；已登记对象照常，不请求 catalog。
  @Test
  void should_reject_first_interaction_when_catalog_down_but_keep_registered() {
    stubById(100L, "1", "ACTIVE");
    SubjectId registered = gateway.resolveForInteraction(PUB, "100", COLLECT);
    catalog.resetAll();
    catalog.stubFor(any(anyUrl()).willReturn(problem(503)));

    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "200", COLLECT))
        .isInstanceOfSatisfying(
            SubjectSourceUnavailableException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE));
    assertThat(rowCount()).isEqualTo(1L);

    catalog.resetRequests();
    assertThat(gateway.resolveForInteraction(PUB, "100", COLLECT)).isEqualTo(registered);
    catalog.verify(0, postRequestedFor(anyUrl()));
  }

  /// AC3：连接被重置（客户端拿到 `ResourceAccessException`）同样是依赖不可用。
  @Test
  void should_reject_first_interaction_when_catalog_connection_fails() {
    catalog.stubFor(
        any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "200", COLLECT))
        .isInstanceOf(SubjectSourceUnavailableException.class);
    assertThat(rowCount()).isZero();
  }

  /// AC4：不存在、没有 PMID、已软删都拒绝，登记表里不出现。
  @Test
  void should_reject_missing_and_not_register() {
    catalog.stubFor(
        post(urlEqualTo(BY_IDS))
            .withRequestBody(equalToJson("{\"ids\":[100]}"))
            .willReturn(okJson("{\"items\":[{\"key\":\"100\",\"exists\":false}]}")));
    stubById(200L, null, "ACTIVE");
    stubById(300L, "3", "DELETED");

    for (String key : List.of("100", "200", "300")) {
      assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, key, COLLECT))
          .isInstanceOf(SubjectNotFoundException.class);
    }
    assertThat(rowCount()).isZero();
  }

  /// PMID 整行消失：失效、清空 ID、保留快照；重导出现新 ID 后用新 ID 首次互动复用原对象并复活。
  @Test
  void should_unlink_vanished_pmid_and_relink_after_reimport() {
    stubById(100L, "1", "ACTIVE");
    SubjectId subject = gateway.resolveForInteraction(PUB, "100", COLLECT);

    stubByPmids(Map.of());
    reconcile();
    SubjectView vanished = readPort.findByIds(List.of(subject)).get(subject);
    assertThat(vanished.state()).isEqualTo(SubjectState.UNAVAILABLE);
    assertThat(vanished.key()).isNull();
    assertThat(vanished.snapshot().title()).isEqualTo("Title 1 (ACTIVE)");

    stubById(500L, "1", "ACTIVE");
    assertThat(gateway.resolveForInteraction(PUB, "500", COLLECT)).isEqualTo(subject);
    SubjectView relinked = readPort.findByIds(List.of(subject)).get(subject);
    assertThat(relinked.state()).isEqualTo(SubjectState.ACTIVE);
    assertThat(relinked.key()).isEqualTo("500");
  }

  /// 一批失败跳过、其余照常；全部失败时什么都不改。
  @Test
  void should_skip_failed_batch_and_refresh_others() {
    IntStream.rangeClosed(1, 150)
        .forEach(
            i ->
                jdbcTemplate.update(
                    "INSERT INTO subject.registry (subject_id, target_type, natural_key, source_id,"
                        + " state, snapshot, snapshot_at) VALUES (?, 1, ?, ?, 'ACTIVE',"
                        + " '{\"title\":\"old\"}'::jsonb, now())",
                    (long) i,
                    "pmid:" + i,
                    (long) i * 10));
    String firstBatch = pmidsJson(1, 100);
    catalog.stubFor(
        post(urlEqualTo(BY_PMIDS))
            .withRequestBody(equalToJson(firstBatch))
            .willReturn(problem(503)));
    catalog.stubFor(
        post(urlEqualTo(BY_PMIDS))
            .withRequestBody(equalToJson(pmidsJson(101, 150)))
            .willReturn(
                okJson(
                    itemsJson(
                        IntStream.rangeClosed(101, 150)
                            .mapToObj(i -> item(i * 10L + 1, Integer.toString(i), "ACTIVE"))
                            .toList()))));

    ReconciliationResult partial = reconcile();

    assertThat(partial.failedBatches()).isEqualTo(1);
    assertThat(partial.scanned()).isEqualTo(150);
    assertThat(sourceIdOf(1)).isEqualTo(10L);
    assertThat(sourceIdOf(150)).isEqualTo(1501L);

    String before = snapshotOfTable();
    catalog.resetAll();
    catalog.stubFor(any(anyUrl()).willReturn(problem(503)));
    ReconciliationResult allDown = reconcile();
    assertThat(allDown.failedBatches()).isEqualTo(2);
    assertThat(snapshotOfTable()).isEqualTo(before);
  }

  // ---------- 桩与辅助 ----------

  /// AC：期刊锚定在 NLM ID 上。catalog 重导换了期刊 ID，且原来的主 NLM 成了副 NLM（按它解析返回的 `nlmId` 是别的
  /// 值）：对账后登记行仍是同一个 `subject_id`、锚不变，指向新的期刊 ID。
  @Test
  void should_anchor_journal_on_nlm_and_follow_reimport() {
    stubVenueById(100L, "0404401", "ACTIVE");
    SubjectId journal = gateway.resolveForInteraction(JOURNAL, "100", EXPERIENCE);
    assertThat(naturalKeyOf(journal.value())).isEqualTo("nlm:0404401");

    stubVenuesByNlm(Map.of("0404401", venue("0404401", 200L, "100970884", "ACTIVE")));
    ReconciliationResult result = reconcile();

    assertThat(result.failedBatches()).isZero();
    assertThat(naturalKeyOf(journal.value())).isEqualTo("nlm:0404401");
    assertThat(readPort.findByIds(List.of(journal)).get(journal).key()).isEqualTo("200");
    assertThat(readPort.findIdsByKeys(JOURNAL, List.of("200"))).containsEntry("200", journal);
    catalog.verify(
        postRequestedFor(urlEqualTo(VENUE_BY_NLM_IDS))
            .withRequestBody(equalToJson("{\"nlmIds\":[\"0404401\"]}")));
    catalog.resetRequests();
    assertThat(gateway.resolveForInteraction(JOURNAL, "200", EXPERIENCE)).isEqualTo(journal);
    catalog.verify(0, postRequestedFor(anyUrl()));
  }

  /// 期刊不存在、没有 NLM 都按不存在拒绝；软删的期刊不能首次登记（带「已失效」原因码）；都不建行。
  @Test
  void should_reject_journal_that_is_missing_soft_deleted_or_without_nlm() {
    catalog.stubFor(
        post(urlEqualTo(VENUE_BY_IDS))
            .withRequestBody(equalToJson("{\"ids\":[100]}"))
            .willReturn(okJson(itemsJson(List.of(venueMissing("100"))))));
    stubVenueById(200L, "2985213R", "DELETED");
    stubVenueById(300L, null, "ACTIVE");

    assertThatThrownBy(() -> gateway.resolveForInteraction(JOURNAL, "100", EXPERIENCE))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex ->
                assertThat(ex.getErrorTraits())
                    .doesNotContain(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
    assertThatThrownBy(() -> gateway.resolveForInteraction(JOURNAL, "200", EXPERIENCE))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
    assertThatThrownBy(() -> gateway.resolveForInteraction(JOURNAL, "300", EXPERIENCE))
        .isInstanceOf(SubjectNotFoundException.class);
    assertThat(rowCount()).isZero();
  }

  /// 期刊在 catalog 里整行消失：失效、清空来源 ID、保留快照。
  @Test
  void should_mark_vanished_journal_unavailable() {
    stubVenueById(100L, "0404401", "ACTIVE");
    SubjectId journal = gateway.resolveForInteraction(JOURNAL, "100", EXPERIENCE);

    stubVenuesByNlm(Map.of("0404401", venueMissing("0404401")));
    reconcile();

    SubjectView view = readPort.findByIds(List.of(journal)).get(journal);
    assertThat(view.state()).isEqualTo(SubjectState.UNAVAILABLE);
    assertThat(view.key()).isNull();
    assertThat(view.snapshot().title()).isEqualTo("Journal 100");
    assertThat(view.snapshot().abbreviation()).isEqualTo("J 100");
  }

  /// 期刊不能收藏：能力矩阵先拒绝，不请求 catalog。
  @Test
  void should_reject_collecting_a_journal() {
    assertThatThrownBy(() -> gateway.resolveForInteraction(JOURNAL, "100", COLLECT))
        .isInstanceOf(SubjectCapabilityNotSupportedException.class);
    catalog.verify(0, postRequestedFor(anyUrl()));
  }

  /// 按 ID 解析一篇的桩。
  ///
  /// @param id 主键
  /// @param pmid PMID，可空
  /// @param state 状态
  private static void stubById(long id, String pmid, String state) {
    catalog.stubFor(
        post(urlEqualTo(BY_IDS))
            .withRequestBody(equalToJson("{\"ids\":[" + id + "]}"))
            .willReturn(okJson(itemsJson(List.of(item(id, pmid, state))))));
  }

  /// 按 PMID 解析的桩：响应只含给出的行，其余算不存在（不出现在响应里也一样）。
  ///
  /// @param items PMID → 一行 JSON
  private static void stubByPmids(Map<String, String> items) {
    catalog.stubFor(
        post(urlEqualTo(BY_PMIDS)).willReturn(okJson(itemsJson(List.copyOf(items.values())))));
  }

  /// 一行存在的解析结果。
  ///
  /// @param id 主键
  /// @param pmid PMID，可空
  /// @param state 状态
  /// @return JSON
  private static String item(long id, String pmid, String state) {
    return """
        {"key":"%s","exists":true,"state":"%s","id":"%d","pmid":%s,"title":"Title %s (%s)",
         "journal":"Journal","year":2019,"publishedDate":"2019-03"}"""
        .formatted(
            pmid == null ? id : pmid,
            state,
            id,
            pmid == null ? "null" : "\"" + pmid + "\"",
            pmid,
            state);
  }

  /// 按期刊 ID 解析的桩：只对 `{"ids":[id]}` 回这一行。
  ///
  /// @param id 期刊 ID
  /// @param nlmId 主 NLM，可空
  /// @param state 状态
  private static void stubVenueById(long id, String nlmId, String state) {
    catalog.stubFor(
        post(urlEqualTo(VENUE_BY_IDS))
            .withRequestBody(equalToJson("{\"ids\":[" + id + "]}"))
            .willReturn(okJson(itemsJson(List.of(venue(Long.toString(id), id, nlmId, state))))));
  }

  /// 按 NLM ID 解析的桩：响应只含给出的行。
  ///
  /// @param items 请求的 NLM → 一行 JSON
  private static void stubVenuesByNlm(Map<String, String> items) {
    catalog.stubFor(
        post(urlEqualTo(VENUE_BY_NLM_IDS))
            .willReturn(okJson(itemsJson(List.copyOf(items.values())))));
  }

  /// 一行存在的期刊解析结果：刊名 `Journal {期刊 ID}`，缩写 `J {期刊 ID}`（同一期刊按哪种 key 查都一样）。
  ///
  /// @param key 请求键
  /// @param id 期刊 ID
  /// @param nlmId 主 NLM，可空
  /// @param state 状态
  /// @return JSON
  private static String venue(String key, long id, String nlmId, String state) {
    return """
        {"key":"%s","exists":true,"state":"%s","id":"%d","nlmId":%s,"title":"Journal %d",
         "abbreviatedTitle":"J %d"}"""
        .formatted(key, state, id, nlmId == null ? "null" : "\"" + nlmId + "\"", id, id);
  }

  /// 一行不存在的期刊解析结果。
  ///
  /// @param key 请求键
  /// @return JSON
  private static String venueMissing(String key) {
    return """
        {"key":"%s","exists":false,"state":null,"id":null,"nlmId":null,"title":null,
         "abbreviatedTitle":null}"""
        .formatted(key);
  }

  /// 某行的身份锚。
  ///
  /// @param subjectId 本地 ID
  /// @return 身份锚
  private String naturalKeyOf(long subjectId) {
    return jdbcTemplate.queryForObject(
        "SELECT natural_key FROM subject.registry WHERE subject_id = ?", String.class, subjectId);
  }

  /// 包成响应体。
  ///
  /// @param items 各行 JSON
  /// @return JSON
  private static String itemsJson(List<String> items) {
    return "{\"items\":[" + String.join(",", items) + "]}";
  }

  /// by-pmids 的请求体。
  ///
  /// @param from 起
  /// @param to 止（含）
  /// @return JSON
  private static String pmidsJson(int from, int to) {
    return IntStream.rangeClosed(from, to)
        .mapToObj(i -> "\"" + i + "\"")
        .collect(Collectors.joining(",", "{\"pmids\":[", "]}"));
  }

  /// ProblemDetail 错误响应。
  ///
  /// @param status 状态码
  /// @return 响应
  private static ResponseDefinitionBuilder problem(int status) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/problem+json")
        .withBody(DOWN);
  }

  /// 跑一次对账。
  ///
  /// @return 结果
  private ReconciliationResult reconcile() {
    return commandBus.handle(ReconcileSubjectsCommand.of());
  }

  /// 登记表行数。
  ///
  /// @return 行数
  private long rowCount() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM subject.registry", Long.class);
  }

  /// 某行的来源 ID。
  ///
  /// @param subjectId 本地 ID
  /// @return 来源 ID
  private Long sourceIdOf(long subjectId) {
    return jdbcTemplate.queryForObject(
        "SELECT source_id FROM subject.registry WHERE subject_id = ?", Long.class, subjectId);
  }

  /// 整表内容的文本（比较「什么都没改」）。
  ///
  /// @return 按主键排序的行
  private String snapshotOfTable() {
    return String.join(
        "\n",
        jdbcTemplate.queryForList(
            "SELECT concat_ws('|', subject_id, source_id, state, snapshot::text, snapshot_at,"
                + " updated_at) FROM subject.registry ORDER BY subject_id",
            String.class));
  }
}
