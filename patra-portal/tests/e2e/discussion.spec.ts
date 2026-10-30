import { expect, test } from "@playwright/test";
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
  openDiscussion,
  PUBLISH_GAP_MS,
  pickPaper,
  postRoot,
  REVISION_GRACE_MS,
  replyTo,
  waitSince,
} from "@tests/e2e/support/discussion";
import { publicLink, readCopied } from "@tests/e2e/support/library";

/// v0.14 文献讨论（PAP-124，release spec D4）：连 mini 上的后端跑。发评论要邮箱已验证的账号，mini 的 identity 走真实
/// 邮件服务商、新注册的账号收不到验证邮件，所以用预先验证过的测试账号 C（会公开留痕，不用用户本人的账号；凭据不进
/// 仓库，放本机环境变量）；用例最后把自己发的评论全部删掉。邮箱未验证的情形用新注册的账号
const VERIFIED = {
  email: process.env.PATRA_E2E_VERIFIED_C_EMAIL ?? "",
  password: process.env.PATRA_E2E_VERIFIED_C_PASSWORD ?? "",
};

/// 根评论：覆盖 Markdown 子集、PMID、DOI、图片与 HTML 不渲染
const ROOT_BODY = [
  "**样本量**偏小，*回顾性*设计。",
  "",
  "> 每组只有 47 例",
  "",
  "- 单中心",
  "- 前后对照",
  "",
  "用 `Kaplan–Meier` 曲线，见 [NEJM](https://www.nejm.org/)、PMID 27940189、10.1001/jama.2019.22176。",
  "",
  "![图](https://example.com/x.png) <b>粗</b>",
].join("\n");

const VIEWPORTS = [
  { name: "桌面 1440", viewport: { width: 1440, height: 900 } },
  { name: "手机 375", viewport: { width: 375, height: 812 } },
] as const;

