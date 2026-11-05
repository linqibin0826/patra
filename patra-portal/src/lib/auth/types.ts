import type { CurrentUser } from "@/lib/identity/user-schema";

export type { CurrentUser } from "@/lib/identity/user-schema";

/// `GET /api/auth/me` 的响应体，没有登录时 `user` 为 null。
export interface CurrentUserPayload {
  user: CurrentUser | null;
}

/// 登录的入参。
export interface Credentials {
  email: string;
  password: string;
  /// 人机验证令牌，只有被要求时才带
  verificationToken?: string;
}

/// 要做人机验证的操作，门户渲染组件时传，后端校验时核对。
export type HumanVerificationAction = "login" | "register" | "password_reset";

/// 当前人机验证厂商。没有任何密钥。
export interface HumanVerificationConfig {
  provider: "turnstile" | "trustturn";
  siteKey: string;
}

/// 人机验证的三种失败：没带令牌、令牌没通过、验证不了。
export type VerificationFailure = {
  ok: false;
  reason: "verification-required" | "verification-failed" | "verification-unavailable";
};

/// 注册的入参。确认密码只在前端比对，不在这里。
export interface RegisterInput extends Credentials {
  username: string;
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
  /// 邮箱和 / 或用户名已被占用，两者可以同时为 true
  | { ok: false; reason: "taken"; email: boolean; username: boolean }
  | VerificationFailure
  | { ok: false; reason: "unavailable" };

/// `login` 的结果。不带文案，页面按 `reason` 和字段的 `code` 选文案。
export type LoginResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "bad-credentials" }
  | { ok: false; reason: "banned" }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | VerificationFailure
  | { ok: false; reason: "unavailable" };
