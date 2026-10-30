package dev.patra.notification.adapter.preference.rest.request;

/// 改一类提醒的邮件档位。
///
/// @param mode `OFF`、`INSTANT` 或 `DIGEST`；缺失或不认识时 422
public record ChangeEmailModeRequest(String mode) {}
