import type { CurrentUser } from "@/lib/auth/types";

/// 网关 `/auth/me`（以及登录、注册响应里的 `user`）的一份完整响应体，比 `CurrentUser` 多一个门户不读的
/// `accountType`。
export const ME_RESPONSE = {
  userId: "319041872872550658",
  email: "chen.yu@example.com",
  emailVerified: true,
  username: "chen-yu",
  displayName: "陈宇",
  avatarUrl: null,
  avatarPalette: 1,
  status: "ACTIVE",
  deletionDueAt: null,
  nextUsernameChangeAt: null,
  accountType: "user",
} as const;

/// 门户解析出的当前用户：`ME_RESPONSE` 去掉 `accountType`。
export const USER: CurrentUser = {
  userId: ME_RESPONSE.userId,
  email: ME_RESPONSE.email,
  emailVerified: ME_RESPONSE.emailVerified,
  username: ME_RESPONSE.username,
  displayName: ME_RESPONSE.displayName,
  avatarUrl: ME_RESPONSE.avatarUrl,
  avatarPalette: ME_RESPONSE.avatarPalette,
  status: ME_RESPONSE.status,
  deletionDueAt: ME_RESPONSE.deletionDueAt,
  nextUsernameChangeAt: ME_RESPONSE.nextUsernameChangeAt,
};
