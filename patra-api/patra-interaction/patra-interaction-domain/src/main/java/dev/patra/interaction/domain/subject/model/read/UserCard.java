package dev.patra.interaction.domain.subject.model.read;

import dev.patra.interaction.domain.subject.model.enums.AccountState;
import lombok.Builder;

/// identity 给的一个用户的摘要。已注销的是占位：`username`、`displayName`、`avatarUrl` 为 `null`。
///
/// `profileVisible` 为假时不得把这个人的 `avatarUrl`、`username` 放进给这位访客的响应。
///
/// @param userId 用户 ID
/// @param username 用户名，只用来拼主页链接
/// @param displayName 昵称
/// @param avatarUrl 头像地址，可空
/// @param avatarPalette 默认头像色号（0–11）
/// @param state 账号状态
/// @param emailVerified 邮箱是否已验证
/// @param profileVisible identity 替请求里的访客算出的「主页是否可见」
@Builder
public record UserCard(
    long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    AccountState state,
    boolean emailVerified,
    boolean profileVisible) {}
