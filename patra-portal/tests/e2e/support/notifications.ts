import { type Browser, expect, type Locator, type Page, type Request } from "@playwright/test";
import { login, ROUND_TRIP, waitForTakeover } from "@tests/e2e/support/account";

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

const INBOX_PATH = {
  replies: "/notifications",
  mentions: "/notifications/mentions",
  helpful: "/notifications/helpful",
} as const;

/// 打开提醒列表的一个标签
export async function openInbox(page: Page, tab: keyof typeof INBOX_PATH): Promise<Locator> {
  await page.goto(INBOX_PATH[tab]);
  const list = page.locator("main");
  await expect(list.getByRole("heading", { level: 1, name: "提醒" })).toBeVisible(ROUND_TRIP);
  await expect(list.getByTestId("notification-skeleton-row")).toHaveCount(0, ROUND_TRIP);
  return list;
}

/// 列表里摘录含 `text` 的提醒行
export function rowWith(list: Locator, text: string): Locator {
  return list.getByRole("listitem").filter({ hasText: text });
}

/// 门户的推送地址（浏览器的 `EventSource` 连它）
export const STREAM_PATH = "/api/me/notifications/stream";

/// 等这个页面下一次连推送
export function nextStream(page: Page): Promise<Request> {
  return page.waitForRequest((req) => req.url().endsWith(STREAM_PATH), { timeout: 60_000 });
}

/// 等这条推送连接结束（浏览器关掉 `EventSource` 时请求被中止，记为失败），15 秒没结束就报错。要在触发断开的操作
/// 之前调用：监听在调用时就挂上，结束得快也不会漏
export function streamEnded(page: Page, stream: Request): Promise<void> {
  return new Promise((resolve, reject) => {
    const onEnd = (req: Request) => {
      if (req === stream) {
        stop();
        resolve();
      }
    };
    const timer = setTimeout(() => {
      stop();
      reject(new Error("推送连接 15 秒内没有断开"));
    }, 15_000);
    const stop = () => {
      clearTimeout(timer);
      page.off("requestfailed", onEnd);
      page.off("requestfinished", onEnd);
    };
    page.on("requestfailed", onEnd);
    page.on("requestfinished", onEnd);
  });
}

/// 模拟切到后台 / 切回来：无头浏览器的标签页一直可见，改写 `visibilityState` 并像浏览器一样派发冒泡的
/// `visibilitychange`（推送 hook 听 `document`，TanStack 的聚焦管理听 `window`）
export async function setVisibility(page: Page, state: "hidden" | "visible"): Promise<void> {
  await page.evaluate((next) => {
    Object.defineProperty(document, "visibilityState", { configurable: true, get: () => next });
    Object.defineProperty(document, "hidden", { configurable: true, get: () => next === "hidden" });
    document.dispatchEvent(new Event("visibilitychange", { bubbles: true }));
  }, state);
}

/// 通知设置里「回复我的」的三档（单选的读屏名以档位名开头）
export type ReplyMode = "关" | "离站时逐条" | "每日汇总";

/// 打开设置 › 通知，返回「回复我的」那组单选
export async function replyModes(page: Page): Promise<Locator> {
  await page.goto("/settings/notifications");
  const group = page.getByRole("radiogroup", { name: "「回复我的」提醒邮件" });
  await expect(group).toBeVisible(ROUND_TRIP);
  await waitForTakeover(page, '[role="radiogroup"] input');
  return group;
}

/// 「回复我的」当前选中的是 `mode`
export async function expectReplyMode(page: Page, mode: ReplyMode): Promise<void> {
  const group = await replyModes(page);
  await expect(group.getByRole("radio", { name: new RegExp(`^${mode}`) })).toBeChecked();
}

/// 把「回复我的」改成 `mode`（已经是就不动），等保存完
export async function setReplyMode(page: Page, mode: ReplyMode): Promise<void> {
  const group = await replyModes(page);
  const radio = group.getByRole("radio", { name: new RegExp(`^${mode}`) });
  if (await radio.isChecked()) {
    return;
  }
  await radio.check();
  await expect(page.getByText("已保存")).toBeVisible(ROUND_TRIP);
}

/// 点「全部标为已读」（三类都没有未读时直接跳过），等铃铛不挂徽标。未读数回来之前按钮也是禁用的，不能看按钮当下
/// 可不可点：用页面打开时那次未读数请求（新页面第一次不带 ETag，总是 200 带数）判断
export async function markAllRead(page: Page): Promise<void> {
  const counted = page.waitForResponse(
    (res) => res.url().includes("/api/me/notifications/unread-count"),
    { timeout: 60_000 },
  );
  const list = await openInbox(page, "replies");
  const counts = (await (await counted).json()) as {
    replies: number;
    mentions: number;
    helpful: number;
  };
  if (counts.replies + counts.mentions + counts.helpful > 0) {
    const button = list.getByRole("button", { name: "全部标为已读" });
    await expect(button).toBeEnabled(ROUND_TRIP);
    await button.click();
  }
  await expect(bell(page)).toHaveAccessibleName("提醒", ROUND_TRIP);
}
