import type { OAuthNotice } from "@/lib/auth/oauth-notice";
import { type Provider, providerName } from "@/lib/auth/oauth-providers";
import type { PendingRegistration } from "@/lib/auth/registration-types";
import type { FieldError, LoginResult } from "@/lib/auth/types";

/// 字段下方的错误文案（简报 §4.1、§4.2）。前端校验和后端的原因码共用。
export const FIELD_MESSAGES = {
  emailRequired: "请输入邮箱",
  emailInvalid: "邮箱格式不正确",
  emailUnchanged: "新邮箱和现在的一样",
  passwordRequired: "请输入密码",
  passwordTooShort: "密码至少 8 位",
  passwordTooLong: "密码最长 64 位",
  passwordTooCommon: "这个密码太常见，容易被猜到，请换一个",
  passwordInvalid: "密码不符合要求",
  confirmRequired: "请再输入一次密码",
  confirmNewRequired: "请再输入一次新密码",
  currentPasswordRequired: "请输入当前密码",
  currentPasswordWrong: "当前密码不正确",
  newPasswordUnchanged: "新密码不能和当前密码相同",
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
    case "verification-required":
      return VERIFICATION_MESSAGES.required;
    case "verification-failed":
      return VERIFICATION_MESSAGES.failed;
    case "verification-unavailable":
      return VERIFICATION_MESSAGES.unavailable;
    case "unavailable":
      return "登录没有成功，请稍后再试";
  }
}

/// 人机验证的提示。要和账号锁定的“尝试次数过多”完全不同，用户能分清是验证问题。
export const VERIFICATION_MESSAGES = {
  required: "请先完成下面的人机验证，再重新提交",
  failed: "人机验证没有通过，请重新验证。一直不通过的话，刷新页面再试",
  unavailable: "人机验证暂时不可用，请稍后再试",
  loadFailed: "人机验证加载失败。请刷新页面，或换一个网络再试",
} as const;

