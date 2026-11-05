package dev.patra.analytics.domain.collect.port.publish;

import dev.patra.analytics.domain.collect.exception.EventPublishUnavailableException;
import dev.patra.analytics.domain.collect.model.vo.OutboundMessage;
import java.util.List;

/// 把采集结果写进 Kafka。
public interface EventPublishPort {

  /// 写出一批消息，全部确认后返回。
  ///
  /// @param events 要写出的消息
  /// @throws EventPublishUnavailableException 有任何一条没写成（整批视为失败，产生方可重试，入库按事件 ID 去重）
  void publish(List<OutboundMessage> events);
}
