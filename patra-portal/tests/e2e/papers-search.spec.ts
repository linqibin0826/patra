import { expect, type Page, test } from "@playwright/test";

/** 等列表出现（文献卡标题链接指向 /papers/{id}）；后端不可达时返回 false 供 skip。 */
async function waitForList(page: Page): Promise<boolean> {
  return page
    .getByRole("list", { name: "文献列表" })
    .getByRole("link")
    .first()
    .waitFor({ state: "visible", timeout: 10_000 })
    .then(() => true)
    .catch(() => false);
}

test.describe("/papers 桌面冒烟", () => {
  test("检索 → facet → chip → 翻页 → 详情", async ({ page }) => {
    await page.goto("/papers");
    test.skip(!(await waitForList(page)), "/papers 无列表（catalog 不可达）：跳过");
    await expect(page.getByText(/最近同步/)).toBeVisible();

    const box = page.getByRole("searchbox", { name: /按关键词检索文献/ });
    await box.fill("diabetes");
    await page.getByRole("button", { name: "检索", exact: true }).click();
    await expect(page).toHaveURL(/[?&]q=diabetes/);
    await expect(page.locator("mark").first()).toBeVisible();

    const typeGroup = page.getByRole("group", { name: "文献类型" });
    await typeGroup.getByRole("checkbox").first().click();
    await expect(page).toHaveURL(/[?&]type=/);
    const chips = page.getByRole("list", { name: "已选筛选条件" });
    await expect(chips.getByText("类型")).toBeVisible();

    await chips
      .getByRole("button", { name: /^移除 / })
      .last()
      .click();
    await expect(page).not.toHaveURL(/[?&]type=/);

    const next = page.getByRole("link", { name: "下一页" });
    if (await next.isVisible()) {
      await next.click();
      await expect(page).toHaveURL(/[?&]page=2/);
      expect(await waitForList(page)).toBe(true);
    }

    await page.getByRole("list", { name: "文献列表" }).getByRole("link").first().click();
    await expect(page).toHaveURL(/\/papers\/\d+$/);
  });

  test("首页搜关键词 → /papers；搜真实 PMID → 直达详情", async ({ page, request }) => {
    await page.goto("/");
    await page.getByRole("textbox").fill("GLP-1");
    await page.getByRole("button", { name: /搜索/ }).click();
    await expect(page).toHaveURL(/\/papers\?q=GLP-1/);
    test.skip(!(await waitForList(page)), "catalog 不可达：跳过 PMID 直跳");

    const gateway = process.env.PATRA_GATEWAY_BASE_URL;
    test.skip(!gateway, "未设置 PATRA_GATEWAY_BASE_URL：跳过 PMID 直跳");
    const res = await request.get(`${gateway}/patra-catalog/portal/publications/search?pageSize=1`);
    const pmid = (await res.json()).items?.[0]?.pmid as string | undefined;
    test.skip(!pmid, "取不到样例 PMID：跳过");

    await page.goto("/");
    await page.getByRole("tab", { name: "PMID" }).click();
    await page.getByRole("textbox").fill(pmid ?? "");
    await page.getByRole("button", { name: /搜索/ }).click();
    await expect(page).toHaveURL(/\/papers\/\d+$/);
  });

  test("期刊详情 → 查看该刊文献 → 期刊 chip 显示刊名", async ({ page }) => {
    await page.goto("/journals");
    const card = page.locator('a[href^="/journals/"]').first();
    const ok = await card
      .waitFor({ state: "visible", timeout: 10_000 })
      .then(() => true)
      .catch(() => false);
    test.skip(!ok, "期刊列表不可达：跳过");
    await card.click();
    await page.getByRole("link", { name: /查看该刊文献/ }).click();
    await expect(page).toHaveURL(/\/papers\?venue=\d+/);
    const chips = page.getByRole("list", { name: "已选筛选条件" });
    await expect(chips.getByText("期刊", { exact: true })).toBeVisible();
    await expect(chips.getByText(/^期刊 #/)).toHaveCount(0);
  });

  test("导航与主题云入口", async ({ page }) => {
    await page.goto("/");
    await page
      .getByRole("navigation", { name: "主导航" })
      .getByRole("link", { name: "文献" })
      .click();
    await expect(page).toHaveURL(/\/papers$/);
    await page.goto("/");
    await page.getByRole("link", { name: /GLP-1 受体激动剂/ }).click();
    await expect(page).toHaveURL(/\/papers\?q=/);
  });
});

test.describe("/papers 移动端冒烟", () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test("抽屉勾选 → 查看结果", async ({ page }) => {
    await page.goto("/papers");
    test.skip(!(await waitForList(page)), "catalog 不可达：跳过");
    await page.getByRole("button", { name: "筛选" }).click();
    const sheet = page.getByRole("dialog");
    await sheet.getByRole("group", { name: "文献类型" }).getByRole("checkbox").first().click();
    await expect(page).toHaveURL(/[?&]type=/);
    await sheet.getByRole("button", { name: /查看 .* 篇结果/ }).click();
    await expect(sheet).toBeHidden();
    await expect(page.getByTestId("filter-badge")).toHaveText("1");
  });
});
