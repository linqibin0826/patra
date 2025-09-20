package com.patra.starter.web.error.model;

/**
 * 表示单条校验错误（包含字段名、值与消息；敏感值已脱敏）。
 * 用于 ProblemDetail 的扩展字段，便于前端精确提示。
 *
 * @param field 出错字段名
 * @param rejectedValue 被拒绝的值（可能已脱敏）
 * @param message 错误消息
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationError(
    String field,
    Object rejectedValue,
    String message
) {}
