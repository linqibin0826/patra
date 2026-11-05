import { chromium, expect, type Page, test } from "@playwright/test";
import { login, ROUND_TRIP, visit } from "@tests/e2e/support/account";
import { pickPaper } from "@tests/e2e/support/discussion";
import type { Verified } from "@tests/e2e/support/helpful";

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

/// 计数按会话排除爬虫：一个会话里只要有一条事件的浏览器标识命中 COUNTER-Robots 名单（`HeadlessChrome` 就在里面，见
/// `patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/robots/counter-robots.json` 与
/// 埋点方案 §6.7），整个会话都不计入公开数字。上下文级的 `userAgent` 覆盖不住浏览器自己发的 keepalive / sendBeacon
/// 请求，它们仍带无头 Chromium 的 `HeadlessChrome`，会话随时被判成爬虫；所以 B 单独起一个用启动参数改标识的浏览器
const DESKTOP_CHROME_UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

/// 读数用的访客明确用爬虫标识：它反复刷新文献页，自己的浏览不能算进计数
const CRAWLER_UA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

/// 读者一行里某一项的数字（没有时 0）
async function readerCount(page: Page, label: "人看过" | "人读过全文"): Promise<number> {
  const item = page.locator("[data-reader-count]", { hasText: label });
  if ((await item.count()) === 0) return 0;
  return Number((await item.first().textContent())?.replace(/[^\d]/g, "") ?? 0);
}

test("B 读了全文后 70 秒内，文献页的看过与读过全文各加一", async ({ browser, request }) => {
  test.skip(!B.email, "没有测试账号 B 的凭据");
  test.setTimeout(240_000);
  const anon = await (await browser.newContext({ userAgent: CRAWLER_UA })).newPage();
  // 不少文献没有可转载的全文：这时 #fulltext 也在（显示「暂无开放全文」「PMC 收录了这篇文献」等），只有真有正文时
  // 区块头才有「许可与出处」。全文是流式补上的，先等骨架屏换掉再看。同一个人只算一次，B 以前读过的文献再读不会加一，
  // 所以连抽几篇，找一篇有全文、还没人看过的
  let paperId = "";
  for (let i = 0; i < 10 && !paperId; i++) {
    const candidate = await pickPaper(request);
    await visit(anon, `/papers/${candidate}`);
    const section = anon.locator("#fulltext:not([aria-busy])");
    await expect(section).toBeVisible(ROUND_TRIP);
    const hasFulltext = (await section.getByRole("link", { name: /许可与出处/ }).count()) > 0;
    if (hasFulltext && (await readerCount(anon, "人看过")) === 0) {
      paperId = candidate;
    }
  }
  test.skip(!paperId, "连抽 10 篇都没有有全文、还没人看过的");

  const desktop = await chromium.launch({ args: [`--user-agent=${DESKTOP_CHROME_UA}`] });
  const page = await desktop.newPage({
    baseURL: test.info().project.use.baseURL,
    viewport: { width: 1440, height: 900 },
  });
  await login(page, B, `/papers/${paperId}`);
  await expect(page).not.toHaveURL(/\/login/, ROUND_TRIP);
  // 像读者那样把全文顶到视口上沿：居中滚动在全文还是骨架屏时会停在正文上方，正文不到半屏、永远不算在读
  const fulltextRead = page.waitForRequest(
    (req) =>
      req.url().endsWith("/api/reading-signals") && /"fulltext_read"/.test(req.postData() ?? ""),
    { timeout: 30_000 },
  );
  const body = page.locator("#fulltext:not([aria-busy])");
  await expect(body).toBeVisible(ROUND_TRIP);
  await body.evaluate((el) => el.scrollIntoView({ block: "start" }));
  await fulltextRead;

  await expect
    .poll(
      async () => {
        await anon.reload();
        return [await readerCount(anon, "人看过"), await readerCount(anon, "人读过全文")];
      },
      { timeout: 70_000, intervals: [10_000] },
    )
    .toEqual([1, 1]);
  await desktop.close();
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
