import { expect, type Route, test } from "@playwright/test";
import { bell, markAllRead, openInbox, signIn } from "@tests/e2e/support/notifications";

/// 提醒用例共用的辅助函数（support/notifications.ts）自己的回归测试。连 Mac mini 上的真实后端跑 e2e 时，本机到 mini
/// 常走 tailscale 中继，一次网络往返约 0.4～2.6 秒，辅助函数在用例准备阶段就因此失败过。这里用 `page.route` 把慢网络
/// 的时序固定下来，不靠运气复现。账号用 e2e 共用的测试账号 E（提醒用例里收提醒的那个，凭据在本机
/// ~/.patra/secrets/e2e-e.env），只做全部已读，不写内容。和其他提醒用例共用 E，要 `--workers=1` 跑
const E = {
  email: process.env.PATRA_E2E_VERIFIED_E_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_E_PASSWORD ?? "",
};

const UNREAD_PATH = "/api/me/notifications/unread-count";

/// 让这条请求晚 `ms` 毫秒到浏览器（先照常发到门户，拿到响应后再压着）
async function deliverLate(route: Route, ms: number): Promise<void> {
  const response = await route.fetch();
  await new Promise((resolve) => setTimeout(resolve, ms));
  await route.fulfill({ response });
}

test.describe("提醒辅助函数", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过");
  test.skip(!E.email || !E.password, "没有测试账号 E（~/.patra/secrets/e2e-e.env）");

  test("markAllRead：上一页的未读数响应晚到，也能清空", async ({ browser }) => {
    test.setTimeout(3 * 60_000);
    const e = await signIn(browser, { width: 1440, height: 900 }, E);
    try {
      // 首页的未读数响应压 2 秒才到浏览器，提醒页的页面请求压 6 秒，模拟旧页面的请求在全部已读进行中才回来。旧做法
      // 先挂 waitForResponse 再跳转，会等到这条晚到的响应，跳转完成后读它的响应体失败；现在不依赖它，这里验证最终清零
      let first = true;
      await e.page.route(`**${UNREAD_PATH}`, async (route) => {
        const late = first;
        first = false;
        await (late ? deliverLate(route, 2_000) : route.continue());
      });
      await e.page.route(
        (url) => url.pathname === "/notifications",
        async (route) => {
          await new Promise((resolve) => setTimeout(resolve, 6_000));
          await route.continue();
        },
      );
      const sent = e.page.waitForRequest((req) => req.url().includes(UNREAD_PATH));
      await e.page.reload();
      await sent;

      await markAllRead(e.page);

      await expect(bell(e.page)).toHaveAccessibleName("提醒");
      const counts = await (await e.page.request.get(UNREAD_PATH)).json();
      expect(counts).toMatchObject({ replies: 0, mentions: 0, helpful: 0 });
    } finally {
      await e.close();
    }
  });

  test("openInbox：第一次跳转被打断（net::ERR_ABORTED），重新打开", async ({ browser }) => {
    test.setTimeout(3 * 60_000);
    const e = await signIn(browser, { width: 1440, height: 900 }, E);
    try {
      // next dev 编译新路由时让已开的页面整页重载，撞上正在进行的跳转就是这个错误；这里中止第一次页面请求来模拟
      let first = true;
      await e.page.route(
        (url) => url.pathname === "/notifications",
        async (route) => {
          const abort = first;
          first = false;
          await (abort ? route.abort("aborted") : route.continue());
        },
      );

      const list = await openInbox(e.page, "replies");

      await expect(list.getByRole("heading", { level: 1, name: "提醒" })).toBeVisible();
    } finally {
      await e.close();
    }
  });

  test("openInbox：第一次打开时服务端出错（登录状态取不到，进了错误页），重新打开", async ({
    browser,
  }) => {
    test.setTimeout(3 * 60_000);
    const e = await signIn(browser, { width: 1440, height: 900 }, E);
    try {
      // 门户渲染提醒页要先向网关确认登录状态，网络慢时这一步 10 秒超时，页面就交给错误页、没有「提醒」标题；这里让
      // 第一次页面请求直接回一个 500 错误页来模拟，不真等 10 秒
      let first = true;
      await e.page.route(
        (url) => url.pathname === "/notifications",
        async (route) => {
          const broken = first;
          first = false;
          await (broken
            ? route.fulfill({
                status: 500,
                contentType: "text/html",
                body: "<main><p>出错了</p></main>",
              })
            : route.continue());
        },
      );

      const list = await openInbox(e.page, "replies");

      await expect(list.getByRole("heading", { level: 1, name: "提醒" })).toBeVisible();
    } finally {
      await e.close();
    }
  });
});
