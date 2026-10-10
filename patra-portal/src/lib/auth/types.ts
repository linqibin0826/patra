/// 当前登录的用户。`userId` 是字符串：后端的雪花 ID 超出 JS 的安全整数范围。
export interface CurrentUser {
  userId: string;
  email: string;
}

/// `GET /api/auth/me` 的响应体，没有登录时 `user` 为 null。
export interface CurrentUserPayload {
  user: CurrentUser | null;
}

/// 注册、登录的入参。
export interface Credentials {
  email: string;
  password: string;
}

/// 422 里的一个字段错误。
export interface FieldError {
  field: string;
  /// 原因码：REQUIRED、TOO_LONG、INVALID_FORMAT、TOO_SHORT、TOO_COMMON、INVALID_CHARACTER；后端没给时为 null
  code: string | null;
}

/// `register` 的结果。不带文案，页面按 `reason` 和字段的 `code` 选文案。
export type RegisterResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "email-taken" }
  | { ok: false; reason: "unavailable" };

/// `login` 的结果。不带文案，页面按 `reason` 和字段的 `code` 选文案。
export type LoginResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "bad-credentials" }
  | { ok: false; reason: "banned" }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | { ok: false; reason: "unavailable" };
