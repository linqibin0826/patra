import { expect, test } from "@playwright/test";
import { ROUND_TRIP, visit } from "@tests/e2e/support/account";
import { pickPaper } from "@tests/e2e/support/discussion";
import { signedIn, type Verified } from "@tests/e2e/support/helpful";

/// v0.18 阅读历史、阅读记录设置与隐私政策（PAP-174）冒烟：连 mini 上的后端跑（内联 PATRA_GATEWAY_BASE_URL），
/// 要等 patra-analytics 部署之后。测试账号 C 的凭据在 ~/.patra/secrets/e2e.env，不进仓库。跑完把 C 的阅读记录开回去
const C: Verified = {
  email: process.env.PATRA_E2E_VERIFIED_C_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_C_PASSWORD ?? "",
};
const DESKTOP = { width: 1440, height: 900 };

test("C 关闭阅读记录后打开文献，阅读历史里没有这篇；清空后历史为空", async ({
  browser,
  request,
}) => {
  test.skip(!C.email, "没有测试账号 C 的凭据");
  const page = await signedIn(browser, C, DESKTOP, "/settings/privacy#reading");
  const recording = page.getByRole("switch", { name: /记录我的阅读历史/ });
  try {
    if (await recording.isChecked()) {
      await recording.click();
      await expect(page.getByText("已保存")).toBeVisible(ROUND_TRIP);
    }
    const paperId = await pickPaper(request);
    await visit(page, `/papers/${paperId}`);
    await visit(page, "/history");
    await expect(page.getByText("阅读记录已关闭").first()).toBeVisible(ROUND_TRIP);
    await expect(page.locator(`a[href="/papers/${paperId}"]`)).toHaveCount(0);

    const clear = page.getByRole("button", { name: "清空历史" });
    if (await clear.isEnabled()) {
      await clear.click();
      await page.getByRole("dialog").getByRole("button", { name: "清空" }).click();
      await expect(page.getByRole("heading", { name: "阅读历史已清空" })).toBeVisible(ROUND_TRIP);
    }
  } finally {
    await visit(page, "/settings/privacy#reading");
    if (!(await recording.isChecked())) {
      await recording.click();
      await expect(page.getByText("已保存")).toBeVisible(ROUND_TRIP);
    }
    await page.context().close();
  }
});

test("隐私政策从页脚可达", async ({ page }) => {
  await visit(page, "/");
  await page.getByRole("contentinfo").getByRole("link", { name: "隐私政策" }).first().click();
  await expect(page).toHaveURL(/\/privacy$/, ROUND_TRIP);
  await expect(page.getByRole("heading", { level: 1, name: "隐私政策" })).toBeVisible();
});

for (const path of ["/privacy", "/history"]) {
  test(`375 宽下 ${path} 不横向溢出`, async ({ browser }) => {
    test.skip(path === "/history" && !C.email, "没有测试账号 C 的凭据");
    const viewport = { width: 375, height: 812 };
    const page =
      path === "/history"
        ? await signedIn(browser, C, viewport, path)
        : await (await browser.newContext({ viewport })).newPage();
    await visit(page, path);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
    await page.context().close();
  });
}
