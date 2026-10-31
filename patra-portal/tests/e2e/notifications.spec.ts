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
import { helpfulButton, toggleHelpful } from "@tests/e2e/support/helpful";
import {
  bell,
  markAllRead,
  nextStream,
  nextUnread,
  openInbox,
  rowWith,
  type Session,
  STREAM_PATH,
  setVisibility,
  signIn,
  streamEnded,
  switchBack,
} from "@tests/e2e/support/notifications";

/// v0.14 提醒（PAP-126，release spec 验证场景 1、4 与 D6）：两个邮箱已验证的账号 A、B 连 mini 上的后端跑，B 兼任场景里的
/// C。两个都是用户在本机环境变量里给的账号（A 是用户的真实账号），不进仓库。另现注册一个邮箱未验证的账号 X：它是
/// 「主页对别人不可见的正常账号」，也用来改用户名。用例最后删掉自己发的评论、三个账号都全部已读；提醒不能删，A、B 的
/// 列表里会留下测试行（保留 5 个月）。场景 2–4 的投稿经验部分另一条用例：写经验用场景里的 C（专门的测试账号，不占
/// 用户真实账号与 B 的经验额度），B 在 C 的经验下追问并 @C
const A = {
  email: process.env.PATRA_E2E_VERIFIED_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_PASSWORD ?? "",
};
const B = {
  email: process.env.PATRA_E2E_VERIFIED_B_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_B_PASSWORD ?? "",
};
/// 测试账号 E（~/.patra/secrets/e2e-e.env）：v0.16 两条用例（有帮助、实时推送）里写内容、收提醒的人
const E = {
  email: process.env.PATRA_E2E_VERIFIED_E_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_E_PASSWORD ?? "",
};
/// 投稿经验用例里的作者 C：由测试账号 E 担任，免得和改 C 账号的手工验收（找回密码、换邮箱）撞在一起
const C = E;

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

        await test.step("推送连不上、回退轮询时，未读数没变的请求返回 304", async () => {
          // 连着推送时未读数由推送写进缓存、不带 ETag，切回页面的请求拿 200：304 只出现在回退轮询里，先让推送 503
          await a.page.route(`**${STREAM_PATH}`, (route) => route.fulfill({ status: 503 }));
          const first = await nextUnread(a.page, () => a.page.goto("/"));
          expect(first).toEqual({ status: 200, ifNoneMatch: null });
          const second = await nextUnread(a.page, () => switchBack(a.page));
          // ETag 的内容是后端的事，门户只把上次的原样带回去
          expect(second.ifNoneMatch).toMatch(/^".+"$/);
          expect(second.status).toBe(304);
          await expect(bell(a.page)).toHaveAccessibleName("提醒");
          await a.page.unroute(`**${STREAM_PATH}`);
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
    "没有账号 B、E（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_B_* 与 e2e-e.env 的 PATRA_E2E_VERIFIED_E_*）",
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

test.describe("提醒 · 有帮助", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过提醒冒烟");
  test.skip(
    !E.email || !E.password || !A.email || !A.password || !B.email || !B.password,
    "没有账号 A、B、E（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_*、_B_* 与 e2e-e.env 的 _E_*）",
  );
  test.describe.configure({ mode: "serial" });

  // B、A 给 E 的评论点有帮助：A 只点一下、清理时取消，不改它的设置也不发内容
  for (const { name, viewport } of VIEWPORTS) {
    test(`${name}：别人点有帮助进「有帮助」标签、不计入铃铛；同一条第二人点后合成「等 2 人」；点开去那一楼`, async ({
      browser,
      request,
    }) => {
      test.setTimeout(5 * 60_000);
      const paperId = await pickPaper(request);
      const ROOT = `e2e-nh-${Date.now()}-e`;
      const e: Session = await signIn(browser, viewport, E);
      const b: Session = await signIn(browser, viewport, B);
      const a: Session = await signIn(browser, viewport, A);
      const helpers = [b, a];
      const toggled = new Set<Session>();

      try {
        await test.step("开始前 E 全部已读", async () => {
          await markAllRead(e.page);
        });

        await test.step("E 发一条根评论，B 点有帮助", async () => {
          const section = await openDiscussion(e.page, paperId);
          await postRoot(section, `对照组的基线不平衡值得注意 ${ROOT}`);
          await expect(commentWith(section, ROOT)).toBeVisible(ROUND_TRIP);
          const other = await openDiscussion(b.page, paperId);
          await toggleHelpful(commentWith(other, ROOT));
          toggled.add(b);
        });

        await test.step("E：铃铛不挂数字，「有帮助」标签有 1 条未读，行里是 E 自己那条评论", async () => {
          const list = await openInbox(e.page, "helpful");
          const row = rowWith(list, ROOT);
          await expect(row).toHaveCount(1, ROUND_TRIP);
          await expect(row).toContainText("觉得你的评论有帮助");
          await expect(row).not.toContainText("等 2 人");
          await expect(
            e.page
              .getByRole("navigation", { name: "提醒分类" })
              .getByRole("link", { name: "有帮助" }),
          ).toHaveText(/^有帮助\s*1$/);
          await expect(bell(e.page)).toHaveAccessibleName("提醒");
          await expect(rowWith(await openInbox(e.page, "replies"), ROOT)).toHaveCount(0);
        });

        await test.step("A 也点有帮助：E 的「有帮助」仍是一行，写「等 2 人」，头像角标 +1", async () => {
          const section = await openDiscussion(a.page, paperId);
          await toggleHelpful(commentWith(section, ROOT));
          toggled.add(a);
          const list = await openInbox(e.page, "helpful");
          const row = rowWith(list, ROOT);
          await expect(row).toContainText("等 2 人觉得你的评论有帮助", ROUND_TRIP);
          await expect(row).toHaveCount(1);
          await expect(row.getByText("+1", { exact: true })).toBeVisible();
          await expect(bell(e.page)).toHaveAccessibleName("提醒");
        });

        await test.step("E 点开这行：单楼视图高亮自己的评论，「有帮助」标签不再有未读", async () => {
          const list = await openInbox(e.page, "helpful");
          await rowWith(list, ROOT).getByRole("link").click();
          await expect(e.page).toHaveURL(new RegExp(`/papers/${paperId}\\?comment=\\d+$`), {
            timeout: 60_000,
          });
          const section = e.page.locator("#discussion");
          await expect(section.locator('[aria-current="true"]')).toContainText(ROOT, ROUND_TRIP);
          await openInbox(e.page, "helpful");
          await expect(
            e.page
              .getByRole("navigation", { name: "提醒分类" })
              .getByRole("link", { name: "有帮助" }),
          ).toHaveText(/^有帮助$/, ROUND_TRIP);
        });
      } finally {
        await test.step("清理：B、A 取消有帮助，E 删掉评论并全部已读", async () => {
          for (const helper of helpers) {
            if (!toggled.has(helper)) {
              continue;
            }
            const section = await openDiscussion(helper.page, paperId);
            const target = commentWith(section, ROOT).first();
            if (
              (await target.isVisible().catch(() => false)) &&
              (await helpfulButton(target).getAttribute("aria-pressed")) === "true"
            ) {
              await toggleHelpful(target);
            }
          }
          const section = await openDiscussion(e.page, paperId);
          const target = commentWith(section, ROOT).first();
          if (await target.isVisible().catch(() => false)) {
            await deleteOwn(target);
          }
          await markAllRead(e.page);
        });
        await e.close();
        await b.close();
        await a.close();
      }
    });
  }
});

test.describe("提醒 · 实时推送", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过提醒冒烟");
  test.skip(
    !E.email || !E.password || !B.email || !B.password,
    "没有账号 B、E（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_B_* 与 e2e-e.env 的 PATRA_E2E_VERIFIED_E_*）",
  );

  // 连接管理与视口无关，只在桌面跑
  test("PAP-149：B 回复后 E 的铃铛 3 秒内变化且没有轮询；切到后台断开、切回来立即刷新；退出登录后不再连", async ({
    browser,
    request,
  }) => {
    test.setTimeout(5 * 60_000);
    const viewport = { width: 1440, height: 900 };
    const paperId = await pickPaper(request);
    const tag = `e2e-ns-${Date.now()}`;
    const ROOT = `${tag}-e`;
    const B1 = `${tag}-b1`;
    const B2 = `${tag}-b2`;
    let e: Session = await signIn(browser, viewport, E);
    const b: Session = await signIn(browser, viewport, B);
    let bAt = 0;

    /// E 页面上的未读数请求与推送连接，从挂上起记
    const polls: string[] = [];
    const streams: string[] = [];
    const watch = (session: Session) =>
      session.page.on("request", (req) => {
        if (req.url().includes("/api/me/notifications/unread-count")) {
          polls.push(req.url());
        } else if (req.url().endsWith(STREAM_PATH)) {
          streams.push(req.url());
        }
      });

    try {
      await test.step("开始前 E、B 全部已读；E 发一条根评论", async () => {
        await markAllRead(e.page);
        await markAllRead(b.page);
        const section = await openDiscussion(e.page, paperId);
        await postRoot(section, `亚组分析是否预先注册？ ${ROOT}`);
        await expect(commentWith(section, ROOT)).toBeVisible(ROUND_TRIP);
      });

      const first = await test.step("E 回到首页，推送连上（200 事件流）", async () => {
        const stream = nextStream(e.page);
        await e.page.goto("/");
        const connection = await stream;
        const response = await connection.response();
        expect(response?.status()).toBe(200);
        expect(response?.headers()["content-type"]).toContain("text/event-stream");
        await expect(bell(e.page)).toHaveAccessibleName("提醒", ROUND_TRIP);
        watch(e);
        return connection;
      });

      await test.step("B 回复 E：E 不切页面、不轮询，铃铛 3 秒内变成 1 条未读", async () => {
        const section = await openDiscussion(b.page, paperId);
        await replyTo(section, commentWith(section, ROOT), `预注册里有，见附录 ${B1}`);
        await expect(commentWith(section, B1)).toBeVisible(ROUND_TRIP);
        bAt = Date.now();
        await expect(bell(e.page)).toHaveAccessibleName("提醒，1 条未读", { timeout: 3_000 });
        expect(polls).toEqual([]);
      });

      await test.step("E 切到后台：推送连接断开；后台时 B 再回复，E 的铃铛不变、也不重连", async () => {
        const ended = streamEnded(e.page, first);
        await setVisibility(e.page, "hidden");
        await ended;
        await waitSince(b.page, bAt, PUBLISH_GAP_MS);
        const section = await openDiscussion(b.page, paperId);
        await replyTo(section, commentWith(section, ROOT), `补一句：结局也预先定义了 ${B2}`);
        await expect(commentWith(section, B2)).toBeVisible(ROUND_TRIP);
        await waitSince(e.page, Date.now(), 3_000);
        await expect(bell(e.page)).toHaveAccessibleName("提醒，1 条未读");
        expect(streams).toEqual([]);
        expect(polls).toEqual([]);
      });

      const second = await test.step("E 切回来：立刻重连，铃铛 3 秒内变成 2 条未读", async () => {
        const stream = nextStream(e.page);
        await setVisibility(e.page, "visible");
        const connection = await stream;
        await expect(bell(e.page)).toHaveAccessibleName("提醒，2 条未读", { timeout: 3_000 });
        return connection;
      });

      await test.step("E 退出登录：推送断开，之后不再连", async () => {
        const ended = streamEnded(e.page, second);
        const before = streams.length;
        await e.page.getByRole("button", { name: "账号菜单" }).click();
        await e.page.getByRole("menuitem", { name: "退出登录" }).click();
        await ended;
        await expect(e.page.getByRole("link", { name: "登录" }).first()).toBeAttached(ROUND_TRIP);
        await expect(bell(e.page)).toHaveCount(0);
        await waitSince(e.page, Date.now(), 5_000);
        expect(streams.length).toBe(before);
      });
    } finally {
      await test.step("清理：删掉两条回复与根评论，E、B 全部已读", async () => {
        if ((await currentlySignedIn(e.page)) === false) {
          await e.close();
          e = await signIn(browser, viewport, E);
        }
        const replies = await openDiscussion(b.page, paperId);
        for (const text of [B1, B2]) {
          const target = commentWith(replies, text).first();
          if (await target.isVisible().catch(() => false)) {
            await deleteOwn(target);
          }
        }
        const section = await openDiscussion(e.page, paperId);
        const target = commentWith(section, ROOT).first();
        if (await target.isVisible().catch(() => false)) {
          await deleteOwn(target);
        }
        await markAllRead(e.page);
        await markAllRead(b.page);
      });
      await e.close();
      await b.close();
    }
  });
});

/// 这个页面所在的会话还登录着吗
async function currentlySignedIn(page: Page): Promise<boolean> {
  const res = await page.request.get("/api/auth/me");
  return ((await res.json()) as { user: unknown }).user !== null;
}
