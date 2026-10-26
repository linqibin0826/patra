import { expect, type Locator, type Page } from "@playwright/test";
import { ROUND_TRIP, waitForTakeover } from "@tests/e2e/support/account";

/// 还没收藏的收藏按钮（读屏名「收藏「标题」」）
export function unsavedButtons(scope: Page | Locator): Locator {
  return scope.getByRole("button", { name: /^收藏「/ });
}

/// 点一个收藏按钮：等页面接管、我的状态回来（按钮不再 aria-disabled），点下去，等浮层「已收进文献库」，Esc 关掉。
/// 返回读屏名里的标题前 40 字
export async function save(page: Page, button: Locator): Promise<string> {
  await expect(button).not.toHaveAttribute("aria-disabled", "true", ROUND_TRIP);
  const name = (await button.getAttribute("aria-label")) ?? "";
  await button.click();
  await expect(page.getByText("已收进文献库")).toBeVisible(ROUND_TRIP);
  await page.keyboard.press("Escape");
  return name.replace(/^收藏「/, "").replace(/」$/, "");
}

/// 打开文献库「全部」，等列表出来
export async function openLibrary(page: Page, path = "/library") {
  await page.goto(path);
  await waitForTakeover(page, "main");
  await expect(page.getByRole("heading", { level: 1, name: "我的文献库" })).toBeVisible();
}

/// 文献库里的条目
export function libraryItems(page: Page): Locator {
  return page.getByRole("list", { name: "文献库条目" }).getByRole("article");
}
