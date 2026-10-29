package dev.patra.identity.api.account.dto;

import lombok.Builder;

/// 一个用户的摘要。已注销的是占位：`username`、`displayName`、`avatarUrl` 为 `null`，「已注销用户」文案由门户出。
///
/// `profileVisible` 为假时，调用方不能把这个人的 `avatarUrl`、`username` 放进给这位访客的响应：头像地址里的版本号
/// 是「只出现在有权看到的响应里」的凭证。
///
/// @param userId 用户 ID
/// @param username 用户名，只用来拼主页链接 `/u/{username}`；已注销为 `null`
/// @param displayName 昵称，没设时已回退成用户名；已注销为 `null`
/// @param avatarUrl `/users/{userId}/avatar?v={hash}`；没上传、已封禁、已注销为 `null`
/// @param avatarPalette 默认头像色号（0–11）
/// @param accountStatus 账号状态
/// @param emailVerified 邮箱是否已验证；已注销为 `false`
/// @param profileVisible identity 按主页隐私规则替请求里的访客算出的「主页是否可见」
@Builder
public record UserSummaryResp(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    AccountStatus accountStatus,
    boolean emailVerified,
    boolean profileVisible) {}