/// 人机验证失败的提示，别的失败返回 null。
export function verificationMessage(reason: string): string | null {
  switch (reason) {
    case "verification-required":
      return VERIFICATION_MESSAGES.required;
    case "verification-failed":
      return VERIFICATION_MESSAGES.failed;
    case "verification-unavailable":
      return VERIFICATION_MESSAGES.unavailable;
    default:
      return null;
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

/// 账号页 · 邮箱卡片（简报 §3.10 卡片 2、§4.7）
export const EMAIL_MESSAGES = {
  verified: "已验证",
  unverified: "未验证",
  unverifiedNote: "验证邮件已发到这个地址，24 小时内有效。",
  changeNote:
    "我们会向新邮箱发一封确认邮件，1 小时内点链接后生效；旧邮箱会收到通知。每天最多更改 3 次。",
  taken: "这个邮箱已被其他账号使用",
  pendingExists: "已有一个待确认的更改，请先取消或等它过期",
  throttled: "今天更改邮箱的次数已用完，请明天再试",
  cancelled: "已取消更改邮箱",
} as const;

/// 改邮箱 `newEmail` 的原因码 → 文案：为空、与当前相同各一句，其余（含不认识的）是「邮箱格式不正确」
export function newEmailMessage(code: string | null): string {
  if (code === "REQUIRED") return FIELD_MESSAGES.emailRequired;
  if (code === "UNCHANGED") return FIELD_MESSAGES.emailUnchanged;
  return FIELD_MESSAGES.emailInvalid;
}

/// 注销卡片与对话框（简报 §3.10 卡片 3、§3.15、§4.7）
export const DELETE_ACCOUNT_MESSAGES = {
  cardNotes: [
    "提交后进入 15 天冷静期：所有设备立即退出登录，主页对别人消失。",
    "冷静期内登录即可取消。到期后资料、头像、关注领域被删除，邮箱可以重新注册，用户名永久保留、不能再被使用。",
    "被封禁的账号不能自助注销。",
  ],
  open: "注销账号…",
  stepOneTitle: "注销账号",
  stepTwoTitle: "确认注销",
  doneTitle: "注销申请已提交",
  mismatch: "和你的用户名不一致",
  failed: "没有提交成功，请稍后再试",
  submit: "注销账号",
  submitting: "提交中…",
} as const;

/// 安全页 · 改密码（简报 §3.11 卡片 1、§4.8）
export const CHANGE_PASSWORD_MESSAGES = {
  note: "修改后，除本机外的所有设备都会退出登录，你会收到一封通知邮件。",
  forgot: "忘记当前密码？",
  submit: "修改密码",
  submitting: "修改中…",
  changed: "密码已修改，其他设备已退出登录",
  failed: "没有保存成功，请稍后再试",
} as const;

/// 新密码字段的原因码 → 文案：新旧相同单独一句，其余同注册页
export function newPasswordMessage(code: string | null): string {
  return code === "UNCHANGED"
    ? FIELD_MESSAGES.newPasswordUnchanged
    : fieldErrorMessage("password", code);
}

/// 安全页 · 登录设备（简报 §3.11 卡片 2、§4.8）
export const SESSIONS_MESSAGES = {
  description: "这里列出所有正在登录的设备。不认识的设备请退出它，并修改密码。",
  loading: "正在加载登录设备",
  failed: "设备列表加载失败",
  retry: "重试",
  revokeAll: "退出其他所有设备",
  revokingAll: "退出中…",
  noOthersTitle: "没有其他设备登录着",
  noOthers: "没有其他设备登录着。",
  here: "本机",
  online: "当前在线",
  unknownDevice: "未知设备",
  privateNetwork: "内网",
  unknownRegion: "属地未知",
  logout: "退出登录",
  revoke: "退出",
  revoking: "退出中…",
  revoked: "已退出该设备",
  revokeFailed: "没有退出成功，请稍后再试",
} as const;

/// 「已退出其他 N 台设备」
export function revokedOthersToast(count: number): string {
  return `已退出其他 ${count} 台设备`;
}

/// 登录页的成功提示（简报 §3.2、§4.2）
export const LOGIN_NOTICES = {
  passwordReset: "密码已重设，请用新密码登录",
} as const;

/// 忘记密码页（简报 §3.17、§4.10）
export const FORGOT_MESSAGES = {
  title: "找回密码",
  description: "输入注册时用的邮箱，我们会发一封重设密码的邮件。",
  submit: "发送重设邮件",
  submitting: "发送中…",
  remembered: "想起来了？",
  sentTitle: "查看你的邮箱",
  spam: "没收到？先看看垃圾邮件。",
  resend: "重新发送",
  changeEmail: "换一个邮箱",
  backToLogin: "返回登录",
  failed: "没有发出去，请稍后再试",
} as const;

/// 验证邮箱落地页（简报 §3.18 a，画板 Landing）
export const VERIFY_LANDING_MESSAGES = {
  title: "验证你的邮箱",
  description: "点下面的按钮完成验证。",
  action: "验证邮箱",
  pending: "验证中…",
  verifiedTitle: "邮箱已验证",
  invalidTitle: "链接无效或已过期",
  invalidDescription: "验证链接 24 小时内有效，重新发送会让旧链接失效。",
  resend: "重新发送验证邮件",
  loginToResend: "登录后重新发送",
  continue: "继续浏览",
  login: "登录",
} as const;

/// 重设密码落地页（简报 §3.18 b，画板 Landing）
export const RESET_LANDING_MESSAGES = {
  title: "设置新密码",
  description: "提交后，所有设备都会退出登录。",
  action: "设置新密码",
  pending: "提交中…",
  invalidTitle: "链接无效或已过期",
  invalidDescription: "重设链接 1 小时内有效，而且只能用一次。",
  again: "重新申请",
} as const;

/// 确认新邮箱与撤销改邮箱落地页（简报 §3.18 c / d，画板 Landing）
export const EMAIL_CHANGE_LANDING_MESSAGES = {
  confirmTitle: "确认新邮箱",
  confirmDescription: "确认后，这个邮箱会成为你登录 Patra 的邮箱。",
  confirmAction: "确认更改",
  confirmPending: "确认中…",
  confirmedTitle: "邮箱已更改",
  confirmTakenTitle: "这个邮箱已被注册",
  confirmTakenDescription:
    "在你确认之前，这个地址被另一个账号注册了，无法使用。你的登录邮箱没有变。",
  confirmInvalidDescription: "确认链接 1 小时内有效。可以回到设置重新发起更改。",
  revertTitle: "撤销邮箱更改",
  revertDescription: "撤销后：",
  revertConsequences: [
    "登录邮箱改回本地址",
    "所有设备退出登录，原密码作废",
    "我们会给本地址发一封重设密码的邮件",
  ],
  revertAction: "撤销更改并锁定账号",
  revertPending: "撤销中…",
  revertedTitle: "邮箱已改回",
  revertTakenTitle: "无法改回原邮箱",
  revertTakenDescription: "原邮箱已被另一个账号注册。",
  revertInvalidDescription: "撤销链接 7 天内有效。",
  invalidTitle: "链接无效或已过期",
  continue: "继续浏览",
  toAccount: "去账号设置",
  login: "登录",
} as const;

/// 第三方授权结果的提示条
export interface OAuthAlert {
  tone: "danger" | "info";
  text: string;
}

/// 登录页、注册页收到的结果（简报 v0.19，画板 Auth）。`linked` 这类绑定结果到不了这里，到了按服务错误说
export function loginNoticeAlert({ kind, provider }: OAuthNotice): OAuthAlert {
  const name = providerName(provider);
  switch (kind) {
    case "cancelled":
      return { tone: "info", text: `你取消了 ${name} 授权。可以再试一次，或换一种方式。` };
    case "rejected":
      return {
        tone: "danger",
        text: "这次授权没有成功，可能是等得太久或换了浏览器。请重新点一次，或换一种方式。",
      };
    case "unreachable":
      return { tone: "danger", text: `暂时连不上 ${name}，请稍后再试，或换一种方式。` };
    case "banned":
      return { tone: "danger", text: "该账号已被封禁，无法登录" };
    default:
      return { tone: "danger", text: "登录没有成功，请稍后再试" };
  }
}

/// 设置页收到的绑定结果（画板 Security）。绑定成功是 toast，不用提示条
export function linkNoticeAlert({ kind, provider }: OAuthNotice): OAuthAlert | null {
  const name = providerName(provider);
  switch (kind) {
    case "linked":
      return null;
    case "cancelled":
      return { tone: "info", text: `你取消了 ${name} 授权，没有绑定。` };
    case "rejected":
      return {
        tone: "danger",
        text: "这次授权没有成功，可能是等得太久或换了账号，没有绑定任何账号。请重新点「绑定」。",
      };
    case "unreachable":
      return { tone: "danger", text: `暂时连不上 ${name}，没有绑定。请稍后再试。` };
    case "taken":
      return {
        tone: "danger",
        text: `你刚才授权的 ${name} 账号已经绑定了另一个 Patra 账号，不能再绑到这里。如果那个账号也是你的，先登录它解绑，再回来绑定。`,
      };
    case "already-linked":
      return {
        tone: "danger",
        text: `你已经绑定了一个 ${name} 账号，每家只能绑一个。要换账号，先解绑现在这个。`,
      };
    default:
      return { tone: "danger", text: "绑定没有成功，请稍后再试" };
  }
}

/// 绑定成功的提示
export function linkedToast(provider: Provider): string {
  return `已绑定 ${providerName(provider)}，以后可以用它登录。通知邮件已发到你的邮箱。`;
}

/// 第三方首次注册（画板 Complete）
export const REGISTRATION_MESSAGES = {
  fillTitle: "还差一步",
  fillDescription: (name: string) =>
    `第一次用 ${name} 账号登录 Patra，填好用户名和邮箱就能建好账号。`,
  confirmed: (name: string) => `已确认你的 ${name} 账号`,
  usernamePrefilled: (name: string) => `已按你的 ${name} 用户名预填，可以改。`,
  /// 邮箱下方的提示：说明预填来自哪里，或为什么要自己填
  emailHint: ({
    provider,
    suggestedEmail,
  }: Pick<PendingRegistration, "provider" | "suggestedEmail">) => {
    const name = providerName(provider);
    if (provider === "orcid") {
      return `${name} 不提供邮箱，请填一个常用的。我们会发验证码到这个邮箱。`;
    }
    if (suggestedEmail === null) {
      return provider === "github"
        ? `你的 ${name} 没有公开邮箱，请填一个常用的。我们会发验证码到这个邮箱。`
        : "请填一个常用的邮箱。我们会发验证码到这个邮箱。";
    }
    return provider === "github"
      ? `我们会发验证码到这个邮箱。已按你的 ${name} 公开邮箱预填，可以改。`
      : `我们会发验证码到这个邮箱。已按你的 ${name} 账号预填，可以改。`;
  },
  send: "发送验证码",
  sending: "发送中…",
  sendFailed: "验证码没有发出去，请稍后再试",
  usernameRaced: "这个用户名刚被别人用了，请换一个",
  clashTitle: "这个邮箱已经注册过 Patra。",
  clashNoChange: "我们没有建新账号，也没有改动原账号。",
  clashHow: (name: string) =>
    `如果是你的账号：可以验证这个邮箱直接绑定，也可以先用原来的方式登录，再到「设置 › 安全」绑定 ${name}。以后就能用 ${name} 账号登录它了。`,
  toLogin: "去登录",
  changeEmail: "换一个邮箱",
  codeTitle: "输入验证码",
  codeRequired: "请输入验证码",
  codeFormat: "验证码是 6 位数字",
  codeInvalid: "验证码不正确",
  codeExpired: "这个验证码已失效，请重新发送一个",
  verifyFailed: "没有验证成功，请稍后再试",
  resendFailed: "没有发出去，请稍后再试",
  verifying: "验证中…",
  complete: "完成注册",
  spam: "没收到？先看看垃圾邮件。",
  resend: "重新发送",
  backToFill: "改用户名或邮箱（要重新发验证码）",
  expiredTitle: "这次注册已过期",
  /// 进页面就过期时不知道是哪家
  expiredDescription: (name: string | null) =>
    name
      ? `等得太久了，账号没有建。请重新用 ${name} 账号登录，再填一次。`
      : "等得太久了，账号没有建。请回到登录页，重新用第三方账号登录，再填一次。",
  backToLogin: "回到登录页",
  /// 两个标签页并发时，这个第三方账号已经注册好了
  takenTitle: "这个账号已经建好了",
  takenDescription: (name: string) =>
    `你的 ${name} 账号刚刚完成了注册，可能是在另一个标签页。重新用它登录就行。`,
} as const;

/// 设置密码对话框（决策 E，画板 Security）
export const SET_PASSWORD_MESSAGES = {
  title: "设置密码",
  reasonTitle: "先设置一个密码",
  why: "有了密码，就能用邮箱和密码登录，也能完成绑定、解绑、注销这类要验证身份的操作。",
  reasonWhy:
    "这项操作要先验证密码，但你的账号还没有密码。用第三方账号登录不算验证：只要对方网站还登录着，别人坐到你的电脑前也能通过。",
  last: "用新密码或第三方账号重新登录。",
  reasonLast: "用新密码重新登录，再回来完成刚才的操作。",
  send: "发送设置邮件",
  sending: "发送中…",
  cancel: "取消",
  failed: "没有发出去，请稍后再试",
  sentTitle: "查看你的邮箱",
  sentDescription: (email: string) =>
    `设置密码的邮件已发到 ${email}，链接 1 小时内有效。设置好之后所有设备会退出登录，用新密码重新登录就能继续。`,
  spam: "没收到？先看看垃圾邮件。",
  resend: "重新发送",
  done: "好的",
} as const;

/// 安全页 · 登录方式（画板 Security）
export const LOGIN_METHOD_MESSAGES = {
  description: "这些方式都能登录这个账号。绑定和解绑要先验证密码，并会发一封通知邮件。",
  descriptionNoPassword:
    "这些方式都能登录这个账号。绑定和解绑要先验证密码，你还没有密码，需要先设置一个。",
  loading: "正在加载登录方式",
  loadFailed: "登录方式加载失败",
  retry: "重试",
  passwordRow: "邮箱和密码",
  passwordSet: "已设置",
  passwordUnset: "还没有设置",
  setPassword: "设置密码",
  unlinked: "未绑定",
  unlinkedOrcid: "未绑定 · 科研人员身份标识",
  bind: "绑定",
  unlink: "解绑",
  unlinking: "解绑中…",
  cancel: "取消",
  lastOnly: "这是你现在唯一的登录方式，解绑后就登录不了了。先",
  lastOnlyAfter: "，之后可以解绑。",
  tailnetNote: "要绑定新账号，请到 patra.linqibin.dev 操作。",
  unlinkConfirm: (name: string, label: string, email: string) =>
    `解绑后，不能再用 ${name} 账号 ${label} 登录 Patra。通知邮件会发到 ${email}。`,
  lastMethodRejected: "这是你现在唯一的登录方式，不能解绑。先设置密码，之后可以解绑。",
  unlinkFailed: "解绑没有成功，请稍后再试",
} as const;

/// 解绑成功的提示
export function unlinkedToast(provider: Provider): string {
  return `已解绑 ${providerName(provider)}。通知邮件已发到你的邮箱。`;
}

/// 第三方首次登录邮箱撞车时就地绑定。发码接口对未注册、未验证、封禁的邮箱也回 202，只是不发信：这里不能说「已发送」
export const LINK_MESSAGES = {
  entry: "验证这个邮箱，并绑定到已有账号",
  entryBusy: "处理中…",
  title: "验证这个邮箱",
  description: "如果这个邮箱可以绑定，你会收到验证码；收不到就先用原方式登录，到设置页绑定。",
  submit: "完成绑定",
  back: "返回修改邮箱",
  /// 重发冷却：不说「已发送」
  wait: (seconds: number) => `${seconds} 秒后可重发`,
  signedIn: (name: string) => `已绑定 ${name}，已登录`,
  expiredTitle: "这次绑定已失效",
  expiredDescription: (name: string) =>
    `可能是等得太久，或已经在另一个标签页完成。请重新用 ${name} 账号登录。`,
  takenTitle: "这个账号已经绑定了",
  takenDescription: (name: string) =>
    `你的 ${name} 账号已经绑定了 Patra 账号，可能是在另一个标签页。重新用它登录就行。`,
  alreadyLinkedTitle: "没有绑定",
  alreadyLinkedDescription: (name: string) =>
    `这个 Patra 账号已经绑定了另一个 ${name} 账号，每家只能绑一个。要换账号，先用原方式登录，到「设置 › 安全」解绑现在这个。`,
} as const;
