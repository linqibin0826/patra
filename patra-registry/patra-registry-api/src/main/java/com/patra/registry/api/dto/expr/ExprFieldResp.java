package com.patra.registry.api.dto.expr;

/**
 * 暴露给 RPC 客户端的表达式字段定义。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>fieldKey - 与领域词汇表对齐的唯一字段标识符
 *   <li>displayName - 用于 UI 和日志的人类可读字段标签
 *   <li>description - 呈现给消费者的可选字段文档
 *   <li>dataTypeCode - 数据类型鉴别器(STRING/NUMBER/DATE 等)
 *   <li>cardinalityCode - 基数鉴别器(SINGLE/MULTI)
 *   <li>exposable - 字段是否暴露给客户端
 *   <li>dateField - 字段是否表示用于下游解析的日期语义
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprFieldResp(
    String fieldKey,
    String displayName,
    String description,
    String dataTypeCode,
    String cardinalityCode,
    boolean exposable,
    boolean dateField) {}
