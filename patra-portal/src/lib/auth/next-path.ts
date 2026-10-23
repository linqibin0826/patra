/// 解析 `next` 用的占位地址：只用来判断解析后是否还在本站，不会出现在结果里
const PLACEHOLDER_ORIGIN = "http://patra.invalid";

/// 登录页、注册页本身不能作为 `next`（跳转规则 5）
const AUTH_PAGES = new Set(["/login", "/register"]);

/// 校验 `next` 参数：合法时返回解析后的「路径 + 查询串」，`#` 及之后的部分丢掉；不合法时一律返回 `/`。
/// 1. 必须是非空字符串。
/// 2. 第一个字符是 `/`，第二个字符不能是 `/` 或 `\`（跳转规则 4）。
/// 3. 按 URL 解析后仍是本站。解析会去掉制表符和换行、把 `\` 当成 `/`，`/\t/evil.com` 在这一步变成
///    `//evil.com`，被挡住。
/// 4. 解析后的路径去掉末尾的 `/`，不能是登录页或注册页。`/papers/../login` 解析后就是 `/login`。
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
  if (url.origin !== PLACEHOLDER_ORIGIN || AUTH_PAGES.has(url.pathname.replace(/\/+$/, ""))) {
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
