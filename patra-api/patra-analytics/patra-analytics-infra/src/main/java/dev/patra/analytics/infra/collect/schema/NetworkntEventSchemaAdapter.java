package dev.patra.analytics.infra.collect.schema;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.AnnotationKeyword;
import dev.patra.analytics.domain.collect.exception.InvalidBatchException;
import dev.patra.analytics.domain.collect.model.enums.RejectionReason;
import dev.patra.analytics.domain.collect.model.vo.CollectedEnvelope;
import dev.patra.analytics.domain.collect.model.vo.EnvelopeCheck;
import dev.patra.analytics.domain.collect.model.vo.EventEnrichment;
import dev.patra.analytics.domain.collect.model.vo.RejectedEnvelope;
import dev.patra.analytics.domain.collect.port.schema.EventSchemaPort;
import dev.patra.analytics.domain.kernel.model.vo.EventName;
import dev.patra.analytics.domain.kernel.model.vo.SchemaVersion;
import dev.patra.analytics.infra.collect.support.AnalyticsResources;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 用 networknt json-schema-validator（3.x，Jackson 3）实现事件格式校验（埋点方案 §5、采集 spec §4.2）。
///
/// schema 的 `$id` 前缀映射到 classpath（`patra-analytics-api` 的资源），校验时不联网；`x-patra-version` 注册为注解
/// 关键字。schema 在构造时加载一次。预检先于 schema 校验：`oneOf` 失败时只报「没有分支匹配」，分不出原因。
@Component
public final class NetworkntEventSchemaAdapter implements EventSchemaPort {

  /// dlq 记录里原文的上限（字符）
  static final int RAW_LIMIT = 16_384;

  /// dlq 记录里事件名的上限（字符，`dlq-record` 的 `maxLength`）
  private static final int EVENT_NAME_LIMIT = 64;

  /// 校验说明的上限（字符）
  private static final int DETAIL_LIMIT = 1_000;

  /// 只用来查地区与判爬虫、不写出的两个键
  private static final List<String> TRANSIENT_KEYS = List.of("client_ip", "user_agent");

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private final JsonMapper jsonMapper;
  private final Schema batchSchema;
  private final Schema envelopeSchema;
  private final Schema enrichedSchema;

