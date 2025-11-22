package com.patra.ingest.app.usecase.execution.publisher;

import com.patra.ingest.domain.outbox.OutboxHeaders;

/// 出版物就绪事件的Outbox消息头
///
/// @param provenanceCode Provenance代码
/// @param taskId 任务标识符
/// @param runId 执行运行标识符
/// @param storageKeyCount 载荷中携带的存储键数量
/// @param occurredAt 事件生成时间(epochMillis)
public record PublicationReadyHeaders(
    String provenanceCode, Long taskId, Long runId, Integer storageKeyCount, Long occurredAt)
    implements OutboxHeaders {}
