package com.patra.registry.api.dto.expr;

import java.time.Instant;

/**
 * 表达式求值的 API 参数映射。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>provenanceId - 支持该映射的内部数据源标识符
 *   <li>operationType - 操作类型鉴别器(如 HARVEST/UPDATE)
 *   <li>endpointName - 映射应用的端点鉴别器
 *   <li>stdKey - 引擎内部使用的标准化参数键
 *   <li>providerParamName - 原始提供者参数名称
 *   <li>transformCode - 应用于值的可选转换标识符
 *   <li>notesJson - 用于下游诊断的结构化注释负载
 *   <li>effectiveFrom - 映射生效的时间戳
 *   <li>effectiveTo - 映射保持有效的截止时间戳
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ApiParamMappingResp(
    Long provenanceId,
    String operationType,
    String endpointName,
    String stdKey,
    String providerParamName,
    String transformCode,
    String notesJson,
    Instant effectiveFrom,
    Instant effectiveTo) {}
