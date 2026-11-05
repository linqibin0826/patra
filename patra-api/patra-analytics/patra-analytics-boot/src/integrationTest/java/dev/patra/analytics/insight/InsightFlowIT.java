package dev.patra.analytics.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.app.insight.usecase.coread.RefreshCoReadingCommand;
import dev.patra.analytics.app.insight.usecase.hot.RefreshHotReadingCommand;
import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.catalog.api.publication.dto.PublicationLatestResp;
import dev.patra.catalog.api.publication.dto.PublicationResolutionResp;
import dev.patra.catalog.api.publication.dto.PublicationResolveByIdsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveResp;
import dev.patra.catalog.api.publication.dto.PublicationStateResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborReasonResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborsResp;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.api.publication.endpoint.PublicationNeighborEndpoint;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.KafkaContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 计数、热读、看过还看了端到端（insight spec §12）：事件经 Kafka → 入库 → ClickHouse，任务写 PG 快照，三个接口读出；
/// catalog 用替身（全部文献合格、固定的最新收录与两个同主题邻居）。
///
/// ClickHouse 与 PG 容器和其他测试类共用：每个用例用随机的大文献 ID，互不干扰。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "patra.analytics.ingest.enabled=true",
      "patra.analytics.kafka.raw-topic=it-insight.raw",
      "patra.analytics.kafka.dlq-topic=it-insight.dlq"
    })
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      ClickHouseContainerInitializer.class,
      KafkaContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class InsightFlowIT {

  private static final String RAW = "it-insight.raw";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final List<Long> LATEST = LongStream.rangeClosed(1, 28).boxed().toList();

  @Autowired private RestTestClient restClient;
  @Autowired private CommandBus commandBus;
  @Autowired private Client clickhouse;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redis;

  @MockitoBean private PublicationEndpoint publications;
  @MockitoBean private PublicationNeighborEndpoint neighbors;

  @BeforeEach
  void catalogDouble() {
    when(publications.resolveByIds(any()))
        .thenAnswer(
            invocation ->
                PublicationResolveResp.of(
                    invocation.<PublicationResolveByIdsReq>getArgument(0).ids().stream()
                        .map(
                            id ->
                                PublicationResolutionResp.builder()
                                    .key(Long.toString(id))
                                    .exists(true)
                                    .state(PublicationStateResp.ACTIVE)
                                    .id(id)
                                    .build())
                        .toList()));
    when(neighbors.latest(anyInt())).thenReturn(PublicationLatestResp.of(LATEST));
    when(neighbors.subjectNeighbors(anyLong(), anyInt()))
        .thenReturn(
            SubjectNeighborsResp.of(
                List.of(
                    SubjectNeighborResp.builder()
                        .id(71L)
                        .reason(SubjectNeighborReasonResp.SAME_MESH)
                        .meshTerm("Shock, Septic")
                        .build(),
                    SubjectNeighborResp.builder()
                        .id(72L)
                        .reason(SubjectNeighborReasonResp.SAME_VENUE)
                        .venueName("Chest")
                        .build())));
  }

  @Test
  @Timeout(60)
  void stats_reflect_ingested_event_within_a_minute() {
    long article = newArticleId();
    sendReads(article, 1, "article_viewed", "fulltext_read");
    awaitIngested(article, 2);

    JsonNode item = json(stats("[\"" + article + "\"]")).get("items").get(0);

    assertThat(item.get("articleId").asString()).isEqualTo(Long.toString(article));
    assertThat(item.get("viewerCount").asInt()).isEqualTo(1);
    assertThat(item.get("fulltextReaderCount").asInt()).isEqualTo(1);
    assertThat(item.get("hot").asBoolean()).isFalse();
    assertThat(redis.getExpire("analytics:insight:stats:real:" + article)).isBetween(1L, 60L);
  }

  @Test
  void stats_dedupes_and_keeps_big_ids_as_strings() {
    JsonNode items =
        json(stats("[\"9007199254740993\", \"9007199254740993\", \"1\"]")).get("items");

    assertThat(items).hasSize(2);
    assertThat(items.get(0).get("articleId").isString()).isTrue();
    assertThat(items.get(0).get("articleId").asString()).isEqualTo("9007199254740993");
    assertThat(items.get(0).get("viewerCount").asInt()).isZero();
    assertThat(items.get(1).get("articleId").asString()).isEqualTo("1");
  }

  @Test
  void stats_rejects_bad_requests() {
    String ids101 =
        LongStream.rangeClosed(1, 101)
            .mapToObj(i -> "\"" + i + "\"")
            .collect(Collectors.joining(","));
    assertThat(status(post("/articles/stats", "{}"))).isEqualTo(422);
    assertThat(status(post("/articles/stats", "{\"articleIds\":[]}"))).isEqualTo(422);
    assertThat(status(post("/articles/stats", "{\"articleIds\":[" + ids101 + "]}"))).isEqualTo(422);
    assertThat(status(post("/articles/stats", "{\"articleIds\":[\"0\"]}"))).isEqualTo(422);
    assertThat(status(post("/articles/stats", "{\"articleIds\":[\"abc\"]}"))).isEqualTo(400);
  }

  @Test
  @Timeout(60)
  void hot_reads_after_job() {
    long article = newArticleId();
    for (long person = 1; person <= 3; person++) {
      sendReads(article, person, "article_viewed", "fulltext_read");
    }
    awaitIngested(article, 6);

    commandBus.handle(RefreshHotReadingCommand.of());

    JsonNode page = json(get("/hot-reads"));
    assertThat(page.get("computedAt").asString()).endsWith("Z");
    assertThat(page.get("rankedTotal").asInt()).isGreaterThanOrEqualTo(1);
    List<JsonNode> items = new ArrayList<>();
    page.get("items").forEach(items::add);
    assertThat(items).hasSize(14);
    JsonNode mine =
        items.stream()
            .filter(i -> i.get("articleId").asString().equals(Long.toString(article)))
            .findFirst()
            .orElseThrow();
    assertThat(mine.get("source").asString()).isEqualTo("RANKED");
    assertThat(mine.get("rank").asInt()).isPositive();
    assertThat(items)
        .filteredOn(i -> i.get("source").asString().equals("LATEST"))
        .allSatisfy(i -> assertThat(i.get("rank").isNull()).isTrue());

    JsonNode stats = json(stats("[\"" + article + "\", \"1\"]")).get("items");
    assertThat(stats.get(0).get("hot").asBoolean()).isTrue();
    assertThat(stats.get(1).get("hot").asBoolean()).isFalse();
  }

  @Test
  void hot_reads_empty_before_first_run() {
    jdbcTemplate.update("TRUNCATE insight.hot_reading, insight.snapshot_run");

    JsonNode page = json(get("/hot-reads"));

    assertThat(page.get("computedAt").isNull()).isTrue();
    assertThat(page.get("rankedTotal").asInt()).isZero();
    assertThat(page.get("items")).isEmpty();
  }

  @Test
  @Timeout(60)
  void also_read_after_job() {
    long x = newArticleId();
    long y = newArticleId();
    for (long person = 1; person <= 3; person++) {
      sendSession(person, x, y);
    }
    awaitIngested(x, 3);
    awaitIngested(y, 3);

    commandBus.handle(RefreshCoReadingCommand.of());

    JsonNode items = json(get("/articles/" + x + "/also-read")).get("items");
    assertThat(items.get(0).get("articleId").asString()).isEqualTo(Long.toString(y));
    assertThat(items.get(0).get("source").asString()).isEqualTo("CO_READ");
    assertThat(items.get(0).get("meshTerm").isNull()).isTrue();
    assertThat(items.get(1).get("source").asString()).isEqualTo("SAME_MESH");
    assertThat(items.get(1).get("meshTerm").asString()).isEqualTo("Shock, Septic");
    assertThat(items.get(2).get("source").asString()).isEqualTo("SAME_VENUE");
    assertThat(items.get(2).get("venueName").asString()).isEqualTo("Chest");
    assertThat(items).hasSize(3);
    assertThat(status(get("/articles/0/also-read"))).isEqualTo(422);
  }

  // ===== fixtures =====

  private static long newArticleId() {
    return ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_000_000_000_000L);
  }

  /// 一个人在一个新的真人会话里（先有一条浏览器的页面浏览）对一篇文献依次发几条事件。
  private static void sendReads(long article, long person, String... eventNames) {
    String session = UUID.randomUUID().toString();
    List<String> values = new ArrayList<>();
    values.add(event("page_viewed", "client", person, session, null));
    for (String name : eventNames) {
      values.add(event(name, "server", person, session, article));
    }
    send(values);
  }

  /// 一个人在一个真人会话里看了几篇。
  private static void sendSession(long person, long... articles) {
    String session = UUID.randomUUID().toString();
    List<String> values = new ArrayList<>();
    values.add(event("page_viewed", "client", person, session, null));
    for (long article : articles) {
      values.add(event("article_viewed", "server", person, session, article));
    }
    send(values);
  }

  /// 富化事件（PAP-164 的样例换上事件名、来源、人、会话、文献与当前时间）。
  private static String event(
      String name, String source, long person, String session, Long article) {
    try (InputStream in =
        InsightFlowIT.class
            .getClassLoader()
            .getResourceAsStream(
                "analytics/examples/valid/enriched-event--server-article-viewed.json")) {
      ObjectNode node =
          (ObjectNode) MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      String now = Instant.now().toString();
      node.put("event_id", UUID.randomUUID().toString());
      node.put("event_name", name);
      node.put("source", source);
      node.put("user_id", Long.toString(7_000_000L + person));
      node.put("session_id", session);
      node.put("created_at", now);
      node.put("sent_at", now);
      node.put("received_at", now);
      node.put("derived_at", now);
      ObjectNode properties = node.putObject("properties");
      if (article != null) {
        properties.put("article_id", Long.toString(article));
      }
      return MAPPER.writeValueAsString(node);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void send(List<String> values) {
    Map<String, Object> config =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaContainerInitializer.getKafkaContainer().getBootstrapServers());
    try (KafkaProducer<String, String> producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      for (String value : values) {
        producer.send(new ProducerRecord<>(RAW, value)).get(10, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      throw new IllegalStateException("写 Kafka 失败", e);
    }
  }

  /// 等这篇文献的事件全部入库（只看明细表，不经接口：接口会把「还没到」缓存 60 秒）。
  private void awaitIngested(long article, int rows) {
    BooleanSupplier ingested =
        () ->
            clickhouse
                    .queryAll("SELECT count() AS n FROM events WHERE article_id = " + article)
                    .getFirst()
                    .getLong("n")
                >= rows;
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!ingested.getAsBoolean()) {
      assertThat(System.nanoTime()).as("等入库超时").isLessThan(deadline);
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }

  private EntityExchangeResult<String> stats(String idsJson) {
    return post("/articles/stats", "{\"articleIds\":" + idsJson + "}");
  }

  private EntityExchangeResult<String> post(String uri, String body) {
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  private EntityExchangeResult<String> get(String uri) {
    return restClient.get().uri(uri).exchange().expectBody(String.class).returnResult();
  }

  private static int status(EntityExchangeResult<String> result) {
    return result.getStatus().value();
  }

  private static JsonNode json(EntityExchangeResult<String> result) {
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(200);
    return MAPPER.readTree(result.getResponseBody());
  }
}
