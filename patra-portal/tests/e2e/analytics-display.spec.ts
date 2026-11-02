import { expect, type Page, test } from "@playwright/test";
import { ROUND_TRIP, visit } from "@tests/e2e/support/account";
import { pickPaper } from "@tests/e2e/support/discussion";
import { signedIn, type Verified } from "@tests/e2e/support/helpful";

/// v0.18 计数、热读、看过还看了（PAP-174）冒烟：连 mini 上的后端跑（内联 PATRA_GATEWAY_BASE_URL），要等 patra-analytics
/// 部署之后。测试账号 B / C / E 的凭据在 ~/.patra/secrets/e2e.env、e2e-e.env，不进仓库。
///
/// 人工核对（页面上不好自动化）：
/// 1. 热读每小时重算一次：B、C、E 都读过同一篇后，等下一次计算再看首页「热读」与检索结果的「近期热读」。
/// 2. 停掉 patra-analytics（`docker stop patra-analytics`）后打开首页、检索、文献页：主体照常，计数与推荐不出现，
///    「热读」标签显示加载失败；跑完再启动。
const B: Verified = {
  email: process.env.PATRA_E2E_VERIFIED_B_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_B_PASSWORD ?? "",
};

/// 读者一行里某一项的数字（没有时 0）
async function readerCount(page: Page, label: "人看过" | "人读过全文"): Promise<number> {
  const item = page.locator("[data-reader-count]", { hasText: label });
  if ((await item.count()) === 0) return 0;
  return Number((await item.first().textContent())?.replace(/[^\d]/g, "") ?? 0);
}

test("B 读了全文后 70 秒内，文献页的看过与读过全文各加一", async ({ browser, request }) => {
  test.skip(!B.email, "没有测试账号 B 的凭据");
  test.setTimeout(180_000);
  const paperId = await pickPaper(request);
  const anon = await (await browser.newContext()).newPage();
  await visit(anon, `/papers/${paperId}`);
  const viewers = await readerCount(anon, "人看过");
  const readers = await readerCount(anon, "人读过全文");

  const page = await signedIn(browser, B, { width: 1440, height: 900 }, `/papers/${paperId}`);
  const body = page.locator("#fulltext");
  test.skip((await body.count()) === 0, "这篇没有全文");
  await body.scrollIntoViewIfNeeded();
  await page.waitForTimeout(12_000);
  await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));

  await expect
    .poll(
      async () => {
        await anon.reload();
        return [await readerCount(anon, "人看过"), await readerCount(anon, "人读过全文")];
      },
      { timeout: 70_000, intervals: [10_000] },
    )
    .toEqual([viewers + 1, readers + 1]);
  await page.context().close();
  await anon.context().close();
});

test("首页有「热读」标签，切过去有内容或说明句", async ({ page }) => {
  await visit(page, "/?tab=hot");
  await expect(page.getByRole("tab", { name: /热读/ })).toHaveAttribute("aria-selected", "true");
  await expect(page.locator("[data-section=explore-feed]")).toContainText(
    /按近 14 天的阅读排|近 7 天/,
    ROUND_TRIP,
  );
});

for (const path of ["/", "/papers"]) {
  test(`375 宽下 ${path} 不横向溢出`, async ({ browser }) => {
    const page = await (
      await browser.newContext({ viewport: { width: 375, height: 812 } })
    ).newPage();
    await visit(page, path);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
    await page.context().close();
  });
}
