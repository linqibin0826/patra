package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

/**
 * 执行上下文（配置快照、编译表达式）。
 *
 * @param taskId 任务ID
 * @param runId 运行ID
 * @param provenanceCode 数据源编码
 * @param operationCode 操作编码
 * @param configSnapshot 配置快照（使用领域快照对象）
 * @param exprHash 表达式哈希
 * @param compiledQuery 编译后的查询
 * @param compiledParams 编译后的参数
 * @param normalizedExpression 规范化表达式
 * @param windowSpec 窗口规格
 * @author linqibin
 * @since 0.1.0
 */
public record ExecutionContext(
        Long taskId,
        Long runId,
        String provenanceCode,
        String operationCode,
        ProvenanceConfigSnapshot configSnapshot,
        String exprHash,
        String compiledQuery,
        JsonNode compiledParams,
        String normalizedExpression,
        WindowSpec windowSpec
) {
}
