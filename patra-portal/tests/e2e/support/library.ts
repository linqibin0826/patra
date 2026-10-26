import { readFile } from "node:fs/promises";
import {
  type BrowserContext,
  type Download,
  expect,
  type Locator,
  type Page,
} from "@playwright/test";
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

/// 模拟 mini 的 HTTP 环境：剪贴板接口不可用（`writeText` 拒绝），逼复制走 `execCommand` 那一步（简报 §3.8）。
/// 本机 localhost 是安全上下文，不模拟就测不到降级。读剪贴板（`readCopied`）仍走真实的 `readText`
export async function simulateInsecureClipboard(context: BrowserContext): Promise<void> {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await context.addInitScript(() => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText = () =>
        Promise.reject(new DOMException("insecure context", "NotAllowedError"));
    }
  });
}

/// 剪贴板里现在的文字（要先 `simulateInsecureClipboard` 授权读剪贴板）
export function readCopied(page: Page): Promise<string> {
  return page.evaluate(() => navigator.clipboard.readText());
}

/// 下载的文件内容（文本）
export async function downloadedText(download: Download): Promise<string> {
  const path = await download.path();
  return readFile(path, "utf8");
}

/// 门户给出的规范地址（canonical 与「复制链接」），口径同 `src/lib/public-url.ts`：配了 `PATRA_PORTAL_PUBLIC_URL` 用它，
/// 没配时用这次访问的 Host 拼 `http://`
export function publicLink(page: Page, path: string): string {
  const configured = process.env.PATRA_PORTAL_PUBLIC_URL?.trim().replace(/\/+$/, "");
  return `${configured || `http://${new URL(page.url()).host}`}${path}`;
}
