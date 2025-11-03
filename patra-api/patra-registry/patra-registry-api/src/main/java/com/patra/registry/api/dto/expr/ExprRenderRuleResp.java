package com.patra.registry.api.dto.expr;

import java.time.Instant;

/**
 * 表达式输出模板的渲染规则。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>provenanceId - 拥有该规则的内部数据源标识符
 *   <li>operationType - 规则应用的操作类型鉴别器
 *   <li>fieldKey - 渲染规则目标的表达式字段键
 *   <li>opCode - 求值字段时使用的逻辑运算符
 *   <li>matchTypeCode - 用于渲染求值的匹配类型鉴别器
 *   <li>negated - 渲染的表达式是否被否定
 *   <li>valueTypeCode - 用于替换的值类型鉴别器
 *   <li>emitTypeCode - 模板化输出的发出策略
 *   <li>template - 顶级模板片段
 *   <li>itemTemplate - 列表项的模板片段
 *   <li>joiner - 在渲染片段之间应用的连接标记
 *   <li>wrapGroup - 是否用分组标记包装渲染片段
 *   <li>paramsJson - 序列化的参数元数据
 *   <li>functionCode - 渲染期间应用的可选函数标识符
 *   <li>effectiveFrom - 规则生效的时间戳
 *   <li>effectiveTo - 规则保持有效的截止时间戳
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprRenderRuleResp(
    Long provenanceId,
    String operationType,
    String fieldKey,
    String opCode,
    String matchTypeCode,
    Boolean negated,
    String valueTypeCode,
    String emitTypeCode,
    String template,
    String itemTemplate,
    String joiner,
    boolean wrapGroup,
    String paramsJson,
    String functionCode,
    Instant effectiveFrom,
    Instant effectiveTo) {}
