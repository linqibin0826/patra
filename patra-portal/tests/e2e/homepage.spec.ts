import { expect, test } from "@playwright/test";

test("homepage smoke — 6 区块 + Hero composer + AI 速读", async ({ page }) => {
  await page.goto("/");

  // TopNav
  await expect(page.getByRole("banner")).toBeVisible();

  // Hero h1
  await expect(page.getByRole("heading", { level: 1 })).toContainText("可被检索");

  // Composer input
  await expect(page.getByPlaceholder(/JAK1|PMID|DOI|Topol/)).toBeVisible();

  // TopicCloud
  await expect(page.getByText("此刻热议")).toBeVisible();
  await expect
    .poll(async () => page.locator("[data-section='topic-cloud'] button").count())
    .toBeGreaterThanOrEqual(4);

  // Journals
  await expect
    .poll(async () => page.locator("[data-section='journals'] button").count())
    .toBeGreaterThanOrEqual(3);

  // ExploreFeed AI 速读
  await expect(page.getByText("AI 速读").first()).toBeVisible();

  // Footer
  await expect(page.getByRole("contentinfo")).toBeVisible();
});
