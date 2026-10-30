/// 解析 `next` 用的占位地址：只用来判断解析后是否还在本站，不会出现在结果里
const PLACEHOLDER_ORIGIN = "http://patra.invalid";

/// 专注布局的页面（简报 §0）：登录、注册、找回密码、四个邮件落地页、取消注销页，以及 v0.16 的退订确认页与
/// 一键退订地址。它们不能作为 `next`（跳转规则 5：登录后回到登录页没有意义；落地页的令牌在片段里，跳回去也已经
/// 没了），冷静期跳转也不拦它们（spec §9.1）：冷静期账号的退订链接照常有效，不拦一键退订地址也免得令牌被抄进
/// 取消注销页的 `next`（PAP-148 spec 第 2.3 节）。
export const FOCUS_PAGES: ReadonlySet<string> = new Set([
  "/login",
  "/register",
  "/forgot-password",
  "/verify-email",
  "/reset-password",
  "/confirm-email-change",
  "/revert-email-change",
  "/account/deletion-pending",
  "/unsubscribe",
  "/unsubscribe/one-click",
]);

/// 路径（不含查询串）是不是专注布局的页面，末尾的 `/` 不算
export function isFocusPage(pathname: string): boolean {
  return FOCUS_PAGES.has(pathname.replace(/\/+$/, ""));
}

/// 校验 `next` 参数：合法时返回解析后的「路径 + 查询串」，`#` 及之后的部分丢掉；不合法时一律返回 `/`。
/// 1. 必须是非空字符串。
/// 2. 第一个字符是 `/`，第二个字符不能是 `/` 或 `\`（跳转规则 4）。
/// 3. 按 URL 解析后仍是本站。解析会去掉制表符和换行、把 `\` 当成 `/`，`/\t/evil.com` 在这一步变成
///    `//evil.com`，被挡住。
/// 4. 解析后的路径不能以 `//` 开头：点路径段折叠之后（`/..//evil.com`、`/%2e%2e//evil.com`）会变成协议相对地址，
///    原样拿去跳转就到了站外。
/// 5. 解析后的路径去掉末尾的 `/`，不能是专注布局的页面（`FOCUS_PAGES`）。`/papers/../login` 解析后就是 `/login`。
export function safeNextPath(raw: string | null | undefined): string {
  if (raw?.[0] !== "/" || raw[1] === "/" || raw[1] === "\\") {
    return "/";
  }
  let url: URL;
  try {
    url = new URL(raw, PLACEHOLDER_ORIGIN);
  } catch {
    return "/";
  }
  if (
    url.origin !== PLACEHOLDER_ORIGIN ||
    url.pathname.startsWith("//") ||
    isFocusPage(url.pathname)
  ) {
    return "/";
  }
  return `${url.pathname}${url.search}`;
}

/// 登录页、注册页的链接：`next` 先经过 `safeNextPath`，结果是 `/` 时不带参数。
export function authPageHref(page: "/login" | "/register", next: string): string {
  const target = safeNextPath(next);
  return target === "/" ? page : `${page}?${new URLSearchParams({ next: target })}`;
}

/// 取消注销页的地址（简报 §2.2 规则 2）：冷静期账号登录后去这里，确认取消后再回 `next`。`next` 先经过
/// `safeNextPath`，结果是 `/` 时不带参数。
export function deletionPendingHref(next: string): string {
  const target = safeNextPath(next);
  const page = "/account/deletion-pending";
  return target === "/" ? page : `${page}?${new URLSearchParams({ next: target })}`;
}

/// 忘记密码页的地址：带上 `next`，找回页的「返回」「返回登录」回到带同一个 `next` 的登录页。`next` 先经过
/// `safeNextPath`，结果是 `/` 时不带参数。
export function forgotPasswordHref(next: string): string {
  const target = safeNextPath(next);
  return target === "/"
    ? "/forgot-password"
    : `/forgot-password?${new URLSearchParams({ next: target })}`;
}
