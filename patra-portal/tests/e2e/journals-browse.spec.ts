import { expect, test } from "@playwright/test";

test("/journals 浏览检索 e2e smoke", async ({ page }) => {
  await page.goto("/");

  // 首页期刊模块由 RSC fetchVenues 取自后端；无后端时整个 Journals 区块返回 null，
  // 「浏览全部期刊」链接不渲染。happy-path 依赖后端，无则跳过。
  const browseLink = page.getByRole("link", { name: /浏览全部期刊/ });
  test.skip(
    (await browseLink.count()) === 0,
    "首页无「浏览全部期刊」链接（后端不可达）：跳过 happy-path e2e",
  );

  // 1. 点击「浏览全部期刊」→ 进入 /journals
  await browseLink.click();
  await expect(page).toHaveURL(/\/journals/);

  // 2. 等期刊网格渲染成功（期刊卡 Link 指向 /journals/{id}）。
  //    /journals 整页依赖 fetchVenuesPage/fetchVenuesFacets；无后端时渲染全局 error 屏。
  const firstCard = page.locator('a[href^="/journals/"]').first();
  const gridVisible = await firstCard
    .waitFor({ state: "visible", timeout: 5000 })
    .then(() => true)
    .catch(() => false);
  test.skip(!gridVisible, "/journals 无期刊卡（catalog 不可达）：跳过 happy-path e2e");

  // 3. 检索：在检索框输入常见词，等防抖（300ms）后断言 URL 含 q=
  const searchBox = page.getByRole("searchbox");
  await searchBox.fill("lancet");
  await expect(page).toHaveURL(/[?&]q=lancet/, { timeout: 2000 });

  // 4. 清除检索词，恢复无筛选状态（避免影响后续步骤）
  await searchBox.fill("");
  await expect(page).toHaveURL(/\/journals(?:\?.*)?$/, { timeout: 2000 });
  // 显式断言 q 已移除（仅 path 正则会被 q=lancet 误放行，无法验证回归）
  await expect(page).not.toHaveURL(/[?&]q=/, { timeout: 2000 });
  // 确保网格刷新完毕（等第一张卡重新可见）
  const refreshed = await page
    .locator('a[href^="/journals/"]')
    .first()
    .waitFor({ state: "visible", timeout: 5000 })
    .then(() => true)
    .catch(() => false);
  // 网格未恢复时显式 skip，避免静默 return 把「未验证」记成通过
  test.skip(!refreshed, "清空检索后网格未恢复可见：跳过后续 happy-path 断言");

  // 5. 筛选：JCR 分区 Q1（fieldset[aria-label="JCR 分区"] 内的 checkbox label）
  //    FacetGroup 在后端有数据才会渲染 Q1；若不存在则容错跳过此步。
  const jcrQ1 = page.locator('fieldset[aria-label="JCR 分区"] label').filter({ hasText: /^Q1/ });
  if ((await jcrQ1.count()) > 0) {
    await jcrQ1.first().click();
    await expect(page).toHaveURL(/[?&]jcr=Q1/);
    // 点一次清除 Q1，恢复无筛选
    await jcrQ1.first().click();
    await expect(page).not.toHaveURL(/[?&]jcr=Q1/, { timeout: 2000 });
  }

  // 6. 翻页：若分页导航存在则点「下一页」，断言 URL 含 page=2。
  //    结果不足一页时 BrowsePagination 返回 null，跳过此步。
  const nextPageLink = page.getByRole("link", { name: "下一页" });
  const paginationExists =
    (await nextPageLink.count()) > 0 &&
    (await nextPageLink.getAttribute("aria-disabled")) !== "true";
  if (paginationExists) {
    await nextPageLink.click();
    await expect(page).toHaveURL(/[?&]page=2/);
  }

  // 7. 点一张期刊卡 → 跳转详情页
  await page.goto("/journals");
  await page.locator('a[href^="/journals/"]').first().waitFor({ state: "visible", timeout: 5000 });
  await page.locator('a[href^="/journals/"]').first().click();
  await expect(page).toHaveURL(/\/journals\/\d+/);
});
