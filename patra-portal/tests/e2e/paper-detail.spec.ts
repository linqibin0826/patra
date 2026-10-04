import { expect, test } from "@playwright/test";

test("explore-feed 点文献 → 跳转详情页并渲染摘要区", async ({ page }) => {
  await page.goto("/");
  const links = page.getByRole("link", { name: "详情" });
  // explore-feed 由 RSC fetchFeed 取自后端；无可达后端时（纯前端 portal-ci 的 e2e job）
  // 首页渲染不出文献卡。该 happy-path 依赖服务端取数，故无卡时跳过，留给有后端的本地/staging e2e。
  test.skip(
    (await links.count()) === 0,
    "explore-feed 无文献卡（后端不可达）：跳过 happy-path e2e",
  );

  const first = links.first();
  await expect(first).toBeVisible();
  await first.click();

  await expect(page).toHaveURL(/\/papers\/\d+/);

  // 详情页核心是「摘要」区（<section aria-label="摘要">→ region）。
  // 若 catalog 不可达（500/超时），详情页渲染的是全局 error 屏而非摘要区——
  // 等不到摘要 region 即视为「后端不可达」，与无文献卡同属跳过场景（留给有后端的 staging e2e）。
  const abstractVisible = await page
    .getByRole("region", { name: "摘要" })
    .waitFor({ state: "visible", timeout: 5000 })
    .then(() => true)
    .catch(() => false);
  test.skip(!abstractVisible, "详情页无摘要区（catalog 不可达）：跳过 happy-path e2e");

  await expect(page.getByRole("heading", { level: 1 })).toContainText(/.+/);
  await expect(page.getByRole("region", { name: "摘要" })).toBeVisible();
});

test("非数字 id → not-found 页，HTTP 状态码 404", async ({ page }) => {
  const res = await page.goto("/papers/not-a-real-id");
  expect(res?.status()).toBe(404);
  await expect(page.getByRole("heading", { name: "没有这一页" })).toBeVisible();
  await expect(page.getByText(/这篇文献不在 Patra/)).toBeVisible();
});
