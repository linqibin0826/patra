package dev.patra.identity.api.account.dto;

import lombok.Builder;

/// 一个用户的收件信息：notification 发提醒邮件、退订落地页显示脱敏邮箱时按 ID 现查（PAP-142）。
///
/// @param userId 用户 ID
/// @param email 账号邮箱；已注销为 `null`
/// @param emailVerified 邮箱是否已验证；已注销为 `false`
/// @param accountStatus 账号状态
/// @param username 用户名；已注销为 `null`
/// @param displayName 昵称，没设时已回退成用户名；已注销为 `null`
@Builder
public record UserRecipientResp(
    Long userId,
    String email,
    boolean emailVerified,
    AccountStatus accountStatus,
    String username,
    String displayName) {}
