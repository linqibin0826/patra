import { expect, test } from "@playwright/test";
import { ROUND_TRIP } from "@tests/e2e/support/account";
import {
  commentWith,
  deleteOwn,
  openDiscussion,
  pickPaper,
  postRoot,
  replyTo,
  waitSince,
} from "@tests/e2e/support/discussion";
import { linkAfter, localPath, mailHeaders, waitForMail } from "@tests/e2e/support/mailpit";
import {
  expectReplyMode,
  markAllRead,
  type Session,
  setReplyMode,
  signIn,
} from "@tests/e2e/support/notifications";

/// v0.16 提醒邮件与退订（PAP-148，release spec D4、D5 的门户部分）：连 mini 上的后端跑，读 mini 的 Mailpit。收信人
/// 用测试账号 E、回复人用 B（凭据在 ~/.patra/secrets/e2e.env、e2e-e.env，不进仓库）。不用账号 A：它是用户本人的真实
/// 账号，退订会改它的偏好。mini 平时经真实服务商发信，跑之前要先把 notification 切到 Mailpit（见 support/mailpit.ts）。
/// 离站提醒要等 10～11 分钟才发，这条用例跑 12～15 分钟，只在桌面跑：落地页与设置页的两种尺寸由组件测试与走查覆盖
const E = {
  email: process.env.PATRA_E2E_VERIFIED_E_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_E_PASSWORD ?? "",
};
const B = {
  email: process.env.PATRA_E2E_VERIFIED_B_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_B_PASSWORD ?? "",
};

/// 在站按推送心跳记（每 25 秒一轮）：E 关掉页面后等两轮多，免得断开前最后一轮心跳落在 B 回复之后，把 E 记成在站
const AWAY_GAP_MS = 60_000;

/// 提醒生成后等 10 分钟、之后每分钟扫一次：最晚约 11 分钟发出，留出余量
const MAIL_TIMEOUT_MS = 13 * 60_000;

