package com.patra.starter.web.error.model;

/**
 * 通过 ProblemDetail 扩展暴露的验证错误条目的不可变表示。 敏感值会预先掩码,以避免泄露机密数据。
 *
 * @param field 逻辑字段名
 * @param rejectedValue 已清理的被拒绝值,如果可用
 * @param message 人类可读的验证消息
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationError(String field, Object rejectedValue, String message) {}
