import type { Provider } from "@/lib/auth/oauth-providers";
import type { CurrentUser, FieldError } from "@/lib/auth/types";

/// 服务端和浏览器都用：补填页的结果类型，不带文案

/// 已经发过的验证码，刷新页面时靠 `purpose` 回到对应的步骤
export type PendingCode = {
  email: string;
  codeExpiresAt: string;
  nextResendAt: string;
} & (
  | {
      purpose: "REGISTER";
      /// 发码时提交的用户名：重发要连它一起交
      username: string;
    }
  | { purpose: "LINK"; username: null }
);

/// 补填页要显示的内容
export interface PendingRegistration {
  provider: Provider;
  accountLabel: string;
  suggestedUsername: string | null;
  suggestedEmail: string | null;
  expiresAt: string;
  pendingCode: PendingCode | null;
}

/// 两步都可能遇到的失败
type RegistrationFailure =
  /// `ticket` 缺失、过期或已用完
  | { ok: false; reason: "expired" }
  /// 邮箱和 / 或用户名已被占用
  | { ok: false; reason: "taken"; email: boolean; username: boolean }
  | { ok: false; reason: "unavailable" };

/// 发验证码的结果
export type SendCodeResult =
  | { ok: true; email: string; codeExpiresAt: string; nextResendAt: string }
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "throttled"; retryAfterSeconds: number | null }
  | RegistrationFailure;

/// 两种验证码步都可能遇到的结果
type CodeResult =
  | { ok: true; user: CurrentUser }
  | { ok: false; reason: "code-invalid" | "code-expired" | "provider-taken" }
  | { ok: false; reason: "expired" | "unavailable" };

/// 提交验证码、建账号的结果
export type CompleteResult =
  | CodeResult
  /// 邮箱和 / 或用户名被别人抢先
  | { ok: false; reason: "taken"; email: boolean; username: boolean };

/// 就地绑定 · 发验证码的结果。不会有 `taken`：邮箱未注册时响应也一样，只是不发信
export type SendLinkCodeResult = Exclude<SendCodeResult, { reason: "taken" }>;

/// 就地绑定 · 提交验证码的结果
export type LinkResult =
  | CodeResult
  /// 目标账号已经绑了这一家的另一个账号
  | { ok: false; reason: "already-linked" };
