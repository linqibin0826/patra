import { expect, type Page, test } from "@playwright/test";
import {
  freshAccount,
  login,
  ROUND_TRIP,
  register,
  waitForTakeover,
} from "@tests/e2e/support/account";
import {
  commentMenu,
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
import {
  bell,
  markAllRead,
  nextUnread,
  openInbox,
  rowWith,
  type Session,
  signIn,
  switchBack,
} from "@tests/e2e/support/notifications";

/// v0.14 提醒（PAP-126，release spec 验证场景 1、4 与 D6）：两个邮箱已验证的账号 A、B 连 mini 上的后端跑，B 兼任场景里的
/// C。两个都是用户在本机环境变量里给的账号（A 是用户的真实账号），不进仓库。另现注册一个邮箱未验证的账号 X：它是
/// 「主页对别人不可见的正常账号」，也用来改用户名。用例最后删掉自己发的评论、三个账号都全部已读；提醒不能删，A、B 的
/// 列表里会留下测试行（保留 5 个月）。场景 2–4 的投稿经验部分另一条用例：写经验用账号 C（专门的测试账号，不占
/// 用户真实账号与 B 的经验额度），B 在 C 的经验下追问并 @C
const A = {
  email: process.env.PATRA_E2E_VERIFIED_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_PASSWORD ?? "",
};
const B = {
  email: process.env.PATRA_E2E_VERIFIED_B_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_B_PASSWORD ?? "",
};
const C = {
  email: process.env.PATRA_E2E_VERIFIED_C_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_C_PASSWORD ?? "",
};

/// 场景 2 的那条经验：3 月投稿，首轮 6 周（42 天）大修，之后录用
const EXPERIENCE: ExperienceInput = {
  submittedOn: "2026-03-02",
  firstDecisionOn: "2026-04-13",
  firstDecision: "大修",
  finalOutcome: "录用",
  finalDecisionOn: "2026-05-25",
  articleType: "原创研究",
};

const VIEWPORTS = [
  { name: "桌面 1440", viewport: { width: 1440, height: 900 } },
  { name: "手机 375", viewport: { width: 375, height: 812 } },
] as const;

test.describe("提醒", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过提醒冒烟");
  test.skip(
    !A.email || !A.password || !B.email || !B.password,
    "没有两个已验证邮箱的测试账号（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_* 与 PATRA_E2E_VERIFIED_B_*）",
  );
  test.describe.configure({ mode: "serial" });

  for (const { name, viewport } of VIEWPORTS) {
    test(`${name}：讨论引起的提醒、304、去重、编辑新增 @、旧用户名、单楼视图、全部已读、退出`, async ({
      browser,
      request,
    }) => {
      test.setTimeout(8 * 60_000);
      const paperId = await pickPaper(request);
      const tag = `e2e-n-${Date.now()}`;
      const A1 = `${tag}-a1`;
      const B1 = `${tag}-b1`;
      const B2 = `${tag}-b2`;
      const B3 = `${tag}-b3`;
      const B4 = `${tag}-b4`;
      const A2 = `${tag}-a2`;

      const a: Session = await signIn(browser, viewport, A);
      const b: Session = await signIn(browser, viewport, B);
      const xAccount = freshAccount();
      const xContext = await browser.newContext({ viewport });
      const x = await xContext.newPage();
      let xName = xAccount.username;
      let aAt = 0;
      let bAt = 0;

      try {
        await test.step("开始前 A、B 全部已读，铃铛不挂徽标", async () => {
          await markAllRead(a.page);
          await markAllRead(b.page);
        });

        await test.step("未读数没变时请求返回 304", async () => {
          const first = await nextUnread(a.page, () => a.page.goto("/"));
          expect(first).toEqual({ status: 200, ifNoneMatch: null });
          const second = await nextUnread(a.page, () => switchBack(a.page));
          expect(second.ifNoneMatch).toMatch(/^"\d+-\d+"$/);
          expect(second.status).toBe(304);
          await expect(bell(a.page)).toHaveAccessibleName("提醒");
        });

        await test.step("X 注册（邮箱未验证、主页对别人不可见）", async () => {
          await register(x, xAccount);
        });

        await test.step("场景 1：A 发根评论质疑样本量，并 @ 自己（不提醒自己）", async () => {
          const section = await openDiscussion(a.page, paperId);
          await postRoot(section, `样本量偏小，每组只有 47 例。@${a.username} ${A1}`);
          await expect(commentWith(section, A1)).toBeVisible(ROUND_TRIP);
          aAt = Date.now();
        });

        await test.step("B 回复 A 的根评论并 @A：只有一条，归「回复我的」", async () => {
          const section = await openDiscussion(b.page, paperId);
          await replyTo(section, commentWith(section, A1), `同意，补充一点 @${a.username} ${B1}`);
          await expect(commentWith(section, B1)).toBeVisible(ROUND_TRIP);
          bAt = Date.now();
        });

        await test.step("联想里没有主页不可见的 X；完整输入 X 的用户名照样 @ 到", async () => {
          await waitSince(b.page, bAt, PUBLISH_GAP_MS);
          const section = await openDiscussion(b.page, paperId);
          const box = await openComposer(section, "写下你对这篇文献的看法、疑问或补充证据…");
          // 联想弹层在没有结果且查询词超过 6 个字时自动收起，界面上搜不到完整用户名：直接问联想用的门户接口
          const suggest = async (q: string) => {
            const res = await b.page.request.get(
              `/api/discussions/publication/${paperId}/mention-suggestions?q=${encodeURIComponent(q)}`,
            );
            expect(res.status()).toBe(200);
            return ((await res.json()) as { username: string }[]).map((item) => item.username);
          };
          expect(await suggest(a.username)).toContain(a.username);
          expect(await suggest(xName)).not.toContain(xName);
          await box.fill(`${B2} 请教 @${xName} `);
          await section.getByRole("button", { name: "发表", exact: true }).click();
          await expect(commentWith(section, B2)).toBeVisible(ROUND_TRIP);
          bAt = Date.now();
        });

        await test.step("B 编辑这条加上 @A：A 收到一条 @；再编辑（@A 还在）不再提醒", async () => {
          const section = await openDiscussion(b.page, paperId);
          for (const extra of [` @${a.username}`, " 补充"]) {
            await commentMenu(commentWith(section, B2), "编辑");
            const box = section.getByRole("combobox", { name: "评论内容" }).last();
            await expect(box).toHaveValue(new RegExp(B2), ROUND_TRIP);
            await box.fill(`${await box.inputValue()}${extra}`);
            await section.getByRole("button", { name: "保存", exact: true }).click();
            await expect(commentWith(section, B2)).toContainText(extra.trim(), ROUND_TRIP);
          }
        });

        await test.step("X 改用户名；B 用旧名 @ 仍落到 X，正文显示新名", async () => {
          const renamed = `${xName}-n`;
          await x.goto("/settings/account");
          await waitForTakeover(x, "main");
          await x.getByRole("button", { name: "修改" }).click();
          await x.getByLabel("新用户名").fill(renamed);
          await x.getByRole("button", { name: "保存" }).click();
          await expect(x.getByText(`用户名已改为 @${renamed}`)).toBeVisible(ROUND_TRIP);
          const old = xName;
          xName = renamed;
          await waitSince(b.page, bAt, PUBLISH_GAP_MS);
          const section = await openDiscussion(b.page, paperId);
          await postRoot(section, `${B3} 旧名 @${old} `);
          await expect(commentWith(section, B3)).toContainText(`@${renamed}`, ROUND_TRIP);
          bAt = Date.now();
        });

        await test.step("场景 4：A 切回页面就看到未读数", async () => {
          await a.page.goto("/");
          await expect(bell(a.page)).toBeVisible(ROUND_TRIP);
          await switchBack(a.page);
          await expect(bell(a.page)).toHaveAccessibleName("提醒，2 条未读", ROUND_TRIP);
        });

        await test.step("两栏：B 的回复在「回复我的」，编辑新增的 @ 在「@ 我的」，各一条；A 的自 @ 没有提醒", async () => {
          const replies = await openInbox(a.page, "replies");
          await expect(rowWith(replies, B1)).toHaveCount(1);
          await expect(rowWith(replies, B1)).toContainText("回复了你");
          await expect(rowWith(replies, A1)).toHaveCount(0);
          const mentions = await openInbox(a.page, "mentions");
          await expect(rowWith(mentions, B1)).toHaveCount(0);
          await expect(rowWith(mentions, B2)).toHaveCount(1);
          await expect(rowWith(mentions, B2)).toContainText("@ 了你");
          await expect(rowWith(mentions, A1)).toHaveCount(0);
        });

        await test.step("X 在「@ 我的」看到完整用户名与旧用户名的两条", async () => {
          const mentions = await openInbox(x, "mentions");
          await expect(rowWith(mentions, B2)).toHaveCount(1, ROUND_TRIP);
          await expect(rowWith(mentions, B3)).toHaveCount(1);
          await expect(rowWith(mentions, B3)).toContainText(`@${xName}`);
          await markAllRead(x);
        });

        await test.step("A 点开回复：单楼视图高亮目标；在单楼视图里回复", async () => {
          const replies = await openInbox(a.page, "replies");
          await rowWith(replies, B1).getByRole("link").click();
          await expect(a.page).toHaveURL(new RegExp(`/papers/${paperId}\\?comment=\\d+$`), {
            timeout: 60_000,
          });
          const section = a.page.locator("#discussion");
          await expect(section.locator('[aria-current="true"]')).toContainText(B1, ROUND_TRIP);
          await expect(bell(a.page)).toHaveAccessibleName("提醒，1 条未读", ROUND_TRIP);
          await waitSince(a.page, aAt, PUBLISH_GAP_MS);
          await replyTo(section, commentWith(section, B1), `谢谢补充 ${A2}`);
          await expect(commentWith(section, A2)).toBeVisible(ROUND_TRIP);
        });

        await test.step("全部标为已读后未读数归零", async () => {
          const list = await openInbox(a.page, "replies");
          await list.getByRole("button", { name: "全部标为已读" }).click();
          await expect(a.page.getByText("已全部标为已读")).toBeVisible(ROUND_TRIP);
          await expect(bell(a.page)).toHaveAccessibleName("提醒", ROUND_TRIP);
          await expect(list.getByRole("button", { name: "全部标为已读" })).toBeDisabled();
        });

        await test.step("PAP-127：B 回复后又删掉，A 的提醒随之消失、铃铛数字减回去", async () => {
          await waitSince(b.page, bAt, PUBLISH_GAP_MS);
          const section = await openDiscussion(b.page, paperId);
          await replyTo(section, commentWith(section, A1), `临时补一句 ${B4}`);
          await expect(commentWith(section, B4)).toBeVisible(ROUND_TRIP);
          bAt = Date.now();
          await a.page.goto("/");
          await switchBack(a.page);
          await expect(bell(a.page)).toHaveAccessibleName("提醒，1 条未读", ROUND_TRIP);
          await deleteOwn(commentWith(section, B4));
          const replies = await openInbox(a.page, "replies");
          await expect(rowWith(replies, B4)).toHaveCount(0, ROUND_TRIP);
          await expect(bell(a.page)).toHaveAccessibleName("提醒", ROUND_TRIP);
        });

        await test.step("B 收到 A 的回复（场景 4 的「点进去回复」）", async () => {
          const replies = await openInbox(b.page, "replies");
          await expect(rowWith(replies, A2)).toHaveCount(1, ROUND_TRIP);
        });

        await test.step("A 退出登录后铃铛消失", async () => {
          await a.page.goto("/");
          await expect(bell(a.page)).toBeVisible(ROUND_TRIP);
          if (viewport.width <= 880) {
            await a.page.getByRole("button", { name: "打开菜单" }).click();
            await a.page.getByRole("button", { name: "退出登录" }).click();
          } else {
            await a.page.getByRole("button", { name: "账号菜单" }).click();
            await a.page.getByRole("menuitem", { name: "退出登录" }).click();
          }
          await expect(a.page.getByRole("link", { name: "登录" }).first()).toBeAttached(ROUND_TRIP);
          await expect(bell(a.page)).toHaveCount(0);
        });
      } finally {
        await test.step("清理：删掉这次发的评论，A、B 全部已读", async () => {
          const remove = async (page: Page, texts: string[]) => {
            const section = await openDiscussion(page, paperId);
            for (const text of texts) {
              const target = commentWith(section, text).first();
              if (await target.isVisible().catch(() => false)) {
                await deleteOwn(target);
              }
            }
          };
          if ((await currentlySignedIn(a.page)) === false) {
            await login(a.page, A);
            await expect(a.page).toHaveURL("/", { timeout: 60_000 });
          }
          await remove(a.page, [A2]);
          await remove(b.page, [B1, B2, B3]);
          await remove(a.page, [A1]);
          await markAllRead(a.page);
          await markAllRead(b.page);
        });
        await a.close();
        await b.close();
        await xContext.close();
      }
    });
  }
});