test.describe("文献讨论", () => {
  test.skip(!process.env.PATRA_GATEWAY_BASE_URL, "未设置 PATRA_GATEWAY_BASE_URL：跳过讨论冒烟");
  test.skip(
    !VERIFIED.email || !VERIFIED.password,
    "没有已验证邮箱的测试账号（~/.patra/secrets/e2e.env 里的 PATRA_E2E_VERIFIED_C_EMAIL / PATRA_E2E_VERIFIED_C_PASSWORD）",
  );
  test.describe.configure({ mode: "serial" });

  for (const { name, viewport } of VIEWPORTS) {
    test.describe(name, () => {
      test.use({ viewport, actionTimeout: 20_000 });

      test("访客能读；邮箱未验证的账号不能发", async ({ page, browser, request }) => {
        test.setTimeout(180_000);
        const paperId = await pickPaper(request);
        const section = await openDiscussion(page, paperId);
        await expect(section.getByRole("link", { name: "登录后参与讨论" })).toBeVisible();

        const context = await browser.newContext({ viewport });
        const unverified = await context.newPage();
        await register(unverified, freshAccount());
        const own = await openDiscussion(unverified, paperId);
        await expect(own.getByText(/验证邮箱后才能发表评论。验证邮件已发到/)).toBeVisible(
          ROUND_TRIP,
        );
        await expect(own.getByRole("combobox", { name: "评论内容" })).toBeDisabled();
        await context.close();
      });

      test("发表、回复、楼中楼、删除占位、单楼视图、编辑与修改历史", async ({
        page,
        context,
        request,
      }) => {
        test.setTimeout(10 * 60_000);
        await context.grantPermissions(["clipboard-read", "clipboard-write"]);
        const paperId = await pickPaper(request);
        await login(page, VERIFIED, `/papers/${paperId}`);
        await expect(page).toHaveURL(`/papers/${paperId}`, { timeout: 60_000 });
        const section = await openDiscussion(page, paperId);
        const tag = `e2e-${Date.now()}`;
        const ROOT = `${tag}-root`;
        const FIRST = `${tag}-r1`;
        const NESTED = `${tag}-r2`;
        let rootAt = 0;

        try {
          await test.step("发根评论：Markdown 子集正确渲染，标题降成粗体，图片与 HTML 不渲染", async () => {
            await postRoot(section, `# 方法学问题\n\n${ROOT_BODY}\n\n${ROOT}`);
            rootAt = Date.now();
            const root = commentWith(section, ROOT);
            await expect(root).toBeVisible(ROUND_TRIP);
            await expect(root.locator("strong")).toHaveText(["方法学问题", "样本量"]);
            await expect(root.locator("h1")).toHaveCount(0);
            await expect(root.locator("em")).toHaveText("回顾性");
            await expect(root.locator("blockquote")).toHaveText("每组只有 47 例");
            await expect(root.locator("ul > li")).toHaveCount(2);
            await expect(root.locator("code")).toHaveText("Kaplan–Meier");
            await expect(root.getByRole("link", { name: /NEJM/ })).toHaveAttribute(
              "rel",
              "nofollow ugc noopener",
            );
            await expect(root.getByRole("link", { name: /PMID 27940189/ })).toHaveAttribute(
              "href",
              "https://pubmed.ncbi.nlm.nih.gov/27940189/",
            );
            await expect(
              root.getByRole("link", { name: /10\.1001\/jama\.2019\.22176/ }),
            ).toHaveAttribute("href", "https://doi.org/10.1001/jama.2019.22176");
            // 作者头像是 img；正文里的图片语法不能变成 img
            await expect(root.locator('img[src*="example.com"]')).toHaveCount(0);
            await expect(root).toContainText("![图](https://example.com/x.png)");
            await expect(root).toContainText("<b>粗</b>");
          });

          await test.step("10 秒内发第二条被拒，内容保留", async () => {
            await postRoot(section, "紧接着的第二条");
            await expect(section.getByText("发得太快了，请隔 10 秒再发。")).toBeVisible(ROUND_TRIP);
            await expect(section.getByRole("combobox", { name: "评论内容" }).first()).toHaveValue(
              "紧接着的第二条",
            );
            await section.getByRole("button", { name: "取消" }).first().click();
            await section.getByRole("button", { name: "放弃" }).click();
          });

          const root = commentWith(section, ROOT);
          // 只有一个已验证账号，楼中楼是自己回复自己：当作补充，不显示「回复 @X」与「查看对话」（2026-10-28 用户定）；
          // 回复别人时的「回复 @X」与「查看对话」由单测覆盖（CommentItem.test、dialogs.test）
          await test.step("回复根评论，再回复这条回复：自己回复自己不显示「回复 @X」", async () => {
            await waitSince(page, rootAt, PUBLISH_GAP_MS);
            await replyTo(section, root, `第一条回复 ${FIRST}`);
            const first = commentWith(section, `第一条回复 ${FIRST}`);
            await expect(first).toBeVisible(ROUND_TRIP);
            const firstAt = Date.now();

            await waitSince(page, firstAt, PUBLISH_GAP_MS);
            await replyTo(section, first, `楼中楼 ${NESTED}`);
            const nested = commentWith(section, `楼中楼 ${NESTED}`);
            await expect(nested).toBeVisible(ROUND_TRIP);
            await expect(nested).not.toContainText("回复 @");
            await expect(nested.getByRole("button", { name: "查看对话" })).toHaveCount(0);
          });

          await test.step("删除有回复的评论：留下占位，下面的回复写「回复已删除的评论」", async () => {
            await deleteOwn(commentWith(section, `第一条回复 ${FIRST}`));
            await expect(section.getByText("该评论已删除")).toBeVisible(ROUND_TRIP);
            await expect(commentWith(section, `楼中楼 ${NESTED}`)).toContainText(
              "回复已删除的评论",
              ROUND_TRIP,
            );
          });

          await test.step("永久链接：单楼视图高亮目标，复制链接、刷新后仍是单楼", async () => {
            await commentMenu(root, "复制链接");
            const link = await readCopied(page);
            expect(link).toMatch(
              new RegExp(`^${publicLink(page, `/papers/${paperId}`)}\\?comment=\\d+$`),
            );
            const nested = commentWith(section, `楼中楼 ${NESTED}`);
            await nested.getByRole("link", { name: /刚刚|分钟前/ }).click();
            await expect(page).toHaveURL(/\?comment=\d+$/);
            await expect(section.getByText("你正在看讨论中的一楼。")).toBeVisible(ROUND_TRIP);
            await expect(section.locator('[aria-current="true"]')).toContainText(
              `楼中楼 ${NESTED}`,
            );
            await page.reload();
            await waitForTakeover(page, "#discussion button");
            await expect(page.locator('#discussion [aria-current="true"]')).toContainText(
              `楼中楼 ${NESTED}`,
              ROUND_TRIP,
            );
            await page.locator("#discussion").getByRole("button", { name: "查看全部讨论" }).click();
            await expect(page).toHaveURL(`/papers/${paperId}`);
          });

          await test.step("发布 5 分钟后编辑：显示已编辑，修改历史有增删标记", async () => {
            await waitSince(page, rootAt, REVISION_GRACE_MS);
            const current = await openDiscussion(page, paperId);
            const target = commentWith(current, ROOT);
            await commentMenu(target, "编辑");
            const box = current.getByRole("combobox", { name: "评论内容" }).last();
            await expect(box).toHaveValue(new RegExp(ROOT), ROUND_TRIP);
            await box.fill(`${await box.inputValue()} 补充说明`);
            await current.getByRole("button", { name: "保存", exact: true }).click();
            const edited = commentWith(current, "补充说明");
            await edited.getByRole("button", { name: "已编辑" }).click();
            const history = page.getByRole("dialog", { name: "修改历史" });
            await expect(history.getByText(/^当前版本 · /)).toBeVisible(ROUND_TRIP);
            await expect(history.getByText(/^最初版本 · /)).toBeVisible();
            await expect(history.locator("ins")).toContainText("补充说明");
            await page.keyboard.press("Escape");
          });
        } finally {
          await test.step("清理：删掉这次发的评论", async () => {
            const current = await openDiscussion(page, paperId);
            for (const text of [NESTED, ROOT]) {
              const target = commentWith(current, text).first();
              if (await target.isVisible().catch(() => false)) {
                await deleteOwn(target);
              }
            }
            await expect(commentWith(current, tag)).toHaveCount(0, ROUND_TRIP);
          });
        }
      });
    });
  }
});
