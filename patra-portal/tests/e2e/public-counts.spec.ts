import { type APIRequestContext, expect, type Locator, type Page, test } from "@playwright/test";
import { ROUND_TRIP, visit, waitForTakeover } from "@tests/e2e/support/account";
import {
  commentWith,
  deleteOwn,
  openComposer,
  openDiscussion,
  PUBLISH_GAP_MS,
  pickPaper,
  postRoot,
  replyTo,
  waitSince,
} from "@tests/e2e/support/discussion";
import {
  deletePost,
  type ExperienceInput,
  openExperiences,
  pickEmptyJournal,
  publishExperience,
} from "@tests/e2e/support/experience";
import { signedIn, toggleHelpful, type Verified } from "@tests/e2e/support/helpful";
import { libraryItems, openLibrary, save } from "@tests/e2e/support/library";

/// v0.15 公开计数（PAP-135，release spec D6，验证场景 1–4 的计数部分）：连 mini 上的后端跑。测试账号 C 写根评论、
/// 回复经验并点有帮助，测试账号 B 回复、收藏、写经验（凭据在 ~/.patra/secrets/e2e.env，不进仓库）。不用账号 A：它是
/// 用户本人的真实账号。文献随机、账号已有数据，所有计数都相对操作前从接口读到的基线断言。每跑一次 B 写 2 条经验
/// （每天上限 10 条，删除不退）。收藏夹、文献库的准备与收尾直接调 interaction（只做准备，断言都看页面）
const C: Verified = {
  email: process.env.PATRA_E2E_VERIFIED_C_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_C_PASSWORD ?? "",
};
const B: Verified = {
  email: process.env.PATRA_E2E_VERIFIED_B_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_B_PASSWORD ?? "",
};

const VIEWPORTS = [
  { name: "桌面 1440", viewport: { width: 1440, height: 900 }, phone: false },
  { name: "手机 375", viewport: { width: 375, height: 812 }, phone: true },
] as const;

const EXPERIENCE: { month: string; input: ExperienceInput } = {
  month: "2025 年 4 月投稿",
  input: {
    submittedOn: "2025-04-04",
    firstDecisionOn: "2025-04-24",
    firstDecision: "直接拒稿",
    articleType: "综述",
  },
};

const gateway = () => process.env.PATRA_GATEWAY_BASE_URL?.replace(/\/+$/, "") ?? "";

interface Stats {
  collectorCount: number;
  discussionCount: number;
}

/// 一篇文献现在的公开计数（不要会话）
async function statsOf(request: APIRequestContext, key: string): Promise<Stats> {
  const res = await request.post(`${gateway()}/patra-interaction/subjects/stats`, {
    data: { type: "publication", keys: [key] },
  });
  const body = (await res.json()) as { items: Stats[] };
  const [stats] = body.items;
  if (!stats) {
    throw new Error(`没有 ${key} 的计数`);
  }
  return stats;
}

/// 用户现在的获赞（不要会话）
async function helpfulReceivedOf(request: APIRequestContext, userId: string): Promise<number> {
  const res = await request.get(`${gateway()}/patra-interaction/users/${userId}/helpful-received`);
  return ((await res.json()) as { helpfulReceived: number }).helpfulReceived;
}

/// 当前登录用户（门户自己的 `/api/auth/me`）
async function meOf(page: Page): Promise<{ userId: string; username: string }> {
  const body = (await (await page.request.get("/api/auth/me")).json()) as {
    user: { userId: string; username: string } | null;
  };
  if (!body.user) {
    throw new Error("没有登录");
  }
  return body.user;
}

/// 以这个页面的会话直接调 interaction：只做准备与收尾。本机是 HTTP，会话 Cookie 不带 `__Host-` 前缀
async function asUser<T>(page: Page, method: string, path: string, data?: unknown): Promise<T> {
  const token = (await page.context().cookies()).find((c) => c.name === "patra_session")?.value;
  const res = await page.request.fetch(`${gateway()}/patra-interaction${path}`, {
    method,
    headers: { Authorization: `Bearer ${token ?? ""}` },
    ...(data === undefined ? {} : { data }),
  });
  if (!res.ok()) {
    throw new Error(`${method} ${path} → ${res.status()}`);
  }
  return (res.status() === 204 ? null : await res.json()) as T;
}

/// 收进文献库（幂等），返回条目 ID
async function collect(page: Page, key: string): Promise<string> {
  const body = await asUser<{ item: { itemId: string } }>(
    page,
    "PUT",
    `/me/library/publication/${key}`,
  );
  return String(body.item.itemId);
}

/// 从文献库移除（幂等）
async function uncollect(page: Page, key: string): Promise<void> {
  const itemId = await collect(page, key);
  await asUser(page, "DELETE", `/me/library/items?itemIds=${itemId}`);
}

