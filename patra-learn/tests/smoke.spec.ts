// patra-learn/tests/smoke.spec.ts
import { expect, test } from "@playwright/test";

test("首页能打开", async ({ page }) => {
  const response = await page.goto("/");
  expect(response?.status()).toBe(200);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Patra 学习站");
});
