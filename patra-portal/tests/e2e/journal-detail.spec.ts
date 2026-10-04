import { expect, test } from "@playwright/test";

test("首页点期刊卡 → 跳转详情页并渲染核心区块", async ({ page }) => {
  await page.goto("/");
  // 只取封面卡（指向 /journals/{id} 的详情链接），排除区头的「浏览全部期刊」(→ /journals) 链接
  const cards = page.locator('[data-section="journals"] a[href^="/journals/"]');
  // 首页期刊榜由 RSC fetchVenues 取自后端；无可达后端时（如纯前端 portal-ci 的 e2e job）
  // 首页渲染不出期刊卡。该 happy-path 依赖服务端取数、无法在浏览器侧 mock，故无卡时跳过，
  // 留给有后端的本地 / staging e2e 跑（not-found 用例不依赖后端，始终执行）。
  test.skip((await cards.count()) === 0, "首页无期刊卡（后端不可达）：跳过 happy-path e2e");

  const firstCard = cards.first();
  await expect(firstCard).toBeVisible();
  await firstCard.click();

  await expect(page).toHaveURL(/\/journals\/\d+/);
  await expect(page.getByRole("heading", { level: 1 })).toContainText(/.+/);
  await expect(page.getByText(/深度数据/)).toBeVisible();
  await expect(page.getByRole("button", { name: /完整评级明细/ })).toBeVisible();
});

test("非数字 id → not-found 页，HTTP 状态码 404", async ({ page }) => {
  const res = await page.goto("/journals/not-a-real-id");
  expect(res?.status()).toBe(404);
  await expect(page.getByRole("heading", { name: "没有这一页" })).toBeVisible();
  await expect(page.getByText(/这本期刊不在 Patra/)).toBeVisible();
});
