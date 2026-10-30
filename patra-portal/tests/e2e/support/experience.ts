import { type APIRequestContext, expect, type Locator, type Page } from "@playwright/test";
import { ROUND_TRIP, visit, waitForTakeover } from "@tests/e2e/support/account";

const gateway = () => process.env.PATRA_GATEWAY_BASE_URL?.replace(/\/+$/, "") ?? "";

/// 从 catalog 期刊列表里随机找一本还没有任何投稿经验的期刊（统计只来自这次写的经验）
export async function pickEmptyJournal(request: APIRequestContext): Promise<string> {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const page = 1 + Math.floor(Math.random() * 1000);
    const res = await request.get(`${gateway()}/patra-catalog/portal/venues?page=${page}`);
    const body = (await res.json()) as { items: { id: string }[] };
    for (const { id } of body.items) {
      const summary = await request.get(
        `${gateway()}/patra-interaction/journals/${id}/posts/summary?window=all`,
      );
      if (((await summary.json()) as { allCount: number }).allCount === 0) {
        return id;
      }
    }
  }
  throw new Error("找不到没有投稿经验的期刊");
}

/// 打开期刊页并滚到经验区，等它接管
export async function openExperiences(page: Page, journalId: string): Promise<Locator> {
  await visit(page, `/journals/${journalId}`);
  const section = page.locator("#experiences");
  await section.scrollIntoViewIfNeeded();
  await expect(section.getByRole("article", { name: "同行经验" })).toBeVisible(ROUND_TRIP);
  await waitForTakeover(page, "#experiences article");
  return section;
}

export interface ExperienceInput {
  submittedOn: string;
  firstDecisionOn: string;
  firstDecision: "直接拒稿" | "审后拒稿" | "大修" | "小修" | "录用";
  finalOutcome?: "录用" | "拒稿" | "撤稿" | "进行中";
  finalDecisionOn?: string;
  articleType: string;
  reviewRounds?: string;
  reviewQuality?: string;
  editorCommunication?: string;
  apcPaid?: "付了" | "没付";
  previouslyRejected?: "是" | "否";
  body?: string;
  anonymous?: boolean;
}

function choose(form: Locator, group: RegExp, option: string): Promise<void> {
  return form.getByRole("radiogroup", { name: group }).getByText(option, { exact: true }).click();
}

/// 填表单（期刊页新建与经验页编辑共用）。首轮终局时最终结果由表单自动选定，不传 `finalOutcome`
export async function fillExperience(form: Locator, input: ExperienceInput): Promise<void> {
  await form.getByLabel(/^投稿日期/).fill(input.submittedOn);
  await form.getByLabel(/^首轮决定日期/).fill(input.firstDecisionOn);
  await choose(form, /首轮结果/, input.firstDecision);
  if (input.finalOutcome) {
    await choose(form, /最终结果/, input.finalOutcome);
  }
  if (input.finalDecisionOn) {
    await form.getByLabel(/^最终决定日期/).fill(input.finalDecisionOn);
  }
  await form.getByLabel(/^文章类型/).selectOption({ label: input.articleType });
  if (input.reviewRounds) {
    await form.getByLabel(/^审稿轮数/).fill(input.reviewRounds);
  }
  if (input.reviewQuality) {
    await choose(form, /审稿质量/, input.reviewQuality);
  }
  if (input.editorCommunication) {
    await choose(form, /编辑沟通/, input.editorCommunication);
  }
  if (input.apcPaid) {
    await choose(form, /是否付了 APC/, input.apcPaid);
  }
  if (input.previouslyRejected) {
    await choose(form, /是否转投过/, input.previouslyRejected);
  }
  if (input.body) {
    await form.getByRole("textbox", { name: "经历" }).fill(input.body);
  }
  if (input.anonymous) {
    await form.getByRole("checkbox", { name: /匿名发布/ }).check();
  }
}

/// 在期刊页写一条并发布，等表单收起；返回新经验的 ID（列表里按投稿月份找到那一行）
export async function publishExperience(
  section: Locator,
  input: ExperienceInput,
  month: string,
): Promise<string> {
  await section.getByRole("button", { name: "写投稿经验" }).click();
  const form = section.getByRole("form", { name: "写投稿经验" });
  await fillExperience(form, input);
  await form.getByRole("button", { name: "发布投稿经验" }).click();
  await expect(form).toBeHidden(ROUND_TRIP);
  // 经验行是 article，链接只是标题行（v0.15 改结构）：按月份文字找行，再取行里的链接
  const row = section
    .getByRole("article")
    .filter({ hasText: new RegExp(month) })
    .first();
  await expect(row).toBeVisible(ROUND_TRIP);
  const href = (await row.getByRole("link").getAttribute("href")) ?? "";
  return href.replace("/posts/", "");
}

/// 经验页上删除自己的一条，等跳走
export async function deletePost(page: Page, postId: string): Promise<void> {
  await page.goto(`/posts/${postId}`, { timeout: 60_000 });
  await waitForTakeover(page, 'button[aria-label="投稿经验操作"]');
  await page.getByRole("button", { name: "投稿经验操作" }).click();
  await page.getByRole("menuitem", { name: "删除" }).click();
  const dialog = page.getByRole("dialog", { name: "删除这条投稿经验？" });
  await dialog.getByRole("button", { name: "删除投稿经验" }).click();
  await expect(page).toHaveURL(/\/journals\//, ROUND_TRIP);
}

/// 线性插值的分位数（与 PostgreSQL `percentile_cont`、Excel `PERCENTILE.INC` 相同），四舍五入到整天
export function percentile(values: readonly number[], fraction: number): number {
  const sorted = [...values].sort((a, b) => a - b);
  const position = fraction * (sorted.length - 1);
  const low = Math.floor(position);
  const high = Math.ceil(position);
  const lowValue = sorted[low] ?? 0;
  const highValue = sorted[high] ?? 0;
  return Math.round(lowValue + (highValue - lowValue) * (position - low));
}

/// 两个日期之间的天数
export function days(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);
}
