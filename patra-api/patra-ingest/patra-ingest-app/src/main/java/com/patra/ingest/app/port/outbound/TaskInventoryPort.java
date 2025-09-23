package com.patra.ingest.app.port.outbound;

public interface TaskInventoryPort {

    long countQueuedTasks(String provenanceCode, String operationCode);
}
