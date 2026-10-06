package dev.linqibin.starter.web.error.model;

/// 通过 ProblemDetail 扩展暴露的验证错误条目的不可变表示。敏感值会预先掩码，以避免泄露机密数据。
///
/// @param field 逻辑字段名
/// @param code 原因码，大写下划线（如 `NOT_BLANK`、`TOO_COMMON`）；没有时为 `null`
/// @param rejectedValue 已清理的被拒绝值；领域层报的字段错误一律为 `null`
/// @param message 人类可读的验证消息
/// @author linqibin
/// @since 0.1.0
public record ValidationError(String field, String code, Object rejectedValue, String message) {}
