package com.patra.ingest.app.relay;

import com.patra.ingest.app.relay.command.OutboxRelayInstruction;
import com.patra.ingest.app.relay.dto.RelayReport;

/**
 * Outbox Relay 用例契约。
 */
public interface OutboxRelayUseCase {

    /**
     * 执行一次 Outbox Relay。
     *
     * @param instruction 调度指令
     * @return 执行结果
     */
    RelayReport relay(OutboxRelayInstruction instruction);
}
