// patra-learn/tests/smoke.spec.ts
// 本地生产构建上的冒烟：首页、条目页、路线页、标签页、搜索，读者进度刷新仍在与清空，手机宽度不横向溢出
import { expect, type Page, test } from "@playwright/test";

const PROGRESS_KEY = "patra-learn.progress";

/// 页面不横向溢出
async function expectNoHorizontalScroll(page: Page) {
  const [scroll, inner] = await page.evaluate(() => [
    document.documentElement.scrollWidth,
    window.innerWidth,
  ]);
  expect(scroll).toBeLessThanOrEqual(inner);
}

test("首页：新读者看到从这里开始与 8 条路线", async ({ page }) => {
  const response = await page.goto("/");
  expect(response?.status()).toBe(200);
  await expect(page).toHaveTitle("Patra 学习站");
  await expect(page.getByRole("link", { name: "Patra 学习站", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "从这里开始" })).toBeVisible();
  await expect(page.getByRole("region", { name: "今天读这一条" })).toHaveCount(0);
  await expect(page.locator("#routes a[href^='/routes/']")).toHaveCount(8);
  await expect(page.getByRole("heading", { name: "正在长的主题" })).toBeVisible();
});

test("条目页：标题区、先读这几篇、诊断题、掌握度", async ({ page }) => {
  await page.goto("/entries/db-transactions");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("事务：要么全成，要么全不成");
  await expect(page.getByRole("navigation", { name: "位置" })).toContainText("数据库与事务");
  await expect(page.getByRole("region", { name: "先读这几篇" })).toBeVisible();
  await expect(page.getByRole("region", { name: "开头诊断题" })).toBeVisible();
  await expect(page.getByRole("region", { name: "掌握度" })).toBeVisible();
  await expect(page.getByText("下一篇 →")).toBeVisible();
});

test("条目页：标题里的反引号渲染成行内代码", async ({ page }) => {
  await page.goto("/entries/spring-service-discovery");
  await expect(page.getByRole("heading", { level: 1 }).locator("code")).toHaveText("lb://");
  await expect(page).toHaveTitle(/^服务发现：lb:\/\/ 怎么找到服务/);
});

test("路线页：按难度分段，点一行进入条目", async ({ page }) => {
  await page.goto("/routes/web-http");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Web 与 HTTP");
  await expect(page.getByText("从这里开始")).toBeVisible();
  await page.getByRole("link", { name: /打开一个网页时发生了什么/ }).click();
  await expect(page).toHaveURL(/\/entries\/web-what-happens-opening-a-page$/);
});

test("标签页：顶栏进入第一个标签，按类型筛选", async ({ page }) => {
  await page.goto("/");
  await page
    .getByRole("navigation", { name: "主导航" })
    .getByRole("link", { name: "标签" })
    .click();
  await expect(page).toHaveURL(/\/tags\/backend$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("后端");
  await page.getByRole("tab", { name: "坑" }).click();
  const cards = page.locator("main a[href^='/entries/']");
  await expect(cards.first()).toBeVisible();
  for (const card of await cards.all()) await expect(card).toContainText("坑");
});

test("搜索：命中高亮，查不到给常搜词", async ({ page }) => {
  await page.goto("/search");
  const box = page.getByRole("searchbox", { name: "搜索条目" });
  await box.fill("事务");
  await expect(page.getByRole("link", { name: /事务：要么全成/ })).toBeVisible();
  await expect(page.locator("mark").first()).toHaveText("事务");
  await box.fill("不会有这个词的条目");
  await expect(page.getByText("没找到「不会有这个词的条目」")).toBeVisible();
});

test("进度：标掌握度后刷新仍在，首页推这一条；清空后首页回到从这里开始", async ({ page }) => {
  await page.goto("/entries/db-transactions");
  const mastery = page.getByRole("region", { name: "掌握度" });
  await mastery.getByRole("button", { name: /理解中/ }).click();
  await expect(page.getByText("记下了：理解中")).toBeVisible();
  await page.reload();
  await expect(mastery.getByRole("button", { name: /理解中/ })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(page.getByText("你标的是「理解中」")).toBeVisible();

  await page.goto("/");
  const today = page.getByRole("region", { name: "今天读这一条" });
  await expect(today).toContainText("事务：要么全成，要么全不成");
  await expect(today).toContainText("你标过「理解中」");
  await today.getByRole("button", { name: "想好了，翻开看看" }).click();
  await expect(today.getByText("答案")).toBeVisible();

  await page.getByRole("button", { name: "清空这个浏览器里的记录" }).click();
  await page.getByRole("button", { name: "确定清空" }).click();
  await expect(page.getByRole("region", { name: "从这里开始" })).toBeVisible();
  expect(await page.evaluate((key) => localStorage.getItem(key), PROGRESS_KEY)).toBeNull();
  await page.reload();
  await expect(page.getByRole("region", { name: "从这里开始" })).toBeVisible();
});

test("进度：浏览器存储不可用时页面照常工作", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.addInitScript(() => {
    Object.defineProperty(window, "localStorage", {
      get() {
        throw new DOMException("denied", "SecurityError");
      },
    });
  });
  await page.goto("/entries/db-transactions");
  await page
    .getByRole("region", { name: "掌握度" })
    .getByRole("button", { name: /吃透了/ })
    .click();
  await expect(page.getByText("记下了：吃透了")).toBeVisible();
  await page.goto("/");
  await expect(page.getByRole("region", { name: "从这里开始" })).toBeVisible();
  expect(errors).toEqual([]);
});

test("手机宽度：各页正文能读、不横向溢出", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  for (const path of [
    "/",
    "/entries/db-transactions",
    "/routes/web-http",
    "/topics/login-session",
    "/tags/backend",
    "/search",
  ]) {
    await page.goto(path);
    await expectNoHorizontalScroll(page);
  }
});
