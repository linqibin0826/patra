package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 表达式编译结果。
 *
 * @param isValid 是否编译成功
 * @param query 编译后的查询字符串（如PubMed的term）
 * @param params 编译后的参数（如retmax、sort等，JSON格式）
 * @param normalizedExpression 规范化表达式
 * @param validationMessage 验证消息（失败时）
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCompilationResult(
        boolean isValid,
        String query,
        JsonNode params,
        String normalizedExpression,
        String validationMessage
) {
    /**
     * 成功结果。
     */
    public static ExprCompilationResult success(String query,
                                                JsonNode params,
                                                String normalizedExpression) {
        return new ExprCompilationResult(true, query, params, normalizedExpression, null);
    }

    /**
     * 失败结果。
     */
    public static ExprCompilationResult failure(String validationMessage) {
        return new ExprCompilationResult(false, null, null, null, validationMessage);
    }
}