test.describe("提醒 · 投稿经验", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过提醒冒烟");
  test.skip(
    !B.email || !B.password || !C.email || !C.password,
    "没有账号 B、C（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_B_* 与 PATRA_E2E_VERIFIED_C_*）",
  );

  // 每跑一次占 C 当天经验额度里的 1 条（删除不退额度），只在桌面跑；窄屏的经验页由 experience.spec 覆盖
  test("场景 2–4：写经验、汇总更新；在经验下追问并 @ 作者；作者收到一条「评论了你的投稿经验」并回复", async ({
    browser,
    request,
  }) => {
    test.setTimeout(6 * 60_000);
    const viewport = { width: 1440, height: 900 };
    const tag = `e2e-np-${Date.now()}`;
    const ASK = `${tag}-ask`;
    const ANSWER = `${tag}-answer`;
    const c: Session = await signIn(browser, viewport, C);
    const b: Session = await signIn(browser, viewport, B);
    const journalId = await pickEmptyJournal(request);
    let postId: string | null = null;

    /// 经验页的回复区：等它接管，收起的编辑器先点开
    const openReplies = async (page: Page, query = "") => {
      await page.goto(`/posts/${postId}${query}`, { timeout: 60_000 });
      const section = page.locator("#discussion");
      await expect(section.getByRole("heading", { name: /回复 · Replies/ })).toBeVisible(
        ROUND_TRIP,
      );
      await waitForTakeover(page, "#discussion button");
      return section;
    };
    const post = async (section: Awaited<ReturnType<typeof openReplies>>, body: string) => {
      const box = await openComposer(section, "对这条投稿经验有疑问？在这里追问…");
      await box.fill(body);
      await section.getByRole("button", { name: "发表", exact: true }).click();
    };

    try {
      await test.step("开始前 B、C 全部已读", async () => {
        await markAllRead(b.page);
        await markAllRead(c.page);
      });

      await test.step("场景 2：C 在期刊页写一条投稿经验，「同行经验」汇总随之更新", async () => {
        const section = await openExperiences(c.page, journalId);
        await expect(section.getByText("还没有同行分享这本期刊的投稿经验。")).toBeVisible();
        postId = await publishExperience(section, EXPERIENCE, "2026 年 3 月投稿");
        await expect(section.getByRole("article", { name: "同行经验" })).toContainText(
          "近 3 年有 1 条投稿经验，满 5 条后显示统计。",
          ROUND_TRIP,
        );
      });

      await test.step("场景 3：B 在 C 的经验下追问并 @C", async () => {
        const section = await openReplies(b.page);
        await post(section, `第二轮的小修大概花了多久？@${c.username} ${ASK}`);
        await expect(commentWith(section, ASK)).toBeVisible(ROUND_TRIP);
      });

      await test.step("场景 4：C 切回页面看到 1 条未读；只在「回复我的」，不在「@ 我的」", async () => {
        await c.page.goto("/");
        await expect(bell(c.page)).toBeVisible(ROUND_TRIP);
        await switchBack(c.page);
        await expect(bell(c.page)).toHaveAccessibleName("提醒，1 条未读", ROUND_TRIP);
        const replies = await openInbox(c.page, "replies");
        await expect(rowWith(replies, ASK)).toHaveCount(1);
        await expect(rowWith(replies, ASK)).toContainText("评论了你的投稿经验");
        await expect(rowWith(replies, ASK)).toContainText(/你的投稿经验 · .+ · 2026 年 3 月投稿/);
        const mentions = await openInbox(c.page, "mentions");
        await expect(rowWith(mentions, ASK)).toHaveCount(0);
      });

      await test.step("C 点开提醒：经验页的单楼视图高亮追问；在这里回复，未读归零", async () => {
        const replies = await openInbox(c.page, "replies");
        await rowWith(replies, ASK).getByRole("link").click();
        await expect(c.page).toHaveURL(new RegExp(`/posts/${postId}\\?comment=\\d+$`), {
          timeout: 60_000,
        });
        const section = c.page.locator("#discussion");
        await expect(section.locator('[aria-current="true"]')).toContainText(ASK, ROUND_TRIP);
        await expect(bell(c.page)).toHaveAccessibleName("提醒", ROUND_TRIP);
        await waitForTakeover(c.page, "#discussion button");
        await replyTo(section, commentWith(section, ASK), `大概三周 ${ANSWER}`);
        await expect(commentWith(section, ANSWER)).toBeVisible(ROUND_TRIP);
      });

      await test.step("B 收到 C 的回复", async () => {
        const replies = await openInbox(b.page, "replies");
        await expect(rowWith(replies, ANSWER)).toHaveCount(1, ROUND_TRIP);
        await expect(rowWith(replies, ANSWER)).toContainText("回复了你");
      });
    } finally {
      await test.step("清理：删掉回复、追问与经验，B、C 全部已读", async () => {
        if (postId) {
          for (const [session, text] of [
            [c, ANSWER],
            [b, ASK],
          ] as const) {
            const section = await openReplies(session.page);
            const target = commentWith(section, text).first();
            if (await target.isVisible().catch(() => false)) {
              await deleteOwn(target);
            }
          }
          await deletePost(c.page, postId);
        }
        await markAllRead(b.page);
        await markAllRead(c.page);
      });
      await b.close();
      await c.close();
    }
  });
});

/// 这个页面所在的会话还登录着吗
async function currentlySignedIn(page: Page): Promise<boolean> {
  const res = await page.request.get("/api/auth/me");
  return ((await res.json()) as { user: unknown }).user !== null;
}
