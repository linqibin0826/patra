package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import lombok.Builder;

/// 一个用户对外的收件信息：notification 发提醒邮件、退订落地页显示脱敏邮箱时按 ID 现查（PAP-142）。
///
/// @param userId 用户 ID
/// @param email 邮箱；已注销为 `null`
/// @param emailVerified 邮箱是否已验证；已注销为 `false`
/// @param status 状态
/// @param username 用户名；已注销为 `null`
/// @param displayName 昵称，没设时已回退成用户名；已注销为 `null`
@Builder
public record UserRecipientView(
    long userId,
    String email,
    boolean emailVerified,
    UserStatus status,
    String username,
    String displayName) {}
