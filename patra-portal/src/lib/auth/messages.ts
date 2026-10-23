import type { FieldError, LoginResult } from "@/lib/auth/types";

/// 字段下方的错误文案（简报 §4.1、§4.2）。前端校验和后端的原因码共用。
export const FIELD_MESSAGES = {
  emailRequired: "请输入邮箱",
  emailInvalid: "邮箱格式不正确",
  passwordRequired: "请输入密码",
  passwordTooShort: "密码至少 8 位",
  passwordTooLong: "密码最长 64 位",
  passwordTooCommon: "这个密码太常见，容易被猜到，请换一个",
  passwordInvalid: "密码不符合要求",
  confirmRequired: "请再输入一次密码",
  confirmMismatch: "两次输入的密码不一致",
  /// 注册时邮箱已被占用：后面紧跟「去登录」链接
  emailTaken: "该邮箱已注册，",
  emailTakenLink: "去登录",
} as const;

/// 成功提示（简报 §4.4）
export const TOAST_MESSAGES = {
  loggedIn: "已登录",
  registered: "注册成功，已登录",
  loggedOut: "已退出登录",
  deletionCancelled: "已取消注销，欢迎回来",
} as const;

/// 注册失败、只能请用户稍后再试时的提示条
export const REGISTER_UNAVAILABLE_MESSAGE = "注册没有成功，请稍后再试";

/// 用户名的原因码 → 文案（简报 §4.1）。前端规则与后端原因码共用这张表；不认识的码和 null 是通用说法。
const USERNAME_MESSAGES: Record<string, string> = {
  REQUIRED: "请输入用户名",
  TOO_SHORT: "用户名至少 3 个字符",
  TOO_LONG: "用户名最多 30 个字符",
  INVALID_CHARACTER: "只能用小写字母、数字、_ 和 -",
  INVALID_FORMAT: "开头和结尾要是字母或数字，_ 和 - 不能连着用",
  DIGITS_ONLY: "用户名不能全是数字",
  RESERVED: "这个用户名不能使用，请换一个",
  ALREADY_TAKEN: "这个用户名已被使用",
  UNCHANGED: "新用户名和现在的一样",
};

/// 用户名输入框下的常驻提示（注册页、账号页改用户名共用）：空时末尾是 `/u/你的用户名`
export function usernameHint(value: string): string {
  return `3–30 位，小写字母、数字、_ 或 -，用作主页地址 /u/${value.trim() || "你的用户名"}`;
}

/// 用户名下方的错误文案
export function usernameMessage(code: string | null): string {
  return code !== null && Object.hasOwn(USERNAME_MESSAGES, code)
    ? (USERNAME_MESSAGES[code] as string)
    : "用户名不符合要求";
}

/// 后端返回的字段错误 → 字段下方的文案。邮箱只有「为空」单独一种说法，其余原因码、不认识的原因码
/// 和 null 都是「邮箱格式不正确」；密码不认识的原因码和 null 是「密码不符合要求」。
export function fieldErrorMessage(field: "email" | "password", code: string | null): string {
  if (field === "email") {
    return code === "REQUIRED" ? FIELD_MESSAGES.emailRequired : FIELD_MESSAGES.emailInvalid;
  }
  switch (code) {
    case "REQUIRED":
      return FIELD_MESSAGES.passwordRequired;
    case "TOO_SHORT":
      return FIELD_MESSAGES.passwordTooShort;
    case "TOO_LONG":
      return FIELD_MESSAGES.passwordTooLong;
    case "TOO_COMMON":
      return FIELD_MESSAGES.passwordTooCommon;
    default:
      return FIELD_MESSAGES.passwordInvalid;
  }
}

/// 有后端字段错误文案的字段
export type CredentialField = "email" | "username" | "password";

