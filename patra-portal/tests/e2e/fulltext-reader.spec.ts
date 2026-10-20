import { expect, type Page, test } from "@playwright/test";

/// 全文阅读的交互主路径（PAP-88）。依赖真实后端数据：内联 PATRA_GATEWAY_BASE_URL 指向 mini 跑。
/// 文献 id 是 mini 上的样例，重导数据后 id 会变，可用环境变量覆盖；页面上没有对应状态时跳过而不是失败。
const FULLTEXT_ID = process.env.PATRA_E2E_FULLTEXT_ID ?? "352303012816019646"; // PMC12858081，有 PDF
const LONG_TABLE_ID = process.env.PATRA_E2E_LONG_TABLE_ID ?? "352303012807630925"; // 表 1 有 37 行
const NO_FULLTEXT_ID = process.env.PATRA_E2E_NO_FULLTEXT_ID ?? "352303012803436544"; // 不在 PMC

/// 锚点 `#…` → 该元素（JATS 的 id 带点号，不能直接当 CSS 选择器）
const byHash = (page: Page, hash: string) => page.locator(`[id="${hash.slice(1)}"]`);
/// 地址以该锚点结尾
const endsWithHash = (hash: string) =>
  new RegExp(`${hash.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`);

async function openFullText(page: Page, id: string) {
  await page.goto(`/papers/${id}`);
  const read = page.getByRole("link", { name: /阅读全文/ }).first();
  const readable = await read
    .waitFor({ state: "visible", timeout: 15_000 })
    .then(() => true)
    .catch(() => false);
  test.skip(!readable, `文献 ${id} 没有可读全文（后端不可达或样例数据已变）`);
  // 全文区块在 Suspense 里后到
  await expect(page.getByRole("article", { name: "全文" })).not.toHaveAttribute(
    "aria-busy",
    "true",
  );
}

