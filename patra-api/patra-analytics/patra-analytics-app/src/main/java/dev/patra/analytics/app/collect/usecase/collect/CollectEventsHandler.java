package dev.patra.analytics.app.collect.usecase.collect;

import dev.patra.analytics.domain.collect.model.enums.BotReason;
import dev.patra.analytics.domain.collect.model.enums.RejectionReason;
import dev.patra.analytics.domain.collect.model.vo.CollectedEnvelope;
import dev.patra.analytics.domain.collect.model.vo.EnvelopeCheck;
import dev.patra.analytics.domain.collect.model.vo.EventEnrichment;
import dev.patra.analytics.domain.collect.model.vo.OutboundMessage;
import dev.patra.analytics.domain.collect.model.vo.RejectedEnvelope;
import dev.patra.analytics.domain.collect.model.vo.UserAgentInfo;
import dev.patra.analytics.domain.collect.policy.BotClassification;
import dev.patra.analytics.domain.collect.policy.EventTimeCorrection;
import dev.patra.analytics.domain.collect.port.geolocation.GeolocationPort;
import dev.patra.analytics.domain.collect.port.publish.EventPublishPort;
import dev.patra.analytics.domain.collect.port.rate.DeviceRatePort;
import dev.patra.analytics.domain.collect.port.robot.RobotListPort;
import dev.patra.analytics.domain.collect.port.schema.EventSchemaPort;
import dev.patra.analytics.domain.collect.port.useragent.UserAgentPort;
import dev.patra.analytics.domain.kernel.model.vo.EventName;
import dev.patra.tool.cqrs.CommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 采集一批事件（采集 spec §4.2）：拆批 → 逐条预检与校验 → 富化（时间校正、UA、爬虫、地区）→ 合格的写
/// `events.raw`、不合格的写 `events.dlq`，一次写出整批。
///
/// 不开事务：采集链路不写 PG。爬虫判定先看 UA，没命中才查 Redis 的频率窗口；Redis 出错跳过频率规则。
/// 产物不合格（实现有 bug）的那条转进 dlq，不拖累同批其他事件。
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectEventsHandler
    implements CommandHandler<CollectEventsCommand, CollectEventsResult> {

  /// 逐条采集结果的指标
  static final String EVENTS_METRIC = "analytics.collect.events";

  /// 判为爬虫的指标
  static final String BOT_METRIC = "analytics.collect.bot.events";

  /// 跳过频率规则的指标
  static final String RATE_SKIPPED_METRIC = "analytics.collect.rate.rule.skipped";

  private final EventSchemaPort schema;
  private final UserAgentPort userAgents;
  private final RobotListPort robots;
  private final DeviceRatePort rates;
  private final GeolocationPort geolocation;
  private final EventPublishPort publisher;
  private final Clock clock;
  private final MeterRegistry meters;

  /// 采集一批事件。
  ///
  /// @param command 请求体
  /// @return 合格与不合格的条数
  @Override
  public CollectEventsResult handle(CollectEventsCommand command) {
    List<String> events = schema.splitBatch(command.body());
    Instant receivedAt = clock.instant();
    List<OutboundMessage> messages = new ArrayList<>(events.size());
    BatchState state = new BatchState();
    int accepted = 0;
    for (String eventJson : events) {
      Optional<OutboundMessage> raw =
          switch (schema.check(eventJson)) {
            case EnvelopeCheck.Valid(CollectedEnvelope event) ->
                enrich(event, eventJson, receivedAt, messages, state);
            case EnvelopeCheck.Invalid invalid -> {
              messages.add(
                  reject(
                      invalid.reason(),
                      invalid.detail(),
                      invalid.eventName(),
                      eventJson,
                      receivedAt));
              yield Optional.empty();
            }
          };
      if (raw.isPresent()) {
        messages.add(raw.get());
        accepted++;
      }
    }
    publisher.publish(messages);
    return CollectEventsResult.of(accepted, events.size() - accepted);
  }

  /// 富化一条合格的事件，生成 `events.raw` 消息；产物不合格时转进 dlq。
  ///
  /// @param event 合格的事件
  /// @param eventJson 原文（转进 dlq 时用）
  /// @param receivedAt 本批收到的时刻
  /// @param messages 本批的消息（转进 dlq 时加入）
  /// @param state 本批的状态
  /// @return 富化事件消息；转进 dlq 时为空
  private Optional<OutboundMessage> enrich(
      CollectedEnvelope event,
      String eventJson,
      Instant receivedAt,
      List<OutboundMessage> messages,
      BatchState state) {
    UserAgentInfo userAgent = userAgents.parse(event.userAgent());
    BotReason botReason = classify(event, userAgent, receivedAt, state).orElse(null);
    EventEnrichment enrichment =
        EventEnrichment.builder()
            .receivedAt(receivedAt)
            .derivedAt(EventTimeCorrection.derive(event.createdAt(), event.sentAt(), receivedAt))
            .geoLocation(geolocation.locate(event.clientIp()))
            .userAgent(userAgent)
            .botReason(botReason)
            .build();
    String payload;
    try {
      payload = schema.enrichedJson(event, enrichment);
    } catch (IllegalStateException e) {
      log.error("富化事件不合格，转进 dlq: eventId={}, {}", event.eventId(), e.getMessage());
      messages.add(
          reject(
              RejectionReason.SCHEMA_INVALID,
              e.getMessage(),
              event.eventName().wire(),
              eventJson,
              receivedAt));
      return Optional.empty();
    }
    if (botReason != null) {
      meters.counter(BOT_METRIC, "reason", botReason.wire()).increment();
    }
    meters
        .counter(
            EVENTS_METRIC,
            "outcome",
            "accepted",
            "reason",
            "none",
            "event_name",
            event.eventName().wire())
        .increment();
    return Optional.of(OutboundMessage.raw(event.deviceId(), payload));
  }

  /// 判定爬虫：先看 UA，没命中再查频率窗口。同一批里频率窗口第一次出错后，剩下的事件直接跳过频率规则：
  /// Redis 卡住时每条都等满超时，一批几百条会远超门户的超时。
  ///
  /// @param event 事件
  /// @param userAgent UA 解析结果
  /// @param receivedAt 本批收到的时刻
  /// @param state 本批的状态
  /// @return 爬虫原因；不是爬虫为空
  private Optional<BotReason> classify(
      CollectedEnvelope event, UserAgentInfo userAgent, Instant receivedAt, BatchState state) {
    Optional<BotReason> byUserAgent =
        BotClassification.fromUserAgent(robots.listed(event.userAgent()), userAgent.deviceClass());
    if (byUserAgent.isPresent()) {
      return byUserAgent;
    }
    if (state.rateUnavailable) {
      meters.counter(RATE_SKIPPED_METRIC).increment();
      return Optional.empty();
    }
    boolean counted = event.eventName() == EventName.ARTICLE_VIEWED;
    OptionalInt windowCount = rates.observe(event.deviceId(), event.eventId(), counted, receivedAt);
    if (windowCount.isEmpty()) {
      state.rateUnavailable = true;
      meters.counter(RATE_SKIPPED_METRIC).increment();
      return Optional.empty();
    }
    return BotClassification.overRate(windowCount.getAsInt())
        ? Optional.of(BotReason.RATE)
        : Optional.empty();
  }

  /// 生成一条 dlq 消息并记指标。
  ///
  /// @param reason 原因
  /// @param detail 说明
  /// @param eventName 原事件名，可为 `null`
  /// @param eventJson 原文
  /// @param receivedAt 本批收到的时刻
  /// @return dlq 消息
  private OutboundMessage reject(
      RejectionReason reason,
      String detail,
      String eventName,
      String eventJson,
      Instant receivedAt) {
    meters
        .counter(
            EVENTS_METRIC,
            "outcome",
            "rejected",
            "reason",
            reason.wire(),
            "event_name",
            EventName.fromWire(eventName).map(EventName::wire).orElse("unknown"))
        .increment();
    return OutboundMessage.dlq(
        schema.dlqJson(
            RejectedEnvelope.builder()
                .reason(reason)
                .detail(detail)
                .eventName(eventName)
                .rawJson(eventJson)
                .receivedAt(receivedAt)
                .build()));
  }

  /// 一批采集里跨事件共享的状态。
  private static final class BatchState {

    /// 频率窗口在本批里已经出过错
    private boolean rateUnavailable;
  }
}
