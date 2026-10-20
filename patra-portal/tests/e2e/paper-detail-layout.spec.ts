import { expect, type Page, test } from "@playwright/test";

/// 文献详情排版（PAP-89）。依赖真实后端：内联 PATRA_GATEWAY_BASE_URL 指向 mini 跑。
/// 样例是 PMID 41575322（长标题、11 图 10 表、56 条参考文献）；重导后 id 会变，可用环境变量覆盖。
const LAYOUT_ID = process.env.PATRA_E2E_LAYOUT_ID ?? "352303012807630907";

async function openPaper(page: Page) {
  await page.goto(`/papers/${LAYOUT_ID}`);
  const ok = await page
    .getByRole("article", { name: "全文" })
    .locator("[data-ft-body]")
    .waitFor({ state: "attached", timeout: 20_000 })
    .then(() => true)
    .catch(() => false);
  test.skip(!ok, `文献 ${LAYOUT_ID} 没有可读全文（后端不可达或样例数据已变）`);
}

/** 元素占了几行：高度 ÷ 行高。 */
async function lines(page: Page, selector: string): Promise<number> {
  return page.locator(selector).evaluate((el) => {
    const lh = Number.parseFloat(getComputedStyle(el).lineHeight);
    return Math.round(el.getBoundingClientRect().height / lh);
  });
}

async function noHorizontalScroll(page: Page) {
  const [sw, cw] = await page.evaluate(() => [
    document.scrollingElement?.scrollWidth ?? 0,
    document.scrollingElement?.clientWidth ?? 0,
  ]);
  expect(sw, "页面出现横向滚动").toBe(cw);
}

test.describe("桌面 1440", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("长标题拆成主标题 2 行 + 副标题 3 行；插图按宽高比取宽；页面无横向滚动", async ({
    page,
  }) => {
    await openPaper(page);
    expect(await lines(page, "h1")).toBe(2);
    expect(await lines(page, "h1 + p")).toBe(3);

    const widths = await page
      .locator("[data-layout]")
      .evaluateAll((els) =>
        els
          .slice(0, 3)
          .map((el) => [el.getAttribute("data-layout"), el.getBoundingClientRect().width]),
      );
    // Figure 1（1.31）正文宽，Figure 2（2.68）超宽，Figure 3（1.79）加宽
    expect(widths[0]?.[0]).toBe("text");
    expect(Math.abs(Number(widths[0]?.[1]) - 612)).toBeLessThanOrEqual(2);
    expect(widths[1]?.[0]).toBe("wide-zoom");
    expect(Math.abs(Number(widths[1]?.[1]) - 772)).toBeLessThanOrEqual(2);
    expect(widths[2]?.[0]).toBe("wide");
    expect(Math.abs(Number(widths[2]?.[1]) - 772)).toBeLessThanOrEqual(2);

    await noHorizontalScroll(page);
  });

  test("全页只有一组「阅读全文」；文末依次是声明与附注、参考文献、文献档案、许可与出处", async ({
    page,
  }) => {
    await openPaper(page);
    await expect(page.getByRole("link", { name: /阅读全文/ })).toHaveCount(1);
    const order = await page
      .locator("#declarations, #references, #record, #license")
      .evaluateAll((els) => els.map((el) => el.id));
    expect(order).toEqual(["declarations", "references", "record", "license"]);
  });
});

test.describe("手机 375", () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test("标签栏改成单列：摘要标签在正文上方；页面无横向滚动", async ({ page }) => {
    await openPaper(page);
    const headingRow = page
      .locator("[data-ft-body] h2")
      .first()
      .locator("xpath=ancestor::div[contains(@class,'grid')][1]");
    const [label, content] = await headingRow.evaluate((el) =>
      [...el.children].map((c) => c.getBoundingClientRect().toJSON()),
    );
    expect(label.x).toBe(content.x);
    expect(label.y).toBeLessThan(content.y);
    await noHorizontalScroll(page);
  });
});
