package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import lombok.Builder;

/// 按状态与访客处理好的用户摘要（批量用户摘要接口的一项，PAP-108）。已注销的用户是占位：身份字段全空。
///
/// @param userId 用户 ID
/// @param username 用户名，只用来拼主页链接；已注销为 `null`
/// @param displayName 昵称，没设时已回退成用户名；已注销为 `null`
/// @param avatar 头像；没上传、已封禁、已注销为 `null`
/// @param avatarPalette 默认头像色号（0–11）
/// @param status 账号状态
/// @param emailVerified 邮箱是否已验证；已注销为 `false`
/// @param profileVisible 这位访客能不能看到此人的主页
@Builder
public record UserSummaryView(
    long userId,
    String username,
    String displayName,
    Avatar avatar,
    int avatarPalette,
    UserStatus status,
    boolean emailVerified,
    boolean profileVisible) {}
