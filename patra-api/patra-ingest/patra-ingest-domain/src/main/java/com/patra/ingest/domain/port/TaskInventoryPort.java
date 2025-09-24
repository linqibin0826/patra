package com.patra.ingest.domain.port;

/**
 * 任务库存读取端口（Domain Port）
 */
public interface TaskInventoryPort {

    long countQueuedTasks(String provenanceCode, String operationCode);
}