test.describe("有全文 · 桌面", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("阅读全文 → 目录跳转 → 引用号出卡片 → 跳到文末 → 回到正文", async ({ page }) => {
    await openFullText(page, FULLTEXT_ID);
    const article = page.getByRole("article", { name: "全文" });

    await page
      .getByRole("link", { name: /阅读全文/ })
      .first()
      .click();
    await expect(page).toHaveURL(/#fulltext$/);
    await expect(article.getByRole("heading", { level: 2 }).first()).toBeInViewport();

    const toc = page.getByRole("navigation", { name: "目录" });
    const third = toc.getByRole("link").nth(2);
    const target = (await third.getAttribute("href")) ?? "";
    await third.click();
    await expect(page).toHaveURL(endsWithHash(target));
    await expect(byHash(page, target)).toBeInViewport();
    // 当前章节展开二级标题后列表会变，按地址重新定位
    await expect(toc.locator(`a[href="${target}"]`)).toHaveAttribute("aria-current", "location");

    const cite = article.locator("[data-ft-body] a[data-cite-ref]").first();
    await cite.scrollIntoViewIfNeeded();
    const refHref = (await cite.getAttribute("href")) ?? "";
    await cite.click();
    // PAP-89：点引用号先弹参考文献卡片，地址不变
    const card = page.getByRole("dialog");
    await expect(card).toBeVisible();
    await expect(page).not.toHaveURL(endsWithHash(refHref));
    await card.getByRole("link", { name: "跳到文末" }).first().click();
    await expect(card).toBeHidden();
    const ref = byHash(page, refHref);
    await expect(ref).toBeInViewport();
    await expect(ref).toHaveAttribute("data-highlighted", "true");
    await expect(page).toHaveURL(endsWithHash(refHref));

    await ref.getByRole("link", { name: /↩ 正文/ }).click();
    await expect(cite).toBeInViewport();
  });

  test("目录卡吸顶后不随页面移动", async ({ page }) => {
    await openFullText(page, FULLTEXT_ID);
    const toc = page.getByRole("navigation", { name: "目录" });
    const topAfterScrolling = async (y: number) => {
      await page.evaluate((scrollY) => window.scrollTo({ top: scrollY, behavior: "instant" }), y);
      return (await toc.boundingBox())?.y;
    };
    const start = await page.evaluate(
      () =>
        (document.getElementById("fulltext")?.getBoundingClientRect().top ?? 0) + window.scrollY,
    );
    expect(await topAfterScrolling(start + 400)).toBe(134);
    expect(await topAfterScrolling(start + 2400)).toBe(134);
  });

  test("图片放大层：打开、Esc 关闭、焦点回到原图", async ({ page }) => {
    await openFullText(page, FULLTEXT_ID);
    const zoom = page.getByRole("button", { name: /^放大查看/ }).first();
    await zoom.scrollIntoViewIfNeeded();
    await zoom.click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole("link", { name: /在新标签打开原图/ })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(zoom).toBeFocused();
  });

  test("下载 PDF 经门户同源地址，响应是附件形式的 PDF", async ({ page }) => {
    await openFullText(page, FULLTEXT_ID);
    const link = page.getByRole("link", { name: /下载 PDF/ }).first();
    const href = (await link.getAttribute("href")) ?? "";
    expect(href).toBe(`/api/papers/${FULLTEXT_ID}/pdf`);
    // 只取响应头，不把整份 PDF 读完
    const head = await page.evaluate(async (url) => {
      const controller = new AbortController();
      const res = await fetch(url, { signal: controller.signal });
      const out = {
        status: res.status,
        type: res.headers.get("content-type"),
        disposition: res.headers.get("content-disposition"),
      };
      controller.abort();
      return out;
    }, href);
    expect(head.status).toBe(200);
    expect(head.type).toBe("application/pdf");
    expect(head.disposition).toMatch(/attachment; filename="PMC\d+\.pdf"/);
  });

  test("长表格只显示前 10 行，可展开、收起", async ({ page }) => {
    await openFullText(page, LONG_TABLE_ID);
    const expand = page.getByRole("button", { name: /^展开全部 \d+ 行$/ }).first();
    await expand.scrollIntoViewIfNeeded();
    const total = Number(/(\d+)/.exec((await expand.textContent()) ?? "")?.[1]);
    const table = page
      .locator("table.ft-table")
      .filter({ has: page.locator("tbody tr:nth-child(11)") })
      .first();
    await expect(table.locator("tbody tr:visible")).toHaveCount(10);
    await expand.click();
    await expect(table.locator("tbody tr:visible")).toHaveCount(total);
    await page.getByRole("button", { name: "收起" }).first().click();
    await expect(table.locator("tbody tr:visible")).toHaveCount(10);
  });
});

test.describe("有全文 · 移动", () => {
  test.use({ viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true });

  test("面包屑「目录」打开底部面板，点目录项收起面板并跳到章节", async ({ page }) => {
    await openFullText(page, FULLTEXT_ID);
    await page.getByRole("button", { name: "目录" }).click();
    const sheet = page.getByRole("dialog", { name: "目录" });
    await expect(sheet).toBeVisible();
    const item = sheet.getByRole("link").nth(1);
    const target = (await item.getAttribute("href")) ?? "";
    await item.click();
    await expect(sheet).toBeHidden();
    await expect(byHash(page, target)).toBeInViewport();
    await expect(page).toHaveURL(endsWithHash(target));
  });
});

test("无全文：全文区块说明原因，保留去原文的出口", async ({ page }) => {
  await page.goto(`/papers/${NO_FULLTEXT_ID}`);
  const panel = page.getByRole("heading", { name: "暂无开放全文" });
  const shown = await panel
    .waitFor({ state: "visible", timeout: 15_000 })
    .then(() => true)
    .catch(() => false);
  test.skip(!shown, `文献 ${NO_FULLTEXT_ID} 不是「无全文」样例（后端不可达或样例数据已变）`);
  const article = page.getByRole("article", { name: "全文" });
  await expect(article.getByRole("link", { name: /去全文/ })).toHaveAttribute("target", "_blank");
  await expect(page.getByRole("link", { name: /阅读全文/ })).toHaveCount(0);
  await expect(page.getByRole("navigation", { name: "目录" })).toHaveCount(0);
});