  /// 加载 schema。
  ///
  /// @param jsonMapper 解析与生成 JSON
  public NetworkntEventSchemaAdapter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    Dialect dialect =
        Dialect.builder(Dialects.getDraft202012())
            .keyword(new AnnotationKeyword("x-patra-version"))
            .build();
    SchemaRegistry registry =
        SchemaRegistry.withDialect(
            dialect,
            builder ->
                builder.schemaIdResolvers(
                    resolvers ->
                        resolvers.mapPrefix(
                            AnalyticsResources.SCHEMA_BASE_ID,
                            AnalyticsResources.SCHEMA_CLASSPATH)));
    this.batchSchema = load(registry, "collect-batch");
    this.envelopeSchema = load(registry, "collect-envelope");
    this.enrichedSchema = load(registry, "enriched-event");
  }

  /// 校验批次外壳，拆出每条事件的 JSON 文本。
  ///
  /// @param body 请求体
  /// @return 每条事件的 JSON 文本
  @Override
  public List<String> splitBatch(String body) {
    JsonNode root = parse(body).orElseThrow(() -> new InvalidBatchException("请求体不是合法的 JSON 对象"));
    if (!root.isObject()) {
      throw new InvalidBatchException("请求体不是 JSON 对象");
    }
    List<Error> errors = batchSchema.validate(root);
    if (!errors.isEmpty()) {
      throw new InvalidBatchException("批次外壳不合格：" + describe(errors.getFirst()));
    }
    List<String> events = new ArrayList<>();
    root.get("events").forEach(event -> events.add(jsonMapper.writeValueAsString(event)));
    return events;
  }

  /// 预检并校验一条事件。
  ///
  /// @param eventJson 一条事件的 JSON 文本
  /// @return 校验结果
  @Override
  public EnvelopeCheck check(String eventJson) {
    Optional<JsonNode> parsed = parse(eventJson);
    if (parsed.isEmpty() || !parsed.get().isObject()) {
      return new EnvelopeCheck.Invalid(RejectionReason.SCHEMA_INVALID, "事件不是 JSON 对象", null);
    }
    JsonNode node = parsed.get();
    String wireName = text(node, "event_name");
    Optional<EventName> eventName = EventName.fromWire(wireName);
    if (eventName.isEmpty()) {
      return new EnvelopeCheck.Invalid(
          RejectionReason.UNKNOWN_EVENT, "event_name 不在事件字典里：" + wireName, wireName);
    }
    if (!eventName.get().source().wire().equals(text(node, "source"))) {
      return new EnvelopeCheck.Invalid(
          RejectionReason.SOURCE_MISMATCH,
          wireName + " 只能由 " + eventName.get().source().wire() + " 发出",
          wireName);
    }
    Optional<SchemaVersion> version = SchemaVersion.parse(text(node, "schema_version"));
    if (version.isPresent() && !version.get().accepted()) {
      return new EnvelopeCheck.Invalid(
          RejectionReason.VERSION_REJECTED,
          "schema_version 的 MODEL 不是 " + SchemaVersion.CURRENT_MODEL,
          wireName);
    }
    List<Error> errors = envelopeSchema.validate(node);
    if (!errors.isEmpty()) {
      return new EnvelopeCheck.Invalid(
          RejectionReason.SCHEMA_INVALID, describe(errors.getFirst()), wireName);
    }
    try {
      return new EnvelopeCheck.Valid(toEnvelope(node, eventName.get()));
    } catch (DateTimeException e) {
      // schema 只校验形状：2 月 30 日这类不存在的日期过得了正则，解析时才发现
      return new EnvelopeCheck.Invalid(
          RejectionReason.SCHEMA_INVALID,
          "created_at 或 sent_at 不是存在的时刻：" + e.getMessage(),
          wireName);
    }
  }

  /// 生成富化事件的 JSON 并校验。
  ///
  /// @param event 合格的事件
  /// @param enrichment 补上的字段
  /// @return 富化事件 JSON
  @Override
  public String enrichedJson(CollectedEnvelope event, EventEnrichment enrichment) {
    ObjectNode node = jsonMapper.createObjectNode();
    node.put("event_id", event.eventId());
    node.put("event_name", event.eventName().wire());
    node.put("schema_version", event.schemaVersion());
    node.put("source", event.source().wire());
    node.put("created_at", TIMESTAMP.format(event.createdAt()));
    node.put("sent_at", TIMESTAMP.format(event.sentAt()));
    node.put("received_at", TIMESTAMP.format(enrichment.receivedAt()));
    node.put("derived_at", TIMESTAMP.format(enrichment.derivedAt()));
    node.put("device_id", event.deviceId());
    node.put("session_id", event.sessionId());
    node.put("user_id", event.userId());
    node.put("page_path", event.pagePath());
    node.put("page_type", event.pageType());
    node.put("referrer_host", event.referrerHost());
    node.put("country_code", enrichment.geoLocation().countryCode());
    node.put("province", enrichment.geoLocation().province());
    node.put("browser_family", enrichment.userAgent().browserFamily());
    node.put("os_family", enrichment.userAgent().osFamily());
    node.put("device_class", enrichment.userAgent().deviceClass());
    node.put("is_bot", enrichment.bot());
    node.put("bot_reason", enrichment.bot() ? enrichment.botReason().wire() : null);
    node.put("is_synthetic", event.synthetic());
    node.set("properties", jsonMapper.readTree(event.propertiesJson()));
    List<Error> errors = enrichedSchema.validate(node);
    if (!errors.isEmpty()) {
      throw new IllegalStateException("富化事件不合格：" + describe(errors.getFirst()));
    }
    return jsonMapper.writeValueAsString(node);
  }

  /// 生成 dlq 记录的 JSON：先删 IP 与 UA，再截断。
  ///
  /// @param rejected 不合格的事件
  /// @return dlq 记录 JSON
  @Override
  public String dlqJson(RejectedEnvelope rejected) {
    String raw = withoutTransientKeys(rejected.rawJson());
    boolean truncated = raw.length() > RAW_LIMIT;
    ObjectNode node = jsonMapper.createObjectNode();
    node.put("received_at", TIMESTAMP.format(rejected.receivedAt()));
    node.put("reason_code", rejected.reason().wire());
    node.put("reason_detail", truncate(rejected.detail(), DETAIL_LIMIT));
    node.put(
        "event_name",
        rejected.eventName() == null ? null : truncate(rejected.eventName(), EVENT_NAME_LIMIT));
    node.put("raw", truncated ? raw.substring(0, RAW_LIMIT) : raw);
    node.put("raw_truncated", truncated);
    return jsonMapper.writeValueAsString(node);
  }

  /// 原事件是 JSON 对象时删掉顶层的 IP 与 UA；解析不了就原样返回（拆批时已确认是合法 JSON）。
  ///
  /// @param rawJson 原事件
  /// @return 删除后的 JSON 文本
  private String withoutTransientKeys(String rawJson) {
    Optional<JsonNode> parsed = parse(rawJson);
    if (parsed.isEmpty() || !(parsed.get() instanceof ObjectNode object)) {
      return rawJson;
    }
    TRANSIENT_KEYS.forEach(object::remove);
    return jsonMapper.writeValueAsString(object);
  }

  /// 把校验通过的事件转成领域对象。
  ///
  /// @param node 事件
  /// @param eventName 事件名
  /// @return 领域对象
  private CollectedEnvelope toEnvelope(JsonNode node, EventName eventName) {
    return CollectedEnvelope.builder()
        .eventId(text(node, "event_id"))
        .eventName(eventName)
        .schemaVersion(text(node, "schema_version"))
        .source(eventName.source())
        .createdAt(Instant.parse(text(node, "created_at")))
        .sentAt(Instant.parse(text(node, "sent_at")))
        .deviceId(text(node, "device_id"))
        .sessionId(text(node, "session_id"))
        .userId(text(node, "user_id"))
        .pagePath(text(node, "page_path"))
        .pageType(text(node, "page_type"))
        .referrerHost(text(node, "referrer_host"))
        .clientIp(text(node, "client_ip"))
        .userAgent(text(node, "user_agent"))
        .synthetic(node.get("is_synthetic").asBoolean())
        .propertiesJson(jsonMapper.writeValueAsString(node.get("properties")))
        .build();
  }

  /// 解析 JSON；不是合法 JSON 时为空。
  ///
  /// @param json JSON 文本
  /// @return 解析结果
  private Optional<JsonNode> parse(String json) {
    if (json == null || json.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(jsonMapper.readTree(json)).filter(node -> !node.isMissingNode());
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  /// 读一个字符串字段；不存在或不是字符串时为 `null`。
  ///
  /// @param node 对象
  /// @param field 字段名
  /// @return 字段值
  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }

  /// 校验错误的说明：JSON 指针 + 校验器的话。
  ///
  /// @param error 第一条错误
  /// @return 说明，最长 1,000 字符
  private static String describe(Error error) {
    return truncate(error.getInstanceLocation() + ": " + error.getMessage(), DETAIL_LIMIT);
  }

  /// 截断到上限。
  ///
  /// @param text 文本
  /// @param limit 上限
  /// @return 截断后的文本
  private static String truncate(String text, int limit) {
    return text.length() > limit ? text.substring(0, limit) : text;
  }

  /// 按 `$id` 加载一个 schema。
  ///
  /// @param registry schema 注册表
  /// @param name 文件名去掉 `.schema.json`
  /// @return schema
  private static Schema load(SchemaRegistry registry, String name) {
    return registry.getSchema(
        SchemaLocation.of(AnalyticsResources.SCHEMA_BASE_ID + name + ".schema.json"));
  }
}
