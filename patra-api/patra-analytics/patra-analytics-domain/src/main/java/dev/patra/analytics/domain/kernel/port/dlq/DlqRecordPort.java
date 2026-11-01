package dev.patra.analytics.domain.kernel.port.dlq;

import dev.patra.analytics.domain.kernel.model.vo.RejectedEnvelope;

/// 生成 dlq 记录（`dlq-record` schema）：采集的不合格事件与入库被拒的事件都用它。
public interface DlqRecordPort {

  /// 生成 dlq 记录的 JSON：原文先删 `client_ip`、`user_agent`，再截断到上限。
  ///
  /// @param rejected 不合格的事件
  /// @return dlq 记录 JSON
  String dlqJson(RejectedEnvelope rejected);
}
