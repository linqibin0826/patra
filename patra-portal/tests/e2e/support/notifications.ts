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

/// 打开提醒列表的一个标签。打开到出现「提醒」标题为止，没出来就重新打开，最多重试 2 分钟。连 mini 跑时有两种情况
/// 打不开：next dev 编译新路由时让已开的页面整页重载，打断正在进行的跳转（`net::ERR_ABORTED`）；门户渲染前要向网关
/// 确认登录状态，本机经 tailscale 中继访问 mini 时网络慢，这一步会超时（10 秒），页面就交给错误页
export async function openInbox(page: Page, tab: keyof typeof INBOX_PATH): Promise<Locator> {
  const list = page.locator("main");
  await expect(async () => {
    await page.goto(INBOX_PATH[tab], { timeout: 60_000 });
    await expect(list.getByRole("heading", { level: 1, name: "提醒" })).toBeVisible(ROUND_TRIP);
  }).toPass({ timeout: 2 * 60_000 });
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

/// 用例开始前、清理时把这个账号的提醒全部标为已读，再打开提醒页，等铃铛不挂徽标。
///
/// 直接调门户的「全部已读」接口，不点页面上的按钮：按钮要等页面自己的未读数请求成功回来才会启用，连 mini 时本机
/// 走 tailscale 中继、网络慢，这条请求会超时失败（门户回 502），按钮就一直禁用。也不能用 `waitForResponse` 等这条
/// 请求再决定点不点：next dev 每编译一个新模块，已开的页面会整页重载，旧页面在跳转途中还会再发一次，抢先被等到，
/// 跳转完成后它的响应体就读不到了。按钮本身由提醒用例里的「全部标为已读后未读数归零」那一步测
export async function markAllRead(page: Page): Promise<void> {
  // 写操作要带同源的 `Origin`（门户防跨站请求伪造），`page.request` 默认不带。调用时页面必须已经停在门户上，
  // 否则取不到门户的 origin
  const origin = new URL(page.url()).origin;
  await expect(async () => {
    const res = await page.request.put("/api/me/notifications/read", {
      headers: { Origin: origin },
    });
    expect(res.status()).toBe(200);
  }).toPass({ timeout: 60_000 });
  await openInbox(page, "replies");
  await expect(bell(page)).toHaveAccessibleName("提醒", ROUND_TRIP);
}
