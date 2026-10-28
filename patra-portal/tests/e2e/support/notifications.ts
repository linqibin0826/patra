import { type Browser, expect, type Locator, type Page } from "@playwright/test";
import { login, ROUND_TRIP } from "@tests/e2e/support/account";

/// 一个账号的邮箱与密码（来自本机环境变量，不进仓库、不打印）
export interface Credentials {
  email: string;
  password: string;
}

/// 一个已登录的浏览器会话：各账号各用一个 context，同时在线
export interface Session {
  page: Page;
  username: string;
  close: () => Promise<void>;
}

/// 新开一个 context 登录，读出当前用户名
export async function signIn(
  browser: Browser,
  viewport: { width: number; height: number },
  account: Credentials,
): Promise<Session> {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  await login(page, account);
  await expect(page).toHaveURL("/", { timeout: 60_000 });
  return { page, username: await currentUsername(page), close: () => context.close() };
}

/// 当前登录用户的用户名（经门户的 `/api/auth/me`）
export async function currentUsername(page: Page): Promise<string> {
  const res = await page.request.get("/api/auth/me");
  const body = (await res.json()) as { user: { username: string } | null };
  if (!body.user) {
    throw new Error("没有登录");
  }
  return body.user.username;
}

/// 当前视口下看得见的那个铃铛（桌面在账号区、窄屏在顶栏，两个只显示一个）
export function bell(page: Page): Locator {
  return page.locator('header a[href="/notifications"]:visible');
}

/// 模拟切回这个标签页：TanStack 的聚焦管理只听 `visibilitychange`
export async function switchBack(page: Page): Promise<void> {
  await page.evaluate(() => window.dispatchEvent(new Event("visibilitychange")));
}

/// 等下一次未读数请求回来：状态码与请求带的 `If-None-Match`
export async function nextUnread(
  page: Page,
  trigger: () => Promise<unknown>,
): Promise<{ status: number; ifNoneMatch: string | null }> {
  const response = page.waitForResponse((res) =>
    res.url().includes("/api/me/notifications/unread-count"),
  );
  await trigger();
  const res = await response;
  return { status: res.status(), ifNoneMatch: await res.request().headerValue("if-none-match") };
}

/// 打开提醒列表的一个标签
export async function openInbox(page: Page, tab: "replies" | "mentions"): Promise<Locator> {
  await page.goto(tab === "replies" ? "/notifications" : "/notifications/mentions");
  const list = page.locator("main");
  await expect(list.getByRole("heading", { level: 1, name: "提醒" })).toBeVisible(ROUND_TRIP);
  await expect(list.getByTestId("notification-skeleton-row")).toHaveCount(0, ROUND_TRIP);
  return list;
}

/// 列表里摘录含 `text` 的提醒行
export function rowWith(list: Locator, text: string): Locator {
  return list.getByRole("listitem").filter({ hasText: text });
}

/// 点「全部标为已读」（没有未读时按钮禁用，直接跳过），等铃铛不挂徽标
export async function markAllRead(page: Page): Promise<void> {
  const list = await openInbox(page, "replies");
  const button = list.getByRole("button", { name: "全部标为已读" });
  if (await button.isEnabled()) {
    await button.click();
  }
  await expect(bell(page)).toHaveAccessibleName("提醒", ROUND_TRIP);
}