/// 随机找一篇标题够长、没有内联标记的文献，返回 ID 与检索词（标题开头连续的一段，在词边界截断）。检索页按 PMID 精确
/// 定位会直接跳到文献页、看不到结果列表，所以用标题检索
async function pickPaperWithTitle(request: APIRequestContext): Promise<{ id: string; q: string }> {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const page = 1 + Math.floor(Math.random() * 500);
    const res = await request.get(`${gateway()}/patra-catalog/portal/publications?page=${page}`);
    const body = (await res.json()) as { items: { id: string; title: string }[] };
    const found = body.items.find((item) => item.title.length >= 40 && !/[<>]/.test(item.title));
    if (found) {
      const head = found.title.slice(0, 60);
      return { id: found.id, q: head.slice(0, head.lastIndexOf(" ")) };
    }
  }
  throw new Error("找不到标题够长的文献");
}

/// 事实条某一格的值；这一格不出现时是 null
async function factOf(page: Page, term: string): Promise<string | null> {
  const dt = page.locator("#masthead dt").filter({ hasText: new RegExp(`^${term}$`) });
  return (await dt.count()) === 0
    ? null
    : ((await dt.locator("xpath=following-sibling::dd[1]").textContent()) ?? "");
}

/// 「N 人收藏」的写法；0 时这一项不出现
const collectors = (n: number) => `${n.toLocaleString("en-US")} 人收藏`;

/// 讨论区标题旁的条数
function titleOf(section: Locator, title = "讨论 · Discussion"): Locator {
  return section.getByRole("heading", { name: title }).locator("..");
}

