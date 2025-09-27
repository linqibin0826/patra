package com.patra.ingest.app.outbox;

import com.patra.ingest.app.outbox.command.OutboxRelayCommand;
import com.patra.ingest.app.outbox.dto.OutboxRelayResult;

/**
 * Outbox Relay 用例接口。
 */
public interface OutboxRelayUseCase {

    OutboxRelayResult relay(OutboxRelayCommand command);
}
