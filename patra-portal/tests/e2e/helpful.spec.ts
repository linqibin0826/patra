import { expect, test } from "@playwright/test";
import { freshAccount, ROUND_TRIP, register, waitForTakeover } from "@tests/e2e/support/account";
import {
  comments,
  commentWith,
  deleteOwn,
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
import {
  helpfulButton,
  orderOf,
  signedIn,
  toggleHelpful,
  usernameOf,
  type Verified,
} from "@tests/e2e/support/helpful";

/// v0.15「有帮助」与两处排序（PAP-134，release spec D4、D5）：连 mini 上的后端跑。测试账号 C 写评论与经验（也用它看
/// 「自己的内容」），测试账号 B 点有帮助（不能给自己点，所以要两个已验证邮箱的账号，凭据在 ~/.patra/secrets/e2e.env，
/// 不进仓库）。不用账号 A：它是用户本人的真实账号，经验会出现在他的公开主页上。每跑一次 C 写 4 条经验（每天上限 10 条，
/// 删除不退），同一天最多完整跑两次；邮箱未验证用现注册的新账号，
/// 未登录用没有会话的上下文。两种尺寸各跑一遍完整流程，各自写、各自删。每天 300 次上限与账号状态异常不在这里测
const AUTHOR: Verified = {
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

/// 两条经验：投稿月份不同（同刊同投稿日期只能一条），较早的那条被 B 点
const OLDER: { month: string; input: ExperienceInput } = {
  month: "2025 年 3 月投稿",
  input: {
    submittedOn: "2025-03-03",
    firstDecisionOn: "2025-03-20",
    firstDecision: "直接拒稿",
    articleType: "综述",
  },
};
const NEWER: { month: string; input: ExperienceInput } = {
  month: "2025 年 5 月投稿",
  input: {
    submittedOn: "2025-05-05",
    firstDecisionOn: "2025-05-26",
    firstDecision: "直接拒稿",
    articleType: "综述",
  },
};

test.describe("有帮助与排序", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过有帮助冒烟");
  test.skip(
    !AUTHOR.email || !AUTHOR.password || !B.email || !B.password,
    "没有两个已验证邮箱的测试账号（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_C_* 与 PATRA_E2E_VERIFIED_B_*）",
  );
  test.describe.configure({ mode: "serial" });

  for (const { name, viewport, phone } of VIEWPORTS) {
    test.describe(name, () => {
      test.use({ viewport, actionTimeout: 20_000 });

      test("讨论区：点、刷新保持、取消、最有帮助、自己的内容、未登录、未验证、占位", async ({
        browser,
        request,
      }) => {
        test.setTimeout(240_000);
        const tag = `${Date.now().toString(36)}-${phone ? "m" : "d"}`;
        const ROOT1 = `有帮助冒烟根评论一 ${tag}`;
        const REPLY = `有帮助冒烟回复 ${tag}`;
        const ROOT2 = `有帮助冒烟根评论二 ${tag}`;
        const paperId = await pickPaper(request);
        const a = await signedIn(browser, AUTHOR, viewport);
        let section = await openDiscussion(a, paperId);
        try {
          await postRoot(section, ROOT1);
          await expect(commentWith(section, ROOT1)).toBeVisible(ROUND_TRIP);
          let since = Date.now();
          await waitSince(a, since, PUBLISH_GAP_MS);
          await replyTo(section, commentWith(section, ROOT1), REPLY);
          await expect(commentWith(section, REPLY)).toBeVisible(ROUND_TRIP);
          since = Date.now();
          await waitSince(a, since, PUBLISH_GAP_MS);
          await postRoot(section, ROOT2);
          await expect(commentWith(section, ROOT2)).toBeVisible(ROUND_TRIP);

          // B 点根评论一与回复：数字立刻变、已点
          const b = await signedIn(browser, B, viewport);
          let other = await openDiscussion(b, paperId);
          await waitForTakeover(b, "#discussion article button");
          await toggleHelpful(commentWith(other, ROOT1));
          await expect(helpfulButton(commentWith(other, ROOT1))).toHaveAccessibleName(
            "有帮助，1 人觉得有帮助",
          );
          await expect(helpfulButton(commentWith(other, ROOT1))).toHaveAttribute(
            "aria-pressed",
            "true",
          );
          await toggleHelpful(commentWith(other, REPLY));
          await expect(helpfulButton(commentWith(other, REPLY))).toHaveAccessibleName(
            "有帮助，1 人觉得有帮助",
          );
          if (phone) {
            const box = await helpfulButton(commentWith(other, ROOT1)).boundingBox();
            expect(box?.height ?? 0).toBeGreaterThanOrEqual(32);
          }

          // 刷新后保持
          other = await openDiscussion(b, paperId);
          await expect(helpfulButton(commentWith(other, ROOT1))).toHaveAttribute(
            "aria-pressed",
            "true",
          );
          await expect(helpfulButton(commentWith(other, REPLY))).toHaveAccessibleName(
            "有帮助，1 人觉得有帮助",
          );

          // 最新：根评论二在前；最有帮助：根评论一在前，地址带 sort=helpful，楼内回复仍在根评论一之后
          const sort = other.getByRole("group", { name: "排序" });
          await sort.getByRole("button", { name: "最新" }).click();
          await expect
            .poll(async () => {
              const [one = -1, two = -1] = await orderOf(comments(other), [ROOT1, ROOT2]);
              return one > two;
            }, ROUND_TRIP)
            .toBe(true);
          await sort.getByRole("button", { name: "最有帮助" }).click();
          await expect(b).toHaveURL(/sort=helpful/);
          await expect
            .poll(async () => {
              const [one = -1, reply = -1, two = -1] = await orderOf(comments(other), [
                ROOT1,
                REPLY,
                ROOT2,
              ]);
              return one < reply && reply < two;
            }, ROUND_TRIP)
            .toBe(true);
          if (phone) {
            const ys = await Promise.all(
              (await sort.getByRole("button").all()).map(
                async (button) => (await button.boundingBox())?.y,
              ),
            );
            expect(new Set(ys).size).toBe(1);
          }

          // 取消回复的
          await toggleHelpful(commentWith(other, REPLY));
          await expect(helpfulButton(commentWith(other, REPLY))).toHaveAccessibleName("有帮助");

          // 分享同一地址：新页面仍是最有帮助。放在 B 的最后一步：next dev 给新页面编译时会让其他标签页整页刷新，
          // 刷新途中的点击会丢掉
          const shared = await b.context().newPage();
          await shared.goto(b.url(), { timeout: 60_000 });
          await expect(
            shared.locator("#discussion").getByRole("button", { name: "最有帮助" }),
          ).toHaveAttribute("aria-pressed", "true", ROUND_TRIP);
          await shared.close();

          await b.context().close();

          // 作者 C 看自己的：只有人数，没有按钮
          section = await openDiscussion(a, paperId);
          await expect(commentWith(section, ROOT1)).toContainText("1 人觉得有帮助", ROUND_TRIP);
          await expect(helpfulButton(commentWith(section, ROOT1))).toHaveCount(0);

          // 未登录：点了去登录
          const anonContext = await browser.newContext({ viewport });
          const anon = await anonContext.newPage();
          const anonSection = await openDiscussion(anon, paperId);
          await waitForTakeover(anon, "#discussion article button");
          await helpfulButton(commentWith(anonSection, ROOT1)).click();
          await expect(anon).toHaveURL(/\/login\?next=/, ROUND_TRIP);
          await anonContext.close();

          // 邮箱未验证：前端拦下，数字不变
          const freshContext = await browser.newContext({ viewport });
          const fresh = await freshContext.newPage();
          await register(fresh, freshAccount());
          const freshSection = await openDiscussion(fresh, paperId);
          await waitForTakeover(fresh, "#discussion article button");
          await helpfulButton(commentWith(freshSection, ROOT1)).click();
          // 重新发送按钮自己也有一个 status，这里按文字找提示
          await expect(
            commentWith(freshSection, ROOT1).getByText("验证邮箱后才能点有帮助", { exact: false }),
          ).toBeVisible();
          await expect(helpfulButton(commentWith(freshSection, ROOT1))).toHaveAccessibleName(
            "有帮助，1 人觉得有帮助",
          );
          await freshContext.close();

          // 作者 C 删掉有回复的根评论一：变成占位，没有按钮与数字
          await deleteOwn(commentWith(section, ROOT1));
          const placeholder = section.getByText("该评论已删除");
          await expect(placeholder).toBeVisible(ROUND_TRIP);
          await expect(placeholder.locator("..")).not.toContainText("有帮助");
        } finally {
          // 收尾：C 删回复（占位祖先随之去掉）与根评论二；根评论一若还在也删
          section = await openDiscussion(a, paperId);
          for (const body of [REPLY, ROOT2, ROOT1]) {
            try {
              const item = commentWith(section, body);
              if ((await item.count()) > 0) {
                await deleteOwn(item);
              }
            } catch (error) {
              console.error(`删除评论失败（${body}）`, error);
            }
          }
          await a.context().close();
        }
      });

      test("投稿经验：期刊列表点有帮助、两种排序写进地址、经验页、主页", async ({
        browser,
        request,
      }) => {
        test.setTimeout(240_000);
        const journalId = await pickEmptyJournal(request);
        const a = await signedIn(browser, AUTHOR, viewport);
        const postIds: string[] = [];
        try {
          let section = await openExperiences(a, journalId);
          postIds.push(await publishExperience(section, OLDER.input, OLDER.month));
          postIds.push(await publishExperience(section, NEWER.input, NEWER.month));
          const [older] = postIds;
          section = await openExperiences(a, journalId);
          await expect(section.getByText("2 条投稿经验", { exact: true })).toBeVisible(ROUND_TRIP);

          // B 在期刊页给较早的那条点：数字立刻变，点按钮不进经验页
          const b = await signedIn(browser, B, viewport);
          let list = await openExperiences(b, journalId);
          const row = (month: string) => list.getByRole("article").filter({ hasText: month });
          await toggleHelpful(row(OLDER.month));
          await expect(helpfulButton(row(OLDER.month))).toHaveAccessibleName(
            "有帮助，1 人觉得有帮助",
          );
          await expect(b).toHaveURL(new RegExp(`/journals/${journalId}`));

          // 最新投稿：较新的在前；最有帮助：被点的在前，地址带 sort=helpful，刷新保持
          const rows = list.getByRole("article");
          const olderFirst = async () => {
            const [one = -1, two = -1] = await orderOf(rows, [OLDER.month, NEWER.month]);
            return one < two;
          };
          expect(await olderFirst()).toBe(false);
          await list.getByRole("button", { name: "最有帮助" }).click();
          await expect(b).toHaveURL(/sort=helpful/);
          // 换排序要等一次 Route Handler；next dev 冷编译时可能超过 15 秒
          await expect.poll(olderFirst, { timeout: 60_000 }).toBe(true);
          await b.reload({ timeout: 60_000 });
          list = b.locator("#experiences");
          await expect(list.getByRole("button", { name: "最有帮助" })).toHaveAttribute(
            "aria-pressed",
            "true",
            ROUND_TRIP,
          );

          // 经验页：胶囊形按钮已点
          await b.goto(`/posts/${older}`, { timeout: 60_000 });
          const pill = helpfulButton(b.getByRole("article").first());
          await expect(pill).toHaveAttribute("aria-pressed", "true", ROUND_TRIP);
          await expect(pill).toHaveAccessibleName("有帮助，1 人觉得有帮助");
          if (phone) {
            expect((await pill.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);
          }
          await b.context().close();

          // C 的主页：自己的经验只有人数
          await a.goto(`/u/${await usernameOf(a)}/experiences`, { timeout: 60_000 });
          const mine = a
            .getByRole("article")
            .filter({ has: a.locator(`a[href="/posts/${older}"]`) });
          await expect(mine).toContainText("1 人觉得有帮助", ROUND_TRIP);
          await expect(helpfulButton(mine)).toHaveCount(0);
        } finally {
          for (const postId of postIds) {
            try {
              await deletePost(a, postId);
            } catch (error) {
              console.error(`删除经验 ${postId} 失败`, error);
            }
          }
          await a.context().close();
        }
      });
    });
  }
});
