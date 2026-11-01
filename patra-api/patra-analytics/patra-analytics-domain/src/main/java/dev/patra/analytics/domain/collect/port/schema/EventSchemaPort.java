package dev.patra.analytics.domain.collect.port.schema;

import dev.patra.analytics.domain.collect.exception.InvalidBatchException;
import dev.patra.analytics.domain.collect.model.vo.CollectedEnvelope;
import dev.patra.analytics.domain.collect.model.vo.EnvelopeCheck;
import dev.patra.analytics.domain.collect.model.vo.EventEnrichment;
import dev.patra.analytics.domain.collect.model.vo.RejectedEnvelope;
import java.util.List;

/// 事件格式：按 JSON Schema 拆批、逐条校验，并生成写出的 JSON（埋点方案 §2、§5）。
public interface EventSchemaPort {

  /// 校验批次外壳（`collect-batch`），拆出每条事件的 JSON 文本。
  ///
  /// @param body 请求体
  /// @return 每条事件的 JSON 文本，顺序同请求
  /// @throws InvalidBatchException 不是合法 JSON 或外壳不合格
  List<String> splitBatch(String body);

  /// 预检并按 `collect-envelope` 校验一条事件。
  ///
  /// @param eventJson 一条事件的 JSON 文本
  /// @return 合格时带解析好的事件，不合格时带原因
  EnvelopeCheck check(String eventJson);

  /// 生成富化事件的 JSON，并按 `enriched-event` 校验产物。
  ///
  /// @param event 合格的事件
  /// @param enrichment 补上的字段
  /// @return 富化事件 JSON（不含 IP 与原始 UA）
  /// @throws IllegalStateException 产物不合格（实现有 bug）
  String enrichedJson(CollectedEnvelope event, EventEnrichment enrichment);

  /// 生成 dlq 记录的 JSON：原文先删 `client_ip`、`user_agent`，再截断到上限。
  ///
  /// @param rejected 不合格的事件
  /// @return dlq 记录 JSON
  String dlqJson(RejectedEnvelope rejected);
}
