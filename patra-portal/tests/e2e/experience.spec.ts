import { type Browser, expect, type Page, test } from "@playwright/test";
import {
  freshAccount,
  login,
  ROUND_TRIP,
  register,
  waitForTakeover,
} from "@tests/e2e/support/account";
import { openComposer } from "@tests/e2e/support/discussion";
import {
  days,
  deletePost,
  type ExperienceInput,
  fillExperience,
  openExperiences,
  percentile,
  pickEmptyJournal,
  publishExperience,
} from "@tests/e2e/support/experience";

/// v0.14 投稿经验（PAP-125，release spec D5）：连 mini 上的后端跑。写经验要邮箱已验证的账号，用 mini 上专门的测试账号 C
/// （在 mini 注册后只把这一行标成已验证，凭据不进仓库，放本机环境变量），不占用户真实账号的额度。每跑一次写 5 条，占 C 当天
/// 10 条额度里的 5 条（删除不退额度），同一天最多完整跑两次。用例最后把写的回复与经验全部删掉。邮箱未验证、「他人」
/// 视角用新注册的账号与未登录的浏览器
const VERIFIED = {
  email: process.env.PATRA_E2E_VERIFIED_C_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_C_PASSWORD ?? "",
};
const GATEWAY = process.env.PATRA_GATEWAY_BASE_URL?.replace(/\/+$/, "") ?? "";
const DESKTOP = { width: 1440, height: 900 };
const PHONE = { width: 375, height: 812 };

/// 5 条经验：投稿日期各不相同（同刊同投稿日期只能一条），都在近 3 年内；第 5 条匿名
const EXPERIENCES: { month: string; input: ExperienceInput }[] = [
  {
    month: "2026 年 1 月投稿",
    input: {
      submittedOn: "2026-01-05",
      firstDecisionOn: "2026-02-16",
      firstDecision: "大修",
      finalOutcome: "录用",
      finalDecisionOn: "2026-04-06",
      articleType: "原创研究",
      reviewRounds: "2",
      reviewQuality: "4",
      editorCommunication: "3",
      apcPaid: "付了",
      previouslyRejected: "否",
      body: "审稿人有两位，一位主要问**统计方法**，要求补充敏感性分析。",
    },
  },
  {
    month: "2026 年 2 月投稿",
    input: {
      submittedOn: "2026-02-02",
      firstDecisionOn: "2026-02-11",
      firstDecision: "直接拒稿",
      articleType: "综述",
    },
  },
  {
    month: "2026 年 3 月投稿",
    input: {
      submittedOn: "2026-03-02",
      firstDecisionOn: "2026-04-30",
      firstDecision: "审后拒稿",
      articleType: "病例报告",
    },
  },
  {
    month: "2026 年 4 月投稿",
    input: {
      submittedOn: "2026-04-01",
      firstDecisionOn: "2026-05-01",
      firstDecision: "小修",
      finalOutcome: "进行中",
      articleType: "短篇通讯",
    },
  },
  {
    month: "2026 年 5 月投稿",
    input: {
      submittedOn: "2026-05-04",
      firstDecisionOn: "2026-06-08",
      firstDecision: "大修",
      finalOutcome: "录用",
      articleType: "其他",
      anonymous: true,
    },
  },
];

/// 统计口径（spec 2.1.2）：首轮用时不含直接拒稿；录用率 = 录用 ÷（录用 + 拒稿）
function expectedSummary(inputs: readonly ExperienceInput[]) {
  const reviewed = inputs.filter((input) => input.firstDecision !== "直接拒稿");
  const firstRounds = reviewed.map((input) => days(input.submittedOn, input.firstDecisionOn));
  return {
    median: percentile(firstRounds, 0.5),
    p25: percentile(firstRounds, 0.25),
    p75: percentile(firstRounds, 0.75),
  };
}

let journalId = "";
let authorUserId = "";
let authorUsername = "";
/// 作者的主页对访客可见（决定能不能从他人视角看主页标签、联想里有没有他）
let authorVisible = false;
const postIds: string[] = [];
const replies: { postId: string; body: string }[] = [];

