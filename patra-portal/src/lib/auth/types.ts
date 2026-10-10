/// 当前登录的用户。`userId` 是字符串：后端的雪花 ID 超出 JS 的安全整数范围。
export interface CurrentUser {
  userId: string;
  email: string;
}

/// `GET /api/auth/me` 的响应体，没有登录时 `user` 为 null。
export interface CurrentUserPayload {
  user: CurrentUser | null;
}
