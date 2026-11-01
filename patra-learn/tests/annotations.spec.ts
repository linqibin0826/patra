// patra-learn/tests/annotations.spec.ts
// 读者批注：在生产构建上选中正文 → 记「有问题」→ 刷新后挂回原处 → 我的批注页导出给 Claude；手机宽度写批注用底部抽屉
import { expect, type Page, test } from "@playwright/test";

const ENTRY = "/entries/spring-modulith-event-publication-registry";
const PARAGRAPH = "Modulith 推荐的监听写法是";

/// 三击选中一整段（真实的鼠标选区，工具条靠 pointerup 弹出）
async function selectParagraph(page: Page) {
  await page.locator("article p", { hasText: PARAGRAPH }).first().click({ clickCount: 3 });
}

test.use({ permissions: ["clipboard-read", "clipboard-write"] });

test("桌面：选中 → 有问题 → 保存；刷新后还在；导出给 Claude", async ({ page }) => {
  await page.goto(ENTRY);
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();
  await expect(page.locator("article [data-annotation-ui]")).toHaveCount(0);

  await selectParagraph(page);
  const bar = page.getByRole("toolbar", { name: "批注选中的文字" });
  await expect(bar).toBeVisible();
  await bar.getByRole("button", { name: "有问题" }).click();
  const dialog = page.getByRole("dialog", { name: "写批注" });
  await expect(dialog.getByLabel("哪里不对，或者哪里没看懂？")).toBeFocused();
  await dialog.getByLabel("哪里不对，或者哪里没看懂？").fill("三个注解各管什么？");
  await dialog.getByRole("button", { name: "保存" }).click();

  const badge = page.getByRole("button", { name: "查看批注：有问题" });
  await expect(badge).toBeVisible();
  await expect(page.getByRole("link", { name: "批注，1 条" })).toBeVisible();

  await page.reload();
  await page.getByRole("button", { name: "查看批注：有问题" }).click();
  await expect(page.getByRole("dialog", { name: "查看批注" })).toContainText("三个注解各管什么？");

  await page.getByRole("link", { name: "批注，1 条" }).click();
  await expect(page).toHaveURL(/\/notes$/);
  await page.getByRole("button", { name: "导出给 Claude · 1 条" }).click();
  const preview = page.getByTestId("export-preview");
  await expect(preview).toContainText(
    "文件：patra-learn/content/entries/spring-modulith-event-publication-registry.mdx",
  );
  await expect(preview).toContainText("我的问题：三个注解各管什么？");
  await page.getByRole("button", { name: "复制" }).click();
  await expect(page.getByRole("status")).toContainText("已复制 1 条");
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain("三个注解各管什么？");
  await expect(page.getByText(/导出过$/)).toBeVisible();
});

test("中等宽度（侧栏折到下面）：同一行两个徽章也不横向溢出", async ({ page }) => {
  await page.setViewportSize({ width: 800, height: 900 });
  await page.goto(ENTRY);
  await page.evaluate(() => {
    const quote = (exact: string) => ({ exact, prefix: "", suffix: "" });
    const slug = "spring-modulith-event-publication-registry";
    window.localStorage.setItem(
      "patra-learn.annotations",
      JSON.stringify([
        { id: "a1", slug, kind: "note", text: "", quote: quote("推荐的监听写法"), createdAt: 1 },
        { id: "a2", slug, kind: "question", text: "", quote: quote("Modulith 推荐"), createdAt: 1 },
      ]),
    );
  });
  await page.reload();
  await expect(page.getByRole("button", { name: /查看批注/ })).toHaveCount(2);
  const [scroll, inner] = await page.evaluate(() => [
    document.documentElement.scrollWidth,
    window.innerWidth,
  ]);
  expect(scroll).toBeLessThanOrEqual(inner);
});

test("手机：写批注用底部抽屉，页面不横向溢出", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(ENTRY);
  await page.evaluate(() => window.localStorage.clear());
  await page.reload();

  await selectParagraph(page);
  await page
    .getByRole("toolbar", { name: "批注选中的文字" })
    .getByRole("button", { name: "记笔记" })
    .click();
  const sheet = page.getByRole("dialog", { name: "写批注" });
  const box = await sheet.boundingBox();
  expect(box && Math.round(box.y + box.height)).toBe(844);
  expect(box?.width).toBe(390);
  await sheet.getByLabel("用你自己的话记下来").fill("三个注解的组合");
  await sheet.getByRole("button", { name: "保存" }).click();
  await expect(page.getByRole("region", { name: "这一篇的批注" })).toContainText("三个注解的组合");

  const [scroll, inner] = await page.evaluate(() => [
    document.documentElement.scrollWidth,
    window.innerWidth,
  ]);
  expect(scroll).toBeLessThanOrEqual(inner);
});
