import { expect, type Page, test } from "@playwright/test";

/// 详情页右侧栏的吸顶位置必须等于它的自然位置：否则开始滚动时侧栏会先随页面上移一段再吸住，
/// 滚回顶部又落下去，而导航与面包屑不动，看起来就是右边在晃。
async function expectRailStaysPut(page: Page) {
  const rail = page.locator("aside.sticky").filter({ visible: true });
  await expect(rail).toHaveCount(1);

  const topAt = async (y: number) => {
    await page.evaluate((scrollY) => window.scrollTo(0, scrollY), y);
    return (await rail.boundingBox())?.y;
  };

  const natural = await topAt(0);
  for (const y of [5, 10, 20, 40, 120]) {
    expect(await topAt(y), `scrollY=${y} 时侧栏移动了`).toBe(natural);
  }
}

test("文献详情：滚动时右侧栏不移动", async ({ page }) => {
  await page.goto("/");
  const links = page.getByRole("link", { name: "详情" });
  // explore-feed 由 RSC 取自后端；无可达后端时渲染不出文献卡，跳过（同 paper-detail.spec）
  test.skip((await links.count()) === 0, "explore-feed 无文献卡（后端不可达）");
  await links.first().click();
  await expect(page).toHaveURL(/\/papers\/\d+/);
  // catalog 不可达时详情页渲染的是全局 error 屏而非摘要区，同样跳过（同 paper-detail.spec）
  const abstractVisible = await page
    .getByRole("region", { name: "摘要" })
    .waitFor({ state: "visible", timeout: 5000 })
    .then(() => true)
    .catch(() => false);
  test.skip(!abstractVisible, "详情页无摘要区（catalog 不可达）");

  await expectRailStaysPut(page);
});

test("期刊详情：滚动时右侧栏不移动", async ({ page }) => {
  await page.goto("/");
  const cards = page.locator('[data-section="journals"] a[href^="/journals/"]');
  // 首页期刊榜由 RSC 取自后端；无可达后端时渲染不出期刊卡，跳过（同 journal-detail.spec）
  test.skip((await cards.count()) === 0, "首页无期刊卡（后端不可达）");
  await cards.first().click();
  await expect(page).toHaveURL(/\/journals\/\d+/);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

  await expectRailStaysPut(page);
});
