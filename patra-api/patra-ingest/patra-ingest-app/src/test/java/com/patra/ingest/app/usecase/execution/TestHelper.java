package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// 测试辅助类
public class TestHelper {

  public static TaskReadyCommand createTestCommand(long taskId, String idempotentKey) {
    Map<String, Object> headers = new HashMap<>();
    headers.put("correlationId", UUID.randomUUID().toString());
    return new TaskReadyCommand(taskId, idempotentKey, headers);
  }

  public static TaskReadyCommand createTestCommand() {
    return createTestCommand(1001L, "idempotent-key-001");
  }
}
