package com.patra.ingest.domain.model.vo;

import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import java.util.Collections;
import java.util.List;

/** Result summary for a relay batch execution. */
public record RelayBatchResult(
    ChannelKey channel,
    int fetched,
    int published,
    int retried,
    int failed,
    int leaseMissed,
    List<OutboxRelayDomainEvent> events) {
  public RelayBatchResult {
    events = events == null ? List.of() : List.copyOf(events);
  }

  public static RelayBatchResult empty(ChannelKey channel) {
    return new RelayBatchResult(channel, 0, 0, 0, 0, 0, Collections.emptyList());
  }
}