/// `invalid-fields` 里的字段错误 → 各字段下方的文案。同一字段只取第一条；`email`、`username`、
/// `password` 以外的字段丢掉，一条都对不上时返回空对象。
export function fieldErrorMessages(
  fieldErrors: FieldError[],
): Partial<Record<CredentialField, string>> {
  const messages: Partial<Record<CredentialField, string>> = {};
  for (const { field, code } of fieldErrors) {
    if (field === "username" && messages.username === undefined) {
      messages.username = usernameMessage(code);
    } else if ((field === "email" || field === "password") && messages[field] === undefined) {
      messages[field] = fieldErrorMessage(field, code);
    }
  }
  return messages;
}

/// 登录失败时用提示条显示的结果。`invalid-fields` 显示在字段下方，不在这里。
export type LoginAlertFailure = Exclude<LoginResult, { ok: true } | { reason: "invalid-fields" }>;

/// 登录失败时提示条的文案。限流时按等待秒数向上取整到分钟，至少 1 分钟；秒数为 null 时不说时长。
export function loginAlertMessage(failure: LoginAlertFailure): string {
  switch (failure.reason) {
    case "bad-credentials":
      return "邮箱或密码错误";
    case "banned":
      return "该账号已被封禁，无法登录";
    case "throttled":
      return failure.retryAfterSeconds === null
        ? "尝试次数过多，请稍后再试"
        : `尝试次数过多，请 ${Math.max(1, Math.ceil(failure.retryAfterSeconds / 60))} 分钟后再试`;
    case "unavailable":
      return "登录没有成功，请稍后再试";
  }
}

/// 重发验证邮件（简报 §3.4、§4.4）
export const RESEND_MESSAGES = {
  sent: "验证邮件已发送",
  verified: "邮箱已验证",
  failed: "没有发出去，请稍后再试",
  sending: "发送中…",
  ready: "可以重新发送了",
} as const;

/// 重发冷却中的文案：刚发出是「已发送 · N 秒后可重发」；限流时不到 60 秒按秒说，否则向上取整到分钟
export function resendWaitMessage(seconds: number, limited: boolean): string {
  if (!limited) {
    return `已发送 · ${seconds} 秒后可重发`;
  }
  return seconds < 60
    ? `发送太频繁，请 ${seconds} 秒后再试`
    : `发送太频繁，请 ${Math.ceil(seconds / 60)} 分钟后再试`;
}

/// 取消注销页（简报 §3.16）
export const DELETION_PENDING_MESSAGES = {
  title: "你的账号正在注销",
  warning: "删除后资料、头像和关注领域无法恢复，用户名不会释放。",
  cancel: "取消注销，恢复账号",
  cancelling: "恢复中…",
  failed: "没有取消成功，请稍后再试",
  logout: "退出登录",
  loggingOut: "正在退出…",
} as const;

/// 再认证对话框（简报 §3.14、§4.9）
export const REAUTH_MESSAGES = {
  title: "确认是你本人",
  description: "这项操作需要验证密码。验证后 10 分钟内不用再输。",
  forgot: "忘记密码？",
  cancel: "取消",
  confirm: "确认",
  verifying: "验证中…",
  required: "请输入密码",
  wrong: "密码不正确",
  failed: "没有验证成功，请稍后再试",
} as const;

/// 「尝试次数过多，请 N 分钟后再试」：秒数向上取整到分钟、至少 1 分钟；没给秒数时按 1 分钟
export function waitMinutes(retryAfterSeconds: number | null): number {
  return Math.max(1, Math.ceil((retryAfterSeconds ?? 60) / 60));
}

/// 账号页（简报 §3.10、§4.7）
export const ACCOUNT_MESSAGES = {
  usernameNote:
    "改名后，旧用户名保留 90 天：访问旧地址会跳到新地址，期间只有你能改回。两次修改至少间隔 30 天。",
  cooldown: "30 天内只能修改一次",
  saveFailed: "没有保存成功，请稍后再试",
} as const;

/// 改名成功的提示
export function usernameChangedToast(username: string): string {
  return `用户名已改为 @${username}`;
}
