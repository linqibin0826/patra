import { expect, test } from "@playwright/test";
import { login, ROUND_TRIP, visit } from "@tests/e2e/support/account";
import { pickPaper } from "@tests/e2e/support/discussion";
import type { Verified } from "@tests/e2e/support/helpful";

/// v0.18 门户埋点（PAP-173）冒烟：连 mini 上的后端跑（内联 PATRA_GATEWAY_BASE_URL），后端部署 patra-analytics 之后才有意义。
/// 测试账号 B 的凭据在 ~/.patra/secrets/e2e.env，不进仓库。
///
/// 页面上看不到采集端点收到了什么，下面两条在 mini 上人工核对：
/// 1. 打开一篇文献后，Kafka `events.raw` 里有这条 `article_viewed`，带 `device_id`；登录时带 `user_id`；没有 IP 字段
///    （`kafka-console-consumer --topic events.raw` 看最新几条）。
/// 2. 停掉 patra-analytics（`docker stop patra-analytics`）后再跑本文件：页面照常、控制台没有错误；跑完再启动。
const B: Verified = {
  email: process.env.PATRA_E2E_VERIFIED_B_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_B_PASSWORD ?? "",
};

/// 本机 HTTP，Cookie 没有 Secure，名字不带 __Host-（见 playwright.config.ts）
const DEVICE_COOKIE = "patra_device";

test("从没来过的访客打开首页就有设备 Cookie，登录后设备 ID 不变", async ({ page, context }) => {
  test.skip(!B.email, "没有测试账号 B 的凭据");
  await visit(page, "/");
  const before = (await context.cookies()).find((c) => c.name === DEVICE_COOKIE);
  expect(before?.value).toMatch(/^[A-Za-z0-9_-]{43}$/);
  expect((await context.cookies()).some((c) => c.name === "patra_sid")).toBe(true);
  await login(page, B);
  await expect(page).not.toHaveURL(/\/login/, ROUND_TRIP);
  const after = (await context.cookies()).find((c) => c.name === DEVICE_COOKIE);
  expect(after?.value).toBe(before?.value);
});

test("打开文献：浏览器事件经第一方路径发出，门户回 204，控制台没有错误", async ({
  page,
  request,
}) => {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  const paperId = await pickPaper(request);
  const signals = page.waitForResponse(
    (res) => res.url().endsWith("/api/reading-signals") && res.request().method() === "POST",
    { timeout: 30_000 },
  );
  await visit(page, `/papers/${paperId}`);
  // 攒 5 秒一批；切走标签页时立刻用 sendBeacon 发，这里直接等定时那一批
  const res = await signals;
  expect(res.status()).toBe(204);
  const body = res.request().postDataJSON() as {
    events: { event_name: string; page_path: string }[];
  };
  expect(body.events).toContainEqual(
    expect.objectContaining({ event_name: "page_viewed", page_path: `/papers/${paperId}` }),
  );
  // 浏览器批次里没有任何身份字段
  expect(JSON.stringify(body)).not.toMatch(/device_id|user_id|session_id/);
  expect(errors).toEqual([]);
});