test.describe("公开计数", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过公开计数冒烟");
  test.skip(
    !C.email || !C.password || !B.email || !B.password,
    "没有两个已验证邮箱的测试账号（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_C_* 与 PATRA_E2E_VERIFIED_B_*）",
  );
  test.describe.configure({ mode: "serial" });

  for (const { name, viewport, phone } of VIEWPORTS) {
    test.describe(name, () => {
      test.use({ viewport, actionTimeout: 20_000 });

      test("讨论：标题条数、楼内统计、展开其余回复、删回复回退、文献页讨论格", async ({
        browser,
        request,
      }) => {
        test.setTimeout(300_000);
        const tag = `${Date.now().toString(36)}-${phone ? "m" : "d"}`;
        const ROOT = `计数冒烟根评论 ${tag}`;
        const REPLIES = [1, 2, 3, 4].map((n) => `计数冒烟回复${n} ${tag}`);
        const paperId = await pickPaper(request);
        const base = (await statsOf(request, paperId)).discussionCount;
        const c = await signedIn(browser, C, viewport);
        const b = await signedIn(browser, B, viewport);
        const posted: string[] = [];
        let rootPosted = false;
        try {
          let section = await openDiscussion(c, paperId);
          await postRoot(section, ROOT);
          await expect(commentWith(section, ROOT)).toBeVisible(ROUND_TRIP);
          rootPosted = true;
          await expect(titleOf(section)).toContainText(`${base + 1} 条`);

          // B 回复：本人发表后楼内统计与条数立刻变，最后回复是「刚刚」
          let other = await openDiscussion(b, paperId);
          let since = 0;
          for (const reply of REPLIES) {
            await waitSince(b, since, PUBLISH_GAP_MS);
            await replyTo(other, commentWith(other, ROOT), reply);
            await expect(commentWith(other, reply)).toBeVisible(ROUND_TRIP);
            since = Date.now();
            posted.push(reply);
            if (posted.length === 1) {
              await expect(commentWith(other, ROOT).first()).toContainText(
                "1 条回复 · 最后回复 刚刚",
              );
              await expect(titleOf(other)).toContainText(`${base + 2} 条`);
            }
          }

          // C 重新打开：服务端的统计；第 4 条起收起，按钮带剩余条数，展开后按钮消失
          section = await openDiscussion(c, paperId);
          await expect(commentWith(section, ROOT).first()).toContainText(/4 条回复 · 最后回复 /);
          await expect(titleOf(section)).toContainText(`${base + 5} 条`);
          const more = section.getByRole("button", { name: "展开其余 1 条回复" });
          await expect(more).toBeVisible(ROUND_TRIP);
          await more.click();
          await expect(commentWith(section, REPLIES[3] ?? "")).toBeVisible(ROUND_TRIP);
          await expect(section.getByRole("button", { name: /展开/ })).toHaveCount(0);

          // 文献页事实条的讨论格跟着讨论区
          await expect.poll(() => factOf(c, "讨论")).toBe(`${base + 5} 条`);

          // B 删一条回复：楼内回复数与条数回退
          other = await openDiscussion(b, paperId);
          await deleteOwn(commentWith(other, REPLIES[0] ?? ""));
          posted.shift();
          await expect(commentWith(other, ROOT).first()).toContainText("3 条回复");
          await expect(titleOf(other)).toContainText(`${base + 4} 条`);
        } finally {
          // 先等楼渲染出来再数：页面刚打开时列表可能还没到，count() 不等待，会把还在的回复当成没有而漏删
          try {
            const section = await openDiscussion(b, paperId);
            if (posted.length > 0) {
              await expect(commentWith(section, ROOT).first()).toBeVisible(ROUND_TRIP);
              const expand = section.getByRole("button", { name: /展开/ });
              if ((await expand.count()) > 0) {
                await expand.click();
                await expect(expand).toHaveCount(0, ROUND_TRIP);
              }
            }
            for (const reply of posted) {
              const item = commentWith(section, reply);
              if ((await item.count()) === 0) {
                console.error(`删除回复失败：页面上找不到「${reply}」`);
                continue;
              }
              await deleteOwn(item);
            }
          } catch (error) {
            console.error("删除回复失败", error);
          }
          if (rootPosted) {
            try {
              const section = await openDiscussion(c, paperId);
              const root = commentWith(section, ROOT);
              await expect(root.first()).toBeVisible(ROUND_TRIP);
              await deleteOwn(root);
            } catch (error) {
              console.error("删除根评论失败", error);
            }
          }
          await b.context().close();
          await c.context().close();
        }
      });

      test("文献：页头收藏 ±1、检索结果 / 文献库 / 公开收藏夹的卡片计数、放进两个收藏夹只算一次", async ({
        browser,
        request,
      }) => {
        test.setTimeout(240_000);
        const paper = await pickPaperWithTitle(request);
        const b = await signedIn(browser, B, viewport);
        const collections: string[] = [];
        try {
          await uncollect(b, paper.id);
          const base = await statsOf(request, paper.id);

          // 页头收藏：收藏格立刻 +1（0 → 1 时这一格出现），刷新后是服务端的数
          await visit(b, `/papers/${paper.id}`);
          await waitForTakeover(b, "#masthead button");
          await expect
            .poll(() => factOf(b, "收藏"))
            .toBe(base.collectorCount > 0 ? `${base.collectorCount} 人` : null);
          await save(b, b.locator("#masthead").getByRole("button", { name: /^收藏「/ }));
          await expect.poll(() => factOf(b, "收藏")).toBe(`${base.collectorCount + 1} 人`);
          await b.reload({ timeout: 60_000 });
          await expect.poll(() => factOf(b, "收藏")).toBe(`${base.collectorCount + 1} 人`);

          // 放进两个收藏夹（一个公开）：收藏人数仍只多 1
          const itemId = await collect(b, paper.id);
          for (const n of [1, 2]) {
            const created = await asUser<{ collectionId: string }>(b, "POST", "/me/collections", {
              name: `计数冒烟 ${Date.now().toString(36)}-${n}`,
              description: null,
            });
            collections.push(String(created.collectionId));
          }
          for (const id of collections) {
            await asUser(b, "POST", `/me/collections/${id}/items`, { itemIds: [itemId] });
          }
          const [publicId = ""] = collections;
          await asUser(b, "PUT", `/me/collections/${publicId}/visibility`, {
            visibility: "PUBLIC",
          });
          const expected = collectors(base.collectorCount + 1);
          const card = (scope: Locator) =>
            scope.getByRole("article").filter({ has: b.locator(`a[href="/papers/${paper.id}"]`) });

          // 检索结果（按标题词检索）
          await visit(b, `/papers?q=${encodeURIComponent(paper.q)}`);
          await expect(card(b.getByRole("list", { name: "文献列表" }))).toContainText(
            expected,
            ROUND_TRIP,
          );

          // 我的文献库
          await openLibrary(b);
          await expect(
            libraryItems(b).filter({ has: b.locator(`a[href="/papers/${paper.id}"]`) }),
          ).toContainText(expected, ROUND_TRIP);

          // 公开收藏夹
          await visit(b, `/collections/${publicId}`);
          await expect(card(b.getByRole("list", { name: "收藏夹条目" }))).toContainText(
            expected,
            ROUND_TRIP,
          );
        } finally {
          for (const id of collections) {
            await asUser(b, "DELETE", `/me/collections/${id}`).catch((error: unknown) =>
              console.error(`删除收藏夹 ${id} 失败`, error),
            );
          }
          await uncollect(b, paper.id).catch((error: unknown) =>
            console.error("移出文献库失败", error),
          );
          await b.context().close();
        }
      });

      test("首页文献流：卡片计数、本人收藏 +1、窄屏计数在底部行上方", async ({
        browser,
        request,
      }) => {
        test.setTimeout(180_000);
        const feed = await request.get(
          `${gateway()}/patra-catalog/portal/publications?tab=recent&page=1&pageSize=14`,
        );
        const [lead] = ((await feed.json()) as { items: { id: string }[] }).items;
        if (!lead) {
          throw new Error("首页文献流为空");
        }
        const b = await signedIn(browser, B, viewport);
        try {
          await uncollect(b, lead.id);
          const base = await statsOf(request, lead.id);
          await visit(b, "/");
          const card = b
            .locator('[data-section="explore-feed"] article')
            .filter({ has: b.locator(`h3 a[href="/papers/${lead.id}"]`) })
            .first();
          await card.scrollIntoViewIfNeeded();
          await save(b, card.getByRole("button", { name: /^收藏「/ }));
          await expect(card).toContainText(collectors(base.collectorCount + 1));
          const stats = card.getByText(collectors(base.collectorCount + 1));
          const readMore = card.getByRole("link", { name: /阅读详情/ });
          const [statsBox, readBox] = [await stats.boundingBox(), await readMore.boundingBox()];
          if (phone) {
            // 窄屏放不下：计数在底部行上方单独一行
            expect((statsBox?.y ?? 0) + (statsBox?.height ?? 0)).toBeLessThanOrEqual(
              (readBox?.y ?? 0) + 1,
            );
          } else {
            // 宽屏：与「阅读详情」同一行
            expect(Math.abs((statsBox?.y ?? 0) - (readBox?.y ?? 0))).toBeLessThan(
              readBox?.height ?? 24,
            );
          }
        } finally {
          await uncollect(b, lead.id).catch((error: unknown) =>
            console.error("移出文献库失败", error),
          );
          await b.context().close();
        }
      });

      test("投稿经验：经验行回复数（期刊页、主页）与主页获赞", async ({ browser, request }) => {
        test.setTimeout(240_000);
        const tag = `${Date.now().toString(36)}-${phone ? "m" : "d"}`;
        const REPLY = `计数冒烟经验回复 ${tag}`;
        const journalId = await pickEmptyJournal(request);
        const b = await signedIn(browser, B, viewport);
        const c = await signedIn(browser, C, viewport);
        let postId: string | null = null;
        let replied = false;
        try {
          const author = await meOf(b);
          const likes = await helpfulReceivedOf(request, author.userId);
          postId = await publishExperience(
            await openExperiences(b, journalId),
            EXPERIENCE.input,
            EXPERIENCE.month,
          );

          // C 在经验页点有帮助、回复一条
          await visit(c, `/posts/${postId}`);
          // 冷编译时页面还没接管就点会落空：先等按钮可交互
          await waitForTakeover(c, "article button[aria-pressed]");
          await toggleHelpful(c.getByRole("article").first());
          const replies = c.locator("#discussion");
          const box = await openComposer(replies, "对这条投稿经验有疑问？在这里追问…");
          await box.fill(REPLY);
          await replies.getByRole("button", { name: "发表", exact: true }).click();
          await expect(commentWith(replies, REPLY)).toBeVisible(ROUND_TRIP);
          replied = true;
          await expect(titleOf(replies, "回复 · Replies")).toContainText("1 条");

          // 期刊页与 B 的主页：这条经验底部有回复数；主页头部获赞 +1
          const row = (scope: Locator) =>
            scope.getByRole("article").filter({ has: b.locator(`a[href="/posts/${postId}"]`) });
          const list = await openExperiences(b, journalId);
          await expect(row(list)).toContainText("1 条回复", ROUND_TRIP);
          await expect(row(list)).toContainText("1 人觉得有帮助");
          await visit(b, `/u/${author.username}/experiences`);
          await expect(row(b.locator("main"))).toContainText("1 条回复", ROUND_TRIP);
          await visit(b, `/u/${author.username}`);
          await expect(
            b.locator("header").filter({ hasText: `@${author.username}` }),
          ).toContainText(`获赞 ${(likes + 1).toLocaleString("en-US")}`, ROUND_TRIP);
        } finally {
          if (replied && postId) {
            try {
              await visit(c, `/posts/${postId}`);
              await deleteOwn(commentWith(c.locator("#discussion"), REPLY));
            } catch (error) {
              console.error("删除经验回复失败", error);
            }
          }
          if (postId) {
            await deletePost(b, postId).catch((error: unknown) =>
              console.error(`删除经验 ${postId} 失败`, error),
            );
          }
          await c.context().close();
          await b.context().close();
        }
      });
    });
  }
});
