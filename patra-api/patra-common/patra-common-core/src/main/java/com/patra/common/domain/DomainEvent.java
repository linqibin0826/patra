package com.patra.common.domain;

import java.io.Serializable;
import java.time.Instant;

/// 领域事件的标记接口。
///
/// 事件应该是不可变的,并可能携带发生时间戳和可选的事件标识符。
public interface DomainEvent extends Serializable {

  /// 表示事件发生时间的时间戳(用于排序/审计)。
  ///
  /// @return 事件发生时间
  Instant occurredAt();
}
