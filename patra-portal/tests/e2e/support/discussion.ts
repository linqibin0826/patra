import { type APIRequestContext, expect, type Locator, type Page } from "@playwright/test";
import { ROUND_TRIP, visit, waitForTakeover } from "@tests/e2e/support/account";

/// 两条评论至少间隔 10 秒（release spec 2.1.1），多等半秒防边界
export const PUBLISH_GAP_MS = 10_500;
/// 发布 5 分钟内的修改不记版本（后端 spec 5.5），多等 15 秒防时钟偏差
export const REVISION_GRACE_MS = 5 * 60_000 + 15_000;

/// 从 catalog 随机取一篇文献（不用固定 ID，讨论不会在同一篇上越积越多）
export async function pickPaper(request: APIRequestContext): Promise<string> {
  const gateway = process.env.PATRA_GATEWAY_BASE_URL?.replace(/\/+$/, "");
  const page = 1 + Math.floor(Math.random() * 500);
  const res = await request.get(`${gateway}/patra-catalog/portal/publications?page=${page}`);
  const body = (await res.json()) as { items: { id: string }[] };
  const first = body.items[0];
  if (!first) {
    throw new Error("catalog 没有返回文献");
  }
  return first.id;
}

/// 打开文献页并滚到讨论区，等它接管
export async function openDiscussion(page: Page, paperId: string, query = ""): Promise<Locator> {
  await visit(page, `/papers/${paperId}${query}`);
  const section = page.locator("#discussion");
  await section.scrollIntoViewIfNeeded();
  await expect(section.getByRole("heading", { name: /讨论 · Discussion/i })).toBeVisible(
    ROUND_TRIP,
  );
  return section;
}

/// 讨论区里的评论条目
export function comments(section: Locator): Locator {
  return section.getByRole("article");
}

/// 正文含 `text` 的那条评论
export function commentWith(section: Locator, text: string): Locator {
  return comments(section).filter({ hasText: text });
}

/// 在顶部编辑器发表一条根评论（收起态先点开）
export async function postRoot(section: Locator, body: string): Promise<void> {
  const box = await openComposer(section, "写下你对这篇文献的看法、疑问或补充证据…");
  await box.fill(body);
  await section.getByRole("button", { name: "发表", exact: true }).click();
}

/// 打开讨论区顶部的编辑器，返回接管好的输入框。编辑器默认收起成一个按钮（文案 `placeholder`），也可能已经展开：
/// 先**等到**按钮或输入框之一出现再判断要不要点开——页面刚打开时两者都还没渲染，这时只看一眼就会跳过点击，
/// 之后再等输入框就永远等不到
export async function openComposer(section: Locator, placeholder: string): Promise<Locator> {
  const collapsed = section.getByRole("button", { name: placeholder });
  const box = section.getByRole("combobox", { name: "评论内容" }).first();
  await expect(collapsed.or(box).first()).toBeVisible(ROUND_TRIP);
  if (await collapsed.isVisible()) {
    await collapsed.click();
  }
  await waitForTakeover(section.page(), '#discussion [role="combobox"]');
  return box;
}

/// 点某条评论的「回复」，在楼里的回复框里写好并提交
export async function replyTo(section: Locator, target: Locator, body: string): Promise<void> {
  await target.getByRole("button", { name: "回复", exact: true }).click();
  const composer = section.locator("[data-composer]");
  await composer.getByRole("combobox", { name: "评论内容" }).fill(body);
  await composer.getByRole("button", { name: "回复", exact: true }).click();
}

/// 打开某条评论的 `···` 菜单并点一项
export async function commentMenu(target: Locator, item: string): Promise<void> {
  await target.getByRole("button", { name: "评论操作" }).click();
  await target.page().getByRole("menuitem", { name: item }).click();
}

/// 删除自己的一条评论（确认对话框里点「删除评论」，等对话框关掉）
export async function deleteOwn(target: Locator): Promise<void> {
  await commentMenu(target, "删除");
  const dialog = target.page().getByRole("dialog", { name: "删除这条评论？" });
  await dialog.getByRole("button", { name: "删除评论" }).click();
  await expect(dialog).toBeHidden(ROUND_TRIP);
}

/// 距离 `since` 不足 `gap` 毫秒时等到够
export async function waitSince(page: Page, since: number, gap: number): Promise<void> {
  const left = since + gap - Date.now();
  if (left > 0) {
    await page.waitForTimeout(left);
  }
}
