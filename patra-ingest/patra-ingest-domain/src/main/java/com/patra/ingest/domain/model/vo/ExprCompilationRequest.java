package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 表达式编译请求。
 *
 * @param provenanceCode 数据源编码（如 PUBMED、EPMC）
 * @param operationCode 操作编码（如 INCREMENTAL、BACKFILL）
 * @param rawExpression 原始表达式字符串
 * @param paramsJson 参数JSON（可为null）
 * @param configSnapshot 配置快照JSON（可为null）
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCompilationRequest(
        String provenanceCode,
        String operationCode,
        String rawExpression,
        JsonNode paramsJson,
        JsonNode configSnapshot
) {
    /**
     * 快捷构造（无配置快照）。
     */
    public static ExprCompilationRequest of(String provenanceCode,
                                            String operationCode,
                                            String rawExpression,
                                            JsonNode paramsJson) {
        return new ExprCompilationRequest(provenanceCode, operationCode, rawExpression, paramsJson, null);
    }
}
