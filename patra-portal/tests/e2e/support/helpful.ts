import { type Browser, expect, type Locator, type Page } from "@playwright/test";
import { login, ROUND_TRIP } from "@tests/e2e/support/account";

/// 一个已验证邮箱的账号（凭据只在本机环境变量里）
export interface Verified {
  email: string;
  password: string;
}

/// 有帮助按钮（未点 0 时读屏名是「有帮助」，有数时是「有帮助，N 人觉得有帮助」）
export function helpfulButton(scope: Locator): Locator {
  return scope.getByRole("button", { name: /^有帮助/ });
}

/// 新开一个浏览器上下文并登录，停在 `next`
export async function signedIn(
  browser: Browser,
  account: Verified,
  viewport: { width: number; height: number },
  next = "/",
): Promise<Page> {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  await login(page, account, next);
  await expect(page).not.toHaveURL(/\/login/, ROUND_TRIP);
  return page;
}

/// 当前登录用户的用户名（门户自己的 `/api/auth/me`）
export async function usernameOf(page: Page): Promise<string> {
  const res = await page.request.get("/api/auth/me");
  const body = (await res.json()) as { user: { username: string } | null };
  if (!body.user) {
    throw new Error("没有登录");
  }
  return body.user.username;
}

/// `items` 里每个文字在 `rows` 全部文字里第一次出现的位置（按页面顺序比较先后）
export async function orderOf(rows: Locator, items: string[]): Promise<number[]> {
  const texts = await rows.allTextContents();
  return items.map((item) => texts.findIndex((text) => text.includes(item)));
}

/// 点一下有帮助并等请求结束：先等已点状态翻过来（乐观更新，证明点击生效），再等请求结束（只看数字分不出成功还是
/// 随后回滚），结束后不该有失败提示
export async function toggleHelpful(scope: Locator): Promise<void> {
  const button = helpfulButton(scope);
  const before = await button.getAttribute("aria-pressed");
  await button.click();
  await expect(button).toHaveAttribute("aria-pressed", before === "true" ? "false" : "true");
  await expect(button).not.toHaveAttribute("aria-busy", "true", ROUND_TRIP);
  await expect(button).toHaveAttribute("aria-pressed", before === "true" ? "false" : "true");
  await expect(
    scope.getByRole("status").filter({ hasText: /请稍后再试|不能点|明天再来|已被删除/ }),
  ).toHaveCount(0);
}