async function signedIn(browser: Browser, viewport = DESKTOP, next = "/"): Promise<Page> {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  await login(page, VERIFIED, next);
  await expect(page).not.toHaveURL(/\/login/, ROUND_TRIP);
  return page;
}

test.describe("投稿经验", () => {
  test.skip(!GATEWAY, "未设置 PATRA_GATEWAY_BASE_URL：跳过投稿经验冒烟");
  test.skip(
    !VERIFIED.email || !VERIFIED.password,
    "没有已验证邮箱的测试账号（~/.patra/secrets/e2e.env 里的 PATRA_E2E_VERIFIED_C_EMAIL / PATRA_E2E_VERIFIED_C_PASSWORD）",
  );
  test.describe.configure({ mode: "serial" });
  test.use({ viewport: DESKTOP, actionTimeout: 20_000 });

  test.afterAll(async ({ browser }) => {
    // 无论前面是否失败，都把写的回复与经验删掉（经验先删回复：删了经验之后回复不再显示）
    if (postIds.length === 0) {
      return;
    }
    const page = await signedIn(browser);
    for (const reply of replies) {
      try {
        await page.goto(`/posts/${reply.postId}`, { timeout: 60_000 });
        const item = page
          .locator("#discussion")
          .getByRole("article")
          .filter({ hasText: reply.body });
        if ((await item.count()) > 0) {
          await item.getByRole("button", { name: "评论操作" }).click();
          await page.getByRole("menuitem", { name: "删除" }).click();
          await page.getByRole("dialog").getByRole("button", { name: "删除评论" }).click();
          await expect(page.getByRole("dialog")).toBeHidden(ROUND_TRIP);
        }
      } catch (error) {
        console.error(`删除回复失败（经验 ${reply.postId}）`, error);
      }
    }
    for (const postId of postIds) {
      const res = await page.request.get(`${GATEWAY}/patra-interaction/posts/${postId}`);
      const detail = (await res.json().catch(() => null)) as { deleted?: boolean } | null;
      if (res.ok() && detail && !detail.deleted) {
        try {
          await deletePost(page, postId);
        } catch (error) {
          console.error(`删除经验 ${postId} 失败`, error);
        }
      }
    }
    await page.context().close();
  });

  test("访客：登录后写投稿经验；邮箱未验证：按钮禁用", async ({ page, browser, request }) => {
    test.setTimeout(180_000);
    journalId = await pickEmptyJournal(request);
    const section = await openExperiences(page, journalId);
    await expect(section.getByRole("link", { name: "登录后写投稿经验" }).first()).toBeVisible();
    await expect(section.getByText("还没有同行分享这本期刊的投稿经验。")).toBeVisible();
    await expect(section.getByText("还没有人分享这本期刊的投稿经验")).toBeVisible();

    const context = await browser.newContext({ viewport: DESKTOP });
    const unverified = await context.newPage();
    await register(unverified, freshAccount());
    const own = await openExperiences(unverified, journalId);
    await expect(own.getByRole("button", { name: "写投稿经验" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await expect(own.getByText("验证邮箱后才能写投稿经验")).toBeVisible();
    await context.close();
  });

  test("写 5 条：校验、联动、重复投稿日期、不到 5 条只显示条数、满 5 条显示统计", async ({
    browser,
  }) => {
    test.setTimeout(480_000);
    const page = await signedIn(browser, DESKTOP, `/journals/${journalId}`);
    const section = await openExperiences(page, journalId);

    // 校验与联动
    await section.getByRole("button", { name: "写投稿经验" }).click();
    const form = section.getByRole("form", { name: "写投稿经验" });
    await form.getByRole("button", { name: "发布投稿经验" }).click();
    for (const text of [
      "请填写投稿日期",
      "请填写首轮决定日期",
      "请选择首轮结果",
      "请选择最终结果",
      "请选择文章类型",
    ]) {
      await expect(form.getByText(text)).toBeVisible();
    }
    await form.getByLabel(/^投稿日期/).fill("2026-03-02");
    await form.getByLabel(/^首轮决定日期/).fill("2026-03-01");
    await form.getByLabel(/^首轮决定日期/).blur();
    await expect(form.getByText("首轮决定日期不能早于投稿日期")).toBeVisible();
    await form
      .getByRole("radiogroup", { name: /首轮结果/ })
      .getByText("直接拒稿", { exact: true })
      .click();
    await expect(
      form.getByRole("radiogroup", { name: /最终结果/ }).getByRole("radio", { name: "拒稿" }),
    ).toBeChecked();
    await expect(form.getByLabel(/^最终决定日期/)).toBeDisabled();
    await expect(form.getByLabel(/^审稿轮数/)).toBeDisabled();
    await form.getByRole("button", { name: "取消" }).click();
    await page
      .getByRole("dialog", { name: "放弃这条投稿经验？" })
      .getByRole("button", { name: "放弃" })
      .click();

    // 第 1 条：不到 5 条只显示条数
    const [first, ...rest] = EXPERIENCES;
    if (!first) throw new Error("没有经验数据");
    postIds.push(await publishExperience(section, first.input, first.month));
    const card = section.getByRole("article", { name: "同行经验" });
    await expect(card).toContainText("近 3 年有 1 条投稿经验，满 5 条后显示统计。", ROUND_TRIP);
    await expect(section.getByRole("link", { name: /2026 年 1 月投稿/ })).toContainText(
      "首轮 42 天 · 大修 → 录用",
    );

    // 同刊同投稿日期
    await section.getByRole("button", { name: "写投稿经验" }).click();
    const again = section.getByRole("form", { name: "写投稿经验" });
    await fillExperience(again, {
      ...first.input,
      body: undefined,
      reviewQuality: undefined,
      editorCommunication: undefined,
      apcPaid: undefined,
      previouslyRejected: undefined,
    });
    await again.getByRole("button", { name: "发布投稿经验" }).click();
    await expect(
      again.getByText("你已经写过 2026 年 1 月 5 日投这本期刊的经验，同一天的投稿只能写一条。"),
    ).toBeVisible(ROUND_TRIP);
    await expect(again.getByRole("link", { name: "查看那条经验" })).toHaveAttribute(
      "href",
      `/posts/${postIds[0]}`,
    );
    await again.getByRole("button", { name: "取消" }).click();
    await page
      .getByRole("dialog", { name: "放弃这条投稿经验？" })
      .getByRole("button", { name: "放弃" })
      .click();

    // 第 2–4 条仍不到 5 条，第 5 条（匿名）之后满 5 条
    for (const [index, item] of rest.entries()) {
      postIds.push(await publishExperience(section, item.input, item.month));
      if (index < rest.length - 1) {
        await expect(card).toContainText(`近 3 年有 ${index + 2} 条投稿经验`, ROUND_TRIP);
      }
    }
    const expected = expectedSummary(EXPERIENCES.map((item) => item.input));
    await expect(card).toContainText("首轮用时 · 中位数", ROUND_TRIP);
    await expect(card).toContainText(`${expected.median}天`);
    await expect(card).toContainText(`P25–P75 ${expected.p25}–${expected.p75} 天`);
    await expect(card).toContainText("9 天");
    await expect(card).toContainText("50%");
    await expect(card).toContainText("已完结的 4 条：录用 2、拒稿 2");
    await expect(card).toContainText("录用 2 · 拒稿 2 · 撤稿 0 · 进行中 1");
    await expect(card).toContainText("5 条经验 · 近 3 年（按投稿日期）· 首轮用时不含直接拒稿");
    await page.context().close();
  });

  test("匿名经验：他人视角没有作者、接口不返回作者 ID；作者的回复也是匿名作者；不进 @ 联想", async ({
    browser,
    page,
  }) => {
    test.setTimeout(300_000);
    const named = postIds[0] ?? "";
    const anonymous = postIds[4] ?? "";

    // 作者是谁：从作者自己的会话取（主页对他人不可见时，访客拿不到用户名）
    const me = await signedIn(browser);
    const { user } = (await (await me.request.get("/api/auth/me")).json()) as {
      user: { userId: string; username: string } | null;
    };
    authorUserId = user?.userId ?? "";
    authorUsername = user?.username ?? "";
    await me.context().close();
    expect(authorUserId).toMatch(/^\d+$/);
    expect(authorUsername).not.toBe("");

    // 访客：匿名的那条没有作者，接口里也没有作者 ID
    const list = await page.request.get(`${GATEWAY}/patra-interaction/journals/${journalId}/posts`);
    const items = (
      (await list.json()) as {
        items: { postId: string; author: { userId: string; username: string | null } | null }[];
      }
    ).items;
    const namedItem = items.find((item) => item.postId === named);
    authorVisible = Boolean(namedItem?.author?.username);
    const anonymousItem = items.find((item) => item.postId === anonymous);
    expect(anonymousItem?.author).toBeNull();
    expect(JSON.stringify(anonymousItem)).not.toContain(authorUserId);
    const detail = await page.request.get(`${GATEWAY}/patra-interaction/posts/${anonymous}`);
    expect(await detail.text()).not.toContain(authorUserId);

    const section = await openExperiences(page, journalId);
    const row = section.getByRole("link", { name: /2026 年 5 月投稿/ });
    await expect(row).toContainText("匿名作者");
    await page.goto(`/posts/${anonymous}`);
    const main = page.getByRole("article").first();
    await expect(main).toContainText("匿名作者");
    await expect(main.locator('a[href^="/u/"]')).toHaveCount(0);

    // 作者在自己的匿名经验下回复
    const author = await signedIn(browser, DESKTOP, `/posts/${anonymous}`);
    await expect(author.getByText("匿名发布 · 只有你知道是你写的")).toBeVisible(ROUND_TRIP);
    const body = `e2e 作者补充 ${Date.now()}`;
    const discussion = author.locator("#discussion");
    const box = await openComposer(discussion, "对这条投稿经验有疑问？在这里追问…");
    await box.fill(body);
    await discussion.getByRole("button", { name: "发表", exact: true }).click();
    replies.push({ postId: anonymous, body });
    const own = discussion.getByRole("article").filter({ hasText: body });
    await expect(own).toContainText("匿名作者（你）", ROUND_TRIP);
    await expect(own).toContainText("作者");
    await author.context().close();

    await page.goto(`/posts/${anonymous}`);
    const seen = page.locator("#discussion").getByRole("article").filter({ hasText: body });
    await expect(seen).toContainText("匿名作者", ROUND_TRIP);
    await expect(seen).not.toContainText("匿名作者（你）");
    await expect(seen.locator('a[href^="/u/"]')).toHaveCount(0);

    // @ 联想：另一个登录的人在匿名经验下看不到作者；实名经验下作者在最前（对照）
    const context = await browser.newContext({ viewport: DESKTOP });
    const other = await context.newPage();
    await register(other, freshAccount());
    const suggest = async (postId: string) => {
      const res = await other.request.get(`/api/discussions/post/${postId}/mention-suggestions?q=`);
      expect(res.ok()).toBe(true);
      return ((await res.json()) as { userId: string }[]).map((item) => item.userId);
    };
    expect(await suggest(anonymous)).not.toContain(authorUserId);
    // 对照：实名经验的作者排在联想最前（作者主页对他人可见时；不可见的人本来就不进联想）
    if (authorVisible) {
      expect(await suggest(named)).toContain(authorUserId);
    }
    await context.close();
  });

  test("主页标签：他人看不到匿名经验，本人看得到并标注", async ({ browser, page }) => {
    test.setTimeout(180_000);
    const anonymous = postIds[4] ?? "";
    if (authorVisible) {
      await page.goto(`/u/${authorUsername}/experiences`);
      await expect(page.getByRole("link", { name: "投稿经验" })).toHaveAttribute(
        "aria-current",
        "page",
      );
      for (const postId of postIds.slice(0, 4)) {
        await expect(page.locator(`a[href="/posts/${postId}"]`)).toBeVisible(ROUND_TRIP);
      }
      await expect(page.locator(`a[href="/posts/${anonymous}"]`)).toHaveCount(0);
    } else {
      test.info().annotations.push({
        type: "skip-part",
        description: "作者主页对访客不可见：他人视角的主页标签不可测，只测本人视角",
      });
    }

    const author = await signedIn(browser, DESKTOP, `/u/${authorUsername}/experiences`);
    const anonymousRow = author.locator(`a[href="/posts/${anonymous}"]`);
    await expect(anonymousRow).toContainText("匿名 · 仅你可见", ROUND_TRIP);
    await expect(
      author.getByText("这里列出你写过的投稿经验，匿名发布的只有你看得到。"),
    ).toBeVisible();
    await author.context().close();
  });

  test("编辑：经验页原地保存，期刊页汇总随之变化", async ({ browser }) => {
    test.setTimeout(180_000);
    const named = postIds[0] ?? "";
    const page = await signedIn(browser, DESKTOP, `/posts/${named}`);
    await waitForTakeover(page, 'button[aria-label="投稿经验操作"]');
    await page.getByRole("button", { name: "编辑", exact: true }).click();
    const form = page.getByRole("form", { name: "编辑投稿经验" });
    await expect(form.getByText("这条经验以你的名字发布，不能改成匿名。")).toBeVisible();
    await form.getByLabel(/^首轮决定日期/).fill("2026-03-02");
    await form.getByRole("button", { name: "保存修改" }).click();
    await expect(form).toBeHidden(ROUND_TRIP);
    await expect(page.getByRole("list", { name: "投稿时间线" })).toContainText("2026 年 3 月 2 日");

    const edited = EXPERIENCES.map((item, index) =>
      index === 0 ? { ...item.input, firstDecisionOn: "2026-03-02" } : item.input,
    );
    const expected = expectedSummary(edited);
    const section = await openExperiences(page, journalId);
    const card = section.getByRole("article", { name: "同行经验" });
    await expect(card).toContainText(`${expected.median}天`, ROUND_TRIP);
    await expect(card).toContainText(`P25–P75 ${expected.p25}–${expected.p75} 天`);
    await page.context().close();
  });

  test("手机 375：汇总上下排、表单单列、时间线竖排、侧栏在正文与回复之间", async ({ browser }) => {
    test.setTimeout(180_000);
    const page = await signedIn(browser, PHONE, `/journals/${journalId}`);
    const section = await openExperiences(page, journalId);
    const card = section.getByRole("article", { name: "同行经验" });
    await expect(card).toContainText("首轮用时 · 中位数");
    await section.getByRole("button", { name: "写投稿经验" }).click();
    const form = section.getByRole("form", { name: "写投稿经验" });
    const submitted = await form.getByLabel(/^投稿日期/).boundingBox();
    const firstDate = await form.getByLabel(/^首轮决定日期/).boundingBox();
    expect(firstDate && submitted && firstDate.y > submitted.y).toBe(true);
    await form.getByRole("button", { name: "取消" }).click();

    await page.goto(`/posts/${postIds[0]}`);
    const timeline = page.getByRole("list", { name: "投稿时间线" });
    await expect(timeline).toBeVisible(ROUND_TRIP);
    const nodes = timeline.getByRole("listitem");
    const one = await nodes.nth(0).boundingBox();
    const two = await nodes.nth(1).boundingBox();
    expect(one && two && two.y > one.y).toBe(true);
    const main = await page.getByRole("article").first().boundingBox();
    const rail = await page.getByRole("complementary").boundingBox();
    const replies = await page.locator("#discussion").boundingBox();
    expect(main && rail && replies && rail.y > main.y && replies.y > rail.y).toBe(true);
    await page.context().close();
  });

  test("删除：跳回期刊页经验区，汇总去掉这条", async ({ browser }) => {
    test.setTimeout(240_000);
    const page = await signedIn(browser);
    const [first, ...others] = postIds;
    if (!first) throw new Error("没有写成经验");
    await deletePost(page, first);
    await expect(page).toHaveURL(new RegExp(`/journals/${journalId}#experiences$`));
    await expect(page.getByText("投稿经验已删除")).toBeVisible();
    const card = page.getByRole("article", { name: "同行经验" });
    await expect(card).toContainText("近 3 年有 4 条投稿经验", ROUND_TRIP);
    await page.goto(`/posts/${first}`);
    await expect(page.getByRole("heading", { name: "该经验已删除" })).toBeVisible();
    await expect(page.locator("#discussion")).toHaveCount(0);
    await page.context().close();
    // 其余几条交给 afterAll（先删回复再删经验）
    expect(others.length).toBe(4);
  });
});
