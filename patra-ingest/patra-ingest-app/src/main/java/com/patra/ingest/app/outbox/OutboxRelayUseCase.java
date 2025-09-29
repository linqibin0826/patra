package com.patra.ingest.app.outbox;

import com.patra.ingest.app.outbox.command.OutboxRelayInstruction;
import com.patra.ingest.app.outbox.dto.RelayReport;

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