test.describe("提醒邮件与退订", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过提醒邮件冒烟");
  test.skip(!process.env.PATRA_MAILPIT_BASE_URL, "未设置 PATRA_MAILPIT_BASE_URL：跳过提醒邮件冒烟");
  test.skip(
    !E.email || !E.password || !B.email || !B.password,
    "没有账号 B、E（~/.patra/secrets/e2e.env 的 PATRA_E2E_VERIFIED_B_* 与 e2e-e.env 的 PATRA_E2E_VERIFIED_E_*）",
  );

  test("离站时收到回复提醒邮件，链接回到那一楼；退订落地页与一键退订都把「回复我的」改成关", async ({
    browser,
    request,
  }) => {
    test.setTimeout(20 * 60_000);
    const viewport = { width: 1440, height: 900 };
    const paperId = await pickPaper(request);
    const tag = `e2e-m-${Date.now()}`;
    const ROOT = `${tag}-e`;
    const REPLY = `${tag}-b`;
    let e: Session = await signIn(browser, viewport, E);
    const b: Session = await signIn(browser, viewport, B);
    let eAway = false;
    let repliedAt = 0;

    try {
      await test.step("开始前 E 的「回复我的」是离站时逐条，E、B 全部已读", async () => {
        await setReplyMode(e.page, "离站时逐条");
        await markAllRead(e.page);
        await markAllRead(b.page);
      });

      await test.step("E 发一条根评论，然后关掉门户（离站）", async () => {
        const section = await openDiscussion(e.page, paperId);
        await postRoot(section, `随访时间是否足够评估远期结局？ ${ROOT}`);
        await expect(commentWith(section, ROOT)).toBeVisible(ROUND_TRIP);
        await e.close();
        eAway = true;
      });

      await test.step("等 E 的推送连接与最后一轮心跳过去，B 回复 E", async () => {
        await waitSince(b.page, Date.now(), AWAY_GAP_MS);
        const section = await openDiscussion(b.page, paperId);
        await replyTo(section, commentWith(section, ROOT), `中位随访 5 年，够看远期 ${REPLY}`);
        await expect(commentWith(section, REPLY)).toBeVisible(ROUND_TRIP);
        repliedAt = Date.now();
      });

      const mail =
        await test.step("10～12 分钟内 Mailpit 收到「B 回复了你的评论」，摘录是 B 的回复", async () => {
          const found = await waitForMail({
            to: E.email,
            subject: "回复了你的评论",
            marker: REPLY,
            after: repliedAt,
            timeoutMs: MAIL_TIMEOUT_MS,
          });
          // 先等满 10 分钟才发：提前到了说明「离站等一会儿」没生效
          expect(Date.now() - repliedAt).toBeGreaterThan(9 * 60_000);
          return found;
        });

      await test.step("「查看并回复」回到那一楼：单楼视图高亮 B 的回复", async () => {
        e = await signIn(browser, viewport, E);
        eAway = false;
        const href = localPath(linkAfter(mail.text, "查看并回复"));
        expect(href).toMatch(new RegExp(`^/papers/${paperId}\\?comment=\\d+$`));
        await e.page.goto(href, { timeout: 60_000 });
        const section = e.page.locator("#discussion");
        await expect(section.locator('[aria-current="true"]')).toContainText(REPLY, ROUND_TRIP);
      });

      await test.step("退订落地页：免登录打开不改设置，点「退订这类邮件」后 E 的设置页为关", async () => {
        const anonymous = await browser.newContext({ viewport });
        try {
          const page = await anonymous.newPage();
          const href = localPath(linkAfter(mail.text, "退订这类邮件"));
          expect(href).toMatch(/^\/unsubscribe#token=[\w-]+&category=REPLY$/);
          await page.goto(href);
          await expect(page.getByRole("heading", { name: "退订提醒邮件" })).toBeVisible(ROUND_TRIP);
          // 片段读到就从地址栏抹掉
          await expect(page).toHaveURL(/\/unsubscribe$/);
          await expectReplyMode(e.page, "离站时逐条");
          await page.getByRole("button", { name: "退订这类邮件" }).click();
          await expect(
            page.getByRole("heading", { name: "已退订「回复我的」提醒邮件" }),
          ).toBeVisible(ROUND_TRIP);
          await expectReplyMode(e.page, "关");
        } finally {
          await anonymous.close();
        }
      });

      await test.step("一键退订：邮件头的地址 GET 跳到确认页，POST（邮箱服务商的做法）返回 200 且设置页为关", async () => {
        await setReplyMode(e.page, "离站时逐条");
        const headers = await mailHeaders(mail.id);
        expect(headers["List-Unsubscribe-Post"]).toEqual(["List-Unsubscribe=One-Click"]);
        const target = /^<([^>]+)>$/.exec(headers["List-Unsubscribe"]?.[0] ?? "")?.[1] ?? "";
        const href = localPath(target);
        expect(href).toMatch(/^\/unsubscribe\/one-click\?token=[\w-]+&category=REPLY$/);
        const preview = await request.get(href, { maxRedirects: 0 });
        expect(preview.status()).toBe(303);
        expect(preview.headers().location).toMatch(/^\/unsubscribe#token=[\w-]+&category=REPLY$/);
        const res = await request.post(href, {
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          data: "List-Unsubscribe=One-Click",
          maxRedirects: 0,
        });
        expect(res.status()).toBe(200);
        await expectReplyMode(e.page, "关");
      });
    } finally {
      await test.step("清理：E 改回离站时逐条，删掉回复与根评论，E、B 全部已读", async () => {
        if (eAway) {
          e = await signIn(browser, viewport, E);
        }
        await setReplyMode(e.page, "离站时逐条");
        for (const [session, text] of [
          [b, REPLY],
          [e, ROOT],
        ] as const) {
          const section = await openDiscussion(session.page, paperId);
          const target = commentWith(section, text).first();
          if (await target.isVisible().catch(() => false)) {
            await deleteOwn(target);
          }
        }
        await markAllRead(e.page);
        await markAllRead(b.page);
      });
      await e.close();
      await b.close();
    }
  });
});
